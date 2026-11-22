package com.creeperyang.contactquests.llm

import com.flechazo.contact.common.component.ContactDataComponents
import com.flechazo.contact.common.item.PostcardItem
import com.flechazo.contact.common.item.RedPacketItem
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemContainerContents

internal object NpcLlmMailCodec {
    fun formatIncomingMail(valid: List<ItemStack>): String {
        val text = valid.filter { it.item is PostcardItem }
            .mapNotNull { it[ContactDataComponents.POSTCARD_TEXT.get()] }
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .take(2000)
        val attachments = valid.filter { it.item !is PostcardItem }
            .joinToString(", ", transform = ::describe)
            .take(1000)
        return (text + if (attachments.isEmpty()) "" else "\n[Attachments: $attachments]")
            .ifBlank { "[Blank postcard]" }
            .take(2000)
    }

    fun formatTaskReceipt(originals: List<ItemStack>, remaining: List<ItemStack>, consumed: Long): String {
        val originalText = originals.filter { it.item is PostcardItem }
            .joinToString("\n") { it[ContactDataComponents.POSTCARD_TEXT.get()].orEmpty() }
        val attachments = remaining.filter { it.item !is PostcardItem }
            .joinToString(", ", transform = ::describe)
            .take(1000)
        val receipt = "[Task submission: $consumed units accepted]" +
            (if (originalText.isBlank()) "" else "\n$originalText") +
            (if (attachments.isBlank()) "" else "\n[Remaining attachments: $attachments]")
        return receipt.take(2000)
    }

    fun describe(stack: ItemStack): String {
        val contents = parcelContents(stack)
            .joinToString(", ") { "${BuiltInRegistries.ITEM.getKey(it.item)} x${it.count}" }
        val text = if (stack.item is PostcardItem) {
            stack[ContactDataComponents.POSTCARD_TEXT.get()].orEmpty().take(240)
        } else {
            stack[ContactDataComponents.RED_PACKET_BLESSING.get()].orEmpty().take(240)
        }
        return "${BuiltInRegistries.ITEM.getKey(stack.item)} x${stack.count}" +
            (if (contents.isBlank()) "" else " [$contents]") + (if (text.isBlank()) "" else " text=$text")
    }

    fun units(stack: ItemStack): Long = if (stack.item is RedPacketItem) {
        parcelContents(stack).sumOf { it.count.toLong() }
    } else stack.count.toLong()

    private fun parcelContents(stack: ItemStack) =
        stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItems()
}
