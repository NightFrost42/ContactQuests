package com.creeperyang.contactquests.network

import com.creeperyang.contactquests.client.gui.ChoicePostcardScreen
import com.creeperyang.contactquests.data.ChoiceRewardSavedData
import com.creeperyang.contactquests.quest.reward.ParcelChoiceReward
import dev.ftb.mods.ftbquests.quest.ServerQuestFile
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext

class RequestChoiceMessage(val rewardId: Long, val option: Int) : CustomPacketPayload {
    override fun type() = TYPE

    fun handle(context: IPayloadContext) {
        context.enqueueWork {
            val player = context.player() as? ServerPlayer ?: return@enqueueWork
            val reward = ServerQuestFile.INSTANCE?.getReward(rewardId) as? ParcelChoiceReward ?: return@enqueueWork
            runCatching { ChoiceRewardSavedData.get(player.serverLevel()).request(player, reward, option) }
                .onSuccess {
                    PacketDistributor.sendToPlayer(player, ChoicePostcardMessage(rewardId, it.recipient, it.text))
                }
                .onFailure {
                    player.sendSystemMessage(
                        net.minecraft.network.chat.Component.translatable("contactquests.choice.unavailable")
                    )
                }
        }
    }

    companion object {
        val TYPE = CustomPacketPayload.Type<RequestChoiceMessage>(
            ResourceLocation.fromNamespaceAndPath("contactquests", "request_choice")
        )
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, RequestChoiceMessage> = StreamCodec.of(
            { b, m ->
                b.writeLong(m.rewardId)
                b.writeVarInt(m.option)
            },
            { b -> RequestChoiceMessage(b.readLong(), b.readVarInt()) }
        )
    }
}

class PrepareChoicePostcardMessage(val rewardId: Long, val slot: Int) : CustomPacketPayload {
    override fun type() = TYPE

    fun handle(context: IPayloadContext) {
        context.enqueueWork {
            val player = context.player() as? ServerPlayer ?: return@enqueueWork
            runCatching { ChoiceRewardSavedData.get(player.serverLevel()).prepareCard(player, rewardId, slot) }
                .onSuccess {
                    PacketDistributor.sendToPlayer(player, ChoicePostcardMessage(rewardId, it.recipient, it.text, slot))
                }
                .onFailure {
                    player.sendSystemMessage(
                        net.minecraft.network.chat.Component.translatable("contactquests.choice.unavailable")
                    )
                }
        }
    }

    companion object {
        val TYPE = CustomPacketPayload.Type<PrepareChoicePostcardMessage>(
            ResourceLocation.fromNamespaceAndPath("contactquests", "prepare_choice_postcard")
        )
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, PrepareChoicePostcardMessage> = StreamCodec.of(
            { b, m ->
                b.writeLong(m.rewardId)
                b.writeVarInt(m.slot)
            },
            { b -> PrepareChoicePostcardMessage(b.readLong(), b.readVarInt()) }
        )
    }
}

class ChoicePostcardMessage(
    val rewardId: Long,
    val recipient: String,
    val text: String,
    val preparedSlot: Int = -1
) : CustomPacketPayload {
    override fun type() = TYPE

    fun handle(context: IPayloadContext) {
        context.enqueueWork {
            if (preparedSlot < 0) ChoicePostcardScreen(this).openGui() else ChoicePostcardScreen.openPrepared(this)
        }
    }

    companion object {
        val TYPE = CustomPacketPayload.Type<ChoicePostcardMessage>(
            ResourceLocation.fromNamespaceAndPath("contactquests", "choice_postcard")
        )
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, ChoicePostcardMessage> = StreamCodec.of(
            { b, m ->
                b.writeLong(m.rewardId)
                b.writeUtf(m.recipient, 16384)
                b.writeUtf(m.text, 2000)
                b.writeVarInt(m.preparedSlot)
            },
            { b -> ChoicePostcardMessage(b.readLong(), b.readUtf(16384), b.readUtf(2000), b.readVarInt()) }
        )
    }
}
