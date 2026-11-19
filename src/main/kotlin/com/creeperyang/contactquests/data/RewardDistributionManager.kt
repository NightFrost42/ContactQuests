package com.creeperyang.contactquests.data

import com.creeperyang.contactquests.compat.kubejs.KubeJSMailQueueSavedData
import com.creeperyang.contactquests.config.ContactConfig
import com.creeperyang.contactquests.config.NpcConfigManager
import com.flechazo.contact.common.item.ParcelItem
import com.flechazo.contact.common.item.PostcardItem
import com.flechazo.contact.common.item.RedPacketItem
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.*
import kotlin.math.min

object RewardDistributionManager {

    private data class Recipient(val playerId: UUID, val teamId: UUID?)
    private val buffer: MutableMap<Recipient, MutableMap<DeliveryKey, MutableList<ItemStack>>> = mutableMapOf()

    private data class DeliveryKey(val sender: String, val isEnder: Boolean, val preserveSender: Boolean)

    fun distribute(player: ServerPlayer, stack: ItemStack, sender: String, isEnder: Boolean) {
        distributeTo(player.uuid, FTBTeamsAPI.api().manager.getTeamForPlayer(player).orElse(null)?.id, stack, sender, isEnder)
    }

    @JvmOverloads
    fun distributeTo(playerId: UUID, teamId: UUID?, stack: ItemStack, sender: String, isEnder: Boolean, preserveSender: Boolean = false) {
        if (stack.isEmpty) return

        val key = DeliveryKey(sender, isEnder, preserveSender)
        val recipient = Recipient(playerId, teamId)
        val playerMap = buffer.computeIfAbsent(recipient) { mutableMapOf() }
        val items = playerMap.computeIfAbsent(key) { mutableListOf() }

        mergeItemIntoList(items, stack)
    }

    private fun mergeItemIntoList(list: MutableList<ItemStack>, stack: ItemStack) {
        val maxStack = stack.maxStackSize.coerceIn(1, 99)
        var remaining = stack.count
        for (existing in list) {
            if (ItemStack.isSameItemSameComponents(existing, stack) && existing.count < maxStack) {
                val canAdd = min(remaining, maxStack - existing.count)
                existing.grow(canAdd)
                remaining -= canAdd
                if (remaining <= 0) return
            }
        }
        while (remaining > 0) {
            val chunk = min(remaining, maxStack)
            val copy = stack.copy()
            copy.count = chunk
            list.add(copy)
            remaining -= chunk
        }
    }

    fun onServerTick(level: ServerLevel) {
        processBuffer(level)
        RewardDeliverySavedData.get(level).tick(level)
        KubeJSMailQueueSavedData.get(level).tick(level)
    }

    /** 在完成发奖或清空待处理请求前，先将已受理的邮件刷入持久化投递队列。 */
    fun flush(level: ServerLevel) = processBuffer(level)

    private fun processBuffer(level: ServerLevel) {
        if (buffer.isEmpty()) return

        buffer.forEach { (recipient, keyMap) ->
            keyMap.forEach { (key, items) ->
                val parcels = packItems(items, key.isEnder, key.sender)

                val delayTicks =
                    if (key.isEnder || !ContactConfig.enableDeliveryTime.get()) 0
                    else NpcConfigManager.getDeliveryTime(key.sender, level)

                parcels.forEach { parcelStack ->
                    RewardDeliverySavedData.get(level).addPendingReward(recipient.playerId, parcelStack,
                        delayTicks, key.sender, recipient.teamId, key.preserveSender)
                }
            }
        }
        buffer.clear()
    }

    private fun packItems(
        items: List<ItemStack>,
        isEnder: Boolean,
        sender: String
    ): List<ItemStack> {
        val resultParcels = mutableListOf<ItemStack>()

        val containerItem = if (isEnder)
            BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("contact", "ender_parcel"))
        else
            BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("contact", "parcel"))

        if (containerItem == Items.AIR) return items
        val slotsPerParcel = 4
        var currentContents = SimpleContainer(slotsPerParcel)
        var slotIndex = 0

        for (item in items) {
            if (item.item is PostcardItem || item.item is RedPacketItem) {
                resultParcels.add(item)
                continue
            }

            var remaining = item.copy()
            while (!remaining.isEmpty) {
                if (slotIndex >= slotsPerParcel) {
                    resultParcels.add(ParcelItem.getParcel(currentContents, isEnder, sender))
                    currentContents = SimpleContainer(slotsPerParcel)
                    slotIndex = 0
                }

                currentContents.setItem(slotIndex, remaining.copy())
                remaining = ItemStack.EMPTY
                slotIndex++
            }
        }

        if (!currentContents.isEmpty) {
            resultParcels.add(ParcelItem.getParcel(currentContents, isEnder, sender))
        }

        return resultParcels
    }
}
