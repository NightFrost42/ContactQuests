package com.creeperyang.contactquests.network

import dev.ftb.mods.ftbquests.client.ClientQuestFile
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.fml.loading.FMLEnvironment
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
            if (FMLEnvironment.dist.isClient) ClientHandler.apply(this)
        }
        ctx.packetHandled = true
    }
    private object ClientHandler {
        fun apply(message: OpenQuestMessage) = with(message) {
            ClientQuestFile.openBookToQuestObject(id)
        }
    }

}