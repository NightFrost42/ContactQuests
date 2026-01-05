package com.creeperyang.contactquests.network

import dev.ftb.mods.ftbquests.client.ClientQuestFile
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier

class OpenQuestMessage(val id: Long) {

    fun encode(buf: FriendlyByteBuf) {
        buf.writeLong(id)
    }

    companion object {
        @JvmStatic
        fun decode(buf: FriendlyByteBuf): OpenQuestMessage {
            return OpenQuestMessage(buf.readLong())
        }
    }

    fun handle(ctxSupplier: Supplier<NetworkEvent.Context>) {
        val ctx = ctxSupplier.get()
        ctx.enqueueWork {
            ClientQuestFile.openBookToQuestObject(id)
        }
        ctx.packetHandled = true
    }
}