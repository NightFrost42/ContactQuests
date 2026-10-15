package com.creeperyang.contactquests.utils

import com.creeperyang.contactquests.quest.reward.ParcelRewardBase
import dev.ftb.mods.ftbquests.quest.BaseQuestFile

object TagUtils {
    @JvmStatic
    fun getAllExistingTags(file: BaseQuestFile): List<String> {
        val tags = HashSet<String>()

        file.allChapters.forEach { chapter ->
            chapter.quests.forEach { quest ->
                // Quest is final and only gains IQuestExtension at runtime via mixin, so a plain
                // `quest is IQuestExtension` is rejected by the Kotlin 2.x compiler as "always false".
                // Widening to Any first keeps the runtime check (same idiom as EventHandler:77).
                val ext = (quest as Any) as? IQuestExtension
                if (ext != null) {
                    tags.addAll(ext.`contactQuests$getRequiredTags`())
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