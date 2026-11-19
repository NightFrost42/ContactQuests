package com.creeperyang.contactquests.quest

import dev.ftb.mods.ftbquests.quest.QuestObjectBase
import dev.ftb.mods.ftbquests.quest.reward.RandomReward
import dev.ftb.mods.ftbquests.quest.reward.Reward
import dev.ftb.mods.ftbquests.quest.task.Task
import net.minecraft.nbt.*
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel

/** 依据固定版本（2101.1.38/39）FTB 原生编辑器与 reader 建立的 NBT 键白名单与校验规则。 */
object NativeObjectSettings {
    private val schemas = mapOf(
        "ItemTask" to setOf("item", "count", "consume_items", "only_from_crafting", "match_components", "task_screen_only"),
        "CustomTask" to emptySet(), "CheckmarkTask" to emptySet(),
        "XPTask" to setOf("value", "points"), "DimensionTask" to setOf("dimension"),
        "StatTask" to setOf("stat", "value"),
        "KillTask" to setOf("entity", "entityTypeTag", "value", "custom_name", "nbt_filter"),
        "LocationTask" to setOf("dimension", "ignore_dimension", "position", "size"),
        "AdvancementTask" to setOf("advancement", "criterion"),
        "ObservationTask" to setOf("timer", "observation_type", "observe_type", "to_observe"),
        "BiomeTask" to setOf("biome"), "StructureTask" to setOf("structure"),
        "StageTask" to setOf("stage", "team_stage"),
        "InteractionTask" to setOf("interaction_type", "to_interact"),
        "FluidTask" to setOf("fluid", "amount"),
        "ForgeEnergyTask" to setOf("value", "max_input"),
        "ItemReward" to setOf("item", "count", "random_bonus", "only_one"),
        "CommandReward" to setOf("command", "permission_level", "silent", "feedback_message"),
        "CustomReward" to emptySet(), "XPReward" to setOf("xp"), "XPLevelsReward" to setOf("xp_levels"),
        "AdvancementReward" to setOf("advancement", "criterion"), "ToastReward" to setOf("description"),
        "StageReward" to setOf("stage", "remove"), "CurrencyReward" to setOf("amount"))
    fun fields(obj: QuestObjectBase, level: ServerLevel): Set<String> = when {
        obj is RandomReward -> setOf("table_id", "table_data")
        obj.javaClass.simpleName in schemas -> schemas.getValue(obj.javaClass.simpleName)
        // 对于第三方扩展任务或奖励类型，通过原生 writer 实际写出的键集合动态推导允许保留的扩展字段。
        else -> CompoundTag().also { obj.writeData(it, level.registryAccess()) }.allKeys
    }

    fun validate(level: ServerLevel, obj: QuestObjectBase, tag: CompoundTag) {
        val cls = obj.javaClass.simpleName
        for (key in listOf("task_screen_only", "points", "ignore_dimension", "team_stage", "silent", "remove", "only_one")) if (tag.contains(key)) {
            val value = tag[key]
            require(value is NumericTag && value.asDouble in listOf(0.0, 1.0)) { "$key must be boolean" }
            tag.putBoolean(key, value.asDouble == 1.0)
        }
        for (key in listOf("consume_items", "only_from_crafting")) if (tag.contains(key)) {
            val value = tag[key]!!
            when {
                value is StringTag && value.asString.equals("DEFAULT", true) -> tag.putString(key, "DEFAULT")
                value is StringTag && value.asString.uppercase() in setOf("TRUE", "FALSE") -> tag.putBoolean(key, value.asString.equals("TRUE", true))
                value is NumericTag && value.asDouble in listOf(0.0, 1.0) -> tag.putBoolean(key, value.asDouble == 1.0)
                else -> error("$key must be boolean or DEFAULT")
            }
        }
        for (key in listOf("command", "feedback_message", "description", "criterion", "custom_name", "nbt_filter", "stage", "to_observe", "to_interact"))
            if (tag.contains(key)) require(tag[key] is StringTag && tag.getString(key).length <= 16384) { "$key must be a bounded string" }
        if (tag.contains("nbt_filter") && tag.getString("nbt_filter").isNotBlank()) net.minecraft.nbt.TagParser.parseTag(tag.getString("nbt_filter"))
        for (key in listOf("dimension", "stat", "entity", "entityTypeTag", "advancement", "biome", "structure")) if (tag.contains(key)) {
            require(tag[key] is StringTag) { "$key must be a resource ID string" }
            val text = tag.getString(key)
            if (text.isNotEmpty()) require(ResourceLocation.tryParse(text.removePrefix("#")) != null) { "Invalid $key resource ID" }
            when (key) {
                "dimension" -> require(level.server.levelKeys().any { it.location().toString() == text }) { "Unknown dimension" }
                "entity" -> require(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse(text))) { "Unknown entity" }
                "advancement" -> if (text.isNotEmpty()) {
                    val advancement = level.server.advancements.get(ResourceLocation.parse(text)) ?: error("Unknown advancement")
                    if (tag.getString("criterion").isNotBlank()) require(advancement.value().criteria().containsKey(tag.getString("criterion"))) { "Unknown advancement criterion" }
                }
            }
        }
        if (cls == "LocationTask") for (key in listOf("position", "size")) if (tag.contains(key)) {
            val input = tag[key]!!
            val values = if (input is IntArrayTag) input.asIntArray.toList() else {
                require(input is ListTag && input.size == 3) { "$key must have three integers" }
                input.map { numeric(it, key, if (key == "size") 0 else Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt() }
            }
            require(values.size == 3 && (key != "size" || values.all { it >= 0 })) { "Invalid location $key" }
            tag.putIntArray(key, values)
        }
        for (key in listOf("value", "max_input", "timer", "xp", "xp_levels", "amount", "permission_level")) if (tag.contains(key)) {
            val max = when {
                key == "permission_level" -> 4L
                key == "timer" && cls == "ObservationTask" -> 1200L
                key == "max_input" -> Int.MAX_VALUE.toLong()
                cls == "StatTask" || key in listOf("xp", "xp_levels") -> Int.MAX_VALUE.toLong()
                else -> Long.MAX_VALUE
            }
            val min = if (key == "value") 1L else 0L
            val number = numeric(tag[key]!!, key, min, max)
            if (max <= Int.MAX_VALUE) tag.putInt(key, number.toInt()) else tag.putLong(key, number)
        }
        if (cls == "ItemTask" || cls == "ItemReward") for (key in listOf("count", "random_bonus")) if (tag.contains(key)) {
            val max = if (obj is Task) Long.MAX_VALUE else Int.MAX_VALUE.toLong() - if (key == "random_bonus") 1 else 0
            val number = numeric(tag[key]!!, key, if (key == "count") 1L else 0L, max)
            if (obj is Task) tag.putLong(key, number) else tag.putInt(key, number.toInt())
        }
        if (cls == "ItemReward") require((tag.getInt("count").takeIf { tag.contains("count") } ?: 1).toLong() + tag.getInt("random_bonus") <= Int.MAX_VALUE) {
            "count plus random_bonus overflows the reward quantity"
        }
        val observationTypes = listOf("block", "block_tag", "block_state", "block_entity", "block_entity_type", "entity_type", "entity_type_tag")
        if (tag.contains("observation_type")) {
            validateEnum(tag, "observation_type", observationTypes)
            // 原生 writer 还会写出旧版 ordinal 字段 `observe_type`，此处同步回填以防覆盖新枚举值。
            tag.putInt("observe_type", observationTypes.indexOf(tag.getString("observation_type")))
        }
        if (tag.contains("interaction_type")) validateEnum(tag, "interaction_type", dev.ftb.mods.ftbquests.quest.task.InteractionTask.InteractionType.NAME_MAP.keys)
        if (tag.contains("observe_type")) tag.putInt("observe_type", numeric(tag["observe_type"]!!, "observe_type", 0,
            observationTypes.size.toLong() - 1).toInt())
        if (cls == "FluidTask" && tag.contains("fluid")) {
            val fluid = tag["fluid"]!!
            require(fluid is StringTag || fluid is CompoundTag) { "fluid must be a resource ID or fluid NBT" }
            val stack = if (fluid is StringTag) {
                require(ResourceLocation.tryParse(fluid.asString)?.let { net.minecraft.core.registries.BuiltInRegistries.FLUID.containsKey(it) } == true) { "Unknown fluid" }
                dev.architectury.fluid.FluidStack.create(net.minecraft.core.registries.BuiltInRegistries.FLUID.get(ResourceLocation.parse(fluid.asString)), 1000L)
            } else dev.architectury.fluid.FluidStack.read(level.registryAccess(), fluid).orElseThrow { IllegalArgumentException("Invalid fluid NBT") }
            if (tag.contains("amount")) stack.amount = numeric(tag["amount"]!!, "amount", 1, Int.MAX_VALUE.toLong())
            require(!stack.isEmpty && stack.amount > 0) { "Fluid task must contain a positive amount" }
            tag.put("fluid", stack.write(level.registryAccess(), CompoundTag()))
            tag.remove("amount")
        } else if (cls == "FluidTask" && tag.contains("amount")) {
            val current = CompoundTag().also { obj.writeData(it, level.registryAccess()) }.getCompound("fluid")
            tag.put("fluid", current)
            validate(level, obj, tag)
        }
        if (tag.contains("table_data")) {
            require(tag["table_data"] is CompoundTag) { "table_data must be an object" }
            validateTable(level, obj, tag.getCompound("table_data"))
        }
    }

    private fun validateEnum(tag: CompoundTag, key: String, keys: Collection<String>) {
        val text = (tag[key] as? StringTag)?.asString ?: error("$key must be an enum string")
        tag.putString(key, keys.firstOrNull { it.equals(text, true) } ?: error("Unknown $key"))
    }
    fun numeric(value: Tag, key: String, min: Long, max: Long): Long {
        require(value is NumericTag) { "$key must be integral" }
        val number = if (value is FloatTag || value is DoubleTag) {
            val n = value.asDouble
            require(n.isFinite() && n == kotlin.math.floor(n) && n.toBigDecimal() in min.toBigDecimal()..max.toBigDecimal()) { "Invalid $key range" }
            n.toLong()
        } else value.asLong
        require(number in min..max) { "$key must be in $min..$max" }
        return number
    }

    private fun validateTable(level: ServerLevel, obj: QuestObjectBase, data: CompoundTag) {
        InlineQuestSettings.normalizeText(data)
        require(data.allKeys.all { it in setOf("rewards", "empty_weight", "loot_size", "hide_tooltip", "use_title", "icon", "tags", "filename", "title", "loot_table_id", "loot_crate") }) { "Unknown inline table fields" }
        data.allKeys.toList().forEach { key ->
            if (data[key] is StringTag && data.getString(key).isBlank()) data.remove(key)
        }
        if (data.contains("empty_weight")) require(data["empty_weight"] is NumericTag && data.getFloat("empty_weight").let { it.isFinite() && it >= 0 }) { "Invalid empty weight" }
        // RewardTable 构造器默认 loot_size 为 1，而原生 reader 调用无默认值的 getInt；在读取新传入的奖励表前先补齐默认值。
        data.putInt("loot_size", if (data.contains("loot_size"))
            numeric(data["loot_size"]!!, "loot_size", 0, Int.MAX_VALUE.toLong()).toInt() else 1)
        for (key in listOf("hide_tooltip", "use_title")) if (data.contains(key)) {
            val value = data[key]
            require(value is NumericTag && value.asDouble in listOf(0.0, 1.0)) { "$key must be boolean" }
            data.putBoolean(key, value.asDouble == 1.0)
        }
        for (key in listOf("filename", "title", "loot_table_id")) if (data.contains(key)) {
            require(data[key] is StringTag && data.getString(key).length <= 16384) { "$key must be a bounded string" }
        }
        if (data.contains("filename")) {
            val filename = data.getString("filename")
            require(filename.length <= 128 && dev.ftb.mods.ftbquests.quest.QuestObjectBase.sanitizeFilename(filename).orElse("") == filename) { "Invalid inline table filename" }
        }
        if (data.contains("loot_table_id")) require(ResourceLocation.tryParse(data.getString("loot_table_id")) != null) { "Invalid loot table ID" }
        if (data.contains("icon")) data.put("icon", QuestGraphSettings.icon(data["icon"]!!, level))
        if (data.contains("tags")) {
            val tags = data["tags"]
            require(tags is ListTag && tags.size <= 1024 && (tags.isEmpty() || tags.elementType == Tag.TAG_STRING)) { "tags must be a bounded string list" }
        }
        if (data.contains("loot_crate")) {
            val crate = data["loot_crate"] as? CompoundTag ?: error("loot_crate must be an object")
            require(crate.allKeys.all { it in setOf("string_id", "item_name", "color", "glow", "drops") }) { "Unknown loot crate fields" }
            omitBlankFields(crate)
            for (key in listOf("string_id", "item_name")) if (crate.contains(key)) require(crate[key] is StringTag && crate.getString(key).length <= 128) { "$key must be a bounded string" }
            if (crate.getString("string_id").isNotEmpty()) require(crate.getString("string_id").matches(Regex("[a-z0-9_]+")) ||
                crate.getString("string_id") == dev.ftb.mods.ftbquests.quest.QuestObjectBase.getCodeString(-1L)) { "Invalid loot crate string_id" }
            if (crate.contains("color")) crate.putInt("color", numeric(crate["color"]!!, "color", Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt())
            if (crate.contains("glow")) {
                val glow = crate["glow"]
                require(glow is NumericTag && glow.asDouble in listOf(0.0, 1.0)) { "glow must be boolean" }
                crate.putBoolean("glow", glow.asDouble == 1.0)
            }
            if (crate.contains("drops")) {
                val drops = crate["drops"] as? CompoundTag ?: error("drops must be an object")
                require(drops.allKeys.all { it in setOf("passive", "monster", "boss") }) { "Unknown loot crate drops" }
                omitBlankFields(drops)
                drops.allKeys.toList().forEach { key -> drops.putInt(key, numeric(drops[key]!!, key, 0, Int.MAX_VALUE.toLong()).toInt()) }
            }
            // 原生 reader 在字段缺省时会覆盖构造器默认值（最典型的是 WHITE 被写回黑色），因此校验后需按真实对象写出的默认值兜底。
            val defaults = CompoundTag().also {
                dev.ftb.mods.ftbquests.quest.loot.LootCrate(
                    dev.ftb.mods.ftbquests.quest.loot.RewardTable(-1, obj.questFile), false).writeData(it)
            }
            defaults.allKeys.forEach { key -> if (!crate.contains(key)) crate.put(key, defaults[key]!!.copy()) }
        }
        if (data.contains("rewards")) {
            val rewards = data["rewards"]
            require(rewards is ListTag && (rewards.isEmpty() || rewards.elementType == Tag.TAG_COMPOUND) && rewards.size <= 128) { "Invalid table rewards" }
            val fakeQuest = dev.ftb.mods.ftbquests.quest.loot.RewardTable(-1, obj.questFile).fakeQuest
            val entryIds = mutableSetOf<Long>()
            for (i in 0 until rewards.size) {
                val input = rewards.getCompound(i)
                if (input["id"] is StringTag && input.getString("id").isBlank()) input.remove("id")
                if (input.contains("id")) {
                    require(input["id"] is StringTag && input.getString("id").matches(Regex("[0-9a-fA-F]{1,16}")) &&
                        java.lang.Long.parseUnsignedLong(input.getString("id"), 16) != 0L) { "Inline reward id must be a nonzero hex string" }
                } else {
                    val hash = java.security.MessageDigest.getInstance("SHA-256").digest("contactquests:inline:${obj.codeString}:$i".toByteArray())
                    val id = java.nio.ByteBuffer.wrap(hash).long.let { (it and 0x0fffffffffffffffL) or 0x5000000000000000L }
                    input.putString("id", dev.ftb.mods.ftbquests.quest.QuestObjectBase.getCodeString(id))
                }
                val entryId = java.lang.Long.parseUnsignedLong(input.getString("id"), 16)
                require(entryId != -1L && entryIds.add(entryId)) { "Duplicate or reserved inline reward ID" }
                input.putString("id", dev.ftb.mods.ftbquests.quest.QuestObjectBase.getCodeString(entryId))
                // 原生 RewardTable 序列化普通 ItemReward 时会省略 type 字段，读取前先显式补回 ftbquests:item。
                if (!input.contains("type") || input["type"] is StringTag && input.getString("type").isBlank()) input.putString("type", "ftbquests:item")
                require(input["type"] is StringTag) { "A table entry needs a reward type string" }
                if (input["weight"] is StringTag && input.getString("weight").isBlank()) input.remove("weight")
                if (input.contains("weight")) require(input["weight"] is NumericTag && input.getFloat("weight").let { it.isFinite() && it >= 0 }) { "Invalid table weight" }
                val reward = dev.ftb.mods.ftbquests.quest.reward.RewardType.createReward(i.toLong() + 1, fakeQuest, input.getString("type")) ?: error("Unknown table reward")
                require(reward !is RandomReward && reward !is com.creeperyang.contactquests.quest.reward.ParcelRandomReward) { "Nested inline reward tables are unsupported" }
                val config = input.copy().apply { remove("type"); remove("weight"); remove("id") }
                val checked = QuestObjectSettings.prepare(level, reward, config, true)
                val merged = QuestObjectSettings.merge(level, reward, checked)
                validate(level, reward, merged)
                reward.readData(merged, level.registryAccess())
                config.allKeys.forEach { input.remove(it) }
                input.merge(checked)
            }
            InlineQuestSettings.validateEntryIds(obj, entryIds)
        }
    }

    private fun omitBlankFields(data: CompoundTag) {
        data.allKeys.toList().forEach { key ->
            if (data[key] is StringTag && data.getString(key).isBlank()) data.remove(key)
        }
    }
}
