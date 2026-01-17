package com.creeperyang.contactquests.network

import com.creeperyang.contactquests.ContactQuests
import dev.ftb.mods.ftbquests.client.FTBQuestsClient
import dev.ftb.mods.ftbquests.quest.translation.TranslationKey
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

data class SyncQuestDescriptionMessage(val id: Long, val locale: String, val description: List<String>) :
    CustomPacketPayload {

    constructor(buf: RegistryFriendlyByteBuf) : this(
        buf.readLong(),
        buf.readUtf(), // read locale
        buf.readList { it.readUtf() }
    )

    override fun type(): CustomPacketPayload.Type<SyncQuestDescriptionMessage> {
        return TYPE
    }

    fun write(buf: RegistryFriendlyByteBuf) {
        buf.writeLong(id)
        buf.writeUtf(locale) // write locale
        buf.writeCollection(description) { b, s -> b.writeUtf(s) }
    }

    fun handle(context: IPayloadContext) {
        context.enqueueWork {
            val file = FTBQuestsClient.getClientQuestFile()
            if (file != null) {
                val quest = file[id]
                if (quest != null) {
                    file.translationManager.addTranslation(
                        quest,
                        locale,
                        TranslationKey.QUEST_DESC,
                        ArrayList(description)
                    )

                    quest.clearCachedData()

                    file.refreshGui()
                }
            }
        }
    }

    companion object {
        val TYPE = CustomPacketPayload.Type<SyncQuestDescriptionMessage>(
            ResourceLocation.fromNamespaceAndPath(ContactQuests.ID, "sync_quest_description")
        )

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, SyncQuestDescriptionMessage> =
            CustomPacketPayload.codec(
                { msg, buf -> msg.write(buf) },
                ::SyncQuestDescriptionMessage
            )
    }
}