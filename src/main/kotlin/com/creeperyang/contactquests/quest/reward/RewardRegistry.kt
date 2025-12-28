package com.creeperyang.contactquests.quest.reward

import dev.ftb.mods.ftblibrary.icon.Icon
import dev.ftb.mods.ftblibrary.icon.Icons
import dev.ftb.mods.ftbquests.quest.reward.RewardType
import dev.ftb.mods.ftbquests.quest.reward.RewardTypes
import net.minecraft.resources.ResourceLocation

object RewardRegistry {
    val PARCEL: RewardType = RewardTypes.register(
        ResourceLocation.fromNamespaceAndPath("contactquests", "parcel"),
        { id, quest -> ParcelReward(id, quest) },
        { Icon.getIcon("contact:item/parcel") }
    ).setExcludeFromListRewards(true)

    val PARCEL_RANDOM: RewardType = RewardTypes.register(
        ResourceLocation.fromNamespaceAndPath("contactquests", "parcel_random"),
        { id, quest -> ParcelRandomReward(id, quest) },
        { Icons.DICE }
    ).setExcludeFromListRewards(true)

    val PARCEL_ALL_TABLE: RewardType = RewardTypes.register(
        ResourceLocation.fromNamespaceAndPath("contactquests", "parcel_all_table"),
        { id, quest -> ParcelAllTableReward(id, quest) },
        { Icons.COLOR_HSB }
    ).setExcludeFromListRewards(true)

    val POSTCARD: RewardType = RewardTypes.register(
        ResourceLocation.fromNamespaceAndPath("contactquests", "postcard"),
        { id, quest -> PostcardReward(id, quest) },
        { Icon.getIcon("contact:item/postcard") }
    ).setExcludeFromListRewards(true)

    fun init() {
        // Class loading trigger
    }
}