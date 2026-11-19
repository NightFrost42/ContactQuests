package com.creeperyang.contactquests.compat.kubejs

import com.creeperyang.contactquests.bridge.ITeamDataExtension
import com.creeperyang.contactquests.compat.kubejs.KubeJSQuestSync.LOGGER
import com.creeperyang.contactquests.compat.kubejs.KubeJSQuestSync.parseId
import com.creeperyang.contactquests.data.CollectionSavedData
import com.creeperyang.contactquests.quest.QuestGraphService
import com.creeperyang.contactquests.quest.QuestGraphSettings
import com.creeperyang.contactquests.quest.reward.PostcardReward
import com.creeperyang.contactquests.quest.reward.RedPacketReward
import com.creeperyang.contactquests.quest.task.PostcardTask
import com.creeperyang.contactquests.quest.task.RedPacketTask
import dev.ftb.mods.ftbquests.quest.ServerQuestFile
import dev.ftb.mods.ftbquests.quest.TeamData
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI
import dev.latvian.mods.kubejs.event.EventGroup
import dev.latvian.mods.kubejs.event.KubeEvent
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import java.util.UUID
import java.util.function.BiFunction

/** KubeJS 公开门面与事件组定义（对外暴露为 `ContactQuests` 全局绑定与 `ContactEvents` 事件组）。 */
object ContactKubeJSPlugin {
    val GROUP = EventGroup.of("ContactEvents")!!

    val REGISTER_REPLACERS = GROUP.server("registerReplacers") { RegisterReplacersEvent::class.java }!!
    val MAIL_RECEIVED = GROUP.server("mailReceived") { MailReceivedEventJS::class.java }!!
    val TAG_UNLOCKED = GROUP.server("tagUnlocked") { QuestStateEventJS::class.java }!!
    val MUTEX_CHANGED = GROUP.server("mutexChanged") { QuestStateEventJS::class.java }!!
    val SCRIPT_BLOCK_CHANGED = GROUP.server("scriptBlockChanged") { QuestStateEventJS::class.java }!!

    @JvmStatic
    fun init() {
        LOGGER.info("ContactQuests KubeJS 集成入口初始化...")
    }

    @JvmStatic
    fun reload() {
        REGISTER_REPLACERS.post(RegisterReplacersEvent())
        LOGGER.info("已重载 ContactQuests KubeJS 占位符注册事件")
    }

    @JvmStatic
    fun loadPersistentData(server: MinecraftServer) = KubeJSObjectEditors.loadPersistentData(server)

    @JvmStatic
    fun getData(player: Player): TeamData {
        val team = FTBTeamsAPI.api().manager.getTeamForPlayerID(player.uuid).orElseThrow {
            RuntimeException("ContactQuests: 未找到玩家 ${player.name.string} 所属的 FTB 团队")
        }
        return ServerQuestFile.INSTANCE.getOrCreateTeamData(team)
    }

    // region 任务图谱构建与增删改查门面

    @JvmStatic fun createChapter(level: ServerLevel, key: String, titleOrOptions: Any?) = creationArguments(titleOrOptions).let {
        QuestGraphApi.createChapter(level, key, it.first, it.second)
    }

    @JvmStatic fun createChapter(level: ServerLevel, key: String) = QuestGraphApi.createChapter(level, key, "")
    @JvmStatic fun createQuest(level: ServerLevel, chapterId: String, key: String) = QuestGraphApi.createQuest(level, chapterId, key, "")
    @JvmStatic fun createQuest(level: ServerLevel, chapterId: String, key: String, titleOrOptions: Any?) = creationArguments(titleOrOptions).let {
        QuestGraphApi.createQuest(level, chapterId, key, it.first, it.second)
    }

    // Rhino 边界需以 Any? 接收 Map 参数，避免 Map 转换器在 Kotlin 应用默认值之前就拒绝 null 传参。
    @JvmStatic fun createChapter(level: ServerLevel, key: String, title: String?, options: Any?) =
        QuestGraphApi.createChapter(level, key, title.orEmpty(), creationOptions(options))

    @JvmStatic fun createQuest(level: ServerLevel, chapterId: String, key: String, title: String?, options: Any?) =
        QuestGraphApi.createQuest(level, chapterId, key, title.orEmpty(), creationOptions(options))

    private fun creationArguments(value: Any?): Pair<String, Map<String, Any?>> = when (value) {
        null -> "" to emptyMap()
        is CharSequence -> value.toString() to emptyMap()
        is Map<*, *> -> "" to value.entries.associate { (key, option) ->
            require(key is String) { "Creation option keys must be strings" }; key to option
        }
        else -> error("Creation needs a title string, an options object, or null")
    }

    private fun creationOptions(value: Any?): Map<String, Any?> = when {
        value == null || value is CharSequence && value.isBlank() -> emptyMap()
        value is Map<*, *> -> value.entries.associate { (key, option) ->
            require(key is String) { "Creation option keys must be strings" }; key to option
        }
        else -> error("Creation options need an object or a blank default")
    }

    private fun creationChildren(value: Any?): List<Map<String, Any?>> = when {
        value == null || value is CharSequence && value.isBlank() -> emptyList()
        value is List<*> -> value.map { child ->
            require(child is Map<*, *>) { "Quest children must be objects" }
            creationOptions(child)
        }
        else -> error("Quest children need an array or a blank default")
    }

    @JvmStatic fun getChapter(level: ServerLevel, id: String) = QuestGraphApi.getChapter(level, id)
    @JvmStatic fun getQuest(level: ServerLevel, id: String) = QuestGraphApi.getQuest(level, id)
    @JvmStatic fun getTask(level: ServerLevel, id: String) = QuestGraphApi.getTask(level, id)
    @JvmStatic fun getReward(level: ServerLevel, id: String) = QuestGraphApi.getReward(level, id)
    @JvmStatic fun listChapters(level: ServerLevel) = QuestGraphApi.listChapters(level)
    @JvmStatic fun listQuests(level: ServerLevel, chapterId: String) = QuestGraphApi.listQuests(level, chapterId)
    @JvmStatic fun listTasks(level: ServerLevel, questId: String) = QuestGraphApi.listTasks(level, questId)
    @JvmStatic fun listRewards(level: ServerLevel, questId: String) = QuestGraphApi.listRewards(level, questId)
    @JvmStatic fun updateChapter(level: ServerLevel, id: String, options: Map<String, Any?>) = QuestGraphApi.updateChapter(level, id, options)
    @JvmStatic fun updateQuest(level: ServerLevel, id: String, options: Map<String, Any?>) = QuestGraphApi.updateQuest(level, id, options)
    @JvmStatic fun deleteChapter(level: ServerLevel, id: String) = QuestGraphApi.deleteChapter(level, id)
    @JvmStatic fun deleteQuest(level: ServerLevel, id: String) = QuestGraphApi.deleteQuest(level, id)
    @JvmStatic fun moveQuest(level: ServerLevel, id: String, chapterId: String) = QuestGraphApi.moveQuest(level, id, chapterId)

    @JvmStatic @JvmOverloads fun addTask(level: ServerLevel, questId: String, key: String, type: String, data: Any? = null) =
        QuestGraphApi.addTask(level, questId, key, type, QuestGraphSettings.creationData(data))

    @JvmStatic @JvmOverloads fun addReward(level: ServerLevel, questId: String, key: String, type: String, data: Any? = null) =
        QuestGraphApi.addReward(level, questId, key, type, QuestGraphSettings.creationData(data))

    @JvmStatic fun publishQuest(
        level: ServerLevel,
        chapterId: String,
        key: String,
        options: Any?,
        tasks: Any?,
        rewards: Any?
    ) = QuestGraphService.publishQuest(level, chapterId, key, creationOptions(options), creationChildren(tasks), creationChildren(rewards))

    @JvmStatic fun updateTask(level: ServerLevel, id: String, data: CompoundTag) = QuestGraphApi.updateTask(level, id, data)
    @JvmStatic fun updateReward(level: ServerLevel, id: String, data: CompoundTag) = QuestGraphApi.updateReward(level, id, data)
    @JvmStatic fun deleteTask(level: ServerLevel, id: String) = QuestGraphApi.deleteTask(level, id)
    @JvmStatic fun deleteReward(level: ServerLevel, id: String) = QuestGraphApi.deleteReward(level, id)

    // endregion

    // region 团队标签、任务强制解锁/阻塞与 NPC 暂存操作

    @JvmStatic fun addTag(team: TeamData, tag: String): Boolean = (team as ITeamDataExtension).`contactQuests$unlockTag`(tag)
    @JvmStatic fun removeTag(team: TeamData, tag: String): Boolean = (team as ITeamDataExtension).`contactQuests$removeTag`(tag)
    @JvmStatic fun hasTag(team: TeamData, tag: String): Boolean = (team as ITeamDataExtension).`contactQuests$hasTag`(tag)
    @JvmStatic fun getTags(team: TeamData): Collection<String> = (team as ITeamDataExtension).`contactQuests$getTags`()

    @JvmStatic fun setNpcConfig(level: ServerLevel, name: String, deliveryTime: Int, autoFill: Boolean) =
        KubeJSNpcSavedData.get(level).setNpcData(name, deliveryTime, autoFill)

    @JvmStatic fun clearNpcConfigs(level: ServerLevel) = KubeJSNpcSavedData.get(level).clearAll()
    @JvmStatic fun removeNpcConfig(level: ServerLevel, name: String) = KubeJSNpcSavedData.get(level).removeNpcData(name)
    @JvmStatic fun updateNpcDeliveryTime(level: ServerLevel, name: String, deliveryTime: Int) = KubeJSNpcSavedData.get(level).setNpcData(name, deliveryTime, null)
    @JvmStatic fun updateNpcAutoFill(level: ServerLevel, name: String, autoFill: Boolean) = KubeJSNpcSavedData.get(level).setNpcData(name, null, autoFill)

    @JvmStatic fun setTriggerCount(player: ServerPlayer, name: String, count: Int) =
        CollectionSavedData.get(player.serverLevel()).setTriggerCount(player.uuid, name, count)

    @JvmStatic fun setTriggerCount(level: ServerLevel, uuid: UUID, name: String, count: Int) =
        CollectionSavedData.get(level).setTriggerCount(uuid, name, count)

    @JvmStatic fun getNpcStash(player: ServerPlayer, npcName: String): List<ItemStack> =
        CollectionSavedData.get(player.serverLevel()).getStacks(player.uuid, npcName)

    @JvmStatic fun setNpcStash(player: ServerPlayer, npcName: String, items: List<ItemStack>) =
        CollectionSavedData.get(player.serverLevel()).setStacks(player.uuid, npcName, items)

    @JvmStatic fun addToNpcStash(player: ServerPlayer, npcName: String, items: List<ItemStack>) =
        CollectionSavedData.get(player.serverLevel()).addItemsSilent(player, npcName, items)

    @JvmStatic fun clearNpcStash(player: ServerPlayer, npcName: String) =
        CollectionSavedData.get(player.serverLevel()).setStacks(player.uuid, npcName, emptyList())

    @JvmStatic fun forceQuest(team: TeamData, questIdStr: String): Boolean =
        parseId(questIdStr)?.let { (team as ITeamDataExtension).`contactQuests$forceQuest`(it) } ?: false

    @JvmStatic fun unforceQuest(team: TeamData, questIdStr: String): Boolean =
        parseId(questIdStr)?.let { (team as ITeamDataExtension).`contactQuests$unforceQuest`(it) } ?: false

    @JvmStatic fun blockQuest(team: TeamData, questIdStr: String): Boolean =
        parseId(questIdStr)?.let { (team as ITeamDataExtension).`contactQuests$blockQuest`(it) } ?: false

    @JvmStatic fun unblockQuest(team: TeamData, questIdStr: String): Boolean =
        parseId(questIdStr)?.let { (team as ITeamDataExtension).`contactQuests$unblockQuest`(it) } ?: false

    @JvmStatic fun resetQuestState(team: TeamData, questIdStr: String) {
        val id = parseId(questIdStr) ?: return
        val ext = team as ITeamDataExtension
        ext.`contactQuests$unforceQuest`(id)
        ext.`contactQuests$unblockQuest`(id)
    }

    // endregion

    // region 邮件分发、任务/奖励对象修改与动态文案覆盖

    @JvmStatic fun sendParcel(player: ServerPlayer, senderName: String, items: List<ItemStack>, isEnder: Boolean) =
        KubeJSMailHelper.sendParcel(player, senderName, items, isEnder)

    @JvmStatic fun sendMail(player: ServerPlayer, senderName: String, item: ItemStack, isEnder: Boolean) =
        KubeJSMailHelper.sendMail(player, senderName, item, isEnder)

    @JvmStatic fun createRedPacket(sender: String, blessing: String, item: ItemStack): ItemStack =
        KubeJSMailHelper.createRedPacket(sender, blessing, item)

    @JvmStatic fun createPostcard(sender: String, text: String, unlockTags: List<String>, style: String?, isEnder: Boolean): ItemStack =
        KubeJSMailHelper.createPostcard(sender, text, unlockTags, style, isEnder)

    @JvmStatic fun openQuest(player: ServerPlayer, questIdStr: String) =
        KubeJSMailHelper.openQuest(player, questIdStr)

    @JvmStatic fun editParcelTask(id: Any, item: ItemStack?, count: Number?, target: String?) =
        KubeJSObjectEditors.editParcelTask(id, item, count, target)

    @JvmStatic fun editParcelTaskSaved(level: ServerLevel, id: Any, item: ItemStack? = null, count: Number? = null, target: String? = null): Boolean =
        KubeJSObjectEditors.editParcelTaskSaved(level, id, item, count, target)

    @JvmStatic fun editRedPacketTask(id: Any, item: ItemStack?, count: Number?, blessing: String?, target: String?) =
        KubeJSObjectEditors.editRedPacketTask(id, item, count, blessing, target)

    @JvmStatic fun editRedPacketTaskSaved(level: ServerLevel, id: Any, item: ItemStack? = null, count: Number? = null, blessing: String? = null, target: String? = null): Boolean =
        KubeJSObjectEditors.editRedPacketTaskSaved(level, id, item, count, blessing, target)

    @JvmStatic fun editPostcardTask(id: Any, style: String?, text: String?, target: String?) =
        KubeJSObjectEditors.editPostcardTask(id, style, text, target)

    @JvmStatic fun editPostcardTaskSaved(level: ServerLevel, id: Any, style: String? = null, text: String? = null, target: String? = null): Boolean =
        KubeJSObjectEditors.editPostcardTaskSaved(level, id, style, text, target)

    @JvmStatic fun editParcelReward(id: Any, item: ItemStack?, count: Number?, randomBonus: Number?, target: String?) =
        KubeJSObjectEditors.editParcelReward(id, item, count, randomBonus, target)

    @JvmStatic fun editParcelRewardSaved(level: ServerLevel, id: Any, item: ItemStack? = null, count: Number? = null, randomBonus: Number? = null, target: String? = null): Boolean =
        KubeJSObjectEditors.editParcelRewardSaved(level, id, item, count, randomBonus, target)

    @JvmStatic fun editRedPacketReward(id: Any, item: ItemStack?, count: Number?, blessing: String?, randomBonus: Number?, target: String?) =
        KubeJSObjectEditors.editRedPacketReward(id, item, count, blessing, randomBonus, target)

    @JvmStatic fun editRedPacketRewardSaved(level: ServerLevel, id: Any, item: ItemStack? = null, count: Number? = null, blessing: String? = null, randomBonus: Number? = null, target: String? = null): Boolean =
        KubeJSObjectEditors.editRedPacketRewardSaved(level, id, item, count, blessing, randomBonus, target)

    @JvmStatic fun editPostcardReward(id: Any, style: String?, text: String?, sender: String?) =
        KubeJSObjectEditors.editPostcardReward(id, style, text, sender)

    @JvmStatic fun editPostcardRewardSaved(level: ServerLevel, id: Any, style: String? = null, text: String? = null, sender: String? = null): Boolean =
        KubeJSObjectEditors.editPostcardRewardSaved(level, id, style, text, sender)

    @JvmStatic @JvmOverloads fun resetQuestTextSaved(level: ServerLevel, id: Any, locale: String = "zh_cn"): Boolean =
        KubeJSObjectEditors.resetQuestTextSaved(level, id, locale)

    @JvmStatic @JvmOverloads fun editQuestDescriptionSaved(level: ServerLevel, id: Any, desc: Any? = null, locale: String = "zh_cn"): Boolean =
        KubeJSObjectEditors.editQuestDescriptionSaved(level, id, desc, locale)

    @JvmStatic @JvmOverloads fun editQuestDescription(id: Any, desc: Any? = null, locale: String = "zh_cn"): Boolean =
        KubeJSObjectEditors.editQuestDescription(id, desc, locale)

    @JvmStatic @JvmOverloads fun editQuestTitleSaved(level: ServerLevel, id: Any, title: String? = null, locale: String = "zh_cn"): Boolean =
        KubeJSObjectEditors.editQuestTitleSaved(level, id, title, locale)

    @JvmStatic @JvmOverloads fun editQuestTitle(id: Any, title: String? = null, locale: String = "zh_cn"): Boolean =
        KubeJSObjectEditors.editQuestTitle(id, title, locale)

    @JvmStatic @JvmOverloads fun editQuestSubtitleSaved(level: ServerLevel, id: Any, subtitle: String? = null, locale: String = "zh_cn"): Boolean =
        KubeJSObjectEditors.editQuestSubtitleSaved(level, id, subtitle, locale)

    @JvmStatic @JvmOverloads fun editQuestSubtitle(id: Any, subtitle: String? = null, locale: String = "zh_cn"): Boolean =
        KubeJSObjectEditors.editQuestSubtitle(id, subtitle, locale)

    @JvmStatic fun syncAllOverridesToPlayer(player: ServerPlayer) =
        KubeJSQuestSync.syncAllOverridesToPlayer(player)

    @JvmStatic fun refreshQuests() = KubeJSMailHelper.refreshQuests()

    fun updatePlayerTaskCache(player: ServerPlayer) = KubeJSMailHelper.updatePlayerTaskCache(player)

    // endregion

    /** `ContactEvents.registerReplacers` 事件上下文，供脚本注册明信片、红包与 NPC 回信的自定义占位符替换器。 */
    class RegisterReplacersEvent : KubeEvent {
        fun registerPostcardTask(key: String, callback: BiFunction<Player, TeamData, String>) {
            PostcardTask.PostcardPlaceholderSupport.register(key) { p, t -> callback.apply(p, t) }
        }

        fun registerRedPacketReward(callback: BiFunction<String, ServerPlayer, String>) {
            RedPacketReward.registerReplacer { text, player -> callback.apply(text, player) }
        }

        fun registerPostcardReward(callback: BiFunction<String, ServerPlayer, String>) {
            PostcardReward.registerReplacer { text, player -> callback.apply(text, player) }
        }

        fun registerNpcReply(callback: BiFunction<String, CollectionSavedData.ReplacerContext, String?>) {
            CollectionSavedData.registerReplacer { text, ctx -> callback.apply(text, ctx) }
        }

        fun registerRedPacketTask(key: String, callback: BiFunction<Player, TeamData, String>) {
            RedPacketTask.RedPacketPlaceholderSupport.register(key) { p, t -> callback.apply(p, t) }
        }
    }
}
