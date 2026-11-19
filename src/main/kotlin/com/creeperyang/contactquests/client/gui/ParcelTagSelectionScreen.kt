package com.creeperyang.contactquests.client.gui

import com.creeperyang.contactquests.quest.task.ParcelTask
import dev.ftb.mods.ftblibrary.icon.Color4I
import dev.ftb.mods.ftblibrary.icon.Icon
import dev.ftb.mods.ftblibrary.ui.Panel
import dev.ftb.mods.ftblibrary.ui.SimpleTextButton
import dev.ftb.mods.ftblibrary.ui.Theme
import dev.ftb.mods.ftblibrary.ui.input.MouseButton
import dev.ftb.mods.ftblibrary.ui.misc.AbstractButtonListScreen
import dev.ftb.mods.ftbquests.api.ItemFilterAdapter
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen
import dev.ftb.mods.ftbquests.net.EditObjectMessage
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item

/** 寄件包裹任务编辑界面使用的物品标签筛选选择器，选定后写回任务过滤器并同步至服务端。 */
class ParcelTagSelectionScreen(
    private val tags: List<TagKey<Item>>,
    private val parcelTask: ParcelTask,
    private val adapter: ItemFilterAdapter,
    private val questScreen: QuestScreen
) : AbstractButtonListScreen() {
    init {
        title = Component.translatable("ftbquests.task.ftbquests.item.select_tag")
        showBottomPanel(false)
        showCloseButton(true)
    }

    override fun addButtons(panel: Panel) {
        tags.stream()
            .sorted(java.util.Comparator.comparing { it.location().toString() })
            .forEach { tag -> panel.add(TagSelectionButton(panel, tag)) }
    }

    override fun onInit(): Boolean {
        val titleW = theme.getStringWidth(title)
        val w = tags.maxOfOrNull { theme.getStringWidth(it.location().toString()) } ?: 100
        setSize(maxOf(titleW, w) + 20, gui.window.guiScaledHeight * 3 / 4)
        return true
    }

    override fun doCancel() {
        questScreen.openGui()
    }

    override fun doAccept() {
        questScreen.openGui()
    }

    private inner class TagSelectionButton(
        panel: Panel,
        private val tag: TagKey<Item>
    ) : SimpleTextButton(panel, Component.literal(tag.location().toString()), Icon.empty()) {
        override fun onClicked(button: MouseButton) {
            questScreen.openGui()
            setTagFilterAndSave(parcelTask, adapter, tag)
        }

        override fun drawBackground(graphics: GuiGraphics, theme: Theme, x: Int, y: Int, w: Int, h: Int) {
            if (isMouseOver) {
                Color4I.WHITE.withAlpha(30).draw(graphics, x, y, w, h)
            }
            Color4I.GRAY.withAlpha(40).draw(graphics, x, y + h, w, 1)
        }

        private fun setTagFilterAndSave(parcelTask: ParcelTask, adapter: ItemFilterAdapter, tag: TagKey<Item>) {
            parcelTask.itemStack = adapter.makeTagFilterStack(tag)
            if (parcelTask.rawTitle.isEmpty()) {
                parcelTask.rawTitle = "Any #${tag.location()}"
            }
            EditObjectMessage.sendToServer(parcelTask)
        }
    }
}
