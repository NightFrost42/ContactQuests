package com.creeperyang.contactquests.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent
import net.minecraftforge.fml.loading.FMLEnvironment
import java.util.function.Supplier

import com.creeperyang.contactquests.registry.ModItems
import net.minecraft.world.item.ItemStack

object RequestBinderMessage {
    fun encode(buf: FriendlyByteBuf) {}
    fun decode(buf: FriendlyByteBuf): RequestBinderMessage = this
    fun handle(supplier: Supplier<NetworkEvent.Context>) {
        val context = supplier.get()
        context.enqueueWork {
            val player = context.sender ?: return@enqueueWork
            val stack = ItemStack(ModItems.TEAM_BINDING_CARD.get())
            if (!player.inventory.add(stack)) player.drop(stack, false)
        }
        context.packetHandled = true
    }
}
