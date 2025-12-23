package com.creeperyang.contactquests.data

import com.creeperyang.contactquests.ContactQuests
import com.creeperyang.contactquests.config.ContactConfig
import com.creeperyang.contactquests.config.NpcConfigManager
import com.creeperyang.contactquests.task.ParcelTask
import com.creeperyang.contactquests.task.PostcardTask
import com.creeperyang.contactquests.task.RedPacketTask
import com.flechazo.contact.common.item.ParcelItem
import dev.ftb.mods.ftbquests.quest.ServerQuestFile
import dev.ftb.mods.ftbquests.quest.TeamData
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemContainerContents
import kotlin.math.min

object DataManager {
    @JvmField
    val parcelReceiver: MutableMap<String, MutableSet<Long>> = mutableMapOf()
    val parcelTasks: MutableMap<Long, ParcelTask> = mutableMapOf()
    val itemTestFunc: MutableMap<Long, (ItemStack) -> Boolean> = mutableMapOf()

    @JvmField
    val redPacketReceiver: MutableMap<String, MutableSet<Long>> = mutableMapOf()
    val redPacketTasks: MutableMap<Long, RedPacketTask> = mutableMapOf()
    val redPacketItemTestFunc: MutableMap<Long, (ItemStack) -> Boolean> = mutableMapOf()

    @JvmField
    val postcardReceiver: MutableMap<String, MutableSet<Long>> = mutableMapOf()
    val postcardTasks: MutableMap<Long, PostcardTask> = mutableMapOf()
    val postcardItemTestFunc: MutableMap<Long, (ItemStack) -> Boolean> = mutableMapOf()

    fun init(){
        parcelReceiver.clear()
        parcelTasks.clear()
        itemTestFunc.clear()

        redPacketReceiver.clear()
        redPacketTasks.clear()
        redPacketItemTestFunc.clear()

        postcardReceiver.clear()
        postcardTasks.clear()
        postcardItemTestFunc.clear()

        NpcConfigManager.reload()

        val questFile = ServerQuestFile.INSTANCE
        questFile.allChapters.forEach { chapter ->
            chapter.quests.forEach { quest ->
                quest.tasks.forEach { task ->
                    when (task) {
                        is ParcelTask -> {
                            initParcelTask(task)
                        }

                        is RedPacketTask -> {
                            initRedPacketTask(task)
                        }

                        is PostcardTask -> {
                            initPostcardTask(task)
                        }
                    }
                }
            }
        }

        NpcConfigManager.syncWithQuests(parcelReceiver.keys, redPacketReceiver.keys, postcardReceiver.keys)
    }

    fun getAvailableTargets(player: ServerPlayer): Map<String, Int> {
        val team = FTBTeamsAPI.api().manager.getTeamForPlayer(player).orElse(null) ?: return emptyMap()
        val teamData = ServerQuestFile.INSTANCE.getNullableTeamData(team.id) ?: return emptyMap()

        val available = mutableMapOf<String, Int>()

        val enableDelay = ContactConfig.enableDeliveryTime.get()

        for ((_, task) in parcelTasks) {
            if (teamData.canStartTasks(task.quest) && !teamData.isCompleted(task)) {
                val npcName = task.targetAddressee

                val time = if (enableDelay) NpcConfigManager.getDeliveryTime(npcName) else 0

                available[npcName] = time
            }
        }

        for ((_, task) in redPacketTasks) {
            if (teamData.canStartTasks(task.quest) && !teamData.isCompleted(task)) {
                val npcName = task.targetAddressee

                val time = if (enableDelay) NpcConfigManager.getDeliveryTime(npcName) else 0

                available[npcName] = time
            }
        }

        for ((_, task) in postcardTasks) {
            if (teamData.canStartTasks(task.quest) && !teamData.isCompleted(task)) {
                val npcName = task.targetAddressee

                val time = if (enableDelay) NpcConfigManager.getDeliveryTime(npcName) else 0

                available[npcName] = time
            }
        }
        return available
    }

    fun initParcelTask(parcelTask: ParcelTask) {
        val target = parcelTask.targetAddressee
        val id = parcelTask.id
        if (!parcelReceiver.containsKey(target)){
            parcelReceiver[target] = mutableSetOf(id)
        } else {
            parcelReceiver[target]!!.add(id)
        }
        parcelTasks[id] = parcelTask
        itemTestFunc[id] = parcelTask::test
    }

    fun initRedPacketTask(redPacketTask: RedPacketTask) {
        val target = redPacketTask.targetAddressee
        val id = redPacketTask.id
        if (!redPacketReceiver.containsKey(target)){
            redPacketReceiver[target] = mutableSetOf(id)
        } else {
            redPacketReceiver[target]!!.add(id)
        }
        redPacketTasks[id] = redPacketTask
        redPacketItemTestFunc[id] = redPacketTask::redPacketTest
    }

    fun initPostcardTask(postcardTask: PostcardTask) {
        val target = postcardTask.targetAddressee
        val id = postcardTask.id
        if (!postcardReceiver.containsKey(target)){
            postcardReceiver[target] = mutableSetOf(id)
        }else{
            postcardReceiver[target]!!.add(id)
        }
        postcardTasks[id] = postcardTask
        postcardItemTestFunc[id] = postcardTask::test
    }

    fun completeParcelTask(parcelTask: ParcelTask) {
        val target = parcelTask.targetAddressee
        val id = parcelTask.id
        parcelReceiver[target]?.let {
            if (it.size > 1) parcelReceiver[target]?.remove(id)
            else parcelReceiver.remove(target)
        }
        parcelTasks.remove(id)
        itemTestFunc.remove(id)
    }

    fun completeRedPacketTask(redPacketTask: RedPacketTask) {
        val target = redPacketTask.targetAddressee
        val id = redPacketTask.id
        redPacketReceiver[target]?.let {
            if (it.size > 1) redPacketReceiver[target]?.remove(id)
            else redPacketReceiver.remove(target)
        }
        redPacketTasks.remove(id)
        redPacketItemTestFunc.remove(id)
    }

    fun completePostcardTask(redPacketTask: PostcardTask) {
        val target = redPacketTask.targetAddressee
        val id = redPacketTask.id
        postcardReceiver[target]?.let {
            if (it.size > 1) postcardReceiver[target]?.remove(id)
            else postcardReceiver.remove(target)
        }
        postcardTasks.remove(id)
        postcardItemTestFunc.remove(id)
    }

    fun matchParcelTaskItem(player: ServerPlayer, parcelStack: ItemStack, parcel: ItemContainerContents, recipientName: String): Boolean {
        val team = FTBTeamsAPI.api().manager.getTeamForPlayer(player).orElse(null)
        val teamData = team?.let { ServerQuestFile.INSTANCE.getNullableTeamData(it.id) } ?: return false

        var anyConsumed = false
        parcel.stream().forEach { itemStack ->
            if (processParcelSingleItem(player, teamData, itemStack, parcelStack, recipientName)) {
                anyConsumed = true
            }
        }
        return anyConsumed
    }

    private fun processParcelSingleItem(player: ServerPlayer, teamData: TeamData, initialStack: ItemStack, parcelStack: ItemStack, recipientName: String): Boolean {
        val targetTaskIds: Set<Long> = parcelReceiver[recipientName] ?: return false

        for (taskId in targetTaskIds) {
            val task = parcelTasks[taskId] ?: continue

            if (!isParcelTaskEligible(task, teamData, initialStack)) continue

            val sendTime = calculateEffectiveSendTime(recipientName, parcelStack)

            if (executeParcelDelivery(player, teamData, task, initialStack, sendTime, recipientName)) {
                return true
            }
        }
        return false
    }

    private fun isParcelTaskEligible(task: ParcelTask, teamData: TeamData, initialStack: ItemStack): Boolean {
        val testFunc = itemTestFunc[task.id] ?: return false

        return testFunc.invoke(initialStack) &&
                teamData.canStartTasks(task.quest) &&
                !teamData.isCompleted(task)
    }

    fun matchRedPacketTaskItem(player: ServerPlayer, redPacket: ItemStack, recipientName: String): Boolean {
        val team = FTBTeamsAPI.api().manager.getTeamForPlayer(player).orElse(null)
        val teamData = team?.let { ServerQuestFile.INSTANCE.getNullableTeamData(it.id) } ?: return false

        var anyConsumed = false
        if (processRedPacketSingleItem(player, teamData, redPacket, recipientName)) {
            anyConsumed = true
        }
        return anyConsumed
    }

    private fun processRedPacketSingleItem(player: ServerPlayer, teamData: TeamData, redPacket: ItemStack, recipientName: String): Boolean {
        val targetTaskIds: Set<Long> = redPacketReceiver[recipientName] ?: return false

        for (taskId in targetTaskIds) {
            val task = redPacketTasks[taskId] ?: continue

            if (!isRedPacketTaskEligible(task, teamData, redPacket)) continue

            val sendTime = calculateEffectiveSendTime(recipientName, redPacket)

            if (executeRedPacketDelivery(player, teamData, task, redPacket, sendTime, recipientName)) {
                return true
            }
        }
        return false
    }

    private fun isRedPacketTaskEligible(task: RedPacketTask, teamData: TeamData, initialStack: ItemStack): Boolean {
        val testFunc = redPacketItemTestFunc[task.id] ?: return false

        return testFunc.invoke(initialStack) &&
                teamData.canStartTasks(task.quest) &&
                !teamData.isCompleted(task)
    }

    fun matchPostcardTaskItem(player: ServerPlayer, postcard: ItemStack, recipientName: String): Boolean {
        val team = FTBTeamsAPI.api().manager.getTeamForPlayer(player).orElse(null)
        val teamData = team?.let { ServerQuestFile.INSTANCE.getNullableTeamData(it.id) } ?: return false

        var anyConsumed = false
        if (processPostcardSingleItem(player, teamData, postcard, recipientName)) {
            anyConsumed = true
        }
        return anyConsumed
    }

    private fun processPostcardSingleItem(player: ServerPlayer, teamData: TeamData, postcard: ItemStack, recipientName: String): Boolean {
        val targetTaskIds: Set<Long> = postcardReceiver[recipientName] ?: return false

        for (taskId in targetTaskIds) {
            val task = postcardTasks[taskId] ?: continue

            if (!isPostcardEligible(task, teamData, postcard)) continue

            val sendTime = calculateEffectiveSendTime(recipientName, postcard)

            if (executePostcardDelivery(player, teamData, task, postcard, sendTime, recipientName)) {
                return true
            }
        }
        return false
    }

    private fun isPostcardEligible(task: PostcardTask, teamData: TeamData, initialStack: ItemStack): Boolean {
        val testFunc = postcardItemTestFunc[task.id] ?: return false

        return testFunc.invoke(initialStack) &&
                teamData.canStartTasks(task.quest) &&
                !teamData.isCompleted(task)
    }

    private fun calculateEffectiveSendTime(recipientName: String, parcelStack: ItemStack): Int {
        if (!ContactConfig.enableDeliveryTime.get()) {
            return 0
        }

        val configTime = NpcConfigManager.getDeliveryTime(recipientName)

        if (configTime <= 0) {
            return 0
        }

        val item = parcelStack.item
        if (item is ParcelItem && item.isEnderType) {
            ContactQuests.debug("Ender Parcel detected! Instant delivery for $recipientName.")
            return 0
        }

        return configTime
    }

    private fun executeParcelDelivery(
        player: ServerPlayer,
        teamData: TeamData,
        task: ParcelTask,
        initialStack: ItemStack,
        sendTime: Int,
        recipientName: String
    ): Boolean {
        if (sendTime > 0) {
            val overworld = player.server.overworld()

            DeliverySavedData[overworld].addParcel(player, task.id, sendTime, recipientName)

            val consumeCount = min(initialStack.count.toLong(), task.count).toInt()
            initialStack.shrink(consumeCount)
            return true
        }

        val result = task.submitParcelTask(teamData, player, initialStack)
        return result.count < initialStack.count
    }

    private fun executeRedPacketDelivery(
        player: ServerPlayer,
        teamData: TeamData,
        task: RedPacketTask,
        initialStack: ItemStack,
        sendTime: Int,
        recipientName: String
    ): Boolean {
        if (sendTime > 0) {
            val overworld = player.server.overworld()

            DeliverySavedData[overworld].addParcel(player, task.id, sendTime, recipientName)

            val consumeCount = min(initialStack.count.toLong(), task.count).toInt()
            initialStack.shrink(consumeCount)
            return true
        }

        val result = task.submitRedPacketTask(teamData, player, initialStack)
        return result.count < initialStack.count
    }

    private fun executePostcardDelivery(
        player: ServerPlayer,
        teamData: TeamData,
        task: PostcardTask,
        initialStack: ItemStack,
        sendTime: Int,
        recipientName: String
    ): Boolean {
        if (sendTime > 0) {
            val overworld = player.server.overworld()

            DeliverySavedData[overworld].addParcel(player, task.id, sendTime, recipientName)

            val consumeCount = min(initialStack.count.toLong(), task.count).toInt()
            initialStack.shrink(consumeCount)
            return true
        }

        val result = task.submitPostcardTask(teamData, player, initialStack)
        return result.count < initialStack.count
    }
}