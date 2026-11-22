package com.creeperyang.contactquests.data

import com.creeperyang.contactquests.ContactQuests
import com.creeperyang.contactquests.quest.task.ParcelTask
import com.creeperyang.contactquests.quest.task.RedPacketTask
import com.creeperyang.contactquests.quest.task.ContactTask
import dev.ftb.mods.ftbquests.quest.ServerQuestFile
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.saveddata.SavedData
import java.util.*

class TaskDeliverySavedData : SavedData() {
    data class PendingParcel(
        val playerUUID: UUID,
        val taskId: Long,
        var ticksLeft: Int,
        val target: String,
        val amount: Long,
        val teamId: UUID?
    )

    private val pendingParcels = Collections.synchronizedList(ArrayList<PendingParcel>())

    fun addParcel(player: ServerPlayer, taskId: Long, duration: Int, target: String, amount: Long, teamId: UUID) {
        require(amount > 0) { "A task delivery must contain a positive submitted amount" }
        val entry = PendingParcel(player.uuid, taskId, duration, target, amount, teamId)
        pendingParcels.add(entry)
        setDirty()
        ContactQuests.debug("为${player.name.string}添加包裹, 任务id $taskId, 剩余时间 $duration ticks")
    }

    fun getPendingAmount(teamId: UUID, taskId: Long): Long = synchronized(pendingParcels) {
        pendingParcels.asSequence()
            .filter { it.teamId == teamId && it.taskId == taskId }
            .fold(0L) { total, parcel -> total + parcel.amount.coerceAtMost(Long.MAX_VALUE - total) }
    }

    fun tick(level: ServerLevel) {
        if (pendingParcels.isEmpty()) return

        val notifiedThisTick = mutableSetOf<Pair<UUID, String>>()

        val iterator = pendingParcels.iterator()
        while (iterator.hasNext()) {
            val parcel = iterator.next()
            if (parcel.ticksLeft > 0) {
                parcel.ticksLeft--
                setDirty()
            }

            if (parcel.ticksLeft <= 0) {
                if (completeDelivery(level, parcel, notifiedThisTick)) {
                    iterator.remove()
                    setDirty()
                } else {
                    // 遇到临时任务锁定或团队数据暂未就绪时保留已受理的数量，延后重试而不能丢单。
                    parcel.ticksLeft = 200;
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

        val team = if (parcel.teamId != null) {
            teamManager.getTeamByID(parcel.teamId).orElse(null)
        } else {
            teamManager.getTeamForPlayerID(parcel.playerUUID).orElse(null)
        }

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

        if (teamData.isLocked) return false

        val task = questFile.getTask(parcel.taskId)

        if (task !is ContactTask) {
            ContactQuests.warn("任务已从文件中删除或 ID 已变更 (Task ID: ${parcel.taskId})")
            return true
        }

        val countToSubmit = parcel.amount.coerceAtMost(task.getAmountNeeded(teamData))
        if (countToSubmit > 0) {
            teamData.addProgress(task, countToSubmit)
        }

        val player = level.server.playerList.getPlayer(parcel.playerUUID)

        if (player != null) {
            val notificationKey = Pair(parcel.playerUUID, parcel.target)

            if (!notifiedTargets.contains(notificationKey)) {
                val addresseeName = if (task is ParcelTask) task.targetAddressee else if (task is RedPacketTask) task.targetAddressee else parcel.target

                player.sendSystemMessage(
                    net.minecraft.network.chat.Component.translatable("contactquests.message.mail_delivered", addresseeName)
                        .withStyle(net.minecraft.ChatFormatting.GREEN)
                )

                notifiedTargets.add(notificationKey)
            }
        }

        return true
    }

    override fun save(tag: CompoundTag): CompoundTag {
        val list = ListTag()
        synchronized(pendingParcels) {
            for (p in pendingParcels) {
                val pTag = CompoundTag()
                pTag.putUUID("uuid", p.playerUUID)
                pTag.putLong("taskId", p.taskId)
                pTag.putInt("ticks", p.ticksLeft)
                pTag.putString("target", p.target)
                pTag.putLong("amount", p.amount)
                if (p.teamId != null) pTag.putUUID("team", p.teamId)
                list.add(pTag)
            }
        }
        tag.put("PendingParcels", list)
        tag.putInt("DeliveryVersion", 2)
        return tag
    }

    companion object {
        operator fun get(level: ServerLevel): TaskDeliverySavedData {
            val storage = level.server.overworld().dataStorage
            return storage.computeIfAbsent(
                ::load,
                ::TaskDeliverySavedData,
                "contactquests_deliveries"
            )
        }

        fun load(tag: CompoundTag): TaskDeliverySavedData {
            val data = TaskDeliverySavedData()
            val list = tag.getList("PendingParcels", Tag.TAG_COMPOUND.toInt())
            for (i in 0 until list.size) {
                val pTag = list.getCompound(i)
                // 旧存档未记录单包裹实际提交数量；按 1 个保守记账，防止错误放大为 task.count 导致刷进度。
                val amount = if (pTag.contains("amount", Tag.TAG_ANY_NUMERIC.toInt())) pTag.getLong("amount") else 1L
                if (amount <= 0 || !pTag.hasUUID("uuid")) {
                    ContactQuests.warn("Skipping an invalid pending task delivery")
                    continue
                }
                if (!pTag.contains("amount")) {
                    ContactQuests.warn("Legacy task delivery ${pTag.getLong("taskId")} has no quantity; crediting one unit")
                }
                data.pendingParcels.add(PendingParcel(
                    pTag.getUUID("uuid"),
                    pTag.getLong("taskId"),
                    pTag.getInt("ticks"),
                    pTag.getString("target"),
                    amount,
                    if (pTag.hasUUID("team")) pTag.getUUID("team") else null
                ))
            }
            return data
        }
    }
}
