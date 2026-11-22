package com.creeperyang.contactquests.utils

import com.flechazo.contact.common.registry.ItemRegistry
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack

object RedPacketUtils {
    fun getRedPacket(contents: SimpleContainer, blessings: String, sender: String): ItemStack {
        val letter = ItemStack(ItemRegistry.RED_PACKET.get())
        ContactMailNbt.writeContents(letter, contents)
        ContactMailNbt.setBlessing(letter, blessings)
        ContactMailNbt.setSender(letter, sender)
        return letter
    }
}
