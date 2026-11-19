package com.creeperyang.contactquests.client.gui

import com.creeperyang.contactquests.client.util.MailItemInteraction
import com.creeperyang.contactquests.client.util.PostcardAutoFiller
import com.creeperyang.contactquests.client.util.TooltipUtils
import com.creeperyang.contactquests.data.ChoiceRewardSavedData
import com.creeperyang.contactquests.network.ChoicePostcardMessage
import com.creeperyang.contactquests.network.PrepareChoicePostcardMessage
import dev.ftb.mods.ftblibrary.icon.Color4I
import dev.ftb.mods.ftblibrary.ui.BaseScreen
import dev.ftb.mods.ftblibrary.ui.Button
import dev.ftb.mods.ftblibrary.ui.SimpleTextButton
import dev.ftb.mods.ftblibrary.ui.Theme
import dev.ftb.mods.ftblibrary.ui.WidgetType
import dev.ftb.mods.ftblibrary.ui.input.MouseButton
import dev.ftb.mods.ftblibrary.util.TooltipList
import dev.ftb.mods.ftbquests.client.gui.FTBQuestsTheme
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.network.PacketDistributor
import org.lwjgl.glfw.GLFW

/** 自选包裹奖励选中后的回邮引导界面，展示收件 NPC 与预设回信内容，并协助复制文本或自动填卡寄出。 */
class ChoicePostcardScreen(val request: ChoicePostcardMessage) : BaseScreen() {
    lateinit var writeButton: Button

    override fun addWidgets() {
        setSize(340, 250)
        add(dev.ftb.mods.ftblibrary.ui.TextField(this).apply {
            setText(Component.literal(request.recipient))
            setPosAndSize(12, 34, 314, 20)
        })
        add(dev.ftb.mods.ftblibrary.ui.TextField(this).apply {
            setText(Component.literal(request.text))
            setPosAndSize(12, 64, 314, 136)
        })
        add(object : SimpleTextButton(this, Component.translatable("contactquests.choice.copy_text"), Color4I.empty()) {
            override fun onClicked(button: MouseButton?) {
                copyToClipboard(request.text)
            }
        }.apply { setPosAndSize(12, 216, 144, 22) })
        writeButton = object : SimpleTextButton(this, Component.translatable("contactquests.choice.write_postcard"), Color4I.empty()) {
            override fun onClicked(button: MouseButton?) {
                writePostcard()
            }

            override fun addMouseOverText(list: TooltipList) {
                list.add(Component.translatable("contactquests.choice.send_hint", request.recipient))
                TooltipUtils.wrap(list)
            }

            override fun getWidgetType(): WidgetType = if (findPostcard() < 0) WidgetType.DISABLED else WidgetType.NORMAL
        }.apply { setPosAndSize(166, 216, 160, 22) }
        add(writeButton)
    }

    private fun findPostcard(): Int =
        Minecraft.getInstance().player?.inventory?.items?.indexOfFirst { ChoiceRewardSavedData.isEditablePostcard(it) } ?: -1

    fun writePostcard() {
        val slot = findPostcard()
        if (slot < 0) return
        PacketDistributor.sendToServer(PrepareChoicePostcardMessage(request.rewardId, slot))
    }

    companion object {
        fun copyToClipboard(text: String) {
            if (text.isEmpty()) return
            val mc = Minecraft.getInstance()
            val window = mc.window
            for (attempt in 0 until 10) {
                var platformBusy = false
                // Windows 的全局剪贴板锁在瞬时竞争时会返回 GLFW_PLATFORM_ERROR，需要短退避重试并保持原回调不被释放。
                val prev = GLFW.glfwSetErrorCallback { error, description ->
                    if (error == GLFW.GLFW_PLATFORM_ERROR && attempt < 9) {
                        platformBusy = true
                    } else {
                        window.defaultErrorCallback(error, description)
                    }
                }
                try {
                    mc.keyboardHandler.clipboard = text
                } finally {
                    GLFW.glfwSetErrorCallback(prev)?.free()
                }
                if (!platformBusy) return
                Thread.sleep(10L)
            }
        }

        fun openPrepared(request: ChoicePostcardMessage) {
            val mc = Minecraft.getInstance()
            val player = mc.player ?: return
            val slot = request.preparedSlot
            if (slot !in player.inventory.items.indices || !ChoiceRewardSavedData.isEditablePostcard(player.inventory.getItem(slot))) return
            mc.keyboardHandler.clipboard = request.recipient
            PostcardAutoFiller.schedule(request.text)
            MailItemInteraction.open(slot)
        }
    }

    override fun alignWidgets() {}
    override fun getTheme(): Theme = FTBQuestsTheme.INSTANCE

    override fun drawBackground(g: GuiGraphics, theme: Theme, x: Int, y: Int, w: Int, h: Int) {
        super.drawBackground(g, theme, x, y, w, h)
        theme.drawString(g, Component.translatable("contactquests.choice.send_hint", request.recipient), x + 12, y + 10, Color4I.WHITE, 0)
    }
}
