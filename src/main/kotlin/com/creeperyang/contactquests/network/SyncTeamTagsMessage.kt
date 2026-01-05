package com.creeperyang.contactquests.network

import com.creeperyang.contactquests.utils.ITeamDataExtension
import dev.ftb.mods.ftbquests.client.ClientQuestFile
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent
import java.util.*
import java.util.function.Supplier

class SyncTeamTagsMessage(val teamId: UUID, val tags: Set<String>) {

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
        ctx.packetHandled = true
    }
}