package com.creeperyang.contactquests.utils

import com.creeperyang.contactquests.bridge.IQuestExtension
import com.creeperyang.contactquests.quest.reward.ParcelRewardBase
import dev.ftb.mods.ftbquests.quest.BaseQuestFile

object TagUtils {
    @JvmStatic
    fun getAllExistingTags(file: BaseQuestFile): List<String> {
        val tags = HashSet<String>()

        file.allChapters.forEach { chapter ->
            chapter.quests.forEach { quest ->
                val extension = (quest as Any) as? IQuestExtension
                if (extension != null) {
                    tags.addAll(extension.`contactQuests$getRequiredTags`())
                }

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
