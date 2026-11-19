package com.creeperyang.contactquests.data

import com.creeperyang.contactquests.config.NpcConfigManager
import com.creeperyang.contactquests.llm.NpcLlmConfig
import com.creeperyang.contactquests.quest.reward.ParcelRewardBase
import com.creeperyang.contactquests.quest.task.ParcelTask
import com.creeperyang.contactquests.quest.task.PostcardTask
import com.creeperyang.contactquests.quest.task.RedPacketTask
import dev.ftb.mods.ftbquests.quest.ServerQuestFile
import dev.ftb.mods.ftbquests.quest.task.Task
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemContainerContents
import java.util.stream.Collectors

/** 邮件任务索引注册表与收信分发总路由，对外维护任务映射缓存并将目标解析与分配委托给专门子模块。 */
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

    @JvmField
    var targetsDirty: Boolean = true

    private val parcelStrategy = TaskMailAllocator.TaskStrategy(parcelReceiver, parcelTasks)
    private val redPacketStrategy = TaskMailAllocator.TaskStrategy(redPacketReceiver, redPacketTasks)
    private val postcardStrategy = TaskMailAllocator.TaskStrategy(postcardReceiver, postcardTasks)

    fun ensureTargetsFresh() {
        if (targetsDirty && ServerQuestFile.INSTANCE != null) init()
    }

    fun init() {
        val file = ServerQuestFile.INSTANCE ?: return
        clearAllMaps()
        NpcConfigManager.reload()

        file.allChapters.forEach { chapter ->
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
        // 若此前中途返回，保持 targetsDirty 为 true，以便后续查询时继续尝试重建索引。
        targetsDirty = false
    }

    private fun clearAllMaps() {
        parcelReceiver.clear(); parcelTasks.clear(); itemTestFunc.clear()
        redPacketReceiver.clear(); redPacketTasks.clear(); redPacketItemTestFunc.clear()
        postcardReceiver.clear(); postcardTasks.clear(); postcardItemTestFunc.clear()
        rewardSenders.clear()
    }

    fun getAvailableTargets(player: ServerPlayer): Map<String, Int> =
        NpcTargetResolver.getAvailableTargets(player)

    fun isChoiceAddress(name: String): Boolean = NpcTargetResolver.isChoiceAddress(name)

    fun receiveChoiceCard(player: ServerPlayer, card: ItemStack, address: String): ItemStack =
        NpcTargetResolver.receiveChoiceCard(player, card, address)

    fun getHiddenTargets(player: ServerPlayer): Map<String, Int> =
        NpcTargetResolver.getHiddenTargets(player)

    fun acceptsNpcMail(player: ServerPlayer, name: String): Boolean =
        NpcTargetResolver.acceptsNpcMail(player, name)

    fun completeParcelTask(task: ParcelTask) =
        completeTaskHelper(task, task.targetAddressee, parcelReceiver, parcelTasks, itemTestFunc)

    fun completeRedPacketTask(task: RedPacketTask) =
        completeTaskHelper(task, task.targetAddressee, redPacketReceiver, redPacketTasks, redPacketItemTestFunc)

    fun completePostcardTask(task: PostcardTask) =
        completeTaskHelper(task, task.targetAddressee, postcardReceiver, postcardTasks, postcardItemTestFunc)

    fun matchParcelTaskItem(
        player: ServerPlayer,
        parcelStack: ItemStack,
        parcel: ItemContainerContents,
        recipientName: String
    ): Boolean {
        ensureTargetsFresh()
        val allItems = parcel.stream().filter { !it.isEmpty }.map { it.copy() }.collect(Collectors.toList())
        if (allItems.isEmpty()) return false
        val teamData = NpcTargetResolver.getTeamData(player)
        val remainder = if (teamData != null) {
            TaskMailAllocator.allocateTasks(player, teamData, allItems, parcelStack, recipientName, parcelStrategy)
        } else {
            allItems
        }
        val consumed = remainder.sumOf { it.count.toLong() } < allItems.sumOf { it.count.toLong() }
        val delay = TaskMailAllocator.calculateEffectiveSendTime(player.serverLevel(), recipientName, parcelStack)
        if (NpcLlmConfig.npc(recipientName) != null) {
            TaskMailAllocator.dispatchToLlmNpc(player, recipientName, delay, allItems, remainder, isPostcard = false, isRedPacket = false)
            return true
        }
        val received = TaskMailAllocator.receiveRemainder(player, recipientName, remainder, delay, isPostcard = false, isRedPacket = false)
        return consumed || received
    }

    fun matchRedPacketTaskItem(player: ServerPlayer, redPacket: ItemStack, recipientName: String): Boolean {
        ensureTargetsFresh()
        val teamData = NpcTargetResolver.getTeamData(player)
        val remainder = if (teamData != null) {
            TaskMailAllocator.allocateTasks(player, teamData, listOf(redPacket), redPacket, recipientName, redPacketStrategy)
        } else {
            listOf(redPacket.copy())
        }
        val consumed = remainder.isEmpty() || !ItemStack.matches(redPacket, remainder.first())
        val delay = TaskMailAllocator.calculateEffectiveSendTime(player.serverLevel(), recipientName, redPacket)
        if (NpcLlmConfig.npc(recipientName) != null) {
            TaskMailAllocator.dispatchToLlmNpc(player, recipientName, delay, listOf(redPacket.copy()), remainder, isPostcard = false, isRedPacket = true)
            return true
        }
        val received = TaskMailAllocator.receiveRemainder(player, recipientName, remainder, delay, isPostcard = false, isRedPacket = true)
        return consumed || received
    }

    fun matchPostcardTaskItem(player: ServerPlayer, postcard: ItemStack, recipientName: String): Boolean {
        ensureTargetsFresh()
        val teamData = NpcTargetResolver.getTeamData(player)
        val remainder = if (teamData != null) {
            TaskMailAllocator.allocateTasks(player, teamData, listOf(postcard), postcard, recipientName, postcardStrategy)
        } else {
            listOf(postcard.copy())
        }
        val consumed = remainder.sumOf { it.count } < postcard.count
        val delay = TaskMailAllocator.calculateEffectiveSendTime(player.serverLevel(), recipientName, postcard)
        if (NpcLlmConfig.npc(recipientName) != null) {
            TaskMailAllocator.dispatchToLlmNpc(player, recipientName, delay, listOf(postcard.copy()), remainder, isPostcard = true, isRedPacket = false)
            return true
        }
        val received = TaskMailAllocator.receiveRemainder(player, recipientName, remainder, delay, isPostcard = true, isRedPacket = false)
        return consumed || received
    }

    fun processParcelDelivery(player: ServerPlayer, items: List<ItemStack>, recipient: String) =
        TaskMailAllocator.dispatchRemainder(player, recipient, items, false)

    fun processRedPacketDelivery(player: ServerPlayer, item: ItemStack, recipient: String) =
        TaskMailAllocator.dispatchRemainder(player, recipient, listOf(item), false)

    fun processPostcardDelivery(player: ServerPlayer, item: ItemStack, recipient: String) =
        TaskMailAllocator.dispatchRemainder(player, recipient, listOf(item), true)

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
}
