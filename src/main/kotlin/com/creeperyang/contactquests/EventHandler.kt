package com.creeperyang.contactquests

import com.creeperyang.contactquests.utils.IQuestExtension
import com.creeperyang.contactquests.utils.ITeamDataExtension

import dev.ftb.mods.ftbquests.quest.Quest
import dev.ftb.mods.ftbquests.quest.ServerQuestFile
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent

object EventHandler {

    @SubscribeEvent
    fun onItemRightClick(event: PlayerInteractEvent.RightClickItem) {
        if (event.level.isClientSide) return
        val player = event.entity as? ServerPlayer ?: return
        val stack = event.itemStack

        val customData = stack[DataComponents.CUSTOM_DATA] ?: return
        if (!customData.contains("ContactQuestsUnlockTags")) return

        val tagsNbt = customData.copyTag().getList("ContactQuestsUnlockTags", 8) // 8 = String
        if (tagsNbt.isEmpty()) return

        val team = FTBTeamsAPI.api().manager.getTeamForPlayer(player).orElse(null) ?: return
        val teamData = ServerQuestFile.INSTANCE.getNullableTeamData(team.id) ?: return

        val newUnlockedTags = mutableListOf<String>()

        if (teamData is ITeamDataExtension) {
            tagsNbt.forEach {
                val tagStr = it.asString
                if ((teamData as ITeamDataExtension).`contactQuests$unlockTag`(tagStr)) {
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

    private fun checkAndNotifyQuests(player: ServerPlayer, newTags: List<String>) {
        val file = ServerQuestFile.INSTANCE
        val allQuests = file.allChapters.flatMap { it.quests }

        allQuests.forEach { quest: Quest ->
            val ext = (quest as Any) as? IQuestExtension ?: return@forEach

            val reqTags = ext.`contactQuests$getRequiredTags`()

            if (reqTags.isNotEmpty() && reqTags.any { newTags.contains(it) }) {
                val openComponent = Component.translatable("contactquests.message.click_to_open", quest.title)
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