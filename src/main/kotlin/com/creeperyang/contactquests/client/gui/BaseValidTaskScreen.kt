package com.creeperyang.contactquests.client.gui

import com.creeperyang.contactquests.ContactQuests
import dev.ftb.mods.ftblibrary.icon.Color4I
import dev.ftb.mods.ftblibrary.ui.*
import dev.ftb.mods.ftblibrary.ui.input.Key
import dev.ftb.mods.ftblibrary.ui.input.MouseButton
import dev.ftb.mods.ftblibrary.util.TooltipList
import dev.ftb.mods.ftbquests.client.ClientQuestFile
import dev.ftb.mods.ftbquests.client.gui.FTBQuestsTheme
import dev.ftb.mods.ftbquests.quest.task.Task
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import kotlin.math.max

abstract class BaseValidTaskScreen<T : Task>(
    val task: T,
    titleKey: String,
    val targetAddressee: String
) : BaseScreen() {

    protected val titleComponent: Component = Component.translatable(titleKey, task.title)

    lateinit var mainPanel: Panel
    lateinit var backButton: Button
    lateinit var submitButton: Button

    abstract fun createMainPanel(): Panel
    abstract fun findValidSlot(player: Player): Int
    abstract fun scheduleAutoFiller()
    abstract fun addSubmitTooltip(list: TooltipList, hasValidSlot: Boolean)

    override fun addWidgets() {
        setWidth(max(156, theme.getStringWidth(titleComponent) + 12))

        mainPanel = createMainPanel()
        mainPanel.setPosAndSize(0, 22, 144, 0)
        add(mainPanel)

        backButton = object : SimpleTextButton(this, Component.translatable("contactquest.gui.back"), Color4I.empty()) {
            override fun onClicked(button: MouseButton?) {
                playClickSound()
                onBack()
            }
            override fun renderTitleInCenter() = true
        }

        submitButton = object : SimpleTextButton(this, Component.translatable("contactquest.gui.submit"), Color4I.empty()) {
            override fun onClicked(button: MouseButton?) {
                playClickSound()
                handleSubmit()
            }
            override fun renderTitleInCenter() = true
            override fun addMouseOverText(list: TooltipList) {
                val hasSlot = findValidSlot(Minecraft.getInstance().player ?: return) != -1
                addSubmitTooltip(list, hasSlot)
            }
            override fun getWidgetType(): WidgetType {
                val hasSlot = findValidSlot(Minecraft.getInstance().player ?: return WidgetType.DISABLED) != -1
                return if (hasSlot) WidgetType.NORMAL else WidgetType.DISABLED
            }
        }

        add(backButton)
        add(submitButton)
    }

    private fun handleSubmit() {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val connection = mc.connection ?: return

        val slot = findValidSlot(player)
        if (slot == -1) {
            ContactQuests.debug("屏幕：未找到有效槽位。")
            return
        }

        mc.keyboardHandler.clipboard = targetAddressee
        player.displayClientMessage(
            Component.translatable("contactquest.msg.copied", targetAddressee).withStyle(ChatFormatting.GREEN), true
        )

        scheduleAutoFiller()
        executeOpenInteraction(mc, player, connection, slot)
    }

    private fun executeOpenInteraction(mc: Minecraft, player: Player, connection: ClientPacketListener, slot: Int) {
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

    override fun getTheme(): Theme = FTBQuestsTheme.INSTANCE

    override fun drawBackground(graphics: GuiGraphics?, theme: Theme, x: Int, y: Int, w: Int, h: Int) {
        super.drawBackground(graphics, theme, x, y, w, h)
        if (graphics != null) {
            theme.drawString(graphics, titleComponent, x + w / 2, y + 6, Color4I.WHITE, Theme.CENTERED)
        }
    }

    override fun keyPressed(key: Key): Boolean {
        if (super.keyPressed(key)) return true
        if (key.esc()) { onBack(); return true }
        return false
    }

    override fun doesGuiPauseGame(): Boolean = ClientQuestFile.exists() && ClientQuestFile.INSTANCE.isPauseGame

    override fun onClosedByKey(key: Key?): Boolean { if (super.onClosedByKey(key)) onBack(); return false }
}