package com.creeperyang.contactquests.compat.kubejs

import com.creeperyang.contactquests.network.NetworkHandler
import com.creeperyang.contactquests.network.SyncQuestTextMessage
import dev.ftb.mods.ftbquests.net.SyncQuestsMessage
import dev.ftb.mods.ftbquests.quest.Quest
import dev.ftb.mods.ftbquests.quest.QuestObjectBase
import dev.ftb.mods.ftbquests.quest.ServerQuestFile
import dev.ftb.mods.ftbquests.quest.reward.Reward
import net.minecraft.nbt.Tag
import net.minecraft.server.TickTask
import net.minecraft.server.level.ServerPlayer
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

/** FTB 2001 native NBT synchronization and per-save text override broadcasting. */
internal object KubeJSQuestSync {
    val LOGGER: Logger = LogManager.getLogger("contactquests-kubejs")

    fun syncObject(obj: QuestObjectBase, saveToConfig: Boolean = true) {
        val file = ServerQuestFile.INSTANCE
        obj.clearCachedData()
        file.clearCachedData()
        file.clearCachedProgress()
        if (saveToConfig) {
            file.markDirty()
            file.saveNow()
        }
        val server = file.server ?: return
        SyncQuestsMessage(file).sendToAll(server)
        server.playerList.players.forEach { syncAllOverridesToPlayer(it) }
        server.tell(TickTask(server.tickCount + 1) {
            server.playerList.players.forEach { syncAllOverridesToPlayer(it) }
        })
    }

    fun syncAllOverridesToPlayer(player: ServerPlayer) {
        val savedData = QuestOverrideSavedData.get(player.serverLevel())
        savedData.overrides.forEach { (id, tag) ->
            if (tag.contains("title", Tag.TAG_COMPOUND.toInt())) {
                val map = tag.getCompound("title")
                map.allKeys.forEach { locale ->
                    NetworkHandler.sendToPlayer(SyncQuestTextMessage(id, locale, 0, map.getString(locale)), player)
                }
            }
            if (tag.contains("subtitle", Tag.TAG_COMPOUND.toInt())) {
                val map = tag.getCompound("subtitle")
                map.allKeys.forEach { locale ->
                    NetworkHandler.sendToPlayer(SyncQuestTextMessage(id, locale, 1, map.getString(locale)), player)
                }
            }
            if (tag.contains("description", Tag.TAG_COMPOUND.toInt())) {
                val map = tag.getCompound("description")
                map.allKeys.forEach { locale ->
                    val list = map.getList(locale, Tag.TAG_STRING.toInt()).map { it.asString }
                    NetworkHandler.sendToPlayer(SyncQuestTextMessage(id, locale, 2, list), player)
                }
            }
        }
    }

    fun parseId(idStr: String): Long? {
        return try {
            java.lang.Long.parseUnsignedLong(idStr, 16)
        } catch (_: Exception) {
            try {
                idStr.toLong()
            } catch (_: Exception) {
                null
            }
        }
    }

    fun resolveId(id: Any): Long? =
        if (id is Number) id.toLong() else parseId(id.toString())

    fun getQuest(id: Any): Quest? {
        val longId = resolveId(id) ?: return null
        return ServerQuestFile.INSTANCE.get(longId) as? Quest
    }

    fun getReward(id: Any): Reward? {
        val longId = resolveId(id) ?: return null
        val file = ServerQuestFile.INSTANCE

        val reward = file.getReward(longId)
        if (reward != null) return reward

        LOGGER.warn("ContactQuests: 索引中未找到奖励 $longId，正在扫描 ${file.allChapters.size} 个章节...")
        var questCount = 0
        var rewardCount = 0

        for (chapter in file.allChapters) {
            for (quest in chapter.quests) {
                questCount++
                for (r in quest.rewards) {
                    rewardCount++
                    if (r.id == longId) return r
                }
            }
        }

        LOGGER.warn("ContactQuests: 深度扫描完成（共检查 $questCount 个任务、$rewardCount 个奖励），仍未找到奖励 $longId。")
        return null
    }
}
