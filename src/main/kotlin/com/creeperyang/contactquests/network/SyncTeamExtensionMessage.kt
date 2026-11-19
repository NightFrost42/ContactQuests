package com.creeperyang.contactquests.network

import com.creeperyang.contactquests.ContactQuests
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

class SyncTeamExtensionMessage(
    val teamId: UUID,
    tags: Set<String>,
    forcedQuests: Set<Long>,
    blockedQuests: Set<Long>,
    postcardData: Map<Long, String>,
    redPacketData: Map<Long, String>
) : CustomPacketPayload {
    // 拷贝为不可变快照，防止 Netty 异步编码发送期间服务端并发修改原团队集合。
    val tags: Set<String> = Collections.unmodifiableSet(HashSet(tags))
    val forcedQuests: Set<Long> = Collections.unmodifiableSet(HashSet(forcedQuests))
    val blockedQuests: Set<Long> = Collections.unmodifiableSet(HashSet(blockedQuests))
    val postcardData: Map<Long, String> = Collections.unmodifiableMap(HashMap(postcardData))
    val redPacketData: Map<Long, String> = Collections.unmodifiableMap(HashMap(redPacketData))

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath("contactquests", "sync_team_extension")
        val TYPE = CustomPacketPayload.Type<SyncTeamExtensionMessage>(ID)

        private val LONG_CODEC: StreamCodec<RegistryFriendlyByteBuf, Long> =
            StreamCodec.of({ buf, value -> buf.writeLong(value) }, { buf -> buf.readLong() })

        val LONG_SET_CODEC: StreamCodec<RegistryFriendlyByteBuf, Set<Long>> = ByteBufCodecs.collection(
            ::HashSet,
            StreamCodec.of({ buf, value -> buf.writeLong(value) }, { buf -> buf.readLong() })
        )

        val MAP_CODEC: StreamCodec<RegistryFriendlyByteBuf, Map<Long, String>> = ByteBufCodecs.map(
            { HashMap() },
            LONG_CODEC,
            ByteBufCodecs.STRING_UTF8
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
            MAP_CODEC,
            SyncTeamExtensionMessage::postcardData,
            MAP_CODEC,
            SyncTeamExtensionMessage::redPacketData,
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

                    val currentTags = ArrayList(ext.`contactQuests$getTags`())
                    currentTags.forEach { ext.`contactQuests$removeTag`(it) }
                    tags.forEach { ext.`contactQuests$unlockTag`(it) }

                    ext.`contactQuests$setForcedQuests`(forcedQuests)
                    ext.`contactQuests$setBlockedQuests`(blockedQuests)

                    ext.`contactQuests$setAllPostcardTexts`(postcardData)
                    ext.`contactQuests$setAllRedPacketBlessings`(redPacketData)

                    if (postcardData.isNotEmpty()) {
                        ContactQuests.info("[Debug] Applied postcard texts: $postcardData")
                    }

                    file.refreshGui()
                }
            }
        }
    }
}
