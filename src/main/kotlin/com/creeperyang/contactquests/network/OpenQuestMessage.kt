package com.creeperyang.contactquests.network

import dev.ftb.mods.ftbquests.client.ClientQuestFile
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

class OpenQuestMessage(val id: Long) : CustomPacketPayload {

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath("contactquests", "open_quest")
        val TYPE = CustomPacketPayload.Type<OpenQuestMessage>(ID)

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, OpenQuestMessage> = StreamCodec.composite(
            StreamCodec.of({ buf, value -> buf.writeLong(value) }, { buf -> buf.readLong() }),
            OpenQuestMessage::id,
            ::OpenQuestMessage
        )
    }

    override fun type(): CustomPacketPayload.Type<OpenQuestMessage> {
        return TYPE
    }

    fun handle(context: IPayloadContext) {
        context.enqueueWork {
            ClientQuestFile.openBookToQuestObject(id)
        }
    }
}