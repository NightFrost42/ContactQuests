package com.creeperyang.contactquests.data

import com.creeperyang.contactquests.config.ContactConfig
import com.creeperyang.contactquests.mailbox.TeamMailHelper
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.datafix.DataFixTypes
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

        val errorKey = TeamMailHelper.sendParcelToTeam(
            level,
            team.id,
            entry.parcelStack,
            entry.sender,
            entry.preserveSender
        )

        return if (errorKey == null) DeliveryResult.SUCCESS else DeliveryResult.FAIL
    }

    override fun save(tag: CompoundTag, provider: HolderLookup.Provider): CompoundTag {
        val list = ListTag()
        synchronized(pendingRewards) {
            for (p in pendingRewards) {
                val pTag = CompoundTag()
                pTag.putUUID("uuid", p.playerUUID)
                pTag.put("parcel", p.parcelStack.save(provider))
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
            val factory = Factory(::RewardDeliverySavedData, ::load, DataFixTypes.LEVEL)
            val global = overworld.dataStorage.computeIfAbsent(
                factory,
                "contactquests_pending_rewards"
            )
            if (level !== overworld) {
                val dimension = level.dimension().location().toString()
                if (dimension !in global.migratedDimensions) {
                    val legacy = level.dataStorage.get(factory, "contactquests_pending_rewards")
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

        private fun load(tag: CompoundTag, provider: HolderLookup.Provider): RewardDeliverySavedData {
            val data = RewardDeliverySavedData()
            tag.getList("MigratedDimensions", Tag.TAG_STRING.toInt()).forEach {
                data.migratedDimensions.add(it.asString)
            }
            val list = tag.getList("PendingRewards", Tag.TAG_COMPOUND.toInt())
            for (i in 0 until list.size) {
                val pTag = list.getCompound(i)
                val stack = ItemStack.parseOptional(provider, pTag.getCompound("parcel"))
                if (!stack.isEmpty) {
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
