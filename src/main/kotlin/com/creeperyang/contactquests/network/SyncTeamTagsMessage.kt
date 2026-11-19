package com.creeperyang.contactquests.network

import com.creeperyang.contactquests.bridge.ITeamDataExtension
import dev.ftb.mods.ftbquests.client.ClientQuestFile
import net.minecraft.core.UUIDUtil
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.*

class SyncTeamTagsMessage(val teamId: UUID, tags: Set<String>) : CustomPacketPayload {
    val tags: Set<String> = Collections.unmodifiableSet(HashSet(tags))

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath("contactquests", "sync_team_tags")
        val TYPE = CustomPacketPayload.Type<SyncTeamTagsMessage>(ID)

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, SyncTeamTagsMessage> = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            SyncTeamTagsMessage::teamId, 
            ByteBufCodecs.collection(::HashSet, ByteBufCodecs.STRING_UTF8),
            SyncTeamTagsMessage::tags, 
            ::SyncTeamTagsMessage
        )
    }

    override fun type(): CustomPacketPayload.Type<SyncTeamTagsMessage> {
        return TYPE
    }

    fun handle(context: IPayloadContext) {
        context.enqueueWork {
            val file = ClientQuestFile.INSTANCE

            if (file.selfTeamData.teamId == teamId) {
                val teamData = file.selfTeamData

                if (teamData is ITeamDataExtension) {
                    val ext = teamData as ITeamDataExtension

                    val currentTags = ArrayList(ext.`contactQuests$getTags`())
                    currentTags.forEach { ext.`contactQuests$removeTag`(it) }

                    tags.forEach { ext.`contactQuests$unlockTag`(it) }

                    file.refreshGui()
                }
            }
        }
    }
}
