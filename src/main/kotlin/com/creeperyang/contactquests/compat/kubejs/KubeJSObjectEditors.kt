package com.creeperyang.contactquests.compat.kubejs

import com.creeperyang.contactquests.bridge.IQuestExtension
import com.creeperyang.contactquests.compat.kubejs.KubeJSQuestSync.LOGGER
import com.creeperyang.contactquests.compat.kubejs.KubeJSQuestSync.getQuest
import com.creeperyang.contactquests.compat.kubejs.KubeJSQuestSync.getReward
import com.creeperyang.contactquests.compat.kubejs.KubeJSQuestSync.resolveId
import com.creeperyang.contactquests.compat.kubejs.KubeJSQuestSync.syncObject
import com.creeperyang.contactquests.data.DataManager
import com.creeperyang.contactquests.network.SyncQuestTextMessage
import com.creeperyang.contactquests.quest.reward.ParcelReward
import com.creeperyang.contactquests.quest.reward.PostcardReward
import com.creeperyang.contactquests.quest.reward.RedPacketReward
import com.creeperyang.contactquests.quest.task.ContactTask
import com.creeperyang.contactquests.quest.task.ParcelTask
import com.creeperyang.contactquests.quest.task.PostcardTask
import com.creeperyang.contactquests.quest.task.RedPacketTask
import dev.ftb.mods.ftbquests.quest.Quest
import dev.ftb.mods.ftbquests.quest.QuestObjectBase
import dev.ftb.mods.ftbquests.quest.ServerQuestFile
import dev.ftb.mods.ftbquests.quest.reward.Reward
import dev.ftb.mods.ftbquests.quest.task.Task
import dev.ftb.mods.ftbquests.quest.translation.TranslationKey
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.network.PacketDistributor

/** 负责 KubeJS 脚本对邮件任务、奖励及任务文本的全局/按存档覆写修改与重载恢复。 */
internal object KubeJSObjectEditors {
    fun loadPersistentData(server: MinecraftServer) {
        val level = server.overworld()
        val savedData = QuestOverrideSavedData.get(level)
        val overrides = savedData.overrides
        val registryAccess = server.registryAccess()

        if (overrides.isEmpty()) return

        LOGGER.info("ContactQuests: 正在从存档数据加载 ${overrides.size} 条任务覆写...")
        var count = 0

        for ((id, tag) in overrides) {
            try {
                val task = DataManager.parcelTasks[id]
                    ?: DataManager.redPacketTasks[id]
                    ?: DataManager.postcardTasks[id]

                if (task != null) {
                    applyTaskOverride(task, tag, registryAccess)
                    count++
                    continue
                }

                val reward = getReward(id)
                if (reward != null) {
                    applyRewardOverride(reward, tag, registryAccess)
                    count++
                }

                val quest = getQuest(id)
                if (quest != null) {
                    applyQuestOverride(quest, tag)
                    count++
                }
            } catch (e: Exception) {
                LOGGER.error("ContactQuests: 应用 ID $id 的持久化覆写失败", e)
            }
        }
        LOGGER.info("ContactQuests: 已应用 $count 条持久化覆写。")
    }

    private fun applyQuestOverride(quest: Quest, tag: CompoundTag) {
        val ext = (quest as Any) as? IQuestExtension ?: return

        if (tag.contains("description", Tag.TAG_COMPOUND.toInt())) {
            val map = tag.getCompound("description")
            map.allKeys.forEach { locale ->
                val list = map.getList(locale, Tag.TAG_STRING.toInt()).map { it.asString }
                ext.`contactQuests$setDescriptionOverride`(locale, ArrayList(list))
            }
        }

        if (tag.contains("title", Tag.TAG_COMPOUND.toInt())) {
            val map = tag.getCompound("title")
            map.allKeys.forEach { locale ->
                ext.`contactQuests$setTitleOverride`(locale, map.getString(locale))
            }
        }

        if (tag.contains("subtitle", Tag.TAG_COMPOUND.toInt())) {
            val map = tag.getCompound("subtitle")
            map.allKeys.forEach { locale ->
                ext.`contactQuests$setSubtitleOverride`(locale, map.getString(locale))
            }
        }

        quest.clearCachedData()
    }

    private fun applyTaskOverride(task: ContactTask, tag: CompoundTag, provider: HolderLookup.Provider) {
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

    private fun applyRewardOverride(reward: Reward, tag: CompoundTag, provider: HolderLookup.Provider) {
        val helper = OverrideHelper(tag, provider)

        when (reward) {
            is ParcelReward -> {
                helper.updateItem { reward.item = it }
                helper.updateInt("count") { reward.count = it }
                helper.updateInt("randomBonus") { reward.randomBonus = it }
                helper.updateString("target") { reward.targetAddressee = it }
            }

            is RedPacketReward -> {
                helper.updateItem { reward.item = it }
                helper.updateInt("count") { reward.count = it }
                helper.updateInt("randomBonus") { reward.randomBonus = it }
                helper.updateString("blessing") { reward.blessing = it }
                helper.updateString("target") { reward.targetAddressee = it }
            }

            is PostcardReward -> {
                helper.updateString("style") { reward.postcardStyle = it }
                helper.updateString("text") { reward.postcardText = it }
                helper.updateString("target") { reward.targetAddressee = it }
            }
        }

        if (helper.changed) syncObject(reward, false)
    }

    private class OverrideHelper(val tag: CompoundTag, val provider: HolderLookup.Provider) {
        var changed = false

        private inline fun <T> update(key: String, getter: (String) -> T, setter: (T) -> Unit) {
            if (tag.contains(key)) {
                setter(getter(key))
                changed = true
            }
        }

        fun updateString(key: String, setter: (String) -> Unit) =
            update(key, { tag.getString(it) }, setter)

        fun updateInt(key: String, setter: (Int) -> Unit) =
            update(key, { tag.getInt(it) }, setter)

        fun updateLong(key: String, setter: (Long) -> Unit) =
            update(key, { tag.getLong(it) }, setter)

        fun updateItem(key: String = "item", setter: (ItemStack) -> Unit) {
            if (tag.contains(key)) {
                setter(ItemStack.parseOptional(provider, tag.getCompound(key)))
                changed = true
            }
        }
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

    private class ChangeTracker {
        var changed = false

        fun <T> update(current: T, new: T?, setter: (T) -> Unit) {
            if (new != null && current != new) {
                setter(new)
                changed = true
            }
        }

        fun updateItem(current: ItemStack, new: ItemStack?, setter: (ItemStack) -> Unit) {
            if (new != null && !ItemStack.matches(current, new)) {
                setter(new)
                changed = true
            }
        }

        fun setTarget(task: ContactTask, newTarget: String?) {
            if (newTarget != null && task.targetAddressee != newTarget) {
                updateTaskTarget(task, newTarget)
                changed = true
            }
        }
    }

    private fun <T : QuestObjectBase> executeEdit(
        id: Any,
        saveToConfig: Boolean,
        lookup: (Long) -> T?,
        applier: (T, ChangeTracker) -> Unit
    ): Boolean {
        val longId = resolveId(id) ?: return false
        val obj = lookup(longId) ?: return false
        val tracker = ChangeTracker()

        applier(obj, tracker)

        if (tracker.changed) {
            syncObject(obj, saveToConfig)
            LOGGER.info("ContactQuests: 已修改 ${obj.javaClass.simpleName} '$longId' (save=$saveToConfig)")
        }
        return tracker.changed
    }

    private fun saveToNbt(level: ServerLevel, id: Any, data: Map<String, Any?>) {
        val longId = resolveId(id) ?: return
        val savedData = QuestOverrideSavedData.get(level)
        val registry = level.registryAccess()

        data.forEach { (key, value) ->
            when (value) {
                is ItemStack -> savedData.setOverride(longId, key, value, registry)
                is Number -> savedData.setOverride(longId, key, value.toLong(), registry)
                is String -> savedData.setOverride(longId, key, value, registry)
            }
        }
    }

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

    fun editParcelReward(id: Any, item: ItemStack?, count: Number?, randomBonus: Number?, target: String?) =
        editParcelRewardInternal(id, item, count, randomBonus, target, true)

    fun editParcelRewardSaved(
        level: ServerLevel,
        id: Any,
        item: ItemStack?,
        count: Number?,
        randomBonus: Number?,
        target: String?
    ): Boolean {
        if (editParcelRewardInternal(id, item, count, randomBonus, target, false)) {
            saveToNbt(
                level,
                id,
                mapOf(
                    "item" to item,
                    "count" to count?.toInt(),
                    "randomBonus" to randomBonus?.toInt(),
                    "target" to target
                )
            )
            return true
        }
        return false
    }

    private fun editParcelRewardInternal(
        id: Any,
        item: ItemStack?,
        count: Number?,
        randomBonus: Number?,
        target: String?,
        save: Boolean
    ) =
        executeEdit(id, save, { getReward(it) as? ParcelReward }) { reward, tracker ->
            tracker.updateItem(reward.item, item) { reward.item = it }
            tracker.update(reward.count, count?.toInt()) { reward.count = it }
            tracker.update(reward.randomBonus, randomBonus?.toInt()) { reward.randomBonus = it }
            tracker.update(reward.targetAddressee, target) { reward.targetAddressee = it }
        }

    fun editRedPacketReward(
        id: Any,
        item: ItemStack?,
        count: Number?,
        blessing: String?,
        randomBonus: Number?,
        target: String?
    ) =
        editRedPacketRewardInternal(id, item, count, blessing, randomBonus, target, true)

    fun editRedPacketRewardSaved(
        level: ServerLevel,
        id: Any,
        item: ItemStack?,
        count: Number?,
        blessing: String?,
        randomBonus: Number?,
        target: String?
    ): Boolean {
        if (editRedPacketRewardInternal(id, item, count, blessing, randomBonus, target, false)) {
            saveToNbt(
                level,
                id,
                mapOf(
                    "item" to item,
                    "count" to count?.toInt(),
                    "blessing" to blessing,
                    "randomBonus" to randomBonus?.toInt(),
                    "target" to target
                )
            )
            return true
        }
        return false
    }

    private fun editRedPacketRewardInternal(
        id: Any,
        item: ItemStack?,
        count: Number?,
        blessing: String?,
        randomBonus: Number?,
        target: String?,
        save: Boolean
    ) =
        executeEdit(id, save, { getReward(it) as? RedPacketReward }) { reward, tracker ->
            tracker.updateItem(reward.item, item) { reward.item = it }
            tracker.update(reward.count, count?.toInt()) { reward.count = it }
            tracker.update(reward.blessing, blessing) { reward.blessing = it }
            tracker.update(reward.randomBonus, randomBonus?.toInt()) { reward.randomBonus = it }
            tracker.update(reward.targetAddressee, target) { reward.targetAddressee = it }
        }

    fun editPostcardReward(id: Any, style: String?, text: String?, sender: String?) =
        editPostcardRewardInternal(id, style, text, sender, true)

    fun editPostcardRewardSaved(
        level: ServerLevel,
        id: Any,
        style: String?,
        text: String?,
        sender: String?
    ): Boolean {
        if (editPostcardRewardInternal(id, style, text, sender, false)) {
            saveToNbt(level, id, mapOf("style" to style, "text" to text, "target" to sender))
            return true
        }
        return false
    }

    private fun editPostcardRewardInternal(id: Any, style: String?, text: String?, sender: String?, save: Boolean) =
        executeEdit(id, save, { getReward(it) as? PostcardReward }) { reward, tracker ->
            tracker.update(reward.postcardStyle, style) { reward.postcardStyle = it }
            tracker.update(reward.postcardText, text) { reward.postcardText = it }
            tracker.update(reward.targetAddressee, sender) { reward.targetAddressee = it }
        }

    private fun editQuestTextInternal(id: Any, content: Any?, locale: String, type: Int, saveGlobal: Boolean): Boolean {
        val longId = resolveId(id) ?: return false
        val quest = getQuest(longId) ?: return false
        val key = getTranslationKey(type)

        val changed = if (saveGlobal) {
            applyGlobalQuestEdit(quest, longId, locale, key, type, content)
        } else {
            applySavedQuestEdit(quest, longId, locale, type, content)
        }

        if (changed) {
            PacketDistributor.sendToAllPlayers(
                SyncQuestTextMessage(longId, locale, type, content, saveGlobal)
            )
        }

        return changed
    }

    private fun applyGlobalQuestEdit(
        quest: Quest,
        longId: Long,
        locale: String,
        key: TranslationKey,
        type: Int,
        content: Any?
    ): Boolean {
        // 全局编辑会覆盖同语言下旧的按存档局部覆写，以保持与图谱编辑 API 一致的优先级语义。
        applySavedQuestEdit(quest, longId, locale, type, null)
        val field = when (key) {
            TranslationKey.TITLE -> "title"
            TranslationKey.QUEST_SUBTITLE -> "subtitle"
            else -> "description"
        }
        saveTextOverride(ServerQuestFile.INSTANCE.server.overworld(), longId, field, null, locale)
        if (type == 2) {
            val list = if (content == null) {
                ArrayList<String>()
            } else {
                (content as? List<*>)?.mapTo(ArrayList()) { it.toString() } ?: ArrayList()
            }
            quest.questFile.translationManager.addTranslation(quest, locale, key, list)
        } else {
            val str = content?.toString() ?: ""
            quest.questFile.translationManager.addTranslation(quest, locale, key, str)
        }

        ServerQuestFile.INSTANCE.markDirty()
        ServerQuestFile.INSTANCE.saveNow()
        quest.clearCachedData()

        LOGGER.info("ContactQuests: 已静默保存任务 $longId 的全局文本修改")
        return true
    }

    private fun applySavedQuestEdit(
        quest: Quest,
        longId: Long,
        locale: String,
        type: Int,
        content: Any?
    ): Boolean {
        val ext = (quest as Any) as? IQuestExtension ?: return false

        when (type) {
            0 -> ext.`contactQuests$setTitleOverride`(locale, content as? String)
            1 -> ext.`contactQuests$setSubtitleOverride`(locale, content as? String)
            2 -> ext.`contactQuests$setDescriptionOverride`(
                locale,
                (content as? List<*>)?.mapTo(ArrayList()) { it.toString() }
            )
        }

        quest.clearCachedData()
        LOGGER.debug("ContactQuests: 已更新任务 $longId 的内存文本覆写")
        return true
    }

    private fun getTranslationKey(type: Int): TranslationKey = when (type) {
        0 -> TranslationKey.TITLE
        1 -> TranslationKey.QUEST_SUBTITLE
        else -> TranslationKey.QUEST_DESC
    }

    fun resetQuestTextSaved(level: ServerLevel, id: Any, locale: String): Boolean {
        var result = true
        if (!editQuestTextInternal(id, null, locale, 0, false)) result = false
        if (!editQuestTextInternal(id, null, locale, 1, false)) result = false
        if (!editQuestTextInternal(id, null, locale, 2, false)) result = false

        saveTextOverride(level, id, "title", null, locale)
        saveTextOverride(level, id, "subtitle", null, locale)
        saveTextOverride(level, id, "description", null, locale)

        return result
    }

    fun editQuestDescriptionSaved(level: ServerLevel, id: Any, desc: Any?, locale: String): Boolean {
        val lines = descriptionLines(desc) ?: return false
        if (editQuestTextInternal(id, lines, locale, 2, false)) {
            saveTextOverride(level, id, "description", lines, locale)
            return true
        }
        return false
    }

    fun editQuestDescription(id: Any, desc: Any?, locale: String): Boolean {
        val lines = descriptionLines(desc) ?: return false
        return editQuestTextInternal(id, lines, locale, 2, true)
    }

    private fun descriptionLines(value: Any?): List<String>? {
        if (value == null) return null
        require(value is List<*>) { "Description needs a string array" }
        return value.map { require(it is CharSequence) { "Description lines must be strings" }; it.toString() }
    }

    fun editQuestTitleSaved(level: ServerLevel, id: Any, title: String?, locale: String): Boolean {
        if (title != null && editQuestTextInternal(id, title, locale, 0, false)) {
            saveTextOverride(level, id, "title", title, locale)
            return true
        }
        return false
    }

    fun editQuestTitle(id: Any, title: String?, locale: String): Boolean {
        if (title != null) {
            return editQuestTextInternal(id, title, locale, 0, true)
        }
        return false
    }

    fun editQuestSubtitleSaved(level: ServerLevel, id: Any, subtitle: String?, locale: String): Boolean {
        if (subtitle != null && editQuestTextInternal(id, subtitle, locale, 1, false)) {
            saveTextOverride(level, id, "subtitle", subtitle, locale)
            return true
        }
        return false
    }

    fun editQuestSubtitle(id: Any, subtitle: String?, locale: String): Boolean {
        if (subtitle != null) {
            return editQuestTextInternal(id, subtitle, locale, 1, true)
        }
        return false
    }

    private fun saveTextOverride(level: ServerLevel, id: Any, key: String, content: Any?, locale: String) {
        val savedData = QuestOverrideSavedData.get(level)
        val questId = resolveId(id) ?: return
        val currentOverride = savedData.getOverride(questId) ?: CompoundTag()

        val currentMap = if (currentOverride.contains(key, Tag.TAG_COMPOUND.toInt())) {
            currentOverride.getCompound(key)
        } else {
            CompoundTag()
        }

        if (content != null) {
            if (content is String) currentMap.putString(locale, content)
            else if (content is List<*>) {
                val listTag = ListTag()
                content.forEach { listTag.add(StringTag.valueOf(it.toString())) }
                currentMap.put(locale, listTag)
            }
        } else {
            currentMap.remove(locale)
        }

        val mapToSave = HashMap<String, Any>()
        currentMap.allKeys.forEach { k ->
            if (currentMap.contains(k, Tag.TAG_STRING.toInt())) mapToSave[k] = currentMap.getString(k)
            else if (currentMap.contains(k, Tag.TAG_LIST.toInt())) {
                mapToSave[k] = currentMap.getList(k, Tag.TAG_STRING.toInt()).map { it.asString }
            }
        }
        savedData.setOverride(questId, key, mapToSave, level.registryAccess())
    }
}
