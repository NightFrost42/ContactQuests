package com.creeperyang.contactquests.network

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

/** 注册模组在客户端与服务端之间通信的全部自定义网络消息包。 */
object NetworkHandler {

    fun register(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("contactquests")
            .versioned("1.3.0")

        registrar.playToServer(RequestChoiceMessage.TYPE, RequestChoiceMessage.STREAM_CODEC, RequestChoiceMessage::handle)
        registrar.playToServer(PrepareChoicePostcardMessage.TYPE, PrepareChoicePostcardMessage.STREAM_CODEC, PrepareChoicePostcardMessage::handle)
        registrar.playToClient(ChoicePostcardMessage.TYPE, ChoicePostcardMessage.STREAM_CODEC, ChoicePostcardMessage::handle)

        registrar.playToServer(
            RequestBinderMessage.TYPE,
            RequestBinderMessage.STREAM_CODEC
        ) { _, context -> RequestBinderMessage.handle(context) }

        registrar.playToClient(
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

        registrar.playToClient(
            SyncQuestTextMessage.TYPE,
            SyncQuestTextMessage.STREAM_CODEC,
            SyncQuestTextMessage::handle
        )
    }
}
