package com.creeperyang.contactquests.data

import com.creeperyang.contactquests.ContactQuests
import com.creeperyang.contactquests.task.ParcelTask
import dev.ftb.mods.ftbquests.quest.ServerQuestFile
import dev.ftb.mods.ftbquests.quest.TeamData
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemContainerContents

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

        val received = parcelReceiver.keys
        ContactQuests.debug("已加载的收件人$received")
        ContactQuests.debug("$parcelTasks")
        ContactQuests.debug("$itemTestFunc")
    }

    fun getAvailableTargets(player: ServerPlayer): Set<String> {
        val team = FTBTeamsAPI.api().manager.getTeamForPlayer(player).orElse(null) ?: return emptySet()
        val teamData = ServerQuestFile.INSTANCE.getNullableTeamData(team.id) ?: return emptySet()

        val available = mutableSetOf<String>()

        for ((_, task) in parcelTasks) {
            if (teamData.canStartTasks(task.quest) && !teamData.isCompleted(task)) {
                available.add(task.targetAddressee)
            }
        }

        return available
    }

    fun getSendTime(target: String): Int{
        //TODO: 未来这里要可以使用json配置
        return 60
    }

    fun initTask(parcelTask: ParcelTask) {
        val target = parcelTask.targetAddressee
        val id = parcelTask.id
        if (!parcelReceiver.containsKey(target)){
            parcelReceiver[target] = mutableSetOf(id)
        }
        else {
            parcelReceiver[target]!!.add(id)
        }
        parcelTasks[id] = parcelTask
        val testFunc = parcelTask::test
        itemTestFunc[id] = testFunc
    }

    fun completeTask(parcelTask: ParcelTask) {
        val target = parcelTask.targetAddressee
        val id = parcelTask.id
        parcelReceiver[target]?.let {
            if ( it.size > 1)
                parcelReceiver[target]?.remove(id)
            else
                parcelReceiver.remove(target)
        }
        parcelTasks.remove(id)
        itemTestFunc.remove(id)
    }

    fun matchTaskItem(player: ServerPlayer, parcel: ItemContainerContents, recipientName: String) {
        val team = FTBTeamsAPI.api().manager.getTeamForPlayer(player).orElse(null)
        val teamData = team?.let { ServerQuestFile.INSTANCE.getNullableTeamData(it.id) } ?: return // 如果没有 teamData 直接返回，减少后续判断

        parcel.stream().forEach { itemStack ->
            processSingleItem(player, teamData, itemStack, recipientName)
        }
    }

    private fun processSingleItem(player: ServerPlayer, teamData: TeamData, initialStack: ItemStack, recipientName: String) {
        var result = initialStack

        val targetTaskIds: Set<Long> = parcelReceiver[recipientName] ?: return

        for (taskId in targetTaskIds) {
            val task = parcelTasks[taskId] ?: continue
            val testFunc = itemTestFunc[taskId] ?: continue

            if (!testFunc.invoke(result)) {
                continue
            }

            if (!teamData.canStartTasks(task.quest)) {
                ContactQuests.debug("Task ${task.id} matches item but is locked.")
                continue
            }

            if (teamData.isCompleted(task)) {
                continue
            }

            // 4. 提交
            ContactQuests.debug("Submitting item to task ${task.id}")
            result = task.submitParcelTask(teamData, player, result)

            val changed = result.isEmpty
            if (changed) {
                break
            }
        }
    }
}