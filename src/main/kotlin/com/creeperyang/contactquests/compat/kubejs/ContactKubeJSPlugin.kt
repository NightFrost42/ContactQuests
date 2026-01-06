package com.creeperyang.contactquests.compat.kubejs

import com.creeperyang.contactquests.data.CollectionSavedData
import com.creeperyang.contactquests.quest.reward.PostcardReward
import com.creeperyang.contactquests.quest.task.PostcardTask
import com.creeperyang.contactquests.utils.ITeamDataExtension
import dev.ftb.mods.ftbquests.quest.ServerQuestFile
import dev.ftb.mods.ftbquests.quest.TeamData
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI
import dev.latvian.mods.kubejs.event.EventGroup
import dev.latvian.mods.kubejs.event.KubeEvent
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import org.apache.logging.log4j.LogManager
import java.util.function.BiFunction

object ContactKubeJSPlugin {
    val GROUP = EventGroup.of("ContactEvents")

    val REGISTER_REPLACERS = GROUP.server("registerReplacers") { RegisterReplacersEvent::class.java }

    private val LOGGER = LogManager.getLogger("contactquests-kubejs")

    @JvmStatic
    fun init() {
        LOGGER.info("Initializing ContactQuests KubeJS Plugin Helper...")
    }

    @JvmStatic
    fun reload() {
        REGISTER_REPLACERS.post(RegisterReplacersEvent())
        LOGGER.info("Reloaded ContactQuests KubeJS integration")
    }

    @JvmStatic
    fun getData(player: Player): TeamData {
        val team = FTBTeamsAPI.api().manager.getTeamForPlayerID(player.uuid).orElseThrow {
            RuntimeException("ContactQuests: Could not find FTB Team for player ${player.name.string}")
        }
        return ServerQuestFile.INSTANCE.getOrCreateTeamData(team)
    }

    @JvmStatic
    fun addTag(team: TeamData, tag: String): Boolean {
        return (team as ITeamDataExtension).`contactQuests$unlockTag`(tag)
    }

    @JvmStatic
    fun removeTag(team: TeamData, tag: String): Boolean {
        return (team as ITeamDataExtension).`contactQuests$removeTag`(tag)
    }

    @JvmStatic
    fun hasTag(team: TeamData, tag: String): Boolean {
        return (team as ITeamDataExtension).`contactQuests$hasTag`(tag)
    }

    @JvmStatic
    fun getTags(team: TeamData): Collection<String> {
        return (team as ITeamDataExtension).`contactQuests$getTags`()
    }

    class RegisterReplacersEvent : KubeEvent {
        fun registerPostcardTask(key: String, callback: BiFunction<Player, TeamData, String>) {
            PostcardTask.PostcardPlaceholderSupport.register(key) { p, t -> callback.apply(p, t) }
        }

        fun registerPostcardReward(callback: BiFunction<String, ServerPlayer, String>) {
            PostcardReward.registerReplacer { text, player -> callback.apply(text, player) }
        }

        fun registerNpcReply(callback: BiFunction<String, CollectionSavedData.ReplacerContext, String?>) {
            CollectionSavedData.registerReplacer { text, ctx -> callback.apply(text, ctx) }
        }
    }
}