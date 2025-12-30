package com.creeperyang.contactquests.network

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

data object RequestBinderPayload : CustomPacketPayload {
    val TYPE = CustomPacketPayload.Type<RequestBinderPayload>(
        ResourceLocation.fromNamespaceAndPath("contactquests", "request_binder")
    )

    val STREAM_CODEC: StreamCodec<ByteBuf, RequestBinderPayload> = StreamCodec.unit(this)

    override fun type(): CustomPacketPayload.Type<RequestBinderPayload> {
        return TYPE
    }
}