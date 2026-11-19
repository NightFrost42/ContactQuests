package com.creeperyang.contactquests.compat.kubejs

import com.creeperyang.contactquests.network.SyncQuestTextMessage
import dev.ftb.mods.ftbquests.quest.Quest
import dev.ftb.mods.ftbquests.quest.QuestObjectBase
import dev.ftb.mods.ftbquests.quest.ServerQuestFile
import dev.ftb.mods.ftbquests.quest.reward.Reward
import net.minecraft.nbt.Tag
import net.minecraft.server.MinecraftServer
import net.minecraft.server.TickTask
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

/** 负责 KubeJS 侧对任务图谱对象的同步广播、ID 解析及按存档隔离的文本覆写同步。 */
internal object KubeJSQuestSync {
    val LOGGER: Logger = LogManager.getLogger("contactquests-kubejs")

    fun syncObject(obj: QuestObjectBase, saveToConfig: Boolean = true) {
        obj.clearCachedData()
        handleConfigState(saveToConfig)

        val server = ServerQuestFile.INSTANCE.server ?: return
        val message = createSyncPacket() ?: run {
            fallbackRefresh("创建全量任务同步包失败")
            return
        }

        val sent = sendViaNetworkHelper(server, message) || sendViaArchitectury(server, message)
        if (!sent) {
            fallbackRefresh("全量任务同步包广播失败")
        }

        resendAllOverrides()
    }

    private fun resendAllOverrides() {
        val server = ServerQuestFile.INSTANCE.server ?: return

        server.tell(TickTask(server.tickCount + 1) {
            server.playerList.players.forEach { player ->
                // FTB 全量同步会重置客户端的翻译表，因此需要先恢复原生翻译表再补发按存档覆写的文本。
                ServerQuestFile.INSTANCE.translationManager.sendTranslationsToPlayer(player)
                syncAllOverridesToPlayer(player)
            }
            LOGGER.info("ContactQuests: 已向 ${server.playerList.playerCount} 名玩家补发原生翻译与存档覆写文本（延迟 1 tick）。")
        })
    }

    private fun handleConfigState(saveToConfig: Boolean) {
        if (saveToConfig) {
            ServerQuestFile.INSTANCE.markDirty()
            ServerQuestFile.INSTANCE.saveNow()
            LOGGER.info("ContactQuests: 全局任务修改已保存到磁盘。")
        }
    }

    private fun createSyncPacket(): Any? {
        val packetClasses = listOf(
            "dev.ftb.mods.ftbquests.net.SyncQuestsMessage",
            "dev.ftb.mods.ftbquests.network.SyncQuestsMessage"
        )

        return packetClasses.firstNotNullOfOrNull { className ->
            runCatching {
                val msgClass = Class.forName(className)
                msgClass.constructors.firstOrNull { ctor ->
                    ctor.parameterCount == 1 &&
                        ctor.parameterTypes[0].isAssignableFrom(ServerQuestFile.INSTANCE.javaClass)
                }?.newInstance(ServerQuestFile.INSTANCE)
            }.getOrNull()
        }
    }

    private fun sendViaNetworkHelper(server: Any, message: Any): Boolean {
        return runCatching {
            val helperClass = Class.forName("dev.ftb.mods.ftblibrary.util.NetworkHelper")
            val sendMethod = helperClass.methods.find { m ->
                m.name == "sendToAll" &&
                    m.parameterCount == 2 &&
                    m.parameterTypes[0] == MinecraftServer::class.java &&
                    m.parameterTypes[1].isInstance(message)
            } ?: return false

            sendMethod.invoke(null, server, message)
            LOGGER.info("ContactQuests: 已通过 NetworkHelper 发送全量同步包。")
            true
        }.getOrDefault(false)
    }

    private fun sendViaArchitectury(server: MinecraftServer, message: Any): Boolean {
        return runCatching {
            val netManagerClass = Class.forName("dev.architectury.networking.NetworkManager")
            val sendMethod = netManagerClass.methods.find { m ->
                m.name == "sendToPlayer" &&
                    m.parameterCount == 2 &&
                    m.parameterTypes[0] == ServerPlayer::class.java
            } ?: return false

            var sentCount = 0
            for (player in server.playerList.players) {
                sendMethod.invoke(null, player, message)
                sentCount++
            }

            if (sentCount > 0) {
                LOGGER.info("ContactQuests: 已通过 NetworkManager 向 $sentCount 名玩家发送全量同步包。")
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    private fun fallbackRefresh(reason: String) {
        ServerQuestFile.INSTANCE.refreshGui()
        LOGGER.warn("ContactQuests: $reason，已退化为刷新 GUI。")
    }

    fun syncAllOverridesToPlayer(player: ServerPlayer) {
        val savedData = QuestOverrideSavedData.get(player.serverLevel())
        savedData.overrides.forEach { (id, tag) ->
            if (tag.contains("title", Tag.TAG_COMPOUND.toInt())) {
                tag.getCompound("title").allKeys.forEach { locale ->
                    PacketDistributor.sendToPlayer(
                        player,
                        SyncQuestTextMessage(id, locale, 0, tag.getCompound("title").getString(locale))
                    )
                }
            }
            if (tag.contains("subtitle", Tag.TAG_COMPOUND.toInt())) {
                tag.getCompound("subtitle").allKeys.forEach { locale ->
                    PacketDistributor.sendToPlayer(
                        player,
                        SyncQuestTextMessage(id, locale, 1, tag.getCompound("subtitle").getString(locale))
                    )
                }
            }
            if (tag.contains("description", Tag.TAG_COMPOUND.toInt())) {
                val map = tag.getCompound("description")
                map.allKeys.forEach { locale ->
                    val list = map.getList(locale, Tag.TAG_STRING.toInt()).map { it.asString }
                    PacketDistributor.sendToPlayer(player, SyncQuestTextMessage(id, locale, 2, list))
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
