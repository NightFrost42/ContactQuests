package com.creeperyang.contactquests.quest

import dev.ftb.mods.ftbquests.quest.QuestObjectBase
import dev.ftb.mods.ftbquests.quest.loot.RewardTable
import dev.ftb.mods.ftbquests.quest.reward.Reward
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.StringTag

/** 内嵌奖励表共享 FTB 伪造 ID `-1`，其标题必须直接保存在宿主对象本地以避免相互覆盖。 */
object InlineQuestSettings {
    @JvmStatic
    fun isInline(obj: QuestObjectBase): Boolean =
        obj is RewardTable && obj.id == -1L || obj is Reward &&
            ((obj.quest as Any) as? com.creeperyang.contactquests.bridge.IInlineQuestTextOwner)?.`contactQuests$usesLocalInlineText`() == true

    fun table(obj: QuestObjectBase?): RewardTable? = when (obj) {
        is com.creeperyang.contactquests.quest.reward.ParcelRandomReward -> obj.table
        is dev.ftb.mods.ftbquests.quest.reward.RandomReward -> obj.table
        else -> null
    }

    /** 内嵌条目未必写入 FTB 部分 ID 索引表，删除或校验前需同时检查其实际宿主对象。 */
    fun validateEntryIds(owner: QuestObjectBase, ids: Set<Long>) {
        require(owner is Reward && owner.id !in ids && owner.quest.id !in ids) { "Inline ID belongs to its parent" }
        val file = owner.questFile
        val ownEntries = table(file.getReward(owner.id))?.takeIf { it.id == -1L }
            ?.weightedRewards?.associate { it.reward.id to it.reward }.orEmpty()
        for (id in ids) {
            val registered = file.getBase(id)
            require(registered == null || registered === ownEntries[id]) { "Inline ID belongs to an existing object" }
        }
        file.rewardTables.forEach { registered ->
            require(registered.id !in ids && registered.weightedRewards.none { it.reward.id in ids }) {
                "Inline ID belongs to a standalone reward table"
            }
        }
        file.allChapters.flatMap { it.quests }.forEach { quest -> quest.rewards.forEach { reward ->
            if (reward.id != owner.id) require(table(reward)?.takeIf { it.id == -1L }
                    ?.weightedRewards?.none { it.reward.id in ids } != false) {
                "Inline ID belongs to another reward"
            }
        } }
    }

    fun entryIds(owner: QuestObjectBase): List<Long> = table(owner)?.takeIf { it.id == -1L }
        ?.weightedRewards?.map { it.reward.id }.orEmpty()

    fun normalizeText(data: CompoundTag) {
        if (!data.contains("translate")) return
        if (data["translate"] is StringTag && data.getString("translate").isBlank()) {
            data.remove("translate")
            return
        }
        val translated = data["translate"] as? CompoundTag ?: error("translate must be an object")
        require(translated.allKeys.all { it == "title" }) { "Only title is applicable to inline reward translations" }
        if (translated.contains("title")) {
            require(translated["title"] is StringTag && translated.getString("title").length <= 16384) { "title must be a bounded string" }
            if (!data.contains("title")) data.putString("title", translated.getString("title"))
        }
        data.remove("translate")
    }
}
