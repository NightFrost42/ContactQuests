package com.creeperyang.contactquests.client.gui

import com.creeperyang.contactquests.client.util.ParcelAutoFiller
import com.creeperyang.contactquests.task.ParcelTask
import com.flechazo.contact.common.item.EnvelopeItem
import com.flechazo.contact.common.item.WrappingPaperItem
import dev.ftb.mods.ftblibrary.util.TooltipList
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

class ValidParcelItemsScreen(task: ParcelTask, validItems: MutableList<ItemStack>) :
    BaseGridTaskScreen<ParcelTask>(task, "contactquest.task.ftbquests.parcel.valid_for", task.targetAddressee, validItems) {

    override fun findValidSlot(player: Player): Int {
        return player.inventory.items.indexOfFirst {
            !it.isEmpty && (it.item is EnvelopeItem || it.item is WrappingPaperItem)
        }
    }

    override fun scheduleAutoFiller() {
        ParcelAutoFiller.schedule(task.targetAddressee)
    }

    override fun addSubmitTooltip(list: TooltipList, hasValidSlot: Boolean) {
        val player = Minecraft.getInstance().player ?: return
        val hasQuestItem = player.inventory.items.any { !it.isEmpty && task.test(it) }

        if (hasValidSlot && hasQuestItem) {
            list.add(Component.translatable("contactquest.gui.put_in_parcel").withStyle(ChatFormatting.GRAY))
        } else {
            list.add(Component.translatable("contactquest.gui.missing_requirements").withStyle(ChatFormatting.RED))
            if (!hasValidSlot) {
                list.add(Component.literal("- ").append(
                    Component.translatable("item.contact.envelope").append(" or ").append(Component.translatable("item.contact.wrapping_paper"))
                ).withStyle(ChatFormatting.DARK_RED))
            }
            if (!hasQuestItem) {
                list.add(Component.literal("- ").append("${task.count}x ").append(task.itemStack.hoverName).withStyle(ChatFormatting.DARK_RED))
            }
        }
    }
}