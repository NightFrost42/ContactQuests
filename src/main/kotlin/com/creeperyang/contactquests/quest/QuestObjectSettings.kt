package com.creeperyang.contactquests.quest

import com.creeperyang.contactquests.quest.reward.*
import com.creeperyang.contactquests.quest.task.*
import dev.ftb.mods.ftbquests.quest.QuestObjectBase
import dev.ftb.mods.ftbquests.quest.reward.Reward
import dev.ftb.mods.ftbquests.quest.reward.RewardAutoClaim
import dev.ftb.mods.ftbquests.quest.task.Task
import net.minecraft.nbt.*
import net.minecraft.server.level.ServerLevel
import java.util.Locale

/** 先依据真实 NBT 结构校验输入键，防止原生 reader 在解析时默默钳位或丢弃非法字段。 */
object QuestObjectSettings {
    fun prepare(level: ServerLevel, obj: QuestObjectBase, input: CompoundTag, creating: Boolean = false): CompoundTag {
        val tag = input.copy()
        if (InlineQuestSettings.isInline(obj)) InlineQuestSettings.normalizeText(tag)
        val common = setOf("icon", "tags", "custom_id", "title", "locale")
        val native = if (obj is Task) setOf("optional_task", "disable_toast") else setOf(
            "team_reward", "auto", "exclude_from_claim_all", "ignore_reward_blocking", "disable_reward_screen_blur")
        val contact = if (obj is ContactTask) setOf("TargetAddressee", "count") else if (obj is ParcelRewardBase) setOf(
            "target_addressee", "is_ender", "is_team_reward", "team_reward_multiplier", "unlock_tags", "hide_contents") else emptySet()
        val type = when (obj) {
            is ParcelTask -> setOf("item", "match_components")
            is RedPacketTask -> setOf("item", "inner_item", "match_components", "blessing")
            is PostcardTask -> setOf("postcard_style", "postcard_text")
            is ParcelReward -> setOf("item", "count", "random_bonus")
            is RedPacketReward -> setOf("item", "count", "random_bonus", "blessing")
            is PostcardReward -> setOf("postcard_style", "postcard_text")
            is ParcelRandomReward -> setOf("table_id", "table_data")
            else -> NativeObjectSettings.fields(obj, level)
        }
        require(tag.allKeys.all { it in common + native + contact + type + choiceFields(obj) }) {
            "Unknown ${obj.javaClass.simpleName} fields: ${tag.allKeys - (common + native + contact + type + choiceFields(obj))}"
        }
        if (creating) tag.allKeys.toList().forEach { key ->
            if (tag[key] is StringTag && tag.getString(key).isBlank()) tag.remove(key)
        }
        val strings = setOf("TargetAddressee", "target_addressee", "blessing", "postcard_style", "postcard_text", "title", "locale", "custom_id", "template_group")
        strings.forEach { key -> if (tag.contains(key)) {
            require(tag[key] is StringTag && tag.getString(key).length <= 16384) { "$key must be a bounded string" }
        } }
        if (tag.contains("locale")) require(tag.getString("locale").matches(Regex("[a-z]{2,3}_[a-z0-9]{2,8}"))) { "Invalid locale" }
        for (key in setOf("optional_task", "disable_toast", "is_ender", "is_team_reward", "hide_contents", "exclude_from_claim_all",
            "ignore_reward_blocking", "disable_reward_screen_blur")) if (tag.contains(key)) {
            val value = tag[key]
            require(value is NumericTag && value.asDouble in listOf(0.0, 1.0)) { "$key must be boolean" }
            tag.putBoolean(key, tag.getBoolean(key))
        }
        if (tag.contains("team_reward")) {
            val value = tag["team_reward"]!!
            val state: Any = if (value is StringTag) value.asString else {
                require(value is NumericTag && value.asDouble in listOf(0.0, 1.0)) { "team_reward must be boolean or DEFAULT" }
                value.asDouble == 1.0
            }
            if (state is String && state.equals("DEFAULT", true)) tag.putString("team_reward", "DEFAULT")
            else QuestGraphSettings.tristate(tag, "team_reward", state)
        }
        if (tag.contains("auto")) {
            val value = tag["auto"]!!
            val id = if (value is StringTag) value.asString else if (value is NumericTag && value.asDouble in listOf(0.0, 1.0)) {
                if (value.asDouble == 1.0) "enabled" else "disabled"
            } else error("auto must be a native autoclaim enum")
            require(RewardAutoClaim.NAME_MAP.keys.any { it.equals(id, true) }) { "Invalid auto setting" }
            tag.putString("auto", id.lowercase(Locale.ROOT))
        }
        for (key in listOf("tags", "unlock_tags", "option_labels")) if (tag.contains(key)) {
            val value = tag[key]
            require(value is ListTag && (value.isEmpty() || value.elementType == Tag.TAG_STRING)) { "$key must be a string list" }
            require(value.size <= 1024) { "$key is too large" }
        }
        if (tag.contains("icon")) tag.put("icon", QuestGraphSettings.icon(tag["icon"]!!, level))
        if (tag.contains("match_components")) {
            val matching = dev.ftb.mods.ftbquests.integration.item_filtering.ItemMatchingSystem.ComponentMatchType.NAME_MAP.keys
            val value = (tag["match_components"] as? StringTag)?.asString
            val canonical = matching.firstOrNull { it.equals(value, true) }
            require(canonical != null) {
                "match_components must be none, fuzzy or strict"
            }
            tag.putString("match_components", canonical)
        }
        if ((obj is ParcelRandomReward || obj is dev.ftb.mods.ftbquests.quest.reward.RandomReward) && tag.contains("table_id")) {
            val value = tag["table_id"]!!
            val id = if (value is StringTag) java.lang.Long.parseUnsignedLong(value.asString.removePrefix("0x"), 16)
                else { require(value is NumericTag && value !is FloatTag && value !is DoubleTag) { "Use a hex string for table_id" }; value.asLong }
            require(id == 0L || id == -1L && tag.contains("table_data", Tag.TAG_COMPOUND.toInt()) || obj.questFile.getRewardTable(id) != null) { "Missing reward table" }
            tag.putLong("table_id", id)
        }
        if (obj is RedPacketTask && tag.contains("item")) {
            require(!tag.contains("inner_item")) { "Use either item or inner_item for a red packet task" }
            tag.put("inner_item", tag["item"]!!.copy())
            tag.remove("item")
        }
        for (key in listOf("item", "inner_item")) if (tag.contains(key)) {
            val item = tag[key]!!
            require(item is StringTag || item is CompoundTag) { "$key must be an item ID or item NBT" }
            val parsed = QuestObjectBase.itemOrMissingFromNBT(item, level.registryAccess())
            require(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(parsed.item).toString() != "ftbquests:missing_item") { "Unknown item" }
            tag.put(key, if (parsed.isEmpty) CompoundTag() else parsed.save(level.registryAccess()))
        }
        NativeObjectSettings.validate(level, obj, tag)
        if (obj is ContactTask && tag.contains("count")) tag.putLong("count", NativeObjectSettings.numeric(tag["count"]!!, "count", 1, Long.MAX_VALUE))
        if (obj is ParcelReward || obj is RedPacketReward) {
            for (key in listOf("count", "random_bonus")) if (tag.contains(key)) tag.putInt(key,
                NativeObjectSettings.numeric(tag[key]!!, key, if (key == "count") 1 else 0, Int.MAX_VALUE.toLong() - if (key == "random_bonus") 1 else 0).toInt())
        }
        if (obj is ParcelRewardBase && tag.contains("team_reward_multiplier")) require(tag["team_reward_multiplier"] is NumericTag &&
            tag.getFloat("team_reward_multiplier").let { it.isFinite() && it > 0 }) { "team_reward_multiplier must be finite and positive" }
        if (obj is ParcelChoiceReward && tag.contains("option_labels")) require(tag.getList("option_labels", 8).let {
            it.size <= 128 && it.all { label -> label.asString.length <= 128 }
        }) { "Choice labels must be at most 128 bounded strings" }
        return tag
    }

    fun merge(level: ServerLevel, obj: QuestObjectBase, input: CompoundTag): CompoundTag = CompoundTag().also { merged ->
        obj.writeData(merged, level.registryAccess())
        // 编辑指令显式传入的字段整体替换原值（包括传入空图标或内嵌奖励表）。
        input.allKeys.forEach { merged.put(it, input[it]!!.copy()) }
        for (key in listOf("team_reward", "consume_items", "only_from_crafting")) {
            if (input[key] is StringTag && input.getString(key) == "DEFAULT") merged.remove(key)
        }
    }

    private fun choiceFields(obj: QuestObjectBase): Set<String> =
        if (obj is ParcelChoiceReward) setOf("template_group", "option_labels") else emptySet()

    fun text(level: ServerLevel, obj: QuestObjectBase, input: CompoundTag) {
        if (input.contains("title")) obj.questFile.translationManager.addTranslation(obj,
            if (input.contains("locale")) input.getString("locale") else obj.questFile.locale,
            dev.ftb.mods.ftbquests.quest.translation.TranslationKey.TITLE, input.getString("title"))
    }
}
