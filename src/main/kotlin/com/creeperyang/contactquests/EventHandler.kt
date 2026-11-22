package com.creeperyang.contactquests

import com.creeperyang.contactquests.compat.kubejs.ContactKubeJSPlugin
import com.creeperyang.contactquests.bridge.IQuestExtension
import com.creeperyang.contactquests.bridge.ITeamDataExtension
import com.creeperyang.contactquests.utils.QuestObjectText
import dev.ftb.mods.ftbquests.quest.Quest
import dev.ftb.mods.ftbquests.quest.ServerQuestFile
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.ModList
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.event.entity.player.PlayerInteractEvent

object EventHandler {
    init {
        dev.ftb.mods.ftbteams.api.event.TeamEvent.PLAYER_LOGGED_IN.register { syncTeam(it.player) }
        dev.ftb.mods.ftbteams.api.event.TeamEvent.PLAYER_CHANGED.register { event ->
            event.player?.let { syncTeam(it) }
        }
    }

    private fun syncTeam(player: ServerPlayer) {
        if (!FTBTeamsAPI.api().isManagerLoaded) return
        val file = ServerQuestFile.INSTANCE ?: return
        val team = FTBTeamsAPI.api().manager.getTeamForPlayer(player).orElse(null) ?: return
        val data = file.getNullableTeamData(team.id) as? ITeamDataExtension ?: return
        com.creeperyang.contactquests.network.NetworkHandler.sendToPlayer(
            com.creeperyang.contactquests.network.SyncTeamExtensionMessage(
                team.id, data.`contactQuests$getTags`().toSet(),
                data.`contactQuests$getForcedQuests`().toSet(), data.`contactQuests$getBlockedQuests`().toSet(),
                data.`contactQuests$getAllPostcardTexts`(), data.`contactQuests$getAllRedPacketBlessings`()
            ), player)
    }

    @SubscribeEvent
    fun onReload(event: net.minecraftforge.event.AddReloadListenerEvent) {
        event.addListener(net.minecraft.server.packs.resources.ResourceManagerReloadListener {
            com.creeperyang.contactquests.config.NpcConfigManager.reload()
            com.creeperyang.contactquests.config.ChoiceTemplateConfig.reload()
            com.creeperyang.contactquests.llm.NpcLlmConfig.reload()
        })
    }

    @SubscribeEvent
    fun onServerTick(event: net.minecraftforge.event.TickEvent.ServerTickEvent) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return
        val level = event.server.overworld()
        com.creeperyang.contactquests.data.TaskDeliverySavedData[level].tick(level)
        com.creeperyang.contactquests.data.ChoiceRewardSavedData.get(level).tick(level)
        com.creeperyang.contactquests.llm.NpcLlmService.tick(level)
        com.creeperyang.contactquests.data.RewardDistributionManager.onServerTick(level)
    }


    @SubscribeEvent
    fun onItemRightClick(event: PlayerInteractEvent.RightClickItem) {
        if (event.level.isClientSide) return

        if (event.hand != InteractionHand.MAIN_HAND) return

        val player = event.entity as? ServerPlayer ?: return
        val stack = event.itemStack

        val customData = stack.tag ?: return
        if (!customData.contains("ContactQuestsUnlockTags")) return

        val tagsNbt = customData.getList("ContactQuestsUnlockTags", 8) // 8 = NBT 字符串类型
        if (tagsNbt.isEmpty()) return

        val team = FTBTeamsAPI.api().manager.getTeamForPlayer(player).orElse(null) ?: return
        val teamData = ServerQuestFile.INSTANCE.getNullableTeamData(team.id) ?: return

        // 仅在任务状态真实从“锁定”转变为“可开启”时才上报提示；仅解锁单个 Tag 不足以绕过 ALL 标签、原生依赖或互斥规则。
        val startableBefore = ServerQuestFile.INSTANCE.allChapters.flatMap { it.quests }
            .associate { it.id to teamData.canStartTasks(it) }

        val newUnlockedTags = mutableListOf<String>()

        if (teamData is ITeamDataExtension) {
            com.creeperyang.contactquests.quest.QuestStateEvents.withActor(player, "physical_item") {
                tagsNbt.forEach {
                    val tagStr = it.asString
                    if ((teamData as ITeamDataExtension).`contactQuests$unlockTag`(tagStr)) {
                        newUnlockedTags.add(tagStr)
                    }
                }
            }
        }

        if (newUnlockedTags.isNotEmpty()) {
            val tagsStr = newUnlockedTags.joinToString(", ")
            player.sendSystemMessage(
                Component.translatable("contactquests.message.tags_unlocked", tagsStr)
                    .withStyle(ChatFormatting.GREEN)
            )

            checkAndNotifyQuests(player, newUnlockedTags, startableBefore, teamData)
        }
    }

    @SubscribeEvent
    fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity
        if (player is ServerPlayer) syncTeam(player)
        if (player is ServerPlayer && ServerQuestFile.INSTANCE != null && ModList.get().isLoaded("kubejs")) {
            ContactKubeJSPlugin.updatePlayerTaskCache(player)
            ContactKubeJSPlugin.syncAllOverridesToPlayer(player)
            player.server.tell(net.minecraft.server.TickTask(player.server.tickCount + 1) {
                if (player.server.playerList.getPlayer(player.uuid) === player && ServerQuestFile.INSTANCE != null) {
                    ContactKubeJSPlugin.syncAllOverridesToPlayer(player)
                }
            })
        }
    }

    private fun checkAndNotifyQuests(player: ServerPlayer, newTags: List<String>,
                                     startableBefore: Map<Long, Boolean>, teamData: dev.ftb.mods.ftbquests.quest.TeamData) {
        val file = ServerQuestFile.INSTANCE
        val allQuests = file.allChapters.flatMap { it.quests }

        allQuests.forEach { quest: Quest ->
            val ext = (quest as Any) as? IQuestExtension ?: return@forEach

            val reqTags = ext.`contactQuests$getRequiredTags`()

            if (reqTags.isNotEmpty() && reqTags.any { newTags.contains(it) }
                && startableBefore[quest.id] == false && teamData.canStartTasks(quest) && quest.isVisible(teamData)) {

                val safeTitle: Component = QuestObjectText.safeTitle(quest)


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
                                    Component.translatable("contactquests.quest.id", quest.codeString)
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
