package com.creeperyang.contactquests.client.gui

import com.creeperyang.contactquests.utils.PostcardUtils
import com.flechazo.contact.data.PostcardDataManager
import com.flechazo.contact.data.PostcardStyle
import dev.ftb.mods.ftblibrary.icon.Icon
import dev.ftb.mods.ftblibrary.icon.ImageIcon
import dev.ftb.mods.ftblibrary.icon.IResourceIcon
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import kotlin.math.max
import kotlin.math.min

/** 复用 Contact 读取明信片卡面的同一套资源查找逻辑（含数据包重载后样式）。 */
@OnlyIn(Dist.CLIENT)
class PostcardStyleIcon(private val styleId: String = "") : Icon(), IResourceIcon {
    private var image: ImageIcon? = null

    private fun style(): PostcardStyle = PostcardDataManager.getPostcard(PostcardUtils.resolveStyleId(styleId))

    override fun getResourceLocation(): ResourceLocation = style().getCardTexture()

    override fun aspectRatio(): Double = style().let { it.cardWidth().toDouble() / max(1, it.cardHeight()) }

    override fun draw(graphics: GuiGraphics, x: Int, y: Int, w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val card = style()
        val scale = min(w.toDouble() / max(1, card.cardWidth()), h.toDouble() / max(1, card.cardHeight()))
        val width = max(1, (card.cardWidth() * scale).toInt())
        val height = max(1, (card.cardHeight() * scale).toInt())
        if (image?.texture != card.getCardTexture()) image = ImageIcon(card.getCardTexture())
        image!!.draw(graphics, x + (w - width) / 2, y + (h - height) / 2, width, height)
    }

    override fun toString(): String = getResourceLocation().toString()
}
