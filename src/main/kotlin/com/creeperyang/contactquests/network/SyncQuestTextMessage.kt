package com.creeperyang.contactquests.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent
import net.minecraftforge.fml.loading.FMLEnvironment
import java.util.function.Supplier

import com.creeperyang.contactquests.bridge.IQuestExtension
import dev.ftb.mods.ftbquests.client.ClientQuestFile
import dev.ftb.mods.ftbquests.quest.Quest
import java.util.Collections

class SyncQuestTextMessage @JvmOverloads constructor(
    val id: Long, val locale: String, val type: Int, content: Any?, val global: Boolean = false
) {
    val content: Any? = if (content is List<*>) Collections.unmodifiableList(ArrayList(content)) else content
    fun encode(buf: FriendlyByteBuf) {
        buf.writeLong(id)
        buf.writeUtf(locale)
        buf.writeByte(type)
        buf.writeBoolean(global)
        buf.writeBoolean(content != null)
        if (content != null) when (type) {
            0, 1 -> buf.writeUtf(content as String)
            2 -> buf.writeCollection(content as List<String>) { b, s -> b.writeUtf(s) }
        }
    }
    companion object {
        fun decode(buf: FriendlyByteBuf): SyncQuestTextMessage {
            val id = buf.readLong()
            val locale = buf.readUtf()
            val type = buf.readByte().toInt()
            require(type in 0..2) { "Invalid text field" }
            val global = buf.readBoolean()
            val content: Any? = if (!buf.readBoolean()) null else when (type) {
                0, 1 -> buf.readUtf()
                else -> buf.readList { it.readUtf() }
            }
            return SyncQuestTextMessage(id, locale, type, content, global)
        }
    }
    fun handle(supplier: Supplier<NetworkEvent.Context>) {
        val context = supplier.get()
        context.enqueueWork { if (FMLEnvironment.dist.isClient) ClientHandler.apply(this) }
        context.packetHandled = true
    }
    private object ClientHandler {
        fun apply(message: SyncQuestTextMessage) = with(message) {
            val file = ClientQuestFile.INSTANCE ?: return@with
            val quest = file.getQuest(id) ?: return@with
            val ext = (quest as Any) as? IQuestExtension ?: return@with
            when (type) {
                0 -> ext.`contactQuests$setTitleOverride`(locale, if (global) null else content as? String)
                1 -> ext.`contactQuests$setSubtitleOverride`(locale, if (global) null else content as? String)
                2 -> ext.`contactQuests$setDescriptionOverride`(
                    locale,
                    if (global) null else (content as? List<*>)?.map { it.toString() }
                )
            }
            if (global) when (type) {
                0 -> quest.setRawTitle(content as? String ?: "")
                1 -> quest.setRawSubtitle(content as? String ?: "")
                2 -> ext.`contactQuests$setNativeDescription`((content as? List<*>)?.map { it.toString() }.orEmpty())
            }
            quest.clearCachedData()
            file.clearCachedData()
            file.getQuestScreen().ifPresent { screen ->
                screen.refreshQuestPanel()
                screen.refreshViewQuestPanel()
                screen.refreshChapterPanel()
            }
        }
    }
}
