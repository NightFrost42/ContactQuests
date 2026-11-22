package com.creeperyang.contactquests.compat.kubejs

import com.creeperyang.contactquests.bridge.IQuestExtension
import com.creeperyang.contactquests.compat.kubejs.KubeJSObjectEditors.getQuest
import com.creeperyang.contactquests.compat.kubejs.KubeJSObjectEditors.resolveId
import com.creeperyang.contactquests.compat.kubejs.KubeJSQuestSync.LOGGER
import com.creeperyang.contactquests.network.SyncQuestTextMessage
import dev.ftb.mods.ftbquests.quest.Quest
import dev.ftb.mods.ftbquests.quest.ServerQuestFile
import dev.ftb.mods.ftbquests.quest.translation.TranslationKey
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerLevel
import net.neoforged.neoforge.network.PacketDistributor

/** 编辑任务标题、副标题与描述，处理全局翻译及按存档文本覆写。 */
internal object KubeJSQuestTextEditors {
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
        return value.map {
            require(it is CharSequence) { "Description lines must be strings" }
            it.toString()
        }
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

    internal fun applyQuestOverride(quest: Quest, tag: CompoundTag) {
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
}
