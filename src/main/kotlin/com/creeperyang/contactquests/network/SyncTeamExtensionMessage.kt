package com.creeperyang.contactquests.network

import com.creeperyang.contactquests.utils.ITeamDataExtension
import dev.ftb.mods.ftbquests.client.ClientQuestFile
import net.minecraft.core.UUIDUtil
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.*

class SyncTeamExtensionMessage(
    val teamId: UUID,
    val tags: Set<String>,
    val forcedQuests: Set<Long>,
    val blockedQuests: Set<Long>
) : CustomPacketPayload {

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath("contactquests", "sync_team_extension")
        val TYPE = CustomPacketPayload.Type<SyncTeamExtensionMessage>(ID)

        // Helper to encode/decode Set<Long>
        val LONG_SET_CODEC: StreamCodec<RegistryFriendlyByteBuf, Set<Long>> = ByteBufCodecs.collection(
            ::HashSet,
            StreamCodec.of({ buf, value -> buf.writeLong(value) }, { buf -> buf.readLong() })
        )

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, SyncTeamExtensionMessage> = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            SyncTeamExtensionMessage::teamId,
            ByteBufCodecs.collection(::HashSet, ByteBufCodecs.STRING_UTF8),
            SyncTeamExtensionMessage::tags,
            LONG_SET_CODEC,
            SyncTeamExtensionMessage::forcedQuests,
            LONG_SET_CODEC,
            SyncTeamExtensionMessage::blockedQuests,
            ::SyncTeamExtensionMessage
        )
    }

    override fun type(): CustomPacketPayload.Type<SyncTeamExtensionMessage> {
        return TYPE
    }

    fun handle(context: IPayloadContext) {
        context.enqueueWork {
            val file = ClientQuestFile.INSTANCE

            if (file.selfTeamData.teamId == teamId) {
                val teamData = file.selfTeamData

                if (teamData is ITeamDataExtension) {
                    val ext = teamData as ITeamDataExtension

                    // Sync Tags (Replace all)
                    val currentTags = ArrayList(ext.`contactQuests$getTags`())
                    currentTags.forEach { ext.`contactQuests$removeTag`(it) }
                    tags.forEach { ext.`contactQuests$unlockTag`(it) }

                    // Sync Forced/Blocked Quests
                    ext.`contactQuests$setForcedQuests`(forcedQuests)
                    ext.`contactQuests$setBlockedQuests`(blockedQuests)

                    file.refreshGui()
                }
            }
        }
    }
}