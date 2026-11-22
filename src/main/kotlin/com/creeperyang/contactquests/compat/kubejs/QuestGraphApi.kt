package com.creeperyang.contactquests.compat.kubejs

import com.creeperyang.contactquests.quest.QuestGraphService
import net.minecraft.server.level.ServerLevel
import net.minecraft.nbt.CompoundTag

/** 向后兼容的任务图谱 Kotlin 公开门面；底层服务本身不强依赖 KubeJS。 */
object QuestGraphApi {
    fun createChapter(level: ServerLevel, key: String, title: String) = QuestGraphService.createChapter(level, key, title)
    fun createChapter(level: ServerLevel, key: String, title: String, options: Map<String, Any?>) = QuestGraphService.createChapter(level, key, title, options)
    fun createQuest(level: ServerLevel, chapterId: String, key: String, title: String) = QuestGraphService.createQuest(level, chapterId, key, title)
    fun createQuest(level: ServerLevel, chapterId: String, key: String, title: String, options: Map<String, Any?>) = QuestGraphService.createQuest(level, chapterId, key, title, options)
    fun getChapter(level: ServerLevel, id: String) = QuestGraphService.getChapter(level, id)
    fun getQuest(level: ServerLevel, id: String) = QuestGraphService.getQuest(level, id)
    fun getTask(level: ServerLevel, id: String) = QuestGraphService.getTask(level, id)
    fun getReward(level: ServerLevel, id: String) = QuestGraphService.getReward(level, id)
    fun listChapters(level: ServerLevel) = QuestGraphService.listChapters(level)
    fun listQuests(level: ServerLevel, chapterId: String) = QuestGraphService.listQuests(level, chapterId)
    fun listTasks(level: ServerLevel, questId: String) = QuestGraphService.listTasks(level, questId)
    fun listRewards(level: ServerLevel, questId: String) = QuestGraphService.listRewards(level, questId)
    fun updateQuest(level: ServerLevel, id: String, options: Map<String, Any?>) = QuestGraphService.updateQuest(level, id, options)
    fun updateChapter(level: ServerLevel, id: String, options: Map<String, Any?>) = QuestGraphService.updateChapter(level, id, options)
    fun addTask(level: ServerLevel, questId: String, key: String, type: String, data: CompoundTag) = QuestGraphService.addTask(level, questId, key, type, data)
    fun addReward(level: ServerLevel, questId: String, key: String, type: String, data: CompoundTag) = QuestGraphService.addReward(level, questId, key, type, data)
    fun updateTask(level: ServerLevel, id: String, data: CompoundTag) = QuestGraphService.updateTask(level, id, data)
    fun updateReward(level: ServerLevel, id: String, data: CompoundTag) = QuestGraphService.updateReward(level, id, data)
    fun moveQuest(level: ServerLevel, id: String, chapterId: String) = QuestGraphService.moveQuest(level, id, chapterId)
    fun deleteQuest(level: ServerLevel, id: String) = QuestGraphService.deleteQuest(level, id)
    fun deleteChapter(level: ServerLevel, id: String) = QuestGraphService.deleteChapter(level, id)
    fun deleteTask(level: ServerLevel, id: String) = QuestGraphService.deleteTask(level, id)
    fun deleteReward(level: ServerLevel, id: String) = QuestGraphService.deleteReward(level, id)
}
