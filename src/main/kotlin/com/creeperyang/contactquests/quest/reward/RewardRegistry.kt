package com.creeperyang.contactquests.quest.reward

import dev.ftb.mods.ftblibrary.icon.Icon
import dev.ftb.mods.ftblibrary.icon.Icons
import dev.ftb.mods.ftblibrary.icon.ItemIcon
import dev.ftb.mods.ftbquests.quest.reward.RewardType
import dev.ftb.mods.ftbquests.quest.reward.RewardTypes
import net.minecraft.resources.ResourceLocation

object RewardRegistry {
    val PARCEL_CHOICE: RewardType = RewardTypes.register(
        ResourceLocation.fromNamespaceAndPath("contactquests", "parcel_choice"),
        { id, quest -> ParcelChoiceReward(id, quest) },
        { ItemIcon.getItemIcon("contact:postcard") }
    ).setExcludeFromListRewards(true)

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
        { ItemIcon.getItemIcon("contact:postcard") }
    ).setExcludeFromListRewards(true)

    val RED_PACKET: RewardType = RewardTypes.register(
        ResourceLocation.fromNamespaceAndPath("contactquests", "red_packet"),
        { id, quest -> RedPacketReward(id, quest) },
        { Icon.getIcon("contact:item/red_packet") }
    ).setExcludeFromListRewards(true)

    fun init() {
        // 触发伴生对象类加载以向 FTB Quests 注册全部自定义奖励类型
    }
}
