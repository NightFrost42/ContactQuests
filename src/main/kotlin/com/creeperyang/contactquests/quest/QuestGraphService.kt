package com.creeperyang.contactquests.quest

import com.creeperyang.contactquests.data.DataManager
import com.creeperyang.contactquests.compat.kubejs.ContactKubeJSPlugin
import com.creeperyang.contactquests.compat.kubejs.QuestOverrideSavedData
import net.minecraftforge.fml.ModList
import dev.ftb.mods.ftbquests.net.SyncQuestsMessage
import dev.ftb.mods.ftbquests.quest.*
import dev.ftb.mods.ftbquests.quest.reward.Reward
import dev.ftb.mods.ftbquests.quest.reward.RewardType
import dev.ftb.mods.ftbquests.quest.task.Task
import dev.ftb.mods.ftbquests.quest.task.TaskType
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** 仅允许在服务端主线程调用的任务图谱持久化操作服务，为脚本/AI 工具提供确定且稳定的 64 位对象 ID。 */
object QuestGraphService {
    private val keyPattern = Regex("[a-z0-9][a-z0-9_./:-]{0,127}")

    private fun file(level: ServerLevel): ServerQuestFile {
        val file = ServerQuestFile.INSTANCE ?: error("FTB Quests has not loaded yet")
        require(file.server === level.server && level.server.isSameThread) {
            "Quest graph edits must run on the owning server thread"
        }
        return file
    }

    private fun requireTask(id: Long, quest: Quest, type: String): Task =
        TaskType.createTask(id, quest, type) ?: error("Unknown task type: $type")

    private fun stableId(kind: String, key: String): Long {
        require(keyPattern.matches(key)) { "Use a lowercase stable key, for example pack:chapter/start" }
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest("contactquests:$kind:$key".toByteArray(StandardCharsets.UTF_8))
        var id = 0L
        for (i in 0 until 8) id = (id shl 8) or (bytes[i].toLong() and 255)
        // 保留正数且高半字节为 6 的非零稳定区间；JS 调用方始终通过十六进制字符串传递完整 64 位 ID，避免双精度浮点数截断精度。
        return (id and 0x0fffffffffffffffL) or 0x6000000000000000L
    }

    private fun resolve(value: String): Long {
        val code = value.removePrefix("0x")
        return runCatching { java.lang.Long.parseUnsignedLong(code, 16) }.getOrNull()
            ?: error("Invalid FTB Quests ID: $value; use the returned codeString")
    }

    private fun persist(file: ServerQuestFile) {
        file.refreshIDMap()
        file.clearCachedData()
        file.clearCachedProgress()
        file.markDirty()
        file.saveNow()
        DataManager.ensureTargetsFresh()
        SyncQuestsMessage(file).sendToAll(file.server)
        file.server.playerList.players.forEach {
            if (ModList.get().isLoaded("kubejs")) ContactKubeJSPlugin.syncAllOverridesToPlayer(it)
        }
    }

    @JvmOverloads
    fun createChapter(
        level: ServerLevel,
        key: String,
        title: String,
        options: Map<String, Any?> = emptyMap()
    ): Chapter {
        val file = file(level)
        val id = stableId("chapter", key)
        val existing = file.getBase(id)
        require(existing == null || existing is Chapter) { "ID belongs to another object type" }
        val chapter = existing as? Chapter ?: Chapter(
            id,
            file,
            file.defaultChapterGroup,
            "cq_" + java.lang.Long.toHexString(id)
        )
        val prepared = QuestGraphSettings.chapter(level, chapter, mapOf("title" to title) + options, ::resolve, true)
        // 已存在的章节/任务 Key 保持幂等创建语义；仍校验传入参数合法性，但不隐式覆写已有内容。
        if (existing != null) return chapter
        chapter.readData(prepared.data)
        file.defaultChapterGroup.addChapter(chapter)
        QuestGraphSettings.applyText(chapter, prepared)
        persist(file)
        return chapter
    }

    @JvmOverloads
    fun createQuest(
        level: ServerLevel,
        chapterId: String,
        key: String,
        title: String,
        options: Map<String, Any?> = emptyMap()
    ): Quest {
        val file = file(level)
        val chapter = file.getChapter(resolve(chapterId)) ?: error("Chapter does not exist")
        val id = stableId("quest", key)
        val existing = file.getBase(id)
        require(existing == null || existing is Quest && existing.chapter === chapter) {
            "Quest key belongs to another object/parent"
        }
        val quest = existing as? Quest ?: Quest(id, chapter)
        val prepared = QuestGraphSettings.quest(level, quest, mapOf("title" to title) + options, ::resolve, true)
        if (existing != null) return quest
        quest.readData(prepared.data)
        chapter.addQuest(quest)
        QuestGraphSettings.applyText(quest, prepared)
        persist(file)
        return quest
    }

    fun getChapter(level: ServerLevel, id: String): Chapter? = file(level).getChapter(resolve(id))
    fun getQuest(level: ServerLevel, id: String): Quest? = file(level).getQuest(resolve(id))
    fun getTask(level: ServerLevel, id: String): Task? = file(level).getTask(resolve(id))
    fun getReward(level: ServerLevel, id: String): Reward? = file(level).getReward(resolve(id))
    fun listChapters(level: ServerLevel): List<Chapter> = file(level).allChapters.toList()
    fun listQuests(level: ServerLevel, chapterId: String): List<Quest> =
        getChapter(level, chapterId)?.quests?.toList() ?: emptyList()
    fun listTasks(level: ServerLevel, questId: String): List<Task> =
        getQuest(level, questId)?.tasks?.toList() ?: emptyList()
    fun listRewards(level: ServerLevel, questId: String): List<Reward> =
        getQuest(level, questId)?.rewards?.toList() ?: emptyList()

    /** 在执行图谱写入、保存与同步前先完整构造校验任务及其全部子任务与奖励。 */
    fun publishQuest(
        level: ServerLevel,
        chapterId: String,
        key: String,
        options: Map<String, Any?>,
        tasks: List<Map<String, Any?>>,
        rewards: List<Map<String, Any?>>
    ): Quest {
        val file = file(level)
        val chapter = file.getChapter(resolve(chapterId)) ?: error("Chapter does not exist")
        require(tasks.size <= 128 && rewards.size <= 128) { "Too many quest children" }
        val id = stableId("quest", key)
        val existing = file.getBase(id)
        require(existing == null || existing is Quest && existing.chapter === chapter) {
            "Quest key belongs to another object/parent"
        }
        val quest = Quest(id, chapter)
        val prepared = QuestGraphSettings.quest(level, quest, options, ::resolve, true)
        val ids = mutableSetOf(id)
        val taskData = tasks.mapIndexed { index, fields ->
            require(fields.keys.all { it in setOf("key", "type", "data") }) { "Unknown task bundle fields" }
            val childKey = (fields["key"] as? String)?.takeUnless { it.isBlank() } ?: "$key/task/$index"
            val type = fields["type"] as? String ?: error("Task type is required")
            val childId = stableId("task", childKey)
            require(
                ids.add(childId) &&
                    (file.getBase(childId) == null || existing is Quest && file.getTask(childId)?.quest === existing)
            ) {
                "Task key collision"
            }
            val task = requireTask(childId, quest, type)
            val input = QuestObjectSettings.prepare(level, task, QuestGraphSettings.creationData(fields["data"]), true)
            val merged = QuestObjectSettings.merge(level, task, input)
            NativeObjectSettings.validate(level, task, merged)
            QuestGraphValidation.validateInput(task, merged)
            task.readData(merged)
            QuestGraphValidation.validate(task)
            task to input
        }
        val rewardData = rewards.mapIndexed { index, fields ->
            require(fields.keys.all { it in setOf("key", "type", "data") }) { "Unknown reward bundle fields" }
            val childKey = (fields["key"] as? String)?.takeUnless { it.isBlank() } ?: "$key/reward/$index"
            val type = fields["type"] as? String ?: error("Reward type is required")
            val childId = stableId("reward", childKey)
            require(
                ids.add(childId) &&
                    (file.getBase(childId) == null || existing is Quest && file.getReward(childId)?.quest === existing)
            ) {
                "Reward key collision"
            }
            val reward = RewardType.createReward(childId, quest, type) ?: error("Unknown reward type")
            val input = QuestObjectSettings.prepare(
                level,
                reward,
                QuestGraphSettings.creationData(fields["data"]),
                true
            )
            val merged = QuestObjectSettings.merge(level, reward, input)
            NativeObjectSettings.validate(level, reward, merged)
            QuestGraphValidation.validateInput(reward, merged)
            reward.readData(merged)
            QuestGraphValidation.validate(reward)
            Triple(reward, input, merged)
        }
        rewardData.forEach { (reward, _, merged) ->
            InlineQuestSettings.entryIds(reward, merged).forEach { entryId ->
                require(ids.add(entryId)) { "Inline reward ID collides within the quest bundle" }
            }
        }
        if (existing != null) return existing as Quest
        quest.readData(prepared.data)
        taskData.forEach { quest.addTask(it.first) }
        rewardData.forEach { quest.addReward(it.first) }
        chapter.addQuest(quest)
        QuestGraphSettings.applyText(quest, prepared)
        taskData.forEach { QuestObjectSettings.text(level, it.first, it.second) }
        rewardData.forEach { QuestObjectSettings.text(level, it.first, it.second) }
        persist(file)
        return quest
    }

    /** 未提供的字段保持原值；传入空列表表示清空；传入 `DEFAULT` 恢复继承原生章设置。 */
    fun updateQuest(level: ServerLevel, id: String, options: Map<String, Any?>): Boolean {
        val file = file(level)
        val quest = file.getQuest(resolve(id)) ?: return false
        if (options.isEmpty()) return false
        val prepared = QuestGraphSettings.quest(level, quest, options, ::resolve)
        quest.readData(prepared.data)
        val saved = QuestOverrideSavedData.get(level)
        val ext = (quest as Any) as? com.creeperyang.contactquests.bridge.IQuestExtension
        prepared.text.forEach { (key, _) ->
            val field = when (key) {
                TextKey.TITLE -> "title"
                TextKey.QUEST_SUBTITLE -> "subtitle"
                else -> "description"
            }
            saved.getOverride(quest.id)?.getCompound(field)?.remove(prepared.locale)
            when (key) {
                TextKey.TITLE -> ext?.`contactQuests$setTitleOverride`(prepared.locale, null)
                TextKey.QUEST_SUBTITLE -> ext?.`contactQuests$setSubtitleOverride`(prepared.locale, null)
                else -> ext?.`contactQuests$setDescriptionOverride`(prepared.locale, null)
            }
        }
        saved.setDirty()
        QuestGraphSettings.applyText(quest, prepared)
        persist(file)
        return true
    }

    fun updateChapter(level: ServerLevel, id: String, options: Map<String, Any?>): Boolean {
        val file = file(level)
        val chapter = file.getChapter(resolve(id)) ?: return false
        if (options.isEmpty()) return false
        val prepared = QuestGraphSettings.chapter(level, chapter, options, ::resolve)
        chapter.readData(prepared.data)
        QuestGraphSettings.applyText(chapter, prepared)
        persist(file)
        return true
    }

    fun addTask(level: ServerLevel, questId: String, key: String, type: String, data: CompoundTag): Task {
        val file = file(level)
        val quest = file.getQuest(resolve(questId)) ?: error("Quest does not exist")
        val id = stableId("task", key)
        val existing = file.getBase(id)
        require(
            existing == null ||
                existing is Task && existing.quest === quest && existing.type.typeForNBT.toString() == type
        ) {
            "Stable task key already belongs to another task"
        }
        val candidate = requireTask(id, quest, type)
        val input = QuestObjectSettings.prepare(level, candidate, data, true)
        val merged = QuestObjectSettings.merge(level, existing ?: candidate, input)
        NativeObjectSettings.validate(level, candidate, merged)
        QuestGraphValidation.validateInput(candidate, merged)
        candidate.readData(merged)
        QuestGraphValidation.validate(candidate)
        val task = existing as? Task ?: candidate.also { quest.addTask(it) }
        task.readData(merged)
        clearFieldOverrides(level, task.id, input, true)
        QuestObjectSettings.text(level, task, input)
        persist(file)
        return task
    }

    fun addReward(level: ServerLevel, questId: String, key: String, type: String, data: CompoundTag): Reward {
        val file = file(level)
        val quest = file.getQuest(resolve(questId)) ?: error("Quest does not exist")
        val id = stableId("reward", key)
        val existing = file.getBase(id)
        require(
            existing == null ||
                existing is Reward && existing.quest === quest && existing.type.typeForNBT.toString() == type
        ) {
            "Stable reward key already belongs to another reward"
        }
        val candidate = RewardType.createReward(id, quest, type) ?: error("Unknown reward type: $type")
        val input = QuestObjectSettings.prepare(level, candidate, data, true)
        val merged = QuestObjectSettings.merge(level, existing ?: candidate, input)
        NativeObjectSettings.validate(level, candidate, merged)
        QuestGraphValidation.validateInput(candidate, merged)
        candidate.readData(merged)
        QuestGraphValidation.validate(candidate)
        val reward = existing as? Reward ?: candidate.also { quest.addReward(it) }
        reward.readData(merged)
        clearFieldOverrides(level, reward.id, input, false)
        QuestObjectSettings.text(level, reward, input)
        persist(file)
        return reward
    }

    fun updateTask(level: ServerLevel, id: String, data: CompoundTag): Boolean {
        val task = getTask(level, id) ?: return false
        if (data.isEmpty) return false
        val input = QuestObjectSettings.prepare(level, task, data)
        val merged = QuestObjectSettings.merge(level, task, input)
        val candidate = requireTask(task.id, task.quest, task.type.typeForNBT.toString())
        NativeObjectSettings.validate(level, candidate, merged)
        QuestGraphValidation.validateInput(candidate, merged)
        candidate.readData(merged)
        QuestGraphValidation.validate(candidate)
        task.readData(merged)
        clearFieldOverrides(level, task.id, data, true)
        QuestObjectSettings.text(level, task, input)
        persist(file(level))
        return true
    }

    fun updateReward(level: ServerLevel, id: String, data: CompoundTag): Boolean {
        val reward = getReward(level, id) ?: return false
        if (data.isEmpty) return false
        val input = QuestObjectSettings.prepare(level, reward, data)
        val merged = QuestObjectSettings.merge(level, reward, input)
        val candidate = RewardType.createReward(reward.id, reward.quest, reward.type.typeForNBT.toString())!!
        NativeObjectSettings.validate(level, candidate, merged)
        QuestGraphValidation.validateInput(candidate, merged)
        candidate.readData(merged)
        QuestGraphValidation.validate(candidate)
        reward.readData(merged)
        clearFieldOverrides(level, reward.id, data, false)
        QuestObjectSettings.text(level, reward, input)
        persist(file(level))
        return true
    }

    fun moveQuest(level: ServerLevel, id: String, chapterId: String): Boolean {
        val file = file(level)
        val quest = file.getQuest(resolve(id)) ?: return false
        val chapter = file.getChapter(resolve(chapterId)) ?: error("Chapter does not exist")
        if (quest.chapter === chapter) return false
        quest.onMoved(quest.x, quest.y, chapter.id)
        persist(file)
        return true
    }

    fun deleteQuest(level: ServerLevel, id: String): Boolean = delete(level, id, QuestObjectType.QUEST)
    fun deleteChapter(level: ServerLevel, id: String): Boolean = delete(level, id, QuestObjectType.CHAPTER)
    fun deleteTask(level: ServerLevel, id: String): Boolean = delete(level, id, QuestObjectType.TASK)
    fun deleteReward(level: ServerLevel, id: String): Boolean = delete(level, id, QuestObjectType.REWARD)

    private fun delete(level: ServerLevel, id: String, expected: QuestObjectType): Boolean {
        val file = file(level)
        val obj = file.getBase(resolve(id)) ?: return false
        require(obj.objectType == expected) { "ID is not a $expected" }
        val removed = mutableMapOf<Long, QuestObjectBase>()
        fun collect(value: QuestObjectBase) {
            removed[value.id] = value
            if (value is QuestObject) value.children.toList().forEach(::collect)
        }
        collect(obj)
        file.deleteObject(obj.id)
        val overrides = QuestOverrideSavedData.get(level)
        removed.forEach { (deleted, deletedObject) ->
            overrides.clearOverride(deleted)
            file.allTeamData.forEach { team ->
                val ext = team as? com.creeperyang.contactquests.bridge.ITeamDataExtension
                ext?.`contactQuests$unforceQuest`(deleted)
                ext?.`contactQuests$unblockQuest`(deleted)
            }
        }
        // FTB 原生只清理标准前置依赖引用；此处额外剔除 Contact 互斥引用，避免 ALL 互斥规则残留已删除的任务 ID。
        file.allChapters.flatMap { it.quests }.forEach { quest ->
            val tag = CompoundTag().also { quest.writeData(it) }
            val old = tag.getList("cq_mutex_ids", 8).map { it.asString }
            val retained = old.filter { resolve(it) !in removed }
            if (retained.size != old.size) {
                tag.put("cq_mutex_ids", QuestGraphValidation.nbtList(retained))
                quest.readData(tag)
            }
        }
        persist(file)
        return true
    }

    private fun clearFieldOverrides(level: ServerLevel, id: Long, changed: CompoundTag, task: Boolean) {
        val aliases = if (task) mapOf(
            "TargetAddressee" to "target",
            "postcard_style" to "style",
            "postcard_text" to "text"
        )
            else mapOf(
                "target_addressee" to "target",
                "postcard_style" to "style",
                "postcard_text" to "text",
                "random_bonus" to "randomBonus"
            )
        val saved = QuestOverrideSavedData.get(level)
        changed.allKeys.forEach { saved.clearOverride(id, aliases[it] ?: it) }
    }
}
