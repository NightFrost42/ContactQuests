package com.creeperyang.contactquests.client.gui

import com.creeperyang.contactquests.client.util.RedPacketAutoFiller
import com.creeperyang.contactquests.task.RedPacketTask
import com.flechazo.contact.common.item.RedPacketEnvelopeItem
import dev.ftb.mods.ftblibrary.util.TooltipList
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

class ValidRedPacketItemsScreen(task: RedPacketTask, validItems: MutableList<ItemStack>) :
    BaseGridTaskScreen<RedPacketTask>(task, "contactquest.task.ftbquests.redpacket.valid_for", task.targetAddressee, validItems) {

    override fun findValidSlot(player: Player): Int {
        return player.inventory.items.indexOfFirst { !it.isEmpty && it.item is RedPacketEnvelopeItem }
    }

    override fun scheduleAutoFiller() {
        RedPacketAutoFiller.schedule(task)
    }

    override fun addSubmitTooltip(list: TooltipList, hasValidSlot: Boolean) {
        val player = Minecraft.getInstance().player ?: return
        val hasQuestItem = player.inventory.items.any { !it.isEmpty && task.checkContent(it) }

        if (hasValidSlot && hasQuestItem) {
            list.add(Component.translatable("contactquest.gui.put_in_redpacket").withStyle(ChatFormatting.GRAY))
        } else {
            list.add(Component.translatable("contactquest.gui.missing_requirements").withStyle(ChatFormatting.RED))
            if (!hasValidSlot) {
                list.add(Component.literal("- ").append(Component.translatable("item.contact.red_packet_envelope")).withStyle(ChatFormatting.DARK_RED))
            }
            if (!hasQuestItem) {
                list.add(Component.literal("- ").append("${task.count}x ").append(task.itemStack.hoverName).withStyle(ChatFormatting.DARK_RED))
            }
        }
    }
}