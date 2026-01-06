package com.creeperyang.contactquests.compat.kubejs

import com.creeperyang.contactquests.data.CollectionSavedData
import com.creeperyang.contactquests.quest.reward.PostcardReward
import com.creeperyang.contactquests.quest.task.PostcardTask
import dev.ftb.mods.ftbquests.quest.TeamData
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