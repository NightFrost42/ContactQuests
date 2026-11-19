package com.creeperyang.contactquests.network

import com.creeperyang.contactquests.ContactQuests
import com.creeperyang.contactquests.bridge.IQuestExtension
import dev.ftb.mods.ftbquests.client.ClientQuestFile
import dev.ftb.mods.ftbquests.quest.translation.TranslationKey
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.Collections

class SyncQuestTextMessage @JvmOverloads constructor(
    val id: Long, val locale: String, val type: Int, content: Any?, val global: Boolean = false
) :
    CustomPacketPayload {
    val content: Any? = if (content is List<*>) Collections.unmodifiableList(ArrayList(content)) else content

    override fun type(): CustomPacketPayload.Type<SyncQuestTextMessage> {
        return TYPE
    }

    fun write(buf: RegistryFriendlyByteBuf) {
        buf.writeLong(id)
        buf.writeUtf(locale)
        buf.writeByte(type)
        buf.writeBoolean(global)

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
            val file = ClientQuestFile.INSTANCE
            if (file != null) {
                val quest = file.get(id)

                if (quest is IQuestExtension) {
                    val ext = quest as IQuestExtension

                    if (global) {
                        when (type) {
                            0 -> {
                                file.translationManager.addTranslation(quest, locale, TranslationKey.TITLE, content as String)
                                ext.`contactQuests$setTitleOverride`(locale, null)
                            }
                            1 -> {
                                file.translationManager.addTranslation(quest, locale, TranslationKey.QUEST_SUBTITLE, content as String)
                                ext.`contactQuests$setSubtitleOverride`(locale, null)
                            }
                            2 -> {
                                @Suppress("UNCHECKED_CAST")
                                file.translationManager.addTranslation(quest, locale, TranslationKey.QUEST_DESC, content as List<String>)
                                ext.`contactQuests$setDescriptionOverride`(locale, null)
                            }
                        }
                    } else if (content == null) {
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
                    file.clearCachedData()
                    // 全量刷新会重建 QuestScreen 并关掉当前详情弹窗；仅修改文案时只需重绘面板即可。
                    file.getQuestScreen().ifPresent { screen ->
                        screen.refreshQuestPanel()
                        screen.refreshViewQuestPanel()
                        screen.refreshChapterPanel()
                    }
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
                val global = buf.readBoolean()
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
                SyncQuestTextMessage(id, locale, type, content, global)
            }
        )
    }
}
