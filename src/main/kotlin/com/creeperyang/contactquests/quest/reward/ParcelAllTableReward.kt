package com.creeperyang.contactquests.quest.reward

import dev.ftb.mods.ftblibrary.icon.Icons
import dev.ftb.mods.ftbquests.quest.Quest
import dev.ftb.mods.ftbquests.quest.reward.RewardType
import net.minecraft.server.level.ServerPlayer

class ParcelAllTableReward(id: Long, quest: Quest) : ParcelRandomReward(id, quest) {

    override fun getType(): RewardType = RewardRegistry.PARCEL_ALL_TABLE

    override fun claim(player: ServerPlayer, notify: Boolean) {
        val currentTable = table ?: return

        currentTable.weightedRewards.forEach { wr ->
            handleReward(player, wr.reward, notify)
        }
    }


    override fun getAltIcon(): dev.ftb.mods.ftblibrary.icon.Icon {
        return Icons.COLOR_HSB
    }
}