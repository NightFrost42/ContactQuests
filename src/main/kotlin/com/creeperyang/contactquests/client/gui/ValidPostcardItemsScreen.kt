package com.creeperyang.contactquests.client.gui

import com.creeperyang.contactquests.client.util.PostcardAutoFiller
import com.creeperyang.contactquests.task.PostcardTask
import com.flechazo.contact.client.gui.hud.TexturePos
import com.flechazo.contact.common.item.PostcardItem
import com.flechazo.contact.data.PostcardDataManager
import com.flechazo.contact.data.PostcardStyle
import com.mojang.blaze3d.systems.RenderSystem
import dev.ftb.mods.ftblibrary.ui.Panel
import dev.ftb.mods.ftblibrary.ui.Theme
import dev.ftb.mods.ftblibrary.ui.WidgetType
import dev.ftb.mods.ftblibrary.util.TooltipList
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import com.flechazo.contact.helper.GuiHelper as ContactGuiHelper

class ValidPostcardItemsScreen(task: PostcardTask) :
    BaseValidTaskScreen<PostcardTask>(task, "contactquest.task.ftbquests.postcard.valid_for", task.targetAddressee) {

    private val currentStyle: PostcardStyle

    init {
        val styleId = ResourceLocation.tryParse(task.postcardStyle)
        currentStyle = if (styleId != null) {
            PostcardDataManager.getPostcards()[styleId] ?: PostcardStyle.DEFAULT
        } else {
            PostcardStyle.DEFAULT
        }
    }

    override fun createMainPanel(): Panel {
        return object : Panel(this) {
            override fun addWidgets() { /* 不需要子控件 */ }
            override fun alignWidgets() {
                val cardWidth = currentStyle.cardWidth()
                val cardHeight = currentStyle.cardHeight()

                setSize(cardWidth + 20, cardHeight + 20)

                parent.setHeight(height + 50)

                x = (parent.width - width) / 2

                backButton.setPosAndSize(posX, height + 24, 70, 20)
                submitButton.setPosAndSize(posX + width - 70, height + 24, 70, 20)
            }

            override fun drawBackground(graphics: GuiGraphics, theme: Theme, x: Int, y: Int, w: Int, h: Int) {
                theme.drawButton(graphics, x, y, w, h, WidgetType.NORMAL)
                RenderSystem.setShader(GameRenderer::getPositionTexShader)
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F)

                val cardX = x + (w - currentStyle.cardWidth()) / 2
                val cardY = y + (h - currentStyle.cardHeight()) / 2

                ContactGuiHelper.drawLayerBySize(
                    graphics, currentStyle.cardTexture, cardX, cardY,
                    TexturePos(0, 0, currentStyle.cardWidth(), currentStyle.cardHeight()),
                    currentStyle.cardWidth(), currentStyle.cardHeight()
                )

                if (task.postcardText.isNotEmpty()) {
                    val font = Minecraft.getInstance().font
                    val textToDraw = task.postcardText.replace("\\n", "\n")
                    val textX = cardX + currentStyle.textPosX()
                    val textY = cardY + currentStyle.textPosY()
                    val textW = currentStyle.textWidth()
                    val textColor = currentStyle.textColor()
                    graphics.drawWordWrap(font, Component.literal(textToDraw), textX, textY, textW, textColor)
                }
            }
        }
    }

    override fun findValidSlot(player: Player): Int {
        for (i in 0 until player.inventory.items.size) {
            val stack = player.inventory.items[i]
            if (stack.isEmpty || stack.item !is PostcardItem) continue
            if (task.postcardStyle.isEmpty()) return i
            val stackStyleId = getStackStyleId(stack)
            if (stackStyleId == task.postcardStyle) return i
        }
        return -1
    }

    override fun scheduleAutoFiller() {
        PostcardAutoFiller.schedule(task)
    }

    override fun addSubmitTooltip(list: TooltipList, hasValidSlot: Boolean) {
        if (hasValidSlot) {
            list.add(Component.translatable("contactquest.gui.fill_postcard").withStyle(ChatFormatting.GRAY))
        } else {
            list.add(Component.translatable("contactquest.gui.missing_postcard").withStyle(ChatFormatting.RED))
            if (task.postcardStyle.isNotEmpty()) {
                val rl = ResourceLocation.tryParse(task.postcardStyle)
                val styleName = if (rl != null) Component.translatable("tooltip.postcard.${rl.namespace}.${rl.path}")
                else Component.literal(task.postcardStyle)
                list.add(Component.literal("Style: ").append(styleName).withStyle(ChatFormatting.DARK_RED))
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getStackStyleId(stack: ItemStack): String? {
        val id = ResourceLocation.fromNamespaceAndPath("contact", "postcard_style_id")
        val type = BuiltInRegistries.DATA_COMPONENT_TYPE[id] as? DataComponentType<ResourceLocation>
        return if (type != null) stack[type]?.toString() else null
    }
}