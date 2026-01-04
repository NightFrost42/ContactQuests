package com.creeperyang.contactquests.config

import com.creeperyang.contactquests.client.gui.TagSelectionScreen
import dev.ftb.mods.ftblibrary.config.ConfigCallback
import dev.ftb.mods.ftblibrary.config.StringConfig
import dev.ftb.mods.ftblibrary.ui.Widget
import dev.ftb.mods.ftblibrary.ui.input.MouseButton
import dev.ftb.mods.ftblibrary.util.TooltipList
import dev.ftb.mods.ftbquests.quest.BaseQuestFile
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component

class TagConfig(
    private val file: BaseQuestFile?,
    private val existingTags: Collection<String>
) : StringConfig(null) {

    companion object {
        val sessionTags = HashSet<String>()
    }

    init {
        if (file != null) {
            sessionTags.clear()
        }
    }

    override fun onClicked(clicked: Widget?, button: MouseButton?, callback: ConfigCallback) {
        TagSelectionScreen(this, callback, existingTags).openGui()
    }

    override fun addInfo(list: TooltipList) {
        super.addInfo(list)
        if (value.isNotEmpty()) {
            list.add(
                Component.translatable(
                    "contactquests.gui.current_tag",
                    Component.literal(value).withStyle(ChatFormatting.YELLOW)
                ).withStyle(ChatFormatting.GRAY)
            )
        }
    }
}