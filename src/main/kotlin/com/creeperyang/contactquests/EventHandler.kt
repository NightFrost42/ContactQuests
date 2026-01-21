package com.creeperyang.contactquests

import com.creeperyang.contactquests.compat.kubejs.ContactKubeJSPlugin
import com.creeperyang.contactquests.utils.IQuestExtension
import com.creeperyang.contactquests.utils.ITeamDataExtension
import dev.ftb.mods.ftbquests.quest.Quest
import dev.ftb.mods.ftbquests.quest.ServerQuestFile
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.event.entity.player.PlayerInteractEvent
import net.minecraftforge.eventbus.api.SubscribeEvent

object EventHandler {

    @SubscribeEvent
    fun onItemRightClick(event: PlayerInteractEvent.RightClickItem) {
        if (event.level.isClientSide) return

        if (event.hand != InteractionHand.MAIN_HAND) return

        val player = event.entity as? ServerPlayer ?: return
        val stack = event.itemStack

        val tag = stack.tag ?: return
        if (!tag.contains("ContactQuestsUnlockTags", 9)) return

        val tagsNbt = tag.getList("ContactQuestsUnlockTags", 8)
        if (tagsNbt.isEmpty()) return

        val team = FTBTeamsAPI.api().manager.getTeamForPlayer(player).orElse(null) ?: return
        val teamData = ServerQuestFile.INSTANCE.getNullableTeamData(team.id) ?: return

        val newUnlockedTags = mutableListOf<String>()

        if (teamData is ITeamDataExtension) {
            val ext = teamData as ITeamDataExtension
            for (i in 0 until tagsNbt.size) {
                val tagStr = tagsNbt.getString(i)
                if (ext.`contactQuests$unlockTag`(tagStr)) {
                    newUnlockedTags.add(tagStr)
                }
            }
        }

        if (newUnlockedTags.isNotEmpty()) {
            val tagsStr = newUnlockedTags.joinToString(", ")
            player.sendSystemMessage(
                Component.translatable("contactquests.message.tags_unlocked", tagsStr)
                    .withStyle(ChatFormatting.GREEN)
            )

            checkAndNotifyQuests(player, newUnlockedTags)
        }
    }

    @SubscribeEvent
    fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity
        if (player is ServerPlayer && ServerQuestFile.INSTANCE != null) {
            ContactKubeJSPlugin.updatePlayerTaskCache(player)
            ContactKubeJSPlugin.syncAllOverridesToPlayer(player)
        }
    }

    private fun checkAndNotifyQuests(player: ServerPlayer, newTags: List<String>) {
        val file = ServerQuestFile.INSTANCE
        val allQuests = file.allChapters.flatMap { it.quests }

        allQuests.forEach { quest: Quest ->
            val ext = (quest as Any) as? IQuestExtension ?: return@forEach

            val reqTags = ext.`contactQuests$getRequiredTags`()

            if (reqTags.isNotEmpty() && reqTags.any { newTags.contains(it) }) {
                val rawTitle = quest.rawTitle

                val safeTitle: Component = if (rawTitle.isNotBlank()) {
                    Component.literal(rawTitle)
                } else {
                    Component.literal("Quest #${quest.codeString}")
                }

                val openComponent = Component.translatable("contactquests.message.click_to_open", safeTitle)
                    .withStyle { style ->
                        style.withColor(ChatFormatting.GOLD)
                            .withUnderlined(true)
                            .withClickEvent(
                                ClickEvent(
                                    ClickEvent.Action.RUN_COMMAND,
                                    "/contactquests open ${quest.codeString}"
                                )
                            )
                            .withHoverEvent(
                                HoverEvent(
                                    HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("ID: ${quest.codeString}")
                                )
                            )
                    }

                player.sendSystemMessage(
                    Component.translatable(
                        "contactquests.message.new_quest_found",
                        openComponent
                    )
                )
            }
        }
    }
}