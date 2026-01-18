package com.creeperyang.contactquests.data

import com.creeperyang.contactquests.config.ContactConfig
import com.creeperyang.contactquests.utils.TeamMailHelper
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
        val sender: String
    )

    private val pendingRewards = Collections.synchronizedList(ArrayList<PendingReward>())

    fun addPendingReward(playerUUID: UUID, parcel: ItemStack, delay: Int, sender: String) {
        pendingRewards.add(PendingReward(playerUUID, parcel, delay, sender))
        setDirty()
    }

    fun tick(level: ServerLevel) {
        if (pendingRewards.isEmpty()) return

        val iterator = pendingRewards.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            entry.ticksLeft--

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
        val team = teamManager.getTeamForPlayerID(entry.playerUUID).orElse(null) ?: return DeliveryResult.FAIL

        val errorKey = TeamMailHelper.sendParcelToTeam(
            level,
            team.id,
            entry.parcelStack,
            entry.sender
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
                list.add(pTag)
            }
        }
        tag.put("PendingRewards", list)
        return tag
    }

    companion object {
        fun get(level: ServerLevel): RewardDeliverySavedData {
            return level.dataStorage.computeIfAbsent(
                Factory(::RewardDeliverySavedData, ::load, DataFixTypes.LEVEL),
                "contactquests_pending_rewards"
            )
        }

        private fun load(tag: CompoundTag, provider: HolderLookup.Provider): RewardDeliverySavedData {
            val data = RewardDeliverySavedData()
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
                            pTag.getString("sender")
                        )
                    )
                }
            }
            return data
        }
    }
}