package com.creeperyang.contactquests.client.gui

import com.creeperyang.contactquests.client.util.PostcardAutoFiller
import com.creeperyang.contactquests.data.ChoiceRewardSavedData
import com.creeperyang.contactquests.quest.task.PostcardTask
import com.creeperyang.contactquests.utils.PostcardUtils
import com.flechazo.contact.client.gui.hud.TexturePos
import com.flechazo.contact.common.item.PostcardItem
import com.flechazo.contact.data.PostcardDataManager
import com.flechazo.contact.data.PostcardStyle
import com.mojang.blaze3d.systems.RenderSystem
import dev.ftb.mods.ftblibrary.ui.Panel
import dev.ftb.mods.ftblibrary.ui.Theme
import dev.ftb.mods.ftblibrary.ui.WidgetType
import dev.ftb.mods.ftblibrary.util.TooltipList
import dev.ftb.mods.ftbquests.client.ClientQuestFile
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
import kotlin.math.max
import com.flechazo.contact.helper.GuiHelper as ContactGuiHelper

class ValidPostcardItemsScreen(task: PostcardTask) :
    BaseValidTaskScreen<PostcardTask>(task, "contactquest.task.ftbquests.postcard.valid_for", task.targetAddressee) {

    private val currentStyle: PostcardStyle

    init {
        currentStyle = PostcardDataManager.getPostcard(PostcardUtils.resolveStyleId(task.postcardStyle))
    }

    override fun createMainPanel(): Panel {
        return object : Panel(this) {
            override fun addWidgets() { /* 不需要子控件 */ }
            override fun alignWidgets() {
                val cardWidth = currentStyle.cardWidth()
                val cardHeight = currentStyle.cardHeight()

                setSize(cardWidth + 20, cardHeight + 20)

                val screen = this@ValidPostcardItemsScreen

                val minScreenWidth = max(cardWidth + 40, theme.getStringWidth(screen.titleComponent) + 30)

                screen.setWidth(minScreenWidth)
                screen.setHeight(height + 50)

                setPos((screen.width - width) / 2, 22)

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
                    val textX = cardX + currentStyle.textPosX()
                    val textY = cardY + currentStyle.textPosY()
                    val textW = currentStyle.textWidth()
                    val textColor = currentStyle.textColor()

                    val player = Minecraft.getInstance().player
                    val teamData = ClientQuestFile.INSTANCE.selfTeamData
                    val textToDraw = task.getResolvedText(teamData, player)
                    val textH = currentStyle.textHeight()
                    graphics.enableScissor(textX, textY, textX + textW, textY + textH)
                    try {
                        font.split(Component.literal(textToDraw), textW)
                            .take(textH / 12).forEachIndexed { index, line ->
                                graphics.drawString(font, line, textX, textY + index * 12, textColor, false)
                            }
                    } finally {
                        graphics.disableScissor()
                    }
                }
            }
        }
    }

    override fun findValidSlot(player: Player): Int {
        val expected = PostcardUtils.normalizeStyleId(task.postcardStyle)
        if (task.postcardStyle.isNotEmpty() && expected == null) return -1
        for (i in 0 until player.inventory.items.size) {
            val stack = player.inventory.items[i]
            if (!ChoiceRewardSavedData.isEditablePostcard(stack)) continue
            if (expected == null) return i
            if (getStackStyleId(stack) == expected) return i
        }
        return -1
    }

    override fun scheduleAutoFiller() {
        PostcardAutoFiller.schedule(task)
    }

    override fun addSubmitTooltip(list: TooltipList, hasValidSlot: Boolean) {
        if (hasValidSlot) {
            list.add(Component.translatable("contactquest.gui.fill_postcard").withStyle(ChatFormatting.GRAY))
            val mc = Minecraft.getInstance()
            val text = task.getResolvedText(ClientQuestFile.INSTANCE.selfTeamData, mc.player)
            if (text.length >= 1024 || mc.font.wordWrapHeight(text, currentStyle.textWidth()) >
                currentStyle.textHeight() * mc.font.lineHeight / 12) {
                list.add(Component.translatable("contactquests.postcard.text_too_long").withStyle(ChatFormatting.RED))
            }
        } else {
            list.add(Component.translatable("contactquest.gui.missing_postcard").withStyle(ChatFormatting.RED))
            if (task.postcardStyle.isNotEmpty()) {
                val rl = ResourceLocation.tryParse(task.postcardStyle)
                val styleName = if (rl != null) Component.translatable("tooltip.postcard.${rl.namespace}.${rl.path}")
                else Component.literal(task.postcardStyle)
                list.add(Component.translatable("contactquest.gui.postcard_style", styleName).withStyle(ChatFormatting.DARK_RED))
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getStackStyleId(stack: ItemStack): ResourceLocation? {
        val id = ResourceLocation.fromNamespaceAndPath("contact", "postcard_style_id")
        val type = BuiltInRegistries.DATA_COMPONENT_TYPE[id] as? DataComponentType<ResourceLocation>
        return if (type != null) stack[type] else null
    }
}
