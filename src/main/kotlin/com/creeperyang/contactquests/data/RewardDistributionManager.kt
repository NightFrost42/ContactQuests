package com.creeperyang.contactquests.data

import com.creeperyang.contactquests.compat.kubejs.KubeJSMailQueueSavedData
import com.creeperyang.contactquests.config.ContactConfig
import com.creeperyang.contactquests.config.NpcConfigManager
import com.flechazo.contact.common.item.ParcelItem
import com.flechazo.contact.common.item.PostcardItem
import com.flechazo.contact.common.item.RedPacketItem
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraftforge.registries.ForgeRegistries
import java.util.*
import kotlin.math.min

object RewardDistributionManager {

    private val buffer: MutableMap<UUID, MutableMap<String, MutableList<ItemStack>>> = mutableMapOf()

    private data class DeliveryKey(val sender: String, val isEnder: Boolean) {
        override fun toString(): String = "$sender|$isEnder"

        companion object {
            fun fromString(str: String): DeliveryKey {
                val parts = str.split("|")
                return DeliveryKey(
                    parts[0],
                    parts[1].toBoolean()
                )
            }
        }
    }

    fun distribute(player: ServerPlayer, stack: ItemStack, sender: String, isEnder: Boolean) {
        if (stack.isEmpty) return

        val key = DeliveryKey(sender, isEnder).toString()
        val playerMap = buffer.computeIfAbsent(player.uuid) { mutableMapOf() }
        val items = playerMap.computeIfAbsent(key) { mutableListOf() }

        mergeItemIntoList(items, stack)
    }

    private fun mergeItemIntoList(list: MutableList<ItemStack>, stack: ItemStack) {
        var remaining = stack.count
        for (existing in list) {
            if (ItemStack.isSameItemSameTags(existing, stack) && existing.count < existing.maxStackSize) {
                val canAdd = min(remaining, existing.maxStackSize - existing.count)
                existing.grow(canAdd)
                remaining -= canAdd
                if (remaining <= 0) return
            }
        }
        if (remaining > 0) {
            val copy = stack.copy()
            copy.count = remaining
            list.add(copy)
        }
    }

    fun onServerTick(level: ServerLevel) {
        processBuffer(level)
        RewardDeliverySavedData.get(level).tick(level)
        KubeJSMailQueueSavedData.get(level).tick(level)
    }

    private fun processBuffer(level: ServerLevel) {
        if (buffer.isEmpty()) return

        buffer.forEach { (playerId, keyMap: Map<String, MutableList<ItemStack>>) ->
            keyMap.forEach { (keyStr, items: List<ItemStack>) ->
                val key = DeliveryKey.fromString(keyStr)
                val parcels = packItems(items, key.isEnder, key.sender)

                val delayTicks =
                    if (key.isEnder || !ContactConfig.enableDeliveryTime.get()) 0
                    else NpcConfigManager.getDeliveryTime(key.sender, level)

                parcels.forEach { parcelStack ->
                    RewardDeliverySavedData.get(level).addPendingReward(playerId, parcelStack, delayTicks, key.sender)
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
            ForgeRegistries.ITEMS.getValue(ResourceLocation("contact", "ender_parcel"))
        else
            ForgeRegistries.ITEMS.getValue(ResourceLocation("contact", "parcel"))

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