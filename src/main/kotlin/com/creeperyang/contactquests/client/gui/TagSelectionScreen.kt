package com.creeperyang.contactquests.client.gui

import com.creeperyang.contactquests.config.TagConfig
import com.creeperyang.contactquests.utils.TagUtils
import dev.ftb.mods.ftblibrary.config.ConfigCallback
import dev.ftb.mods.ftblibrary.config.StringConfig
import dev.ftb.mods.ftblibrary.icon.Icons
import dev.ftb.mods.ftblibrary.ui.*
import dev.ftb.mods.ftblibrary.ui.input.MouseButton
import dev.ftb.mods.ftblibrary.ui.misc.AbstractButtonListScreen
import dev.ftb.mods.ftbquests.client.ClientQuestFile
import net.minecraft.network.chat.Component

class TagSelectionScreen(
    val config: StringConfig,
    val callback: ConfigCallback,
    private val existingTags: Collection<String>
) : AbstractButtonListScreen() {

    private var isCreating = false
    private var newTagValue = ""

    init {
        title = Component.translatable("contactquests.gui.select_tag")
        setHasSearchBox(true)
        setBorder(2, 2, 2)
    }

    override fun doCancel() {
        callback.save(false)
    }

    override fun doAccept() {
        callback.save(true)
    }

    override fun addButtons(panel: Panel) {
        if (isCreating) {
            panel.add(CreationRowPanel(panel))
        } else {
            panel.add(object :
                SimpleTextButton(panel, Component.translatable("contactquests.gui.create_tag"), Icons.ADD) {
                override fun onClicked(btn: MouseButton) {
                    playClickSound()
                    startCreating()
                }
            })
        }

        panel.add(VerticalSpaceWidget(panel, 2))

        val allTags = HashSet<String>()

        allTags.addAll(TagUtils.getAllExistingTags(ClientQuestFile.INSTANCE))

        allTags.addAll(TagConfig.sessionTags)

        allTags.removeAll(existingTags.toSet())

        for (tag in allTags.sorted()) {
            panel.add(object : SimpleTextButton(panel, Component.literal(tag), Icons.RIGHT) {
                override fun onClicked(btn: MouseButton) {
                    playClickSound()
                    selectTag(tag)
                }
            })
        }
    }

    private fun startCreating() {
        isCreating = true
        newTagValue = ""
        setHasSearchBox(false)
        refreshWidgets()
    }

    private fun cancelCreating() {
        isCreating = false
        newTagValue = ""
        setHasSearchBox(true)
        refreshWidgets()
    }

    private fun confirmCreating() {
        if (newTagValue.isNotBlank()) {
            selectTag(newTagValue.trim())
        }
    }

    private fun selectTag(tag: String) {
        TagConfig.sessionTags.add(tag)

        config.setCurrentValue(tag)
        callback.save(true)
    }

    private inner class CreationRowPanel(parent: Panel) : Panel(parent) {
        private val textBox: TextBox
        private val acceptBtn: SimpleButton
        private val cancelBtn: SimpleButton

        init {
            height = 20

            textBox = object : TextBox(this) {
                override fun onTextChanged() {
                    newTagValue = this.text
                }

                override fun onEnterPressed() {
                    confirmCreating()
                }
            }
            textBox.text = newTagValue
            textBox.isFocused = true

            acceptBtn = object : SimpleButton(this, Component.translatable("gui.accept"), Icons.ACCEPT, { _, _ ->
                playClickSound()
                confirmCreating()
            }) {}

            cancelBtn = object : SimpleButton(this, Component.translatable("gui.cancel"), Icons.CANCEL, { _, _ ->
                playClickSound()
                cancelCreating()
            }) {}
        }

        override fun setWidth(newWidth: Int) {
            if (width != newWidth) {
                super.setWidth(newWidth)
                alignWidgets()
            }
        }

        override fun addWidgets() {
            add(textBox)
            add(acceptBtn)
            add(cancelBtn)
        }

        override fun alignWidgets() {
            val btnSize = height
            if (width > 0) {
                cancelBtn.setPosAndSize(width - btnSize, 0, btnSize, btnSize)
                acceptBtn.setPosAndSize(width - btnSize * 2 - 2, 0, btnSize, btnSize)
                textBox.setPosAndSize(0, (height - 12) / 2, width - btnSize * 2 - 6, 12)
            }
        }

        override fun getTitle(): Component {
            return Component.literal("creation_row")
        }
    }
}