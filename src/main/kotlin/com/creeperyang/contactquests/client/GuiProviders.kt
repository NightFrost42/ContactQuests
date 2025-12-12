package com.creeperyang.contactquests.client

import dev.ftb.mods.ftblibrary.config.ItemStackConfig
import dev.ftb.mods.ftblibrary.config.ui.resource.SelectItemStackScreen
import dev.ftb.mods.ftblibrary.ui.Panel
import dev.ftb.mods.ftbquests.quest.Quest
import dev.ftb.mods.ftbquests.quest.task.ItemTask
import dev.ftb.mods.ftbquests.quest.task.Task
import dev.ftb.mods.ftbquests.quest.task.TaskType
import dev.ftb.mods.ftbquests.quest.task.TaskTypes
import net.minecraft.nbt.CompoundTag
import java.util.function.BiConsumer

class GuiProviders {
    fun setTaskGuiProviders() {
        TaskTypes.ITEM.guiProvider =
            TaskType.GuiProvider { gui: Panel?, quest: Quest?, callback: BiConsumer<Task?, CompoundTag?>? ->
                val c = ItemStackConfig(false, false)
                SelectItemStackScreen(c) { accepted: Boolean ->
                    gui!!.run()
                    if (accepted) {
                        val itemTask = ItemTask(0L, quest).setStackAndCount(c.getValue(), c.getValue().count)
                        callback!!.accept(itemTask, itemTask.type.makeExtraNBT())
                    }
                }.openGui()
            }
    }
}