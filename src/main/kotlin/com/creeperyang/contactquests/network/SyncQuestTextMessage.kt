package com.creeperyang.contactquests.network

import com.creeperyang.contactquests.utils.IQuestExtension
import com.creeperyang.contactquests.utils.IQuestObjectBaseExtension
import dev.ftb.mods.ftbquests.client.ClientQuestFile
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier

class SyncQuestTextMessage {
    val id: Long
    val locale: String
    val type: Int
    val content: Any?

    constructor(id: Long, locale: String, type: Int, content: Any?) {
        this.id = id
        this.locale = locale
        this.type = type
        this.content = content
    }

    constructor(buf: FriendlyByteBuf) {
        this.id = buf.readLong()
        this.locale = buf.readUtf()
        this.type = buf.readByte().toInt()

        val hasContent = buf.readBoolean()
        this.content = if (hasContent) {
            when (type) {
                0, 1 -> buf.readUtf()
                2 -> {
                    val size = buf.readVarInt()
                    val list = ArrayList<String>(size)
                    for (i in 0 until size) {
                        list.add(buf.readUtf())
                    }
                    list
                }

                else -> null
            }
        } else {
            null
        }
    }

    fun encode(buf: FriendlyByteBuf) {
        buf.writeLong(id)
        buf.writeUtf(locale)
        buf.writeByte(type)

        if (content == null) {
            buf.writeBoolean(false)
        } else {
            buf.writeBoolean(true)
            when (type) {
                0, 1 -> buf.writeUtf(content as String)
                2 -> {
                    val list = content as List<*>
                    buf.writeVarInt(list.size)
                    list.forEach { buf.writeUtf(it.toString()) }
                }
            }
        }
    }

    fun handle(contextSupplier: Supplier<NetworkEvent.Context>) {
        val context = contextSupplier.get()
        context.enqueueWork {
            val file = ClientQuestFile.INSTANCE

            val quest = file.get(id)

            if (quest is IQuestExtension) {
                val ext = quest as IQuestExtension
                val extBase = ext as IQuestObjectBaseExtension

                if (content == null) {
                    when (type) {
                        0 -> extBase.`contactQuests$setTitleOverride`(locale, null)
                        1 -> ext.`contactQuests$setSubtitleOverride`(locale, null)
                        2 -> ext.`contactQuests$setDescriptionOverride`(locale, null)
                    }
                } else {
                    when (type) {
                        0 -> extBase.`contactQuests$setTitleOverride`(locale, content as String)
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
        context.packetHandled = true
    }
}