package com.creeperyang.contactquests.compat.kubejs

import com.creeperyang.contactquests.compat.kubejs.KubeJSObjectEditors.EditTracker
import com.creeperyang.contactquests.compat.kubejs.KubeJSObjectEditors.OverrideHelper
import com.creeperyang.contactquests.compat.kubejs.KubeJSObjectEditors.executeEdit
import com.creeperyang.contactquests.compat.kubejs.KubeJSObjectEditors.saveToNbt
import com.creeperyang.contactquests.compat.kubejs.KubeJSObjectEditors.syncObject
import com.creeperyang.contactquests.compat.kubejs.KubeJSQuestSync.LOGGER
import com.creeperyang.contactquests.data.DataManager
import com.creeperyang.contactquests.quest.task.ContactTask
import com.creeperyang.contactquests.quest.task.ParcelTask
import com.creeperyang.contactquests.quest.task.PostcardTask
import com.creeperyang.contactquests.quest.task.RedPacketTask
import dev.ftb.mods.ftbquests.quest.task.Task
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack

/** 编辑邮件任务及按存档覆写，并维护收件人索引。 */
internal object KubeJSTaskEditors {
    fun editParcelTask(id: Any, item: ItemStack?, count: Number?, target: String?) =
        editParcelTaskInternal(id, item, count, target, true)

    fun editParcelTaskSaved(
        level: ServerLevel,
        id: Any,
        item: ItemStack?,
        count: Number?,
        target: String?
    ): Boolean {
        if (editParcelTaskInternal(id, item, count, target, false)) {
            saveToNbt(level, id, mapOf("item" to item, "count" to count?.toLong(), "target" to target))
            return true
        }
        return false
    }

    private fun editParcelTaskInternal(id: Any, item: ItemStack?, count: Number?, target: String?, save: Boolean) =
        executeEdit(id, save, { DataManager.parcelTasks[it] }) { task, tracker ->
            tracker.updateItem(task.itemStack, item) { task.itemStack = it }
            tracker.update(task.count, count?.toLong()) { task.count = it }
            tracker.setTarget(task, target)
        }

    fun editRedPacketTask(id: Any, item: ItemStack?, count: Number?, blessing: String?, target: String?) =
        editRedPacketTaskInternal(id, item, count, blessing, target, true)

    fun editRedPacketTaskSaved(
        level: ServerLevel,
        id: Any,
        item: ItemStack?,
        count: Number?,
        blessing: String?,
        target: String?
    ): Boolean {
        if (editRedPacketTaskInternal(id, item, count, blessing, target, false)) {
            saveToNbt(
                level,
                id,
                mapOf("item" to item, "count" to count?.toLong(), "blessing" to blessing, "target" to target)
            )
            return true
        }
        return false
    }

    private fun editRedPacketTaskInternal(
        id: Any,
        item: ItemStack?,
        count: Number?,
        blessing: String?,
        target: String?,
        save: Boolean
    ) =
        executeEdit(id, save, { DataManager.redPacketTasks[it] }) { task, tracker ->
            tracker.updateItem(task.itemStack, item) { task.itemStack = it }
            tracker.update(task.count, count?.toLong()) { task.count = it }
            tracker.update(task.blessing, blessing) { task.blessing = it }
            tracker.setTarget(task, target)
        }

    fun editPostcardTask(id: Any, style: String?, text: String?, target: String?) =
        editPostcardTaskInternal(id, style, text, target, true)

    fun editPostcardTaskSaved(
        level: ServerLevel,
        id: Any,
        style: String?,
        text: String?,
        target: String?
    ): Boolean {
        if (editPostcardTaskInternal(id, style, text, target, false)) {
            saveToNbt(level, id, mapOf("style" to style, "text" to text, "target" to target))
            return true
        }
        return false
    }

    private fun editPostcardTaskInternal(id: Any, style: String?, text: String?, target: String?, save: Boolean) =
        executeEdit(id, save, { DataManager.postcardTasks[it] }) { task, tracker ->
            tracker.update(task.postcardStyle, style) { task.postcardStyle = it }
            tracker.update(task.postcardText, text) { task.postcardText = it }
            tracker.setTarget(task, target)
        }

    internal fun applyTaskOverride(task: ContactTask, tag: CompoundTag, provider: HolderLookup.Provider) {
        val helper = OverrideHelper(tag, provider)

        when (task) {
            is ParcelTask -> {
                helper.updateItem { task.itemStack = it }
                helper.updateLong("count") { task.count = it }
                helper.updateString("target") { updateTaskTarget(task, it) }
            }

            is RedPacketTask -> {
                helper.updateItem { task.itemStack = it }
                helper.updateLong("count") { task.count = it }
                helper.updateString("blessing") { task.blessing = it }
                helper.updateString("target") { updateTaskTarget(task, it) }
            }

            is PostcardTask -> {
                helper.updateString("style") { task.postcardStyle = it }
                helper.updateString("text") { task.postcardText = it }
                helper.updateString("target") { updateTaskTarget(task, it) }
            }
        }

        if (helper.changed) syncObject(task, false)
    }

    private fun updateTaskTarget(task: Task, newTarget: String) {
        if (task !is ContactTask) return

        val oldTarget = task.targetAddressee
        if (oldTarget == newTarget) return

        val map = when (task) {
            is ParcelTask -> DataManager.parcelReceiver
            is RedPacketTask -> DataManager.redPacketReceiver
            is PostcardTask -> DataManager.postcardReceiver
            else -> null
        }

        if (map != null) {
            map[oldTarget]?.remove(task.id)
            if (map[oldTarget]?.isEmpty() == true) map.remove(oldTarget)
            map.computeIfAbsent(newTarget) { mutableSetOf() }.add(task.id)
        }

        task.targetAddressee = newTarget
        LOGGER.info("ContactQuests: 已将任务 ${task.id} 的目标收件人从 '$oldTarget' 更新为 '$newTarget'")
    }

    private fun EditTracker.setTarget(task: ContactTask, newTarget: String?) {
        if (newTarget != null && task.targetAddressee != newTarget) {
            updateTaskTarget(task, newTarget)
            changed = true
        }
    }
}
