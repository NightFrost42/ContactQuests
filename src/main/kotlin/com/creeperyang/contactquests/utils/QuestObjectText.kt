package com.creeperyang.contactquests.utils

import com.creeperyang.contactquests.quest.reward.ParcelChoiceReward
import com.creeperyang.contactquests.quest.reward.ParcelRandomReward
import com.creeperyang.contactquests.quest.reward.ParcelReward
import com.creeperyang.contactquests.quest.reward.PostcardReward
import com.creeperyang.contactquests.quest.reward.RedPacketReward
import com.creeperyang.contactquests.quest.task.ParcelTask
import com.creeperyang.contactquests.quest.task.PostcardTask
import com.creeperyang.contactquests.quest.task.RedPacketTask
import dev.ftb.mods.ftbquests.quest.Quest
import dev.ftb.mods.ftbquests.quest.QuestObjectBase
import dev.ftb.mods.ftbquests.quest.reward.ItemReward
import dev.ftb.mods.ftbquests.quest.reward.Reward
import dev.ftb.mods.ftbquests.quest.task.ItemTask
import dev.ftb.mods.ftbquests.quest.task.Task
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraftforge.fml.loading.FMLEnvironment

/** 在客户端与独立服务端上安全解析 FTB Quests 对象标题，避免服务端触发 `@OnlyIn(Dist.CLIENT)` 的 `getTitle()`。 */
object QuestObjectText {
    @JvmStatic
    fun safeTitle(obj: QuestObjectBase): Component {
        if (FMLEnvironment.dist.isClient) return ClientHelper.title(obj)
        if (obj.rawTitle.isNotBlank()) return Component.literal(obj.rawTitle)
        return when (obj) {
            is Quest -> obj.tasks.firstOrNull()?.let(::safeTitle) ?: Component.translatable("ftbquests.unnamed")
            is ItemReward -> itemTitle(obj.item, obj.count)
            is ParcelReward -> itemTitle(obj.item, obj.count)
            is RedPacketReward -> itemTitle(obj.item, obj.count)
            is PostcardReward -> Component.translatable("contactquests.reward.postcard")
            is ParcelChoiceReward -> Component.translatable("contactquests.reward.parcel_choice")
            is ParcelRandomReward -> obj.table?.takeIf { it.rawTitle.isNotBlank() }
                ?.let { Component.literal(it.rawTitle) }
                ?: Component.translatable("contactquests.reward.parcel_random")
            is ItemTask -> itemTitle(obj.itemStack, obj.maxProgress)
            is ParcelTask -> itemTitle(obj.itemStack, obj.count)
            is RedPacketTask -> itemTitle(obj.itemStack, obj.count)
            is PostcardTask -> Component.translatable("contactquests.task.postcard")
            is Reward -> obj.type.displayName.copy()
            is Task -> obj.type.displayName.copy()
            else -> Component.literal(obj.codeString)
        }
    }

    private fun itemTitle(stack: ItemStack, count: Number): Component {
        val amount = count.toLong()
        return if (amount > 1L) Component.literal("${amount}x ").append(stack.hoverName)
        else stack.hoverName.copy()
    }

    @OnlyIn(Dist.CLIENT)
    private object ClientHelper {
        fun title(obj: QuestObjectBase): Component = obj.title
    }
}
