package com.creeperyang.contactquests.compat.kubejs

import com.creeperyang.contactquests.compat.kubejs.KubeJSQuestSync.LOGGER
import com.creeperyang.contactquests.compat.kubejs.KubeJSQuestTextEditors.applyQuestOverride
import com.creeperyang.contactquests.compat.kubejs.KubeJSRewardEditors.applyRewardOverride
import com.creeperyang.contactquests.compat.kubejs.KubeJSTaskEditors.applyTaskOverride
import com.creeperyang.contactquests.data.DataManager
import dev.ftb.mods.ftbquests.quest.Quest
import dev.ftb.mods.ftbquests.quest.QuestObjectBase
import dev.ftb.mods.ftbquests.quest.reward.Reward
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack

/** KubeJS 对象编辑门面，统一处理持久化恢复、变更跟踪与同步。 */
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

    fun editParcelTask(id: Any, item: ItemStack?, count: Number?, target: String?) =
        KubeJSTaskEditors.editParcelTask(id, item, count, target)

    fun editParcelTaskSaved(
        level: ServerLevel,
        id: Any,
        item: ItemStack?,
        count: Number?,
        target: String?
    ): Boolean =
        KubeJSTaskEditors.editParcelTaskSaved(level, id, item, count, target)

    fun editRedPacketTask(id: Any, item: ItemStack?, count: Number?, blessing: String?, target: String?) =
        KubeJSTaskEditors.editRedPacketTask(id, item, count, blessing, target)

    fun editRedPacketTaskSaved(
        level: ServerLevel,
        id: Any,
        item: ItemStack?,
        count: Number?,
        blessing: String?,
        target: String?
    ): Boolean =
        KubeJSTaskEditors.editRedPacketTaskSaved(level, id, item, count, blessing, target)

    fun editPostcardTask(id: Any, style: String?, text: String?, target: String?) =
        KubeJSTaskEditors.editPostcardTask(id, style, text, target)

    fun editPostcardTaskSaved(
        level: ServerLevel,
        id: Any,
        style: String?,
        text: String?,
        target: String?
    ): Boolean =
        KubeJSTaskEditors.editPostcardTaskSaved(level, id, style, text, target)

    fun editParcelReward(id: Any, item: ItemStack?, count: Number?, randomBonus: Number?, target: String?) =
        KubeJSRewardEditors.editParcelReward(id, item, count, randomBonus, target)

    fun editParcelRewardSaved(
        level: ServerLevel,
        id: Any,
        item: ItemStack?,
        count: Number?,
        randomBonus: Number?,
        target: String?
    ): Boolean =
        KubeJSRewardEditors.editParcelRewardSaved(level, id, item, count, randomBonus, target)

    fun editRedPacketReward(
        id: Any,
        item: ItemStack?,
        count: Number?,
        blessing: String?,
        randomBonus: Number?,
        target: String?
    ) =
        KubeJSRewardEditors.editRedPacketReward(id, item, count, blessing, randomBonus, target)

    fun editRedPacketRewardSaved(
        level: ServerLevel,
        id: Any,
        item: ItemStack?,
        count: Number?,
        blessing: String?,
        randomBonus: Number?,
        target: String?
    ): Boolean =
        KubeJSRewardEditors.editRedPacketRewardSaved(level, id, item, count, blessing, randomBonus, target)

    fun editPostcardReward(id: Any, style: String?, text: String?, sender: String?) =
        KubeJSRewardEditors.editPostcardReward(id, style, text, sender)

    fun editPostcardRewardSaved(
        level: ServerLevel,
        id: Any,
        style: String?,
        text: String?,
        sender: String?
    ): Boolean =
        KubeJSRewardEditors.editPostcardRewardSaved(level, id, style, text, sender)

    fun resetQuestTextSaved(level: ServerLevel, id: Any, locale: String): Boolean =
        KubeJSQuestTextEditors.resetQuestTextSaved(level, id, locale)

    fun editQuestDescriptionSaved(level: ServerLevel, id: Any, desc: Any?, locale: String): Boolean =
        KubeJSQuestTextEditors.editQuestDescriptionSaved(level, id, desc, locale)

    fun editQuestDescription(id: Any, desc: Any?, locale: String): Boolean =
        KubeJSQuestTextEditors.editQuestDescription(id, desc, locale)

    fun editQuestTitleSaved(level: ServerLevel, id: Any, title: String?, locale: String): Boolean =
        KubeJSQuestTextEditors.editQuestTitleSaved(level, id, title, locale)

    fun editQuestTitle(id: Any, title: String?, locale: String): Boolean =
        KubeJSQuestTextEditors.editQuestTitle(id, title, locale)

    fun editQuestSubtitleSaved(level: ServerLevel, id: Any, subtitle: String?, locale: String): Boolean =
        KubeJSQuestTextEditors.editQuestSubtitleSaved(level, id, subtitle, locale)

    fun editQuestSubtitle(id: Any, subtitle: String?, locale: String): Boolean =
        KubeJSQuestTextEditors.editQuestSubtitle(id, subtitle, locale)

    // 复用任务图谱的解析、查找与同步逻辑，供各类编辑器统一调用。
    internal fun resolveId(id: Any): Long? = KubeJSQuestSync.resolveId(id)

    internal fun getQuest(id: Any): Quest? = KubeJSQuestSync.getQuest(id)

    internal fun getReward(id: Any): Reward? = KubeJSQuestSync.getReward(id)

    internal fun syncObject(obj: QuestObjectBase, saveToConfig: Boolean = true) =
        KubeJSQuestSync.syncObject(obj, saveToConfig)

    /** 按存档中的字段恢复覆写，保留物品解析所需的注册表上下文。 */
    internal class OverrideHelper(val tag: CompoundTag, val provider: HolderLookup.Provider) {
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

    /** 记录实际字段变化，供任务与奖励编辑共用。 */
    internal class EditTracker {
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
    }

    internal fun <T : QuestObjectBase> executeEdit(
        id: Any,
        saveToConfig: Boolean,
        lookup: (Long) -> T?,
        applier: (T, EditTracker) -> Unit
    ): Boolean {
        val longId = resolveId(id) ?: return false
        val obj = lookup(longId) ?: return false
        val tracker = EditTracker()

        applier(obj, tracker)

        if (tracker.changed) {
            syncObject(obj, saveToConfig)
            LOGGER.info("ContactQuests: 已修改 ${obj.javaClass.simpleName} '$longId' (save=$saveToConfig)")
        }
        return tracker.changed
    }

    internal fun saveToNbt(level: ServerLevel, id: Any, data: Map<String, Any?>) {
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
}
