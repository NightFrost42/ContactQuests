package com.creeperyang.contactquests.network

import com.creeperyang.contactquests.ContactQuests
import com.creeperyang.contactquests.bridge.ITeamDataExtension
import dev.ftb.mods.ftbquests.client.ClientQuestFile
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.fml.loading.FMLEnvironment
import net.minecraftforge.network.NetworkEvent
import java.util.*
import java.util.function.Supplier

class SyncTeamExtensionMessage {
    val teamId: UUID
    val tags: Set<String>
    val forcedQuests: Set<Long>
    val blockedQuests: Set<Long>
    val postcardData: Map<Long, String>
    val redPacketData: Map<Long, String>

    constructor(
        teamId: UUID,
        tags: Set<String>,
        forcedQuests: Set<Long>,
        blockedQuests: Set<Long>,
        postcardData: Map<Long, String>,
        redPacketData: Map<Long, String>
    ) {
        this.teamId = teamId
        this.tags = Collections.unmodifiableSet(HashSet(tags))
        this.forcedQuests = Collections.unmodifiableSet(HashSet(forcedQuests))
        this.blockedQuests = Collections.unmodifiableSet(HashSet(blockedQuests))
        this.postcardData = Collections.unmodifiableMap(HashMap(postcardData))
        this.redPacketData = Collections.unmodifiableMap(HashMap(redPacketData))
    }

    constructor(buf: FriendlyByteBuf) {
        this.teamId = buf.readUUID()

        val tagsCount = buf.readVarInt()
        val tagsSet = HashSet<String>(tagsCount)
        for (i in 0 until tagsCount) {
            tagsSet.add(buf.readUtf())
        }
        this.tags = tagsSet

        val forcedCount = buf.readVarInt()
        val forcedSet = HashSet<Long>(forcedCount)
        for (i in 0 until forcedCount) {
            forcedSet.add(buf.readLong())
        }
        this.forcedQuests = forcedSet

        val blockedCount = buf.readVarInt()
        val blockedSet = HashSet<Long>(blockedCount)
        for (i in 0 until blockedCount) {
            blockedSet.add(buf.readLong())
        }
        this.blockedQuests = blockedSet

        val postcardCount = buf.readVarInt()
        val postcardMap = HashMap<Long, String>(postcardCount)
        for (i in 0 until postcardCount) {
            val key = buf.readLong()
            val value = buf.readUtf()
            postcardMap[key] = value
        }
        this.postcardData = postcardMap

        val redPacketCount = buf.readVarInt()
        val redPacketMap = HashMap<Long, String>(redPacketCount)
        for (i in 0 until redPacketCount) {
            val key = buf.readLong()
            val value = buf.readUtf()
            redPacketMap[key] = value
        }
        this.redPacketData = redPacketMap
    }

    fun encode(buf: FriendlyByteBuf) {
        buf.writeUUID(teamId)

        buf.writeVarInt(tags.size)
        tags.forEach { buf.writeUtf(it) }

        buf.writeVarInt(forcedQuests.size)
        forcedQuests.forEach { buf.writeLong(it) }

        buf.writeVarInt(blockedQuests.size)
        blockedQuests.forEach { buf.writeLong(it) }

        buf.writeVarInt(postcardData.size)
        postcardData.forEach { (k, v) ->
            buf.writeLong(k)
            buf.writeUtf(v)
        }

        buf.writeVarInt(redPacketData.size)
        redPacketData.forEach { (k, v) ->
            buf.writeLong(k)
            buf.writeUtf(v)
        }
    }

    fun handle(contextSupplier: Supplier<NetworkEvent.Context>) {
        val context = contextSupplier.get()
        context.enqueueWork {
            if (FMLEnvironment.dist.isClient) ClientHandler.apply(this)
        }
        context.packetHandled = true
    }
    private object ClientHandler {
        fun apply(message: SyncTeamExtensionMessage) = with(message) {
            val file = ClientQuestFile.INSTANCE ?: return@with

            if (file != null && file.selfTeamData.teamId == teamId) {
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