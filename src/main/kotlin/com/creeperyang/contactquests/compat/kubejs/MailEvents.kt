package com.creeperyang.contactquests.compat.kubejs

import com.flechazo.contact.common.component.ContactDataComponents
import com.flechazo.contact.common.item.LetterItem
import com.flechazo.contact.common.item.ParcelItem
import com.flechazo.contact.common.item.PostcardItem
import com.flechazo.contact.common.item.RedPacketItem
import dev.latvian.mods.kubejs.event.KubeEvent
import dev.latvian.mods.kubejs.typings.Info
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

@Info("Fired when a player tries to send mail (Parcel/Postcard/RedPacket) via a mailbox.")
class MailReceivedEventJS(
    val player: ServerPlayer,
    val recipient: String,
    val items: List<ItemStack>,
    val isPostcard: Boolean
) : KubeEvent {
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
        val container = stack.get(DataComponents.CONTAINER)
        if (container != null) {
            return container.stream().toList()
        }
        return emptyList()
    }

    @Info("Gets the blessing text from a Red Packet item")
    fun getRedPacketBlessing(stack: ItemStack): String {
        return stack.get(ContactDataComponents.RED_PACKET_BLESSING.get()) ?: ""
    }

    @Info("Gets the text content of a Postcard item")
    fun getPostcardText(stack: ItemStack): String {
        val type = getComponent<String>("postcard_text") ?: return ""
        return stack.get(type) ?: ""
    }

    @Info("Gets the style ID of a Postcard item")
    fun getPostcardStyle(stack: ItemStack): String {
        val type = getComponent<ResourceLocation>("postcard_style_id") ?: return ""
        return stack.get(type)?.toString() ?: ""
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getComponent(path: String): DataComponentType<T>? {
        val id = ResourceLocation.fromNamespaceAndPath("contact", path)
        return BuiltInRegistries.DATA_COMPONENT_TYPE[id] as? DataComponentType<T>
    }
}