package com.creeperyang.contactquests.compat.kubejs

import com.flechazo.contact.common.item.LetterItem
import com.flechazo.contact.common.item.ParcelItem
import com.flechazo.contact.common.item.PostcardItem
import com.flechazo.contact.common.item.RedPacketItem
import dev.latvian.mods.kubejs.event.EventJS
import dev.latvian.mods.kubejs.typings.Info
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

@Info("Fired when a player tries to send mail (Parcel/Postcard/RedPacket) via a mailbox.")
class MailReceivedEventJS(
    val player: ServerPlayer,
    val recipient: String,
    val items: List<ItemStack>,
    val isPostcard: Boolean
) : EventJS() {
    private var intercepted = false

    fun intercept() {
        intercepted = true
    }

    fun isIntercepted(): Boolean {
        return intercepted
    }

    @Info("Determines the type of the mail item. Returns 'postcard', 'red_packet', 'parcel', or 'unknown'.")
    fun getMailType(stack: ItemStack): String {
        val item = stack.item
        return when (item) {
            is PostcardItem -> "postcard"
            is RedPacketItem -> "red_packet"
            is ParcelItem, is LetterItem -> "parcel"
            else -> "unknown"
        }
    }

    @Info("Gets the contents of a container item (Parcel, Envelope, Red Packet)")
    fun getContainerContents(stack: ItemStack): List<ItemStack> {
        val items = ArrayList<ItemStack>()
        val tag = stack.tag ?: return items

        if (tag.contains("parcel", Tag.TAG_LIST.toInt())) {
            val list = tag.getList("parcel", Tag.TAG_COMPOUND.toInt())
            for (i in 0 until list.size) {
                items.add(ItemStack.of(list.getCompound(i)))
            }
        } else if (tag.contains("Items", Tag.TAG_LIST.toInt())) {
            val list = tag.getList("Items", Tag.TAG_COMPOUND.toInt())
            for (i in 0 until list.size) {
                items.add(ItemStack.of(list.getCompound(i)))
            }
        }
        return items
    }

    @Info("Gets the blessing text from a Red Packet item")
    fun getRedPacketBlessing(stack: ItemStack): String {
        val tag = stack.tag ?: return ""

        return when {
            tag.contains("red_packet_blessing") -> tag.getString("red_packet_blessing")
            tag.contains("blessing") -> tag.getString("blessing")
            tag.contains("blessings") -> tag.getString("blessings")
            else -> ""
        }
    }

    @Info("Gets the text content of a Postcard item")
    fun getPostcardText(stack: ItemStack): String {
        val tag = stack.tag ?: return ""
        return if (tag.contains("Text")) tag.getString("Text") else ""
    }

    @Info("Gets the style ID of a Postcard item")
    fun getPostcardStyle(stack: ItemStack): String {
        val tag = stack.tag ?: return ""

        if (tag.contains("CardID")) {
            return tag.getString("CardID")
        } else if (tag.contains("Info")) {
            val infoTag = tag.getCompound("Info")
            if (infoTag.contains("ID")) {
                return "contact:" + infoTag.getString("ID")
            }
        }
        return ""
    }
}