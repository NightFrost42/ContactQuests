package com.creeperyang.contactquests.data

import com.creeperyang.contactquests.config.ContactConfig
import com.flechazo.contact.common.handler.MailboxManager
import com.flechazo.contact.platform.PlatformHelper
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.saveddata.SavedData
import java.util.*

class RewardDeliverySavedData : SavedData() {

    data class PendingReward(
        val playerUUID: UUID,
        val parcelStack: ItemStack,
        var ticksLeft: Int,
        val sender: String,
        val teamId: UUID?,
        val preserveSender: Boolean = false
    )

    private val pendingRewards = Collections.synchronizedList(ArrayList<PendingReward>())
    private val migratedDimensions = HashSet<String>()

    @JvmOverloads
    fun addPendingReward(playerUUID: UUID, parcel: ItemStack, delay: Int, sender: String,
                         teamId: UUID? = FTBTeamsAPI.api().manager.getTeamForPlayerID(playerUUID).orElse(null)?.id,
                         preserveSender: Boolean = false) {
        if (parcel.isEmpty) return
        pendingRewards.add(PendingReward(playerUUID, parcel.copy(), delay.coerceAtLeast(0), sender, teamId, preserveSender))
        setDirty()
    }

    fun tick(level: ServerLevel) {
        if (level !== level.server.overworld()) return
        if (pendingRewards.isEmpty()) return

        val iterator = pendingRewards.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            entry.ticksLeft--
            setDirty()

            if (entry.ticksLeft <= 0) {
                val result = tryDeliver(level, entry)

                if (result == DeliveryResult.SUCCESS) {
                    iterator.remove()
                    setDirty()
                } else {
                    entry.ticksLeft = ContactConfig.retryInterval.get()
                    setDirty()
                }
            }
        }
    }

    enum class DeliveryResult { SUCCESS, FAIL }

    private fun tryDeliver(level: ServerLevel, entry: PendingReward): DeliveryResult {
        val teamManager = FTBTeamsAPI.api().manager
        val team = if (entry.teamId != null) teamManager.getTeamByID(entry.teamId).orElse(null)
            else teamManager.getTeamForPlayerID(entry.playerUUID).orElse(null)
        if (team == null || !team.isValid) return DeliveryResult.FAIL

        val pos = PlatformHelper.getMailboxPos(team.id) ?: return DeliveryResult.FAIL
        if (PlatformHelper.isMailboxFull(team.id)) return DeliveryResult.FAIL
        val toSend = entry.parcelStack.copy()
        if (!entry.preserveSender) toSend.orCreateTag.putString("Sender", entry.sender)
        if (!PlatformHelper.addMailboxContents(team.id, toSend)) return DeliveryResult.FAIL

        val mailboxLevel = level.server.getLevel(pos.dimension())
        if (mailboxLevel != null && mailboxLevel.hasChunkAt(pos.pos())) {
            MailboxManager.updateState(mailboxLevel, pos.pos())
        }
        val message = Component.translatable("contactquests.message.parcel_received")
            .withStyle(ChatFormatting.GREEN)
        level.server.playerList.players.filter { it.uuid in team.members }.forEach {
            it.sendSystemMessage(message)
        }
        return DeliveryResult.SUCCESS
    }

    override fun save(tag: CompoundTag): CompoundTag {
        val list = ListTag()
        synchronized(pendingRewards) {
            for (p in pendingRewards) {
                val pTag = CompoundTag()
                pTag.putUUID("uuid", p.playerUUID)
                pTag.put("parcel", p.parcelStack.save(CompoundTag()))
                pTag.putInt("ticks", p.ticksLeft)
                pTag.putString("sender", p.sender)
                if (p.preserveSender) pTag.putBoolean("preserve_sender", true)
                if (p.teamId != null) pTag.putUUID("team", p.teamId)
                list.add(pTag)
            }
        }
        tag.put("PendingRewards", list)
        tag.put("MigratedDimensions", ListTag().apply {
            migratedDimensions.sorted().forEach { add(net.minecraft.nbt.StringTag.valueOf(it)) }
        })
        return tag
    }

    companion object {
        fun get(level: ServerLevel): RewardDeliverySavedData {
            val overworld = level.server.overworld()
            val global = overworld.dataStorage.computeIfAbsent(
                ::load,
                ::RewardDeliverySavedData,
                "contactquests_pending_rewards"
            )
            if (level !== overworld) {
                val dimension = level.dimension().location().toString()
                if (dimension !in global.migratedDimensions) {
                    val legacy = level.dataStorage.get(::load, "contactquests_pending_rewards")
                    if (legacy != null && legacy !== global) {
                        synchronized(legacy.pendingRewards) {
                            legacy.pendingRewards.forEach { old ->
                                global.pendingRewards.add(old.copy(parcelStack = old.parcelStack.copy()))
                            }
                        }
                    }
                    // 保留旧分维度数据文件以便异常恢复；已迁移维度标记可防止重启后重复导入。
                    global.migratedDimensions.add(dimension)
                    global.setDirty()
                }
            }
            return global
        }

        private fun load(tag: CompoundTag): RewardDeliverySavedData {
            val data = RewardDeliverySavedData()
            tag.getList("MigratedDimensions", Tag.TAG_STRING.toInt()).forEach {
                data.migratedDimensions.add(it.asString)
            }
            val list = tag.getList("PendingRewards", Tag.TAG_COMPOUND.toInt())
            for (i in 0 until list.size) {
                val pTag = list.getCompound(i)
                val stack = ItemStack.of(pTag.getCompound("parcel"))
                if (!stack.isEmpty && pTag.hasUUID("uuid")) {
                    data.pendingRewards.add(
                        PendingReward(
                            pTag.getUUID("uuid"),
                            stack,
                            pTag.getInt("ticks"),
                            pTag.getString("sender"),
                            if (pTag.hasUUID("team")) pTag.getUUID("team") else null,
                            pTag.getBoolean("preserve_sender")
                        )
                    )
                }
            }
            return data
        }
    }
}
