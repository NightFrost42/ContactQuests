package com.creeperyang.contactquests.data

import com.creeperyang.contactquests.ContactQuests
import com.creeperyang.contactquests.compat.kubejs.ContactKubeJSPlugin
import com.creeperyang.contactquests.compat.kubejs.KubeJSMailQueueSavedData
import com.creeperyang.contactquests.compat.kubejs.KubeJSNpcSavedData
import com.creeperyang.contactquests.compat.kubejs.MailReceivedEventJS
import com.creeperyang.contactquests.config.ContactConfig
import com.creeperyang.contactquests.config.NpcConfigManager
import com.creeperyang.contactquests.config.NpcData
import com.creeperyang.contactquests.quest.reward.ParcelRewardBase
import com.creeperyang.contactquests.quest.task.ParcelTask
import com.creeperyang.contactquests.quest.task.PostcardTask
import com.creeperyang.contactquests.quest.task.RedPacketTask
import com.flechazo.contact.common.item.ParcelItem
import com.flechazo.contact.common.item.PostcardItem
import dev.ftb.mods.ftbquests.quest.ServerQuestFile
import dev.ftb.mods.ftbquests.quest.TeamData
import dev.ftb.mods.ftbquests.quest.task.Task
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemContainerContents
import java.util.stream.Collectors
import kotlin.math.min

object DataManager {
    @JvmField val parcelReceiver: MutableMap<String, MutableSet<Long>> = mutableMapOf()
    val parcelTasks: MutableMap<Long, ParcelTask> = mutableMapOf()
    val itemTestFunc: MutableMap<Long, (ItemStack) -> Boolean> = mutableMapOf()

    @JvmField val redPacketReceiver: MutableMap<String, MutableSet<Long>> = mutableMapOf()
    val redPacketTasks: MutableMap<Long, RedPacketTask> = mutableMapOf()
    val redPacketItemTestFunc: MutableMap<Long, (ItemStack) -> Boolean> = mutableMapOf()

    @JvmField val postcardReceiver: MutableMap<String, MutableSet<Long>> = mutableMapOf()
    val postcardTasks: MutableMap<Long, PostcardTask> = mutableMapOf()
    val postcardItemTestFunc: MutableMap<Long, (ItemStack) -> Boolean> = mutableMapOf()

    @JvmField
    val rewardSenders: MutableSet<String> = mutableSetOf()

    fun init() {
        clearAllMaps()
        NpcConfigManager.reload()

        ServerQuestFile.INSTANCE.allChapters.forEach { chapter ->
            chapter.quests.forEach { quest ->
                quest.tasks.forEach { task ->
                    when (task) {
                        is ParcelTask -> registerTask(task, task.targetAddressee, parcelReceiver, parcelTasks, itemTestFunc, task::test)
                        is RedPacketTask -> registerTask(task, task.targetAddressee, redPacketReceiver, redPacketTasks, redPacketItemTestFunc, task::test)
                        is PostcardTask -> registerTask(task, task.targetAddressee, postcardReceiver, postcardTasks, postcardItemTestFunc, task::test)
                    }
                }

                quest.rewards.forEach { reward ->
                    if (reward is ParcelRewardBase) {
                        rewardSenders.add(reward.targetAddressee)
                    }
                }
            }
        }

        NpcConfigManager.syncWithQuests(
            parcelReceiver.keys,
            redPacketReceiver.keys,
            postcardReceiver.keys,
            rewardSenders
        )
    }

    private fun clearAllMaps() {
        parcelReceiver.clear(); parcelTasks.clear(); itemTestFunc.clear()
        redPacketReceiver.clear(); redPacketTasks.clear(); redPacketItemTestFunc.clear()
        postcardReceiver.clear(); postcardTasks.clear(); postcardItemTestFunc.clear()
    }

    fun getAvailableTargets(player: ServerPlayer): Map<String, Int> {
        val available = getBaseAvailableTargets(player)
        val level = player.serverLevel()

        val kubeData = KubeJSNpcSavedData.get(level)
        kubeData.getAllNpcs().forEach { (name, config) ->
            if (config.autoFill && !available.containsKey(name)) {
                available[name] = if (ContactConfig.enableDeliveryTime.get()) config.deliveryTime else 0
            }
        }
        return available
    }

    fun getHiddenTargets(player: ServerPlayer): Map<String, Int> {
        val hidden = mutableMapOf<String, Int>()
        val level = player.serverLevel()
        val kubeData = KubeJSNpcSavedData.get(level)

        kubeData.getAllNpcs().forEach { (name, config) ->
            if (!config.autoFill) {
                hidden[name] = if (ContactConfig.enableDeliveryTime.get()) config.deliveryTime else 0
            }
        }
        return hidden
    }

    private fun getBaseAvailableTargets(player: ServerPlayer): MutableMap<String, Int> {
        val team = FTBTeamsAPI.api().manager.getTeamForPlayer(player).orElse(null) ?: return mutableMapOf()
        val teamData = ServerQuestFile.INSTANCE.getNullableTeamData(team.id) ?: return mutableMapOf()
        val enableDelay = ContactConfig.enableDeliveryTime.get()
        val available = mutableMapOf<String, Int>()
        val level = player.serverLevel()

        fun checkAndAdd(task: Task, targetName: String) {
            if (teamData.canStartTasks(task.quest) && !teamData.isCompleted(task)) {
                available[targetName] = if (enableDelay) NpcConfigManager.getDeliveryTime(targetName, level) else 0
            }
        }

        parcelTasks.values.forEach { checkAndAdd(it, it.targetAddressee) }
        redPacketTasks.values.forEach { checkAndAdd(it, it.targetAddressee) }
        postcardTasks.values.forEach { checkAndAdd(it, it.targetAddressee) }
        return available
    }

    fun completeParcelTask(task: ParcelTask) = completeTaskHelper(task, task.targetAddressee, parcelReceiver, parcelTasks, itemTestFunc)
    fun completeRedPacketTask(task: RedPacketTask) = completeTaskHelper(task, task.targetAddressee, redPacketReceiver, redPacketTasks, redPacketItemTestFunc)
    fun completePostcardTask(task: PostcardTask) = completeTaskHelper(task, task.targetAddressee, postcardReceiver, postcardTasks, postcardItemTestFunc)

    fun matchParcelTaskItem(player: ServerPlayer, parcelStack: ItemStack, parcel: ItemContainerContents, recipientName: String): Boolean {
        val level = player.serverLevel()
        var deliveryTime = if (ContactConfig.enableDeliveryTime.get()) {
            NpcConfigManager.getDeliveryTime(recipientName, level)
        } else {
            0
        }

        val item = parcelStack.item
        if (item is ParcelItem && item.isEnderType) {
            deliveryTime = 0
        }

        val allItems = parcel.stream().collect(Collectors.toList())
        if (allItems.isEmpty()) return false

        val isKubeJSNpc = KubeJSNpcSavedData.get(level).getNpcData(recipientName) != null

        if (isKubeJSNpc && deliveryTime > 0) {
            KubeJSMailQueueSavedData.get(level).addMail(
                player, recipientName, allItems, deliveryTime,
                isPostcard = false,
                isRedPacket = false
            )
            return true
        }

        val event = MailReceivedEventJS(player, recipientName, allItems, false)
        ContactKubeJSPlugin.MAIL_RECEIVED.post(event)

        if (event.isIntercepted()) return true

        val teamData = getTeamData(player) ?: return false
        var anyConsumed = false
        val rejectedItems = ArrayList<ItemStack>()
        parcel.stream().forEach { itemStack ->
            val consumed = processSingleItem(player, teamData, itemStack, parcelStack, recipientName, parcelStrategy)
            if (consumed) {
                anyConsumed = true
            } else {
                rejectedItems.add(itemStack)
            }
        }

        if (rejectedItems.isNotEmpty()) {
            CollectionSavedData.get(player.server.overworld()).addItems(player, recipientName, rejectedItems)
        }

        return anyConsumed
    }

    fun matchRedPacketTaskItem(player: ServerPlayer, redPacket: ItemStack, recipientName: String): Boolean {
        val level = player.serverLevel()
        val deliveryTime = if (ContactConfig.enableDeliveryTime.get()) {
            NpcConfigManager.getDeliveryTime(recipientName, level)
        } else {
            0
        }
        val isKubeJSNpc = KubeJSNpcSavedData.get(level)
            .getNpcData(recipientName) != null && NpcConfigManager.getNpcData(recipientName) == NpcData()

        if (isKubeJSNpc && deliveryTime > 0) {
            KubeJSMailQueueSavedData.get(level)
                .addMail(player, recipientName, listOf(redPacket), deliveryTime, isPostcard = false, isRedPacket = true)
            return true
        }

        val event = MailReceivedEventJS(player, recipientName, listOf(redPacket), false)
        ContactKubeJSPlugin.MAIL_RECEIVED.post(event)
        if (event.isIntercepted()) return true

        val teamData = getTeamData(player) ?: return false
        val consumed = processSingleItem(player, teamData, redPacket, redPacket, recipientName, redPacketStrategy)
        if (!consumed) {
            CollectionSavedData.get(player.server.overworld()).addItem(player, recipientName, redPacket)
        }
        return consumed
    }

    fun matchPostcardTaskItem(player: ServerPlayer, postcard: ItemStack, recipientName: String): Boolean {
        val level = player.serverLevel()
        var deliveryTime = if (ContactConfig.enableDeliveryTime.get()) {
            NpcConfigManager.getDeliveryTime(recipientName, level)
        } else {
            0
        }
        val isKubeJSNpc = KubeJSNpcSavedData.get(level)
            .getNpcData(recipientName) != null && NpcConfigManager.getNpcData(recipientName) == NpcData()

        val item = postcard.item
        if (item is PostcardItem && item.isEnderType) {
            deliveryTime = 0
        }

        if (isKubeJSNpc && deliveryTime > 0) {
            KubeJSMailQueueSavedData.get(level)
                .addMail(player, recipientName, listOf(postcard), deliveryTime, isPostcard = true, isRedPacket = false)
            return true
        }

        val event = MailReceivedEventJS(player, recipientName, listOf(postcard), true)
        ContactKubeJSPlugin.MAIL_RECEIVED.post(event)
        if (event.isIntercepted()) return true

        val teamData = getTeamData(player) ?: return false
        val consumed = processSingleItem(player, teamData, postcard, postcard, recipientName, postcardStrategy)
        if (!consumed) {
            CollectionSavedData.get(player.server.overworld()).addItem(player, recipientName, postcard)
        }
        return consumed
    }

    private fun getTeamData(player: ServerPlayer): TeamData? {
        val team = FTBTeamsAPI.api().manager.getTeamForPlayer(player).orElse(null) ?: return null
        return ServerQuestFile.INSTANCE.getNullableTeamData(team.id)
    }

    private fun <T : Task> registerTask(
        task: T,
        target: String,
        receiverMap: MutableMap<String, MutableSet<Long>>,
        taskMap: MutableMap<Long, T>,
        testMap: MutableMap<Long, (ItemStack) -> Boolean>,
        testFunc: (ItemStack) -> Boolean
    ) {
        receiverMap.computeIfAbsent(target) { mutableSetOf() }.add(task.id)
        taskMap[task.id] = task
        testMap[task.id] = testFunc
    }

    private fun <T : Task> completeTaskHelper(
        task: T,
        target: String,
        receiverMap: MutableMap<String, MutableSet<Long>>,
        taskMap: MutableMap<Long, T>,
        testMap: MutableMap<Long, (ItemStack) -> Boolean>
    ) {
        receiverMap[target]?.let {
            if (it.size > 1) it.remove(task.id) else receiverMap.remove(target)
        }
        taskMap.remove(task.id)
        testMap.remove(task.id)
    }

    private data class TaskStrategy<T : Task>(
        val receiverMap: Map<String, MutableSet<Long>>,
        val taskMap: Map<Long, T>,
        val testMap: Map<Long, (ItemStack) -> Boolean>,
        val submitAction: (T, TeamData, ServerPlayer, ItemStack) -> ItemStack
    )

    private val parcelStrategy = TaskStrategy(
        parcelReceiver, parcelTasks, itemTestFunc
    ) { task, td, p, stack -> task.submitParcelTask(td, p, stack) }

    private val redPacketStrategy = TaskStrategy(
        redPacketReceiver, redPacketTasks, redPacketItemTestFunc
    ) { task, td, p, stack -> task.submitRedPacketTask(td, p, stack) }

    private val postcardStrategy = TaskStrategy(
        postcardReceiver, postcardTasks, postcardItemTestFunc
    ) { task, td, p, stack -> task.submitPostcardTask(td, p, stack) }

    fun processParcelDelivery(player: ServerPlayer, items: List<ItemStack>, recipient: String) {
        val event = MailReceivedEventJS(player, recipient, items, false)
        ContactKubeJSPlugin.MAIL_RECEIVED.post(event)
    }

    fun processRedPacketDelivery(player: ServerPlayer, item: ItemStack, recipient: String) {
        val event = MailReceivedEventJS(player, recipient, listOf(item), false)
        ContactKubeJSPlugin.MAIL_RECEIVED.post(event)
    }

    fun processPostcardDelivery(player: ServerPlayer, item: ItemStack, recipient: String) {
        val event = MailReceivedEventJS(player, recipient, listOf(item), true)
        ContactKubeJSPlugin.MAIL_RECEIVED.post(event)
    }

    private fun <T : Task> processSingleItem(
        player: ServerPlayer,
        teamData: TeamData,
        initialStack: ItemStack,
        contextStack: ItemStack,
        recipientName: String,
        strategy: TaskStrategy<T>
    ): Boolean {
        val targetTaskIds: Set<Long> = strategy.receiverMap[recipientName] ?: return false

        for (taskId in targetTaskIds) {
            val task = strategy.taskMap[taskId] ?: continue
            val testFunc = strategy.testMap[taskId] ?: continue

            if (task is PostcardTask) {
                task.setContext(player, teamData)
            } else if (task is RedPacketTask) {
                task.setContext(player, teamData)
            }

            val isMatch = try {
                testFunc(initialStack)
            } finally {
                if (task is PostcardTask) {
                    task.clearContext()
                } else if (task is RedPacketTask) {
                    task.clearContext()
                }
            }

            if (!isMatch || !teamData.canStartTasks(task.quest) || teamData.isCompleted(task)) {
                continue
            }

            val sendTime = calculateEffectiveSendTime(player.serverLevel(), recipientName, contextStack)

            if (executeDelivery(player, teamData, task, initialStack, sendTime, recipientName, strategy.submitAction)) {
                return true
            }
        }

        return false
    }

    private fun <T : Task> executeDelivery(
        player: ServerPlayer,
        teamData: TeamData,
        task: T,
        stackToSubmit: ItemStack,
        sendTime: Int,
        recipientName: String,
        submitAction: (T, TeamData, ServerPlayer, ItemStack) -> ItemStack
    ): Boolean {
        if (sendTime > 0) {
            val overworld = player.server.overworld()
            TaskDeliverySavedData[overworld].addParcel(player, task.id, sendTime, recipientName)

            val taskCount = when (task) {
                is ParcelTask -> task.count
                is RedPacketTask -> task.count
                is PostcardTask -> task.count
                else -> 1L
            }
            val consumeCount = min(stackToSubmit.count.toLong(), taskCount).toInt()

            stackToSubmit.shrink(consumeCount)
            return true
        }

        val resultStack = submitAction(task, teamData, player, stackToSubmit)

        return resultStack.count < stackToSubmit.count
    }

    private fun calculateEffectiveSendTime(level: ServerLevel, recipientName: String, contextStack: ItemStack): Int {
        if (!ContactConfig.enableDeliveryTime.get()) return 0

        val configTime = NpcConfigManager.getDeliveryTime(recipientName, level)
        if (configTime <= 0) return 0

        val item = contextStack.item
        if (item is ParcelItem && item.isEnderType) {
            ContactQuests.debug("检测到末影包裹！$recipientName 即时送达。")
            return 0
        } else if (item is PostcardItem && item.isEnderType) {
            ContactQuests.debug("检测到末影明信片！$recipientName 即时送达。")
            return 0
        }

        return configTime
    }
}