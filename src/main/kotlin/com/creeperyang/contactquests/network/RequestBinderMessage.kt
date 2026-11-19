package com.creeperyang.contactquests.network

import com.creeperyang.contactquests.registry.ModItems
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.network.handling.IPayloadContext

/** 客户端在 FTB 团队界面点击快捷按钮后，向服务端请求发放一张团队绑定卡。 */
data object RequestBinderMessage : CustomPacketPayload {
    // 保持既有网络通道 ID 不变以兼容既有客户端/存档协议
    val TYPE = CustomPacketPayload.Type<RequestBinderMessage>(
        ResourceLocation.fromNamespaceAndPath("contactquests", "request_binder")
    )

    val STREAM_CODEC: StreamCodec<ByteBuf, RequestBinderMessage> = StreamCodec.unit(this)

    override fun type(): CustomPacketPayload.Type<RequestBinderMessage> = TYPE

    fun handle(context: IPayloadContext) {
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
