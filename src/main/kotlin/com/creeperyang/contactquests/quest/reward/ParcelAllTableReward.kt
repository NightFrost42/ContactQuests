package com.creeperyang.contactquests.quest.reward

import com.creeperyang.contactquests.mixin.ItemRewardAccessor
import dev.ftb.mods.ftblibrary.icon.Icons
import dev.ftb.mods.ftbquests.quest.Quest
import dev.ftb.mods.ftbquests.quest.reward.ItemReward
import dev.ftb.mods.ftbquests.quest.reward.RewardType
import net.minecraft.server.level.ServerPlayer

class ParcelAllTableReward(id: Long, quest: Quest) : ParcelRandomReward(id, quest) {

    override fun getType(): RewardType = RewardRegistry.PARCEL_ALL_TABLE

    override fun claim(player: ServerPlayer, notify: Boolean) {
        val currentTable = table ?: return

        currentTable.weightedRewards.forEach { wr ->
            val reward = wr.reward
            if (reward is ItemReward) {
                val stack = reward.item.copy()

                val rBonus = (reward as ItemRewardAccessor).`contactquests$getRandomBonus`()
                val bonus = player.level().random.nextInt(rBonus + 1)

                stack.count = reward.count + bonus
                distributeItem(player, stack)
            } else {
                reward.claim(player, notify)
            }
        }
    }


    override fun getAltIcon(): dev.ftb.mods.ftblibrary.icon.Icon {
        return Icons.COLOR_HSB
    }
}