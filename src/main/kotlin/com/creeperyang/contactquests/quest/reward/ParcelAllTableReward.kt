package com.creeperyang.contactquests.quest.reward

import dev.ftb.mods.ftblibrary.util.TooltipList
import dev.ftb.mods.ftbquests.quest.Quest
import dev.ftb.mods.ftbquests.quest.reward.RewardType
import net.minecraft.server.level.ServerPlayer

class ParcelAllTableReward(id: Long, quest: Quest) : ParcelRandomReward(id, quest) {

    override fun getType(): RewardType = RewardRegistry.PARCEL_ALL_TABLE

    override fun claim(player: ServerPlayer, notify: Boolean): Boolean {
        val currentTable = table ?: return false

        var granted = false
        currentTable.weightedRewards.forEach { wr ->
            granted = handleReward(player, wr.reward, notify) || granted
        }
        return granted
    }


    override fun addMouseOverText(list: TooltipList) {
        if (!isContentsHiddenForViewer()) table?.addMouseOverText(list, false, false)
    }
}
