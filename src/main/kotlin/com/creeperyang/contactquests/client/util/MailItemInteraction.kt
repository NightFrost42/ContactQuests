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
            // 通过背包交换点击同步客户端预测与服务端手持物品。
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

    /** 优先调用 FTB 的配方助手；JEI 缺失时不会加载其桥接类。 */
    fun showRecipes(stack: ItemStack) {
        if (stack.isEmpty) return
        for (name in listOf(
            "dev.ftb.mods.ftblibrary.integration.RecipeModHelper",
            "dev.ftb.mods.ftbquests.client.ClientQuestFile"
        )) {
            try {
                val type = Class.forName(name)
                val method = type.methods.firstOrNull {
                    it.name == "showRecipes" && it.parameterTypes.contentEquals(arrayOf(ItemStack::class.java))
                } ?: continue
                val receiver = if (java.lang.reflect.Modifier.isStatic(method.modifiers)) null
                    else type.getField("INSTANCE").get(null)
                val result = method.invoke(receiver, stack)
                if (result != false) return
            } catch (_: ReflectiveOperationException) {
            } catch (_: LinkageError) {
            }
        }
        if (!net.minecraftforge.fml.ModList.get().isLoaded("jei")) return
        try {
            Class.forName("com.creeperyang.contactquests.compat.jei.ContactQuestsJeiPlugin")
                .getMethod("showRecipes", ItemStack::class.java).invoke(null, stack)
        } catch (_: ReflectiveOperationException) {
        } catch (_: LinkageError) {
        }
    }
}
