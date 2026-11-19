package com.creeperyang.contactquests.llm

import com.flechazo.contact.common.component.ContactDataComponents
import com.flechazo.contact.common.item.PostcardItem
import com.flechazo.contact.data.PostcardStyle
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import java.util.UUID

/** 按原生明信片可渲染区域将完整长回信拆分为连续多张实卡，完整保留 AI 回复文本。 */
object NpcPostcardLetters {
    private const val LINE_SPACING = 12
    // 专服无客户端 Font 实例，按默认字形含加粗后的保守宽度上界估算每行字符数。
    private const val GLYPH_WIDTH_BOUND = 12
    private const val MAX_TEXT_LENGTH = 1023
    private const val FRAGMENT_TAG = "ContactQuestsNpcLetter"

    @JvmStatic
    fun create(npc: String, text: String): List<ItemStack> {
        val blank = PostcardItem.getPostcard(ResourceLocation.parse("contact:default"), false)
        val style = PostcardStyle.fromItemStack(blank)
        val parts = split(text, style.textWidth(), style.textHeight())
        val group = UUID.randomUUID()
        return parts.mapIndexed { index, part ->
            PostcardItem.setText(blank.copy(), part).apply {
                set(ContactDataComponents.POSTCARD_SENDER.get(), npc)
                // 为每段分页卡写入独立分片标识，防止内容恰好相同的分片卡在堆叠时误合并，同时不污染玩家可读的正文、标题或 Lore。
                val data = getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                data.put(FRAGMENT_TAG, CompoundTag().apply {
                    putUUID("group", group)
                    putInt("index", index)
                    putInt("count", parts.size)
                })
                set(DataComponents.CUSTOM_DATA, CustomData.of(data))
            }
        }
    }

    /** 同一封多页回信共享的唯一分组 UUID；普通明信片或无效分片标记返回 null。 */
    @JvmStatic
    fun group(card: ItemStack): UUID? = fragment(card)?.getUUID("group")

    /** 当前卡片在整封回信中的从零序号；普通明信片或无效标记返回 -1。 */
    @JvmStatic
    fun index(card: ItemStack): Int = fragment(card)?.getInt("index") ?: -1

    /** 整封多页回信包含的总卡数；普通明信片或无效标记返回 0。 */
    @JvmStatic
    fun count(card: ItemStack): Int = fragment(card)?.getInt("count") ?: 0

    private fun fragment(card: ItemStack): CompoundTag? {
        if (card.item !is PostcardItem) return null
        val marker = card.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(FRAGMENT_TAG)
        return marker.takeIf {
            it.hasUUID("group") && it.contains("index", Tag.TAG_INT.toInt()) && it.contains("count", Tag.TAG_INT.toInt()) &&
                it.getInt("count") > 0 && it.getInt("index") in 0 until it.getInt("count")
        }
    }

    /** 仅在完整码点边界处切分并严格控制在 Contact 1024 字符限制以内，确保 UTF-16 原文不损坏。 */
    @JvmStatic
    fun split(text: String, width: Int, height: Int): List<String> {
        require(width >= GLYPH_WIDTH_BOUND && height >= LINE_SPACING) { "NPC postcard has no usable text area" }
        if (text.isEmpty()) return emptyList()
        val columns = width / GLYPH_WIDTH_BOUND
        val rows = height / LINE_SPACING
        val result = mutableListOf<String>()
        val part = StringBuilder()
        var offset = 0
        while (offset < text.length) {
            val end = offset + Character.charCount(text.codePointAt(offset))
            val previousLength = part.length
            part.append(text, offset, end)
            if (previousLength > 0 && (part.length > MAX_TEXT_LENGTH || estimatedRows(part, columns) > rows)) {
                part.setLength(previousLength)
                result.add(part.toString())
                part.setLength(0)
                part.append(text, offset, end)
            }
            offset = end
        }
        if (part.isNotEmpty()) result.add(part.toString())
        return result
    }

    /**
     * Contact 的 ReadOnlyTextBox 底层依赖 StringSplitter：仅换行符 `\n` 强制换行、仅 ASCII 空格可作为折行点，
     * 且单词溢出时会回退到上一个空格；末尾的 `\n` 只结束当前行，不会额外生成空渲染行。
     */
    private fun estimatedRows(text: CharSequence, columns: Int): Int {
        var rows = 0
        var start = 0
        while (start < text.length) {
            var cursor = start
            var used = 0
            var lastSpace = -1
            while (cursor < text.length) {
                val point = Character.codePointAt(text, cursor)
                if (point == '\n'.code) { cursor++; break }
                if (point == ' '.code) lastSpace = cursor
                if (used == columns) {
                    cursor = if (lastSpace >= start) lastSpace + 1 else cursor
                    break
                }
                used++
                cursor += Character.charCount(point)
            }
            rows++
            start = cursor
        }
        return rows
    }
}
