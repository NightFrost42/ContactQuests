package com.creeperyang.contactquests.utils

import com.flechazo.contact.common.item.PostcardItem
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack

/** Contact 1.2.3 Forge item NBT; postcard "Text" access stays with PostcardItem. */
object ContactMailNbt {
    fun readContents(stack: ItemStack): List<ItemStack> {
        val contents = stack.tag?.getList("parcel", Tag.TAG_COMPOUND.toInt()) ?: return emptyList()
        return (0 until contents.size).map { ItemStack.of(contents.getCompound(it)) }.filter { !it.isEmpty }
    }

    fun writeContents(stack: ItemStack, contents: SimpleContainer) {
        stack.orCreateTag.put("parcel", contents.createTag())
    }

    fun getSender(stack: ItemStack): String? =
        stack.tag?.takeIf { it.contains("Sender") }?.getString("Sender")

    fun hasSender(stack: ItemStack): Boolean = stack.tag?.contains("Sender") == true

    fun setSender(stack: ItemStack, sender: String) {
        if (sender.isNotEmpty()) stack.orCreateTag.putString("Sender", sender)
    }

    fun getBlessing(stack: ItemStack): String = stack.tag?.getString("blessing").orEmpty()

    fun setBlessing(stack: ItemStack, blessing: String) {
        if (blessing.isNotEmpty()) stack.orCreateTag.putString("blessing", blessing)
    }

    fun getPostcardStyleRaw(stack: ItemStack): String {
        val tag = stack.tag ?: return ""
        return tag.getString("CardID").ifEmpty { tag.getCompound("Info").getString("ID") }
    }

    fun getPostcardStyleId(stack: ItemStack): ResourceLocation? =
        getPostcardStyleRaw(stack).takeIf { it.isNotEmpty() }?.let(ResourceLocation::tryParse)

    fun getText(stack: ItemStack): String = PostcardItem.getText(stack)

    fun setText(stack: ItemStack, text: String): ItemStack = PostcardItem.setText(stack, text)
}
