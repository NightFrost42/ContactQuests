package com.creeperyang.contactquests.utils

import com.flechazo.contact.common.component.ContactDataComponents
import com.flechazo.contact.common.registry.ItemRegistry
import net.minecraft.core.component.DataComponents
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemContainerContents


object RedPacketUtils {

    fun getRedPacket(contents: SimpleContainer, blessings: String, sender: String): ItemStack {
        val letter = ItemStack(ItemRegistry.RED_PACKET.get())

        val itemsList = ArrayList<ItemStack>()
        for (i in 0 until contents.containerSize) {
            itemsList.add(contents.getItem(i))
        }

        letter.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(itemsList))

        if (blessings.isNotEmpty()) {
            letter.set(ContactDataComponents.RED_PACKET_BLESSING.get(), blessings)
        }

        if (sender.isNotEmpty()) {
            letter.set(ContactDataComponents.POSTCARD_SENDER.get(), sender)
        }

        return letter
    }
}