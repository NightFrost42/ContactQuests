package com.creeperyang.contactquests.data

import com.creeperyang.contactquests.ContactQuests
import com.creeperyang.contactquests.config.ContactConfig
import com.creeperyang.contactquests.config.NpcConfigManager
import com.creeperyang.contactquests.task.ParcelTask
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

    fun init(){
        parcelReceiver.clear()
        parcelTasks.clear()
        itemTestFunc.clear()

        val questFile = ServerQuestFile.INSTANCE
        questFile.allChapters.forEach { chapter ->
            chapter.quests.forEach { quest ->
                quest.tasks.forEach { task ->
                    if (task is ParcelTask) {
                        initTask(task)
                    }
                }
            }
        }

        NpcConfigManager.syncWithQuests(parcelReceiver.keys)
    }

    fun getAvailableTargets(player: ServerPlayer): Map<String, Int> {
        val team = FTBTeamsAPI.api().manager.getTeamForPlayer(player).orElse(null) ?: return emptyMap()
        val teamData = ServerQuestFile.INSTANCE.getNullableTeamData(team.id) ?: return emptyMap()

        val available = mutableMapOf<String, Int>()

        // 读取通用开关
        val enableDelay = ContactConfig.enableDeliveryTime.get()

        for ((_, task) in parcelTasks) {
            if (teamData.canStartTasks(task.quest) && !teamData.isCompleted(task)) {
                val npcName = task.targetAddressee

                val time = if (enableDelay) NpcConfigManager.getDeliveryTime(npcName) else 0

                available[npcName] = time
            }
        }
        return available
    }

    fun initTask(parcelTask: ParcelTask) {
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

    fun completeTask(parcelTask: ParcelTask) {
        val target = parcelTask.targetAddressee
        val id = parcelTask.id
        parcelReceiver[target]?.let {
            if (it.size > 1) parcelReceiver[target]?.remove(id)
            else parcelReceiver.remove(target)
        }
        parcelTasks.remove(id)
        itemTestFunc.remove(id)
    }

    fun matchTaskItem(player: ServerPlayer, parcelStack: ItemStack, parcel: ItemContainerContents, recipientName: String): Boolean {
        val team = FTBTeamsAPI.api().manager.getTeamForPlayer(player).orElse(null)
        val teamData = team?.let { ServerQuestFile.INSTANCE.getNullableTeamData(it.id) } ?: return false

        var anyConsumed = false
        parcel.stream().forEach { itemStack ->
            if (processSingleItem(player, teamData, itemStack, parcelStack, recipientName)) {
                anyConsumed = true
            }
        }
        return anyConsumed
    }

    private fun processSingleItem(player: ServerPlayer, teamData: TeamData, initialStack: ItemStack, parcelStack: ItemStack, recipientName: String): Boolean {
        val targetTaskIds: Set<Long> = parcelReceiver[recipientName] ?: return false

        for (taskId in targetTaskIds) {
            val task = parcelTasks[taskId] ?: continue

            if (!isTaskEligible(task, teamData, initialStack)) continue

            val sendTime = calculateEffectiveSendTime(recipientName, parcelStack)

            if (executeDelivery(player, teamData, task, initialStack, sendTime, recipientName)) {
                return true
            }
        }
        return false
    }

    private fun isTaskEligible(task: ParcelTask, teamData: TeamData, initialStack: ItemStack): Boolean {
        val testFunc = itemTestFunc[task.id] ?: return false

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

    private fun executeDelivery(
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
}