package com.creeperyang.contactquests.data

import com.creeperyang.contactquests.config.ContactConfig
import com.creeperyang.contactquests.config.NpcConfigManager
import com.creeperyang.contactquests.compat.kubejs.KubeJSNpcSavedData
import com.creeperyang.contactquests.llm.NpcLlmConfig
import com.creeperyang.contactquests.llm.NpcLlmSavedData
import com.creeperyang.contactquests.quest.reward.ParcelChoiceReward
import com.creeperyang.contactquests.quest.reward.ParcelRewardBase
import com.creeperyang.contactquests.quest.task.ContactTask
import dev.ftb.mods.ftbquests.quest.ServerQuestFile
import dev.ftb.mods.ftbquests.quest.TeamData
import dev.ftb.mods.ftbquests.quest.task.Task
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

/** 负责根据玩家当前的任务进度、KubeJS 动态配置与 AI NPC 对话状态解析可见/隐藏收件目标。 */
internal object NpcTargetResolver {
    fun getTeamData(player: ServerPlayer): TeamData? {
        val team = FTBTeamsAPI.api().manager.getTeamForPlayer(player).orElse(null) ?: return null
        return ServerQuestFile.INSTANCE?.getOrCreateTeamData(team)
    }

    fun getAvailableTargets(player: ServerPlayer): Map<String, Int> {
        DataManager.ensureTargetsFresh()
        val available = getBaseAvailableTargets(player)
        val level = player.serverLevel()
        val enableDelay = ContactConfig.enableDeliveryTime.get()
        val delayFor: (String) -> Int = { name ->
            if (enableDelay) NpcConfigManager.getDeliveryTime(name, level) else 0
        }

        val kubeData = KubeJSNpcSavedData.get(level)
        kubeData.getAllNpcs().forEach { (name, config) ->
            if (config.autoFill && !available.containsKey(name)) {
                available[name] = if (enableDelay) config.deliveryTime else 0
            }
        }
        fileChoiceTargets(player).forEach { name -> available.putIfAbsent(name, delayFor(name)) }

        val llmConfig = NpcLlmConfig.current()
        if (llmConfig.enabled && llmConfig.npcs.isNotEmpty()) {
            val team = FTBTeamsAPI.api().manager.getTeamForPlayer(player).orElse(null)
            val teamData = team?.let { ServerQuestFile.INSTANCE?.getNullableTeamData(it.id) }
            val unlockedQuestTargets = unlockedOrCompletedQuestTargets(teamData)
            val llmSavedData = NpcLlmSavedData.get(level)
            llmConfig.npcs.forEach { (name, npc) ->
                if (!npc.enabled) return@forEach
                val kubeAutoFill = kubeData.getNpcData(name)?.autoFill == true
                val hasConversation = team != null && llmSavedData.hasActiveConversation(name, team.id, player.uuid, npc.scope)
                val visible = available.containsKey(name) || npc.autoFill || kubeAutoFill || name in unlockedQuestTargets || hasConversation
                if (visible) {
                    available.putIfAbsent(name, delayFor(name))
                }
            }
        }
        return available
    }

    fun isChoiceAddress(name: String): Boolean = name.startsWith("CQ-Choice-", true)

    fun receiveChoiceCard(player: ServerPlayer, card: ItemStack, address: String): ItemStack {
        val delay = if (ContactConfig.enableDeliveryTime.get()) {
            NpcConfigManager.getDeliveryTime(address, player.serverLevel())
        } else {
            0
        }
        return ChoiceRewardSavedData.get(player.serverLevel())
            .receive(player, address, listOf(card), delay)
            .firstOrNull() ?: ItemStack.EMPTY
    }

    fun getHiddenTargets(player: ServerPlayer): Map<String, Int> {
        val hidden = mutableMapOf<String, Int>()
        val level = player.serverLevel()
        val kubeData = KubeJSNpcSavedData.get(level)
        val enableDelay = ContactConfig.enableDeliveryTime.get()

        kubeData.getAllNpcs().forEach { (name, config) ->
            if (!config.autoFill) {
                hidden[name] = if (enableDelay) config.deliveryTime else 0
            }
        }
        val visible = getAvailableTargets(player)
        NpcLlmConfig.names().forEach { name ->
            if (!visible.containsKey(name)) {
                hidden[name] = if (enableDelay) NpcConfigManager.getDeliveryTime(name, level) else 0
            }
        }
        return hidden
    }

    fun acceptsNpcMail(player: ServerPlayer, name: String): Boolean {
        DataManager.ensureTargetsFresh()
        return DataManager.parcelReceiver.containsKey(name) ||
            DataManager.redPacketReceiver.containsKey(name) ||
            DataManager.postcardReceiver.containsKey(name) ||
            DataManager.rewardSenders.contains(name) ||
            KubeJSNpcSavedData.get(player.serverLevel()).getNpcData(name) != null ||
            NpcLlmConfig.npc(name) != null
    }

    private fun unlockedOrCompletedQuestTargets(teamData: TeamData?): Set<String> {
        if (teamData == null) return emptySet()
        val targets = mutableSetOf<String>()
        ServerQuestFile.INSTANCE?.allChapters.orEmpty().forEach { chapter ->
            chapter.quests.forEach { quest ->
                if (teamData.canStartTasks(quest) || teamData.isCompleted(quest)) {
                    quest.tasks.forEach { task ->
                        if (task is ContactTask && task.targetAddressee.isNotBlank()) {
                            targets.add(task.targetAddressee)
                        }
                    }
                    quest.rewards.forEach { reward ->
                        if (reward is ParcelRewardBase && reward.targetAddressee.isNotBlank()) {
                            targets.add(reward.targetAddressee)
                        }
                    }
                }
            }
        }
        return targets
    }

    private fun fileChoiceTargets(player: ServerPlayer): Set<String> {
        val data = getTeamData(player) ?: return emptySet()
        return ServerQuestFile.INSTANCE?.allChapters.orEmpty()
            .flatMap { it.quests }
            .flatMap { it.rewards }
            .filterIsInstance<ParcelChoiceReward>()
            .filter { data.isCompleted(it.quest) && !data.isRewardBlocked(it) && !data.isRewardClaimed(player.uuid, it) }
            .map { it.claimRecipient }
            .toSet()
    }

    private fun getBaseAvailableTargets(player: ServerPlayer): MutableMap<String, Int> {
        val team = FTBTeamsAPI.api().manager.getTeamForPlayer(player).orElse(null) ?: return mutableMapOf()
        val teamData = ServerQuestFile.INSTANCE?.getNullableTeamData(team.id) ?: return mutableMapOf()
        val enableDelay = ContactConfig.enableDeliveryTime.get()
        val available = mutableMapOf<String, Int>()
        val level = player.serverLevel()

        fun checkAndAdd(task: Task, targetName: String) {
            if (teamData.canStartTasks(task.quest) && !teamData.isCompleted(task)) {
                available[targetName] = if (enableDelay) NpcConfigManager.getDeliveryTime(targetName, level) else 0
            }
        }

        DataManager.parcelTasks.values.forEach { checkAndAdd(it, it.targetAddressee) }
        DataManager.redPacketTasks.values.forEach { checkAndAdd(it, it.targetAddressee) }
        DataManager.postcardTasks.values.forEach { checkAndAdd(it, it.targetAddressee) }
        return available
    }
}
