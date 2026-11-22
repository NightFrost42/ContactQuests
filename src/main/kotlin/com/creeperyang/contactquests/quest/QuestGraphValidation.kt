package com.creeperyang.contactquests.quest

import com.creeperyang.contactquests.config.ChoiceTemplateConfig
import com.creeperyang.contactquests.quest.reward.ParcelChoiceReward
import com.creeperyang.contactquests.quest.reward.ParcelReward
import com.creeperyang.contactquests.quest.reward.ParcelRewardBase
import com.creeperyang.contactquests.quest.reward.PostcardReward
import com.creeperyang.contactquests.quest.reward.RedPacketReward
import com.creeperyang.contactquests.quest.task.ContactTask
import dev.ftb.mods.ftbquests.quest.Chapter
import dev.ftb.mods.ftbquests.quest.Quest
import dev.ftb.mods.ftbquests.quest.QuestObject
import dev.ftb.mods.ftbquests.quest.QuestObjectBase
import dev.ftb.mods.ftbquests.quest.reward.ItemReward
import dev.ftb.mods.ftbquests.quest.task.Task
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.DoubleTag
import net.minecraft.nbt.FloatTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NumericTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import java.util.Locale

/** 任务图谱的底层依赖检查、对象校验与输入转换；不负责图谱保存或网络同步。 */
internal object QuestGraphValidation {
    /** 沿任务依赖、子任务所属任务与章节中的任务查找目标，使用已访问 ID 避免循环遍历。 */
    fun dependsOn(obj: QuestObject, target: Quest, visited: MutableSet<Long>): Boolean {
        if (!visited.add(obj.id)) return false
        if (obj === target) return true
        return when (obj) {
            is Quest -> obj.streamDependencies().anyMatch { dependsOn(it, target, visited) }
            is Task -> dependsOn(obj.quest, target, visited)
            is Chapter -> obj.quests.any { dependsOn(it, target, visited) }
            else -> false
        }
    }

    /** 校验已读取数据的 Contact 任务与奖励，确保数量、物品和选项满足投递要求。 */
    fun validate(obj: QuestObjectBase) {
        if (obj is ContactTask) require(obj.count > 0) { "Task count must be positive" }
        // 原生物品奖励允许创建空物品栈兜底，但包裹奖励必须配置正数量与有效物品才能真实投递。
        if (obj is ParcelReward) require(obj.count > 0 && !obj.item.isEmpty) {
            "A parcel reward needs a positive count and item"
        }
        if (obj is RedPacketReward) require(obj.count > 0 && !obj.item.isEmpty) {
            "A red packet reward needs a positive count and item"
        }
        if (obj is ParcelChoiceReward) {
            require(ChoiceTemplateConfig.hasGroup(obj.templateGroup)) { "Unknown choice template group" }
            require(obj.table == null || obj.table!!.weightedRewards.size <= 128) { "Choice table exceeds 128 entries" }
            require(obj.table?.weightedRewards.orEmpty().all { entry ->
                entry.reward is ItemReward
                    || entry.reward is ParcelReward
                    || entry.reward is RedPacketReward
                    || entry.reward is PostcardReward
            }) {
                "Choice options must be item or Contact mail rewards"
            }
        }
    }

    /** 在原生 reader 处理前校验数量及倍率，并将合法浮点整数归一化为整型 NBT。 */
    fun validateInput(obj: QuestObjectBase, data: CompoundTag) {
        // 赶在 FTB 原生 readData 把非法数量默默钳位成 1 之前完成严格校验。
        fun integer(key: String, minimum: Long, maximum: Long): Long? {
            val value = data[key] ?: return null
            require(value is NumericTag) { "$key must be an integer" }
            // Rhino 会将 JS 数字（哪怕是整数 `count: 4`）统一转换成 DoubleTag；
            // 先校验是否可无损表示整数且未溢出，再归一化为整型 NBT。
            if (value is FloatTag || value is DoubleTag) {
                val number = data.getDouble(key)
                require(
                    number.isFinite() && number == kotlin.math.floor(number)
                        && number.toBigDecimal() >= minimum.toBigDecimal()
                        && number.toBigDecimal() <= maximum.toBigDecimal()
                ) {
                    "$key must be an integer between $minimum and $maximum"
                }
                data.putLong(key, number.toLong())
            }
            val number = data.getLong(key)
            require(number in minimum..maximum) { "$key must be between $minimum and $maximum" }
            return number
        }

        if (obj is ContactTask) integer("count", 1, Long.MAX_VALUE)
        if (obj is ParcelReward || obj is RedPacketReward) {
            val count = integer("count", 1, Int.MAX_VALUE.toLong()) ?: 1
            val bonus = integer("random_bonus", 0, Int.MAX_VALUE.toLong() - 1) ?: 0
            require(count + bonus <= Int.MAX_VALUE) { "count plus random_bonus overflows the reward quantity" }
        }
        if (obj is ParcelRewardBase && data.contains("team_reward_multiplier")) {
            require(
                data.contains("team_reward_multiplier", Tag.TAG_ANY_NUMERIC.toInt())
                    && data.getFloat("team_reward_multiplier").let { it.isFinite() && it > 0 }
            ) {
                "team_reward_multiplier must be positive and finite"
            }
        }
    }

    /** 严格要求字符串输入，避免隐式转换掩盖脚本参数错误。 */
    fun requireString(key: String, value: Any): String =
        value as? String ?: error("$key must be a string")

    /** 严格要求布尔输入。 */
    fun requireBoolean(key: String, value: Any): Boolean =
        value as? Boolean ?: error("$key must be boolean")

    /** 校验集合中的每个元素，并按原顺序返回字符串列表。 */
    fun stringList(key: String, value: Any): List<String> {
        val list = value as? Collection<*> ?: error("$key must be a list")
        require(list.all { it is String }) { "$key must contain strings" }
        return list.map { it as String }
    }

    /** 按原顺序构造字符串 NBT 列表。 */
    fun nbtList(values: List<String>): ListTag = ListTag().apply {
        values.forEach { add(StringTag.valueOf(it)) }
    }

    /** 使用固定区域设置归一化枚举名，并校验允许值。 */
    fun enumValue(key: String, value: Any, allowed: Set<String>): String =
        requireString(key, value).uppercase(Locale.ROOT).also {
            require(it in allowed) { "$key must be one of $allowed" }
        }

    /** 互斥计数必须是正整数。 */
    fun positiveCount(value: Any): Int {
        return integerCount("mutexCount", value, 1)
    }

    /** 只接受有限、无小数且处于指定下限与 Int 上限之间的数值。 */
    fun integerCount(key: String, value: Any, minimum: Int): Int {
        val number = (value as? Number)?.toDouble() ?: error("$key must be an integer")
        require(
            number.isFinite() && number >= minimum && number <= Int.MAX_VALUE && number == number.toInt().toDouble()
        ) {
            "$key must be an integer from $minimum to ${Int.MAX_VALUE}"
        }
        return number.toInt()
    }
}
