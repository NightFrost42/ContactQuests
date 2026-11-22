package com.creeperyang.contactquests.network

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.network.NetworkDirection
import net.minecraftforge.network.NetworkRegistry
import net.minecraftforge.network.PacketDistributor
import net.minecraftforge.network.simple.SimpleChannel
import java.util.function.BiConsumer

object NetworkHandler {
    private const val PROTOCOL_VERSION = "2"
    val CHANNEL: SimpleChannel = NetworkRegistry.newSimpleChannel(
        ResourceLocation("contactquests", "main"), { PROTOCOL_VERSION },
        { it == PROTOCOL_VERSION }, { it == PROTOCOL_VERSION }
    )
    fun register() {
        CHANNEL.messageBuilder(RequestBinderMessage::class.java, 0, NetworkDirection.PLAY_TO_SERVER)
            .encoder { msg, buf -> msg.encode(buf) }
            .decoder { buf -> RequestBinderMessage.decode(buf) }
            .consumerNetworkThread(BiConsumer { msg, ctx -> msg.handle(ctx) })
            .add()
        CHANNEL.messageBuilder(SyncTeamTagsMessage::class.java, 1, NetworkDirection.PLAY_TO_CLIENT)
            .encoder { msg, buf -> msg.encode(buf) }
            .decoder { buf -> SyncTeamTagsMessage.decode(buf) }
            .consumerNetworkThread(BiConsumer { msg, ctx -> msg.handle(ctx) })
            .add()
        CHANNEL.messageBuilder(OpenQuestMessage::class.java, 2, NetworkDirection.PLAY_TO_CLIENT)
            .encoder { msg, buf -> msg.encode(buf) }
            .decoder { buf -> OpenQuestMessage.decode(buf) }
            .consumerNetworkThread(BiConsumer { msg, ctx -> msg.handle(ctx) })
            .add()
        CHANNEL.messageBuilder(SyncTeamExtensionMessage::class.java, 3, NetworkDirection.PLAY_TO_CLIENT)
            .encoder { msg, buf -> msg.encode(buf) }
            .decoder { buf -> SyncTeamExtensionMessage(buf) }
            .consumerNetworkThread(BiConsumer { msg, ctx -> msg.handle(ctx) })
            .add()
        CHANNEL.messageBuilder(SyncQuestTextMessage::class.java, 4, NetworkDirection.PLAY_TO_CLIENT)
            .encoder { msg, buf -> msg.encode(buf) }
            .decoder { buf -> SyncQuestTextMessage.decode(buf) }
            .consumerNetworkThread(BiConsumer { msg, ctx -> msg.handle(ctx) })
            .add()
        CHANNEL.messageBuilder(RequestChoiceMessage::class.java, 5, NetworkDirection.PLAY_TO_SERVER)
            .encoder { msg, buf -> msg.encode(buf) }
            .decoder { buf -> RequestChoiceMessage.decode(buf) }
            .consumerNetworkThread(BiConsumer { msg, ctx -> msg.handle(ctx) })
            .add()
        CHANNEL.messageBuilder(PrepareChoicePostcardMessage::class.java, 6, NetworkDirection.PLAY_TO_SERVER)
            .encoder { msg, buf -> msg.encode(buf) }
            .decoder { buf -> PrepareChoicePostcardMessage.decode(buf) }
            .consumerNetworkThread(BiConsumer { msg, ctx -> msg.handle(ctx) })
            .add()
        CHANNEL.messageBuilder(ChoicePostcardMessage::class.java, 7, NetworkDirection.PLAY_TO_CLIENT)
            .encoder { msg, buf -> msg.encode(buf) }
            .decoder { buf -> ChoicePostcardMessage.decode(buf) }
            .consumerNetworkThread(BiConsumer { msg, ctx -> msg.handle(ctx) })
            .add()
    }
    @JvmStatic fun sendToPlayer(msg: Any, player: ServerPlayer) {
        CHANNEL.send(PacketDistributor.PLAYER.with { player }, msg)
    }
    @JvmStatic fun sendToServer(msg: Any) { CHANNEL.sendToServer(msg) }
}
