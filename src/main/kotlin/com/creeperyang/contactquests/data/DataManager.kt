package com.creeperyang.contactquests.data

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
        //引入
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

    fun matchTaskItem(player: ServerPlayer, parcel: ItemContainerContents) {
        val team = FTBTeamsAPI.api().manager.getTeamForPlayer(player).orElse(null)
        val teamData = team?.let { ServerQuestFile.INSTANCE.getNullableTeamData(it.id) } ?: return // 如果没有 teamData 直接返回，减少后续判断

        parcel.stream().forEach { itemStack ->
            processSingleItem(player, teamData, itemStack)
        }
    }

    private fun processSingleItem(player: ServerPlayer, teamData: TeamData, initialStack: ItemStack) {
        var result = initialStack

        for ((lng, function) in itemTestFunc) {
            if (!function.invoke(result)) {
                continue
            }

            val task = parcelTasks[lng] ?: continue

            result = task.submitParcelTask(teamData, player, result)
            val changed = result.isEmpty

            if (changed) {
                break
            }
        }
    }
}