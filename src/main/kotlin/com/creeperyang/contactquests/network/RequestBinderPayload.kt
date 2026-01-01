package com.creeperyang.contactquests.network

import net.minecraft.network.FriendlyByteBuf

class RequestBinderPayload {

    companion object {
        fun encode(msg: RequestBinderPayload, buf: FriendlyByteBuf) {

        }

        fun decode(buf: FriendlyByteBuf): RequestBinderPayload {
            return RequestBinderPayload()
        }
    }
}