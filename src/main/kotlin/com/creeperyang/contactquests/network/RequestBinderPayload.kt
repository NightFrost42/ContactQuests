package com.creeperyang.contactquests.network

import com.creeperyang.contactquests.registry.ModItems
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.world.item.ItemStack
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier

class RequestBinderPayload {

    companion object {
        fun encode(msg: RequestBinderPayload, buf: FriendlyByteBuf) {

        }

        fun decode(buf: FriendlyByteBuf): RequestBinderPayload {
            return RequestBinderPayload()
        }

        fun handle(msg: RequestBinderPayload, ctx: Supplier<NetworkEvent.Context>) {
            ctx.get().enqueueWork {
                val player = ctx.get().sender
                if (player != null) {
                    val itemStack = ItemStack(ModItems.TEAM_BINDING_CARD.get())
                    // 尝试添加到物品栏，如果满了则生成掉落物
                    if (!player.inventory.add(itemStack)) {
                        player.drop(itemStack, false)
                    }
                }
            }
            ctx.get().packetHandled = true
        }
    }
}