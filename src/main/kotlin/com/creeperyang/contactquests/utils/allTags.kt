package com.creeperyang.contactquests.utils

import com.creeperyang.contactquests.quest.reward.ParcelRewardBase
import dev.ftb.mods.ftbquests.quest.BaseQuestFile

object TagUtils {
    @JvmStatic
    fun getAllExistingTags(file: BaseQuestFile): List<String> {
        val tags = HashSet<String>()

        file.allChapters.forEach { chapter ->
            chapter.quests.forEach { quest ->
                // 1. 收集任务需求的 Tag (从 Mixin 接口)
                if (quest is IQuestExtension) {
                    tags.addAll((quest as IQuestExtension).`contactQuests$getRequiredTags`())
                }

                // 2. 收集奖励提供的 Tag
                quest.rewards.forEach { reward ->
                    if (reward is ParcelRewardBase) {
                        tags.addAll(reward.unlockTags)
                    }
                }
            }
        }
        return tags.sorted()
    }
}