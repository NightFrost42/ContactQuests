package com.creeperyang.contactquests.network

import com.creeperyang.contactquests.bridge.ITeamDataExtension
import dev.ftb.mods.ftbquests.client.ClientQuestFile
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.fml.loading.FMLEnvironment
import net.minecraftforge.network.NetworkEvent
import java.util.*
import java.util.function.Supplier

class SyncTeamTagsMessage(val teamId: UUID, tags: Set<String>) {
    val tags: Set<String> = Collections.unmodifiableSet(HashSet(tags))

    fun encode(buf: FriendlyByteBuf) {
        buf.writeUUID(teamId)
        buf.writeCollection(tags) { b, tag -> b.writeUtf(tag) }
    }

    companion object {
        @JvmStatic
        fun decode(buf: FriendlyByteBuf): SyncTeamTagsMessage {
            val teamId = buf.readUUID()
            val tags = buf.readCollection({ HashSet() }) { b -> b.readUtf() }
            return SyncTeamTagsMessage(teamId, tags)
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
        fun apply(message: SyncTeamTagsMessage) = with(message) {
            val file = ClientQuestFile.INSTANCE ?: return@with

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