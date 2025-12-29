package com.creeperyang.contactquests.data

import com.creeperyang.contactquests.ContactQuests
import com.creeperyang.contactquests.quest.task.ParcelTask
import com.creeperyang.contactquests.quest.task.RedPacketTask
import dev.ftb.mods.ftbquests.quest.ServerQuestFile
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.world.level.saveddata.SavedData
import java.util.*

class TaskDeliverySavedData : SavedData() {
    data class PendingParcel(
        val playerUUID: UUID,
        val taskId: Long,
        var ticksLeft: Int,
        val target: String
    )

    private val pendingParcels = Collections.synchronizedList(ArrayList<PendingParcel>())

    fun addParcel(player: ServerPlayer, taskId: Long, duration: Int, target: String) {
        val entry = PendingParcel(player.uuid, taskId, duration, target)
        pendingParcels.add(entry)
        setDirty()
        ContactQuests.debug("为${player.name.string}添加包裹, 任务id $taskId, 剩余时间 $duration ticks")
    }

    fun tick(level: ServerLevel) {
        if (pendingParcels.isEmpty()) return

        val notifiedThisTick = mutableSetOf<Pair<UUID, String>>()

        val iterator = pendingParcels.iterator()
        while (iterator.hasNext()) {
            val parcel = iterator.next()
            parcel.ticksLeft--

            if (parcel.ticksLeft <= 0) {
                if (completeDelivery(level, parcel, notifiedThisTick)) {
                    iterator.remove()
                    setDirty()
                } else {
                    ContactQuests.warn("未能将包裹送达${parcel.target}，已从队列中移除。")
                    iterator.remove()
                    setDirty()
                }
            }
            setDirty()
        }
    }

    private fun completeDelivery(
        level: ServerLevel,
        parcel: PendingParcel,
        notifiedTargets: MutableSet<Pair<UUID, String>>
    ) :Boolean {
        val teamManager = FTBTeamsAPI.api().manager

        val team = teamManager.getTeamForPlayerID(parcel.playerUUID).orElse(null)

        if (team == null) {
            ContactQuests.warn("未找到玩家UUID的队伍：${parcel.playerUUID}")
            return false
        }

        val questFile = ServerQuestFile.INSTANCE
        if (questFile == null) {
            ContactQuests.warn("ServerQuestFile 还没加载。")
            return false
        }

        val teamData = questFile.getNullableTeamData(team.id)

        if (teamData == null) {
            ContactQuests.warn("任务 Team未找到团队数据：${team.id}")
            return false
        }

        val task = questFile.getTask(parcel.taskId)

        if (task == null) {
            ContactQuests.warn("任务已从文件中删除或 ID 已变更 (Task ID: ${parcel.taskId})")
            return true
        }

        val countToSubmit = if (task is ParcelTask) task.count else if (task is RedPacketTask) task.count else 1L

        teamData.addProgress(task, countToSubmit)

        val player = level.server.playerList.getPlayer(parcel.playerUUID)

        if (player != null) {
            val notificationKey = Pair(parcel.playerUUID, parcel.target)

            if (!notifiedTargets.contains(notificationKey)) {
                val addresseeName = if (task is ParcelTask) task.targetAddressee else if (task is RedPacketTask) task.targetAddressee else parcel.target

                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a包裹已送达: $addresseeName"))

                notifiedTargets.add(notificationKey)
            }
        }

        return true
    }

    override fun save(tag: CompoundTag, provider: HolderLookup.Provider): CompoundTag {
        val list = ListTag()
        synchronized(pendingParcels) {
            for (p in pendingParcels) {
                val pTag = CompoundTag()
                pTag.putUUID("uuid", p.playerUUID)
                pTag.putLong("taskId", p.taskId)
                pTag.putInt("ticks", p.ticksLeft)
                pTag.putString("target", p.target)
                list.add(pTag)
            }
        }
        tag.put("PendingParcels", list)
        return tag
    }

    companion object {
        operator fun get(level: ServerLevel): TaskDeliverySavedData {
            val storage = level.dataStorage
            return storage.computeIfAbsent(
                Factory(::TaskDeliverySavedData, ::load, DataFixTypes.LEVEL),
                "contactquests_deliveries"
            )
        }

        private fun load(tag: CompoundTag, provider: HolderLookup.Provider): TaskDeliverySavedData {
            val data = TaskDeliverySavedData()
            val list = tag.getList("PendingParcels", Tag.TAG_COMPOUND.toInt())
            for (i in 0 until list.size) {
                val pTag = list.getCompound(i)
                data.pendingParcels.add(PendingParcel(
                    pTag.getUUID("uuid"),
                    pTag.getLong("taskId"),
                    pTag.getInt("ticks"),
                    pTag.getString("target")
                ))
            }
            return data
        }
    }
}