package com.creeperyang.contactquests.client.gui

import com.creeperyang.contactquests.ContactQuests
import com.creeperyang.contactquests.client.util.PostcardAutoFiller
//import com.creeperyang.contactquests.client.util.PostcardAutoFiller
import com.creeperyang.contactquests.task.PostcardTask
import com.flechazo.contact.client.gui.hud.TexturePos
import com.flechazo.contact.common.item.PostcardItem
import com.flechazo.contact.data.PostcardDataManager
import com.flechazo.contact.data.PostcardStyle
import com.flechazo.contact.helper.GuiHelper
import com.mojang.blaze3d.systems.RenderSystem
import dev.ftb.mods.ftblibrary.icon.Color4I
import dev.ftb.mods.ftblibrary.ui.*
import dev.ftb.mods.ftblibrary.ui.input.Key
import dev.ftb.mods.ftblibrary.ui.input.MouseButton
import dev.ftb.mods.ftblibrary.util.TooltipList
import dev.ftb.mods.ftbquests.client.ClientQuestFile
import dev.ftb.mods.ftbquests.client.gui.FTBQuestsTheme
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import kotlin.math.max

class ValidPostcardItemsScreen() : BaseScreen() {
    private var title: Component? = null
    private lateinit var infoPanel: Panel
    private lateinit var backButton: Button
    private lateinit var submitButton: Button
    private lateinit var task: PostcardTask
    private lateinit var currentStyle: PostcardStyle

    constructor(task: PostcardTask) : this() {
        this.task = task
        title = Component.translatable("contactquest.task.ftbquests.postcard.valid_for", task.title)

        val styleId = ResourceLocation.tryParse(task.postcardStyle)
        currentStyle = if (styleId != null) {
            PostcardDataManager.getPostcards().getOrDefault(styleId, PostcardStyle.DEFAULT)
        } else {
            PostcardStyle.DEFAULT
        }

        infoPanel = object : Panel(this) {
            override fun addWidgets() {//不需要
                 }

            override fun alignWidgets() {
                val cardWidth = currentStyle.cardWidth()
                val cardHeight = currentStyle.cardHeight()
                setSize(cardWidth + 20, cardHeight + 20)
                parent.setHeight(height + 50)
                infoPanel.x = (parent.width - width) / 2

                backButton.setPosAndSize(infoPanel.posX, height + 24, 70, 20)
                submitButton.setPosAndSize(infoPanel.posX + width - 70, height + 24, 70, 20)
            }

            override fun drawBackground(graphics: GuiGraphics, theme: Theme, x: Int, y: Int, w: Int, h: Int) {
                theme.drawButton(graphics, x, y, w, h, WidgetType.NORMAL)

                RenderSystem.setShader(GameRenderer::getPositionTexShader)
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F)

                val cardX = x + (w - currentStyle.cardWidth()) / 2
                val cardY = y + (h - currentStyle.cardHeight()) / 2

                GuiHelper.drawLayerBySize(
                    graphics,
                    currentStyle.cardTexture,
                    cardX,
                    cardY,
                    TexturePos(0, 0, currentStyle.cardWidth(), currentStyle.cardHeight()),
                    currentStyle.cardWidth(),
                    currentStyle.cardHeight()
                )

                if (task.postcardText.isNotEmpty()) {
                    val font = Minecraft.getInstance().font

                    // 【关键修改 1】在此处处理预览界面的换行符
                    val textToDraw = task.postcardText.replace("\\n", "\n")

                    val textX = cardX + currentStyle.textPosX()
                    val textY = cardY + currentStyle.textPosY()
                    val textW = currentStyle.textWidth()
                    val textColor = currentStyle.textColor()

                    graphics.drawWordWrap(font, Component.literal(textToDraw), textX, textY, textW, textColor)
                }
            }
        }

        infoPanel.setPosAndSize(0, 22, 144, 0)

        backButton = object : SimpleTextButton(this, Component.translatable("contactquest.gui.back"), Color4I.empty()) {
            override fun onClicked(button: MouseButton?) {
                playClickSound()
                onBack()
            }
            override fun renderTitleInCenter(): Boolean = true
        }

        submitButton = object : SimpleTextButton(this, Component.translatable("contactquest.gui.submit"), Color4I.empty()) {
            override fun onClicked(button: MouseButton?) {
                playClickSound()

                val mc = Minecraft.getInstance()
                val player = mc.player ?: return
                val connection = mc.connection ?: return

                val slot = findValidPostcardSlot(player)

                if (slot == -1) {
                    ContactQuests.debug("ValidPostcardItemsScreen: 未找到符合样式的明信片")
                    return
                }

                mc.keyboardHandler.clipboard = task.targetAddressee
                player.displayClientMessage(
                    Component.translatable("contactquest.msg.copied", task.targetAddressee).withStyle(ChatFormatting.GREEN), true
                )

                ContactQuests.debug("ValidPostcardItemsScreen: 调度自动填充")

                PostcardAutoFiller.schedule(task)

                executeOpenPostcardInteraction(mc, player, connection, slot)
            }

            private fun executeOpenPostcardInteraction(mc: Minecraft, player: Player, connection: ClientPacketListener, slot: Int) {
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

            override fun renderTitleInCenter(): Boolean = true

            override fun addMouseOverText(list: TooltipList) {
                if (checkInventoryForPostcard()) {
                    list.add(Component.translatable("contactquest.gui.fill_postcard").withStyle(ChatFormatting.GRAY))
                } else {
                    list.add(Component.translatable("contactquest.gui.missing_postcard").withStyle(ChatFormatting.RED))

                    if (task.postcardStyle.isNotEmpty()) {
                        val rl = ResourceLocation.tryParse(task.postcardStyle)

                        val styleName = if (rl != null) {
                            Component.translatable("tooltip.postcard.${rl.namespace}.${rl.path}")
                        } else {
                            Component.literal(task.postcardStyle)
                        }

                        list.add(
                            Component.literal("Style: ")
                                .append(styleName)
                                .withStyle(ChatFormatting.DARK_RED)
                        )
                    }
                }
            }
            override fun getWidgetType(): WidgetType {
                return if (checkInventoryForPostcard()) WidgetType.NORMAL else WidgetType.DISABLED
            }
        }
    }

    private fun checkInventoryForPostcard(): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        return findValidPostcardSlot(player) != -1
    }

    private fun findValidPostcardSlot(player: Player): Int {
        for (i in 0 until player.inventory.items.size) {
            val stack = player.inventory.items[i]
            if (stack.isEmpty || stack.item !is PostcardItem) continue

            if (task.postcardStyle.isEmpty()) {
                return i
            }

            val stackStyle = getStackStyleId(stack)

            if (stackStyle == task.postcardStyle) {
                return i
            }
        }
        return -1
    }

    @Suppress("UNCHECKED_CAST")
    private fun getStackStyleId(stack: ItemStack): String? {
        val id = ResourceLocation.fromNamespaceAndPath("contact", "postcard_style_id")
        val type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(id) as? DataComponentType<ResourceLocation>

        if (type != null) {
            val rl = stack.get(type)
            return rl?.toString()
        }
        return null
    }

    override fun addWidgets() {
        val cardW = if (::currentStyle.isInitialized) currentStyle.cardWidth() else 156
        setWidth(max(cardW + 40, theme.getStringWidth(title) + 12))
        add(infoPanel)
        add(backButton)
        add(submitButton)
    }

    override fun getTheme(): Theme = FTBQuestsTheme.INSTANCE

    override fun drawBackground(matrixStack: GuiGraphics?, theme: Theme, x: Int, y: Int, w: Int, h: Int) {
        super.drawBackground(matrixStack, theme, x, y, w, h)
        if (matrixStack != null) {
            theme.drawString(matrixStack, title, x + w / 2, y + 6, Color4I.WHITE, Theme.CENTERED)
        }
    }

    override fun keyPressed(key: Key): Boolean {
        if (super.keyPressed(key)) return true
        if (key.esc()) {
            onBack()
            return true
        }
        return false
    }

    override fun doesGuiPauseGame(): Boolean = ClientQuestFile.exists() && ClientQuestFile.INSTANCE.isPauseGame

    override fun onClosedByKey(key: Key?): Boolean {
        if (super.onClosedByKey(key)) onBack()
        return false
    }
}