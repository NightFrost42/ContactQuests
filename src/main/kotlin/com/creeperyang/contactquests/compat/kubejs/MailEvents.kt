package com.creeperyang.contactquests.compat.kubejs

import dev.latvian.mods.kubejs.event.EventJS
import dev.latvian.mods.kubejs.typings.Info
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
}