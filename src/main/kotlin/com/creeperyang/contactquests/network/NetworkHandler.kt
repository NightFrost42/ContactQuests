package com.creeperyang.contactquests.network

import com.creeperyang.contactquests.registry.ModItems
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadContext

object NetworkHandler {

    fun register(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("contactquests")
            .versioned("1.0.0")

        registrar.playToServer(
            RequestBinderPayload.TYPE,
            RequestBinderPayload.STREAM_CODEC,
            ::handleRequestBinder
        )

        registrar.playBidirectional(
            OpenQuestMessage.TYPE,
            OpenQuestMessage.STREAM_CODEC,
            OpenQuestMessage::handle
        )

        registrar.playToClient(
            SyncTeamExtensionMessage.TYPE,
            SyncTeamExtensionMessage.STREAM_CODEC,
            SyncTeamExtensionMessage::handle
        )

        registrar.playToClient(
            SyncTeamTagsMessage.TYPE,
            SyncTeamTagsMessage.STREAM_CODEC,
            SyncTeamTagsMessage::handle
        )
    }

    private fun handleRequestBinder(payload: RequestBinderPayload, context: IPayloadContext) {
        context.enqueueWork {
            val player = context.player()
            if (player is ServerPlayer) {
                val itemStack = ItemStack(ModItems.TEAM_BINDING_CARD.get())

                if (!player.inventory.add(itemStack)) {
                    player.drop(itemStack, false)
                }
            }
        }
    }
}