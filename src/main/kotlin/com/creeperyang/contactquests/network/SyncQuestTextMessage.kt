package com.creeperyang.contactquests.network

import com.creeperyang.contactquests.ContactQuests
import com.creeperyang.contactquests.utils.IQuestExtension
import dev.ftb.mods.ftbquests.client.FTBQuestsClient
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

data class SyncQuestTextMessage(val id: Long, val locale: String, val type: Int, val content: Any?) :
    CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<SyncQuestTextMessage> {
        return TYPE
    }

    fun write(buf: RegistryFriendlyByteBuf) {
        buf.writeLong(id)
        buf.writeUtf(locale)
        buf.writeByte(type)

        if (content == null) {
            buf.writeBoolean(false)
        } else {
            buf.writeBoolean(true)
            when (type) {
                0, 1 -> buf.writeUtf(content as String)
                2 -> buf.writeCollection(content as List<String>) { b, s -> b.writeUtf(s) }
            }
        }
    }

    fun handle(context: IPayloadContext) {
        context.enqueueWork {
            val file = FTBQuestsClient.getClientQuestFile()
            if (file != null) {
                val quest = file.get(id)

                if (quest is IQuestExtension) {
                    val ext = quest as IQuestExtension

                    if (content == null) {
                        when (type) {
                            0 -> ext.`contactQuests$setTitleOverride`(locale, null)
                            1 -> ext.`contactQuests$setSubtitleOverride`(locale, null)
                            2 -> ext.`contactQuests$setDescriptionOverride`(locale, null)
                        }
                    } else {
                        when (type) {
                            0 -> ext.`contactQuests$setTitleOverride`(locale, content as String)
                            1 -> ext.`contactQuests$setSubtitleOverride`(locale, content as String)
                            2 -> {
                                val list = if (content is List<*>) {
                                    val l = ArrayList<String>()
                                    content.forEach { l.add(it.toString()) }
                                    l
                                } else {
                                    ArrayList()
                                }
                                ext.`contactQuests$setDescriptionOverride`(locale, list)
                            }
                        }
                    }

                    quest.clearCachedData()
                    file.refreshGui()
                }
            }
        }
    }

    companion object {
        val TYPE = CustomPacketPayload.Type<SyncQuestTextMessage>(
            ResourceLocation.fromNamespaceAndPath(ContactQuests.ID, "sync_quest_text")
        )

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, SyncQuestTextMessage> = StreamCodec.of(
            { buf, msg -> msg.write(buf) },
            { buf ->
                val id = buf.readLong()
                val locale = buf.readUtf()
                val type = buf.readByte().toInt()
                val hasContent = buf.readBoolean()

                val content: Any? = if (hasContent) {
                    when (type) {
                        0, 1 -> buf.readUtf()
                        2 -> buf.readList { it.readUtf() }
                        else -> null
                    }
                } else {
                    null
                }
                SyncQuestTextMessage(id, locale, type, content)
            }
        )
    }
}