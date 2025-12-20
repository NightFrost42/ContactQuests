package com.creeperyang.contactquests.data

import com.creeperyang.contactquests.ContactQuests
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

class DeliverySavedData: SavedData() {
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
        ContactQuests.debug("Parcel added for ${player.name.string}, task $taskId, duration $duration ticks")
    }

    fun tick(level: ServerLevel) {
        if (pendingParcels.isEmpty()) return

        val iterator = pendingParcels.iterator()
        while (iterator.hasNext()) {
            val parcel = iterator.next()
            parcel.ticksLeft--

            if (parcel.ticksLeft <= 0) {
                if (completeDelivery(level, parcel)) {
                    iterator.remove()
                    setDirty()
                } else {
                    ContactQuests.warn("Failed to deliver parcel to ${parcel.target}, removing from queue.")
                    iterator.remove()
                    setDirty()
                }
            }

            setDirty()
        }
    }

    private fun completeDelivery(level: ServerLevel, parcel: PendingParcel) :Boolean{
        val teamManager = FTBTeamsAPI.api().manager

        val team = teamManager.getTeamForPlayerID(parcel.playerUUID).orElse(null)

        if (team == null) {
            ContactQuests.warn("Team not found for player UUID: ${parcel.playerUUID}")
            return false
        }

        val teamData = ServerQuestFile.INSTANCE.getNullableTeamData(team.id)

        if (teamData == null) {
            ContactQuests.warn("Quest TeamData not found for team: ${team.id}")
            return false
        }

        val task = DataManager.parcelTasks[parcel.taskId]

        if (task == null) {
            ContactQuests.warn("访问的任务已经不存在")
            return true
        }

        teamData.addProgress(task, task.count)

        val player = level.server.playerList.getPlayer(parcel.playerUUID)
        player?.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a包裹已送达: ${task.targetAddressee}"))
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
        operator fun get(level: ServerLevel): DeliverySavedData {
            val storage = level.dataStorage
            return storage.computeIfAbsent(
                Factory(::DeliverySavedData, ::load, DataFixTypes.LEVEL),
                "contactquests_deliveries"
            )
        }

        private fun load(tag: CompoundTag, provider: HolderLookup.Provider): DeliverySavedData {
            val data = DeliverySavedData()
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