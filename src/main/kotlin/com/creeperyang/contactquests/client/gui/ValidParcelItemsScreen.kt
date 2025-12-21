package com.creeperyang.contactquests.client.gui

import com.creeperyang.contactquests.ContactQuests
import com.creeperyang.contactquests.client.util.ParcelAutoFiller
import com.creeperyang.contactquests.task.ParcelTask
import com.flechazo.contact.common.item.EnvelopeItem
import com.flechazo.contact.common.item.WrappingPaperItem
import dev.ftb.mods.ftblibrary.icon.Color4I
import dev.ftb.mods.ftblibrary.icon.ItemIcon
import dev.ftb.mods.ftblibrary.ui.*
import dev.ftb.mods.ftblibrary.ui.input.Key
import dev.ftb.mods.ftblibrary.ui.input.MouseButton
import dev.ftb.mods.ftblibrary.ui.misc.CompactGridLayout
import dev.ftb.mods.ftblibrary.util.TooltipList
import dev.ftb.mods.ftblibrary.util.client.PositionedIngredient
import dev.ftb.mods.ftbquests.FTBQuests
import dev.ftb.mods.ftbquests.client.ClientQuestFile
import dev.ftb.mods.ftbquests.client.gui.FTBQuestsTheme
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import java.util.*
import kotlin.math.max
import kotlin.math.min

class ValidParcelItemsScreen(): BaseScreen() {
    private var title: Component? = null
    private lateinit var itemPanel: Panel
    private lateinit var backButton: Button
    private lateinit var submitButton: Button
    private lateinit var task: ParcelTask

    constructor (task: ParcelTask, validItems: MutableList<ItemStack>) : this() {
        this.task = task
        title = Component.translatable("contactquest.task.ftbquests.parcel.valid_for", task.title)

        itemPanel = object : Panel(this) {
            override fun addWidgets() {
                for (validItem in validItems) {
                    add(ValidParcelButton(this, validItem))
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

                itemPanel.x = (parent.width - width) / 2
                backButton.setPosAndSize(itemPanel.posX - 1, height + 28, 70, 20)
                submitButton.setPosAndSize(itemPanel.posX + 75, height + 28, 70, 20)
            }

            override fun drawBackground(graphics: GuiGraphics?, theme: Theme, x: Int, y: Int, w: Int, h: Int) {
                theme.drawButton(graphics, x - 1, y - 1, w + 2, h + 2, WidgetType.NORMAL)
            }
        }

        itemPanel.setPosAndSize(0, 22, 144, 0)

        backButton = object : SimpleTextButton(this, Component.translatable("contactquest.gui.back"), Color4I.empty()) {
            override fun onClicked(button: MouseButton?) {
                playClickSound()
                onBack()
            }

            override fun renderTitleInCenter(): Boolean {
                return true
            }
        }

        submitButton = object : SimpleTextButton(this, Component.translatable("contactquest.gui.submit"), Color4I.empty()) {

            override fun onClicked(button: MouseButton?) {
                playClickSound()

                val mc = Minecraft.getInstance()
                val player = mc.player ?: return
                val connection = mc.connection ?: return

                val slot = player.inventory.items.indexOfFirst {
                    !it.isEmpty && (it.item is EnvelopeItem || it.item is WrappingPaperItem)
                }

                if (slot == -1) {
                    ContactQuests.debug("ValidParcelItemsScreen: 未找到包装纸")
                    return
                }

                mc.keyboardHandler.clipboard = task.targetAddressee
                player.displayClientMessage(
                    Component.translatable("contactquest.msg.copied", task.targetAddressee).withStyle(ChatFormatting.GREEN), true
                )

                ContactQuests.debug("ValidParcelItemsScreen: 准备打开槽位: $slot")
                ParcelAutoFiller.schedule(task.targetAddressee)

                executeOpenParcelInteraction(mc, player, connection, slot)
            }

            private fun executeOpenParcelInteraction(
                mc: Minecraft,
                player: net.minecraft.world.entity.player.Player,
                connection: net.minecraft.client.multiplayer.ClientPacketListener,
                slot: Int
            ) {
                mc.setScreen(null)
                player.closeContainer()

                if (slot >= 9) {
                    mc.gameMode?.handlePickItem(slot)
                } else if (player.inventory.selected != slot) {
                    player.inventory.selected = slot
                    connection.send(ServerboundSetCarriedItemPacket(slot))
                }

                mc.tell {
                    if (slot < 9) connection.send(ServerboundSetCarriedItemPacket(slot))
                    mc.gameMode?.useItem(player, InteractionHand.MAIN_HAND)
                }
            }

            override fun renderTitleInCenter(): Boolean {
                return true
            }

            override fun addMouseOverText(list: TooltipList) {

                    list.add(Component.translatable("contactquest.gui.put_in_parcel").withStyle(ChatFormatting.GRAY))

            }

            override fun getWidgetType(): WidgetType {
                return if (checkInventoryForRequiredItems()) WidgetType.NORMAL else WidgetType.DISABLED
            }
        }
    }

    private fun checkInventoryForRequiredItems(): Boolean {
        val player = Minecraft.getInstance().player ?: return false

        var hasParcel = false
        var hasQuestItem = false

        for (item in player.inventory.items) {
            if (item.isEmpty) continue

            if ((item.item is EnvelopeItem || item.item is WrappingPaperItem) && !hasParcel) {
                hasParcel = true
            }
            else if (task.test(item) && !hasQuestItem) {
                hasQuestItem = true
            }

            if (hasParcel && hasQuestItem) return true
        }

        return false
    }

    override fun addWidgets() {
        setWidth(max(156, theme.getStringWidth(title) + 12))
        add(itemPanel)
        add(backButton)
        add(submitButton)
    }

    override fun getTheme(): Theme {
        return FTBQuestsTheme.INSTANCE
    }

    override fun drawBackground(matrixStack: GuiGraphics?, theme: Theme, x: Int, y: Int, w: Int, h: Int) {
        super.drawBackground(matrixStack, theme, x, y, w, h)
        theme.drawString(matrixStack, title, x + w / 2, y + 6, Color4I.WHITE, Theme.CENTERED)
    }

    override fun keyPressed(key: Key): Boolean {
        if (super.keyPressed(key)) return true
        if (key.esc()) {
            onBack()
            return true
        }
        return false
    }

    override fun doesGuiPauseGame(): Boolean {
        return ClientQuestFile.exists() && ClientQuestFile.INSTANCE.isPauseGame
    }

    override fun onClosedByKey(key: Key?): Boolean {
        if (super.onClosedByKey(key)) {
            onBack()
        }

        return false
    }

    private class ValidParcelButton : Button {
        private val stack: ItemStack

        constructor(panel: Panel, stack: ItemStack): super(panel, Component.empty(), ItemIcon.getItemIcon(stack)) {
            this.stack = stack
        }

        override fun onClicked(button: MouseButton?) {
            FTBQuests.getRecipeModHelper().showRecipes(stack)
        }

        override fun getIngredientUnderMouse(): Optional<PositionedIngredient?> {
            return PositionedIngredient.of(stack, this, true)
        }

        override fun draw(graphics: GuiGraphics, theme: Theme?, x: Int, y: Int, w: Int, h: Int) {
            if (isMouseOver()) {
                Color4I.WHITE.withAlpha(33).draw(graphics, x, y, w, h)
            }

            graphics.pose().pushPose()
            graphics.pose().translate(x + w / 2.0, y + h / 2.0, 10.0)
            graphics.pose().scale(2f, 2f, 2f)
            GuiHelper.drawItem(graphics, stack, 0, true, null)
            graphics.pose().popPose()
        }
    }
}