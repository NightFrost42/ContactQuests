package com.creeperyang.contactquests.client.gui

import dev.ftb.mods.ftblibrary.icon.Color4I
import dev.ftb.mods.ftblibrary.icon.ItemIcon
import dev.ftb.mods.ftblibrary.ui.*
import dev.ftb.mods.ftblibrary.ui.input.MouseButton
import dev.ftb.mods.ftblibrary.ui.misc.CompactGridLayout
import dev.ftb.mods.ftblibrary.util.client.PositionedIngredient
import dev.ftb.mods.ftbquests.FTBQuests
import dev.ftb.mods.ftbquests.quest.task.Task
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import java.util.*
import kotlin.math.min

abstract class BaseGridTaskScreen<T : Task>(
    task: T,
    titleKey: String,
    target: String,
    private val validItems: MutableList<ItemStack>
) : BaseValidTaskScreen<T>(task, titleKey, target) {

    override fun createMainPanel(): Panel {
        return object : Panel(this) {
            override fun addWidgets() {
                for (validItem in validItems) {
                    add(ValidItemButton(this, validItem))
                }
            }

            override fun alignWidgets() {
                align(CompactGridLayout(36))

                setHeight(min(160, contentHeight))

                parent.setHeight(height + 53)

                val off = (width - contentWidth) / 2
                for (widget in widgets) {
                    widget.x = widget.posX + off
                }

                x = (parent.width - width) / 2

                backButton.setPosAndSize(posX - 1, height + 28, 70, 20)
                submitButton.setPosAndSize(posX + 75, height + 28, 70, 20)
            }

            override fun drawBackground(graphics: GuiGraphics?, theme: Theme, x: Int, y: Int, w: Int, h: Int) {
                theme.drawButton(graphics, x - 1, y - 1, w + 2, h + 2, WidgetType.NORMAL)
            }
        }
    }

    private class ValidItemButton(panel: Panel, val stack: ItemStack) : Button(panel, Component.empty(), ItemIcon.getItemIcon(stack)) {
        override fun onClicked(button: MouseButton?) = FTBQuests.getRecipeModHelper().showRecipes(stack)

        override fun getIngredientUnderMouse(): Optional<PositionedIngredient> {
            return PositionedIngredient.of(stack, this, true)
        }

        override fun draw(graphics: GuiGraphics, theme: Theme?, x: Int, y: Int, w: Int, h: Int) {
            if (isMouseOver) Color4I.WHITE.withAlpha(33).draw(graphics, x, y, w, h)
            graphics.pose().pushPose()
            graphics.pose().translate(x + w / 2.0, y + h / 2.0, 10.0)
            graphics.pose().scale(2f, 2f, 2f)
            GuiHelper.drawItem(graphics, stack, 0, true, null)
            graphics.pose().popPose()
        }
    }
}