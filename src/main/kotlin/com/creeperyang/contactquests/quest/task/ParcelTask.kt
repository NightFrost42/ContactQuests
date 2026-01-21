package com.creeperyang.contactquests.quest.task

import com.creeperyang.contactquests.client.gui.ValidParcelItemsScreen
import com.creeperyang.contactquests.data.DataManager
import dev.ftb.mods.ftblibrary.util.TooltipList
import dev.ftb.mods.ftbquests.integration.item_filtering.ItemMatchingSystem
import dev.ftb.mods.ftbquests.item.MissingItem
import dev.ftb.mods.ftbquests.quest.Quest
import dev.ftb.mods.ftbquests.quest.TeamData
import dev.ftb.mods.ftbquests.quest.task.TaskType
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn

class ParcelTask(id: Long, quest: Quest) : ItemMatchingTask(id, quest) {

    override fun getType(): TaskType = TaskRegistry.PARCEL

    fun setStackAndCount(stack: ItemStack, count: Int): ParcelTask {
        this.itemStack = stack.copy()
        this.count = count.toLong()
        return this
    }

    override fun test(stack: ItemStack): Boolean {
        if (itemStack.isEmpty) return false
        return ItemMatchingSystem.INSTANCE.doesItemMatch(itemStack, stack, shouldMatchNBT(), weakNBTmatch)
    }

    @OnlyIn(Dist.CLIENT)
    override fun openTaskGui(validItems: MutableList<ItemStack>) {
        ValidParcelItemsScreen(this, validItems).openGui()
    }

    fun submitParcelTask(teamData: TeamData, player: ServerPlayer, submitItemStack: ItemStack): ItemStack {
        if (teamData.isCompleted(this)){
            DataManager.completeParcelTask(this)
            return itemStack
        }
        if (itemStack.item is MissingItem || submitItemStack.item is MissingItem) return itemStack
        return insert(teamData, submitItemStack, false)
    }

    @OnlyIn(Dist.CLIENT)
    override fun addMouseOverText(list: TooltipList, teamData: TeamData) {
        list.blankLine()
        list.add(
            Component.translatable("contactquest.task.parcel.recipient", targetAddressee).withStyle(ChatFormatting.GRAY)
        )

        super.addMouseOverText(list, teamData)
    }
}