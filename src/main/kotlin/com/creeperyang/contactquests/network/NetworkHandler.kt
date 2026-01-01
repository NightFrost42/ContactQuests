package com.creeperyang.contactquests.network

import com.creeperyang.contactquests.registry.ModItems
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraftforge.network.NetworkEvent
import net.minecraftforge.network.NetworkRegistry
import net.minecraftforge.network.simple.SimpleChannel
import java.util.function.Supplier

object NetworkHandler {
    private const val PROTOCOL_VERSION = "1"

    val CHANNEL: SimpleChannel = NetworkRegistry.newSimpleChannel(
        ResourceLocation("contactquests", "main"),
        { PROTOCOL_VERSION },
        { it == PROTOCOL_VERSION },
        { it == PROTOCOL_VERSION }
    )

    fun register() {
        var id = 0
        CHANNEL.messageBuilder(RequestBinderPayload::class.java, id++, NetworkDirection.PLAY_TO_SERVER)
            .encoder { msg, buf -> RequestBinderPayload.encode(msg, buf) }
            .decoder { buf -> RequestBinderPayload.decode(buf) }
            .consumerNetworkThread { msg, ctx -> handleRequestBinder(msg, ctx) }
            .add()
    }

    internal fun handleRequestBinder(msg: RequestBinderPayload, ctxSupplier: Supplier<NetworkEvent.Context>) {
        val ctx = ctxSupplier.get()
        ctx.enqueueWork {
            val player = ctx.sender
            if (player is ServerPlayer) {
                val itemStack = ItemStack(ModItems.TEAM_BINDING_CARD.get())

                if (!player.inventory.add(itemStack)) {
                    player.drop(itemStack, false)
                }
            }
        }
        ctx.packetHandled = true
    }
}