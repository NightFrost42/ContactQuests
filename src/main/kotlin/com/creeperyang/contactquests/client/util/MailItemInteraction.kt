package com.creeperyang.contactquests.client.util

import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.item.ItemStack

/** 打开不在快捷栏中的信封或明信片前，先按原版库存预测交互流程将物品换入主手。 */
object MailItemInteraction {
    fun open(slot: Int) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val connection = mc.connection ?: return
        val gameMode = mc.gameMode ?: return
        if (slot !in player.inventory.items.indices) return
        val expected = player.inventory.getItem(slot).copy()
        if (expected.isEmpty) return
        mc.setScreen(null)
        player.closeContainer()
        if (slot >= 9) {
            // 1.21.1 中 handlePickItem 仅发送网络包，直接调用 useItem 仍会打开交换前的旧手持物品，因此通过背包交换点击同步。
            gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, slot,
                player.inventory.selected, ClickType.SWAP, player)
        } else {
            player.inventory.selected = slot
            connection.send(ServerboundSetCarriedItemPacket(slot))
        }
        mc.tell {
            if (mc.player === player && mc.screen == null && ItemStack.matches(expected, player.mainHandItem)) {
                gameMode.useItem(player, InteractionHand.MAIN_HAND)
            }
        }
    }
}
