package com.creeperyang.contactquests.client.util

import dev.ftb.mods.ftblibrary.util.TooltipList
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import java.util.Optional

object TooltipUtils {
    @JvmStatic
    fun wrap(list: TooltipList) {
        val minecraft = Minecraft.getInstance()
        val width = (minecraft.window.guiScaledWidth - 24).coerceIn(1, 260)
        val lines = list.lines.flatMap { line ->
            if (line.string.isEmpty()) {
                listOf(line)
            } else {
                minecraft.font.splitter.splitLines(line, width, Style.EMPTY).map { text ->
                    val component = Component.empty()
                    text.visit({ style, value ->
                        component.append(Component.literal(value).withStyle(style))
                        Optional.empty<Unit>()
                    }, Style.EMPTY)
                    component
                }
            }
        }
        list.maxWidth = width
        list.lines.clear()
        list.lines.addAll(lines)
    }
}
