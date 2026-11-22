package com.creeperyang.contactquests.network

import com.creeperyang.contactquests.client.gui.ChoicePostcardScreen
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent
import net.minecraftforge.fml.loading.FMLEnvironment
import java.util.function.Supplier

import com.creeperyang.contactquests.data.ChoiceRewardSavedData
import com.creeperyang.contactquests.quest.reward.ParcelChoiceReward
import dev.ftb.mods.ftbquests.quest.ServerQuestFile
import net.minecraft.server.level.ServerPlayer

class RequestChoiceMessage(val rewardId: Long, val option: Int) {
    fun handle(supplier: Supplier<NetworkEvent.Context>) {
        val context = supplier.get()
        context.enqueueWork {
            val player = context.sender ?: return@enqueueWork
            val reward = ServerQuestFile.INSTANCE?.getReward(rewardId) as? ParcelChoiceReward ?: return@enqueueWork
            runCatching { ChoiceRewardSavedData.get(player.serverLevel()).request(player, reward, option) }
                .onSuccess {
                    NetworkHandler.sendToPlayer(ChoicePostcardMessage(rewardId, it.recipient, it.text), player)
                }
                .onFailure {
                    player.sendSystemMessage(
                        net.minecraft.network.chat.Component.translatable("contactquests.choice.unavailable")
                    )
                }
        }
        context.packetHandled = true
    }

    fun encode(b: FriendlyByteBuf) {
        b.writeLong(rewardId)
        b.writeVarInt(option)
    }

    companion object {
        fun decode(b: FriendlyByteBuf) = RequestChoiceMessage(b.readLong(), b.readVarInt())
    }
}

class PrepareChoicePostcardMessage(val rewardId: Long, val slot: Int) {
    fun handle(supplier: Supplier<NetworkEvent.Context>) {
        val context = supplier.get()
        context.enqueueWork {
            val player = context.sender ?: return@enqueueWork
            runCatching { ChoiceRewardSavedData.get(player.serverLevel()).prepareCard(player, rewardId, slot) }
                .onSuccess {
                    NetworkHandler.sendToPlayer(ChoicePostcardMessage(rewardId, it.recipient, it.text, slot), player)
                }
                .onFailure {
                    player.sendSystemMessage(
                        net.minecraft.network.chat.Component.translatable("contactquests.choice.unavailable")
                    )
                }
        }
        context.packetHandled = true
    }

    fun encode(b: FriendlyByteBuf) {
        b.writeLong(rewardId)
        b.writeVarInt(slot)
    }

    companion object {
        fun decode(b: FriendlyByteBuf) = PrepareChoicePostcardMessage(b.readLong(), b.readVarInt())
    }
}

class ChoicePostcardMessage(val rewardId: Long, val recipient: String, val text: String, val preparedSlot: Int = -1) {
    fun handle(supplier: Supplier<NetworkEvent.Context>) {
        val context = supplier.get()
        context.enqueueWork {
            if (FMLEnvironment.dist.isClient) ClientHandler.open(this)
        }
        context.packetHandled = true
    }

    fun encode(b: FriendlyByteBuf) {
        b.writeLong(rewardId)
        b.writeUtf(recipient, 16384)
        b.writeUtf(text, 2000)
        b.writeVarInt(preparedSlot)
    }

    companion object {
        fun decode(b: FriendlyByteBuf) = ChoicePostcardMessage(
            b.readLong(), b.readUtf(16384), b.readUtf(2000), b.readVarInt()
        )
    }

    private object ClientHandler {
        fun open(message: ChoicePostcardMessage) {
            if (message.preparedSlot < 0) ChoicePostcardScreen(message).openGui()
            else ChoicePostcardScreen.openPrepared(message)
        }
    }
}
