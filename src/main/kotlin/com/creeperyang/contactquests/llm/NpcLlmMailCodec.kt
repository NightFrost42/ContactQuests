package com.creeperyang.contactquests.llm

import com.creeperyang.contactquests.utils.ContactMailNbt
import com.flechazo.contact.common.item.PostcardItem
import com.flechazo.contact.common.item.RedPacketItem
import net.minecraft.world.item.ItemStack
import net.minecraftforge.registries.ForgeRegistries

internal object NpcLlmMailCodec {
    fun formatIncomingMail(stacks: List<ItemStack>): String {
        val text = stacks.filter { it.item is PostcardItem }.map { ContactMailNbt.getText(it) }
            .filter { it.isNotBlank() }.joinToString("\n").take(2000)
        val attachments = stacks.filter { it.item !is PostcardItem }
            .joinToString(", ", transform = ::describe).take(1000)
        return (text + if (attachments.isEmpty()) "" else "\n[Attachments: $attachments]")
            .ifBlank { "[Blank postcard]" }.take(2000)
    }

    fun formatTaskReceipt(originals: List<ItemStack>, remaining: List<ItemStack>, consumed: Long): String {
        val originalText = originals.filter { it.item is PostcardItem }
            .joinToString("\n") { ContactMailNbt.getText(it) }
        val attachments = remaining.filter { it.item !is PostcardItem }
            .joinToString(", ", transform = ::describe).take(1000)
        val receipt = "[Task submission: $consumed units accepted]" +
            (if (originalText.isBlank()) "" else "\n$originalText") +
            (if (attachments.isBlank()) "" else "\n[Remaining attachments: $attachments]")
        return receipt.take(2000)
    }

    fun parcelContents(stack: ItemStack): List<ItemStack> = ContactMailNbt.readContents(stack)

    fun units(stack: ItemStack): Long = if (stack.item is RedPacketItem) {
        parcelContents(stack).sumOf { it.count.toLong() }
    } else stack.count.toLong()

    fun describe(stack: ItemStack): String {
        val contents = parcelContents(stack)
            .joinToString(", ") { "${ForgeRegistries.ITEMS.getKey(it.item)} x${it.count}" }
        val text = if (stack.item is PostcardItem) {
            ContactMailNbt.getText(stack).take(240)
        } else {
            ContactMailNbt.getBlessing(stack).take(240)
        }
        return "${ForgeRegistries.ITEMS.getKey(stack.item)} x${stack.count}" +
            (if (contents.isBlank()) "" else " [$contents]") + (if (text.isBlank()) "" else " text=$text")
    }
}
