package com.creeperyang.contactquests.quest

import dev.ftb.mods.ftblibrary.config.Tristate
import dev.ftb.mods.ftbquests.quest.*
import net.minecraft.nbt.*
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import java.util.Locale

/** 基于固定版本（2001.4.22）FTB 编辑器字段构造校验后的图谱属性快照，构造过程绝不直接修改线上图谱。 */
enum class TextKey { TITLE, QUEST_SUBTITLE, CHAPTER_SUBTITLE, QUEST_DESC }

object QuestGraphSettings {
    data class Prepared(val data: CompoundTag, val locale: String, val text: Map<TextKey, Any>)

    fun creationDefaults(options: Map<String, Any?>): Map<String, Any?> = options.filterValues {
        it != null && !(it is String && it.isBlank())
    }

    private val questBooleans = mapOf(
        "disableToast" to "disable_toast",
        "hideWithoutTags" to "cq_hide_no_tags", "hideIfLocked" to "cq_hide_locked",
        "invisible" to "invisible", "optional" to "optional", "hideDependentLines" to "hide_dependent_lines",
        "hideLockIcon" to "hide_lock_icon", "ignoreRewardBlocking" to "ignore_reward_blocking")
    private val questTristates = mapOf(
        "hideUntilDepsVisible" to "hide_until_deps_visible", "hideUntilDepsComplete" to "hide_until_deps_complete",
        "hideDetailsUntilStartable" to "hide_details_until_startable", "hideTextUntilComplete" to "hide_text_until_complete",
        "hideDependencyLines" to "hide_dependency_lines", "disableRecipeMod" to "disable_recipe_mod",
        "canRepeat" to "can_repeat", "requireSequentialTasks" to "require_sequential_tasks")
    private val questIntegers = mapOf(
        "minWidth" to ("min_width" to 3000), "invisibleUntilTasks" to ("invisible_until_tasks" to Int.MAX_VALUE),
        "minRequiredDependencies" to ("min_required_dependencies" to Int.MAX_VALUE),
        "maxCompletableDependents" to ("max_completable_dependents" to Int.MAX_VALUE),
        "repeatCooldown" to ("repeat_cooldown" to Int.MAX_VALUE))
    private val chapterBooleans = mapOf(
        "disableToast" to "disable_toast",
        "alwaysInvisible" to "always_invisible", "defaultHideDependencyLines" to "default_hide_dependency_lines",
        "hideQuestDetailsUntilStartable" to "hide_quest_details_until_startable",
        "hideQuestUntilDepsComplete" to "hide_quest_until_deps_complete",
        "hideQuestUntilDepsVisible" to "hide_quest_until_deps_visible", "hideTextUntilComplete" to "hide_text_until_complete",
        "defaultRepeatable" to "default_repeatable_quest", "requireSequentialTasks" to "require_sequential_tasks")

    fun quest(level: ServerLevel, quest: Quest, supplied: Map<String, Any?>, resolve: (String) -> Long, creating: Boolean = false): Prepared {
        val allowed = questBooleans.keys + questTristates.keys + questIntegers.keys + setOf(
            "title", "subtitle", "description", "locale", "icon", "tags", "customId", "x", "y", "shape", "size",
            "iconScale", "preset", "guidePage", "progressionMode", "requiredTags", "tagMode", "mutex", "mutexMode",
            "mutexCount", "dependencies", "dependencyRequirement", "dependencyControlPoints")
        require(supplied.keys.all { it in allowed }) { "Unknown graph settings: ${supplied.keys - allowed}" }
        val options = if (creating) creationDefaults(supplied) else supplied
        checkKeys(options, allowed)
        val tag = CompoundTag().also { quest.writeData(it) }
        val locale = locale(options, "en_us")
        val text = text(options, false)
        // 无论脚本传参属性顺序如何，必须先解析前置依赖任务 ID，再解析依赖折线控制点。
        for ((key, nullable) in options.entries.sortedBy { if (it.key == "dependencies") 0 else if (it.key == "dependencyControlPoints") 2 else 1 }) {
            val value = nullable!!
            when (key) {
                in questBooleans -> tag.putBoolean(questBooleans.getValue(key), boolean(key, value))
                in questTristates -> tristate(tag, questTristates.getValue(key), value)
                in questIntegers -> questIntegers.getValue(key).let { tag.putInt(it.first, integer(key, value, 0, it.second)) }
                "x", "y" -> tag.putDouble(key, number(key, value))
                "size" -> tag.putDouble("size", number(key, value, 0.0, 8.0))
                "iconScale" -> tag.putDouble("icon_scale", number(key, value, 0.1, 2.0))
                "shape" -> tag.putString("shape", shape(value))
                "preset" -> tag.putString("preset", preset(quest.questFile, value))
                "guidePage" -> tag.putString("guide_page", string(key, value))
                "progressionMode" -> tag.putString("progression_mode", enum(key, value, ProgressionMode.NAME_MAP.keys))
                "requiredTags" -> tag.put("cq_req_tags", list(strings(key, value).distinct()))
                "tagMode" -> tag.putString("cq_tag_mode", enum(key, value, listOf("ANY", "ALL")).uppercase(Locale.ROOT))
                "mutexMode" -> tag.putString("cq_mutex_mode", enum(key, value, listOf("ANY", "ALL", "NUMBER")).uppercase(Locale.ROOT))
                "mutexCount" -> tag.putInt("cq_mutex_count", integer(key, value, 1))
                "mutex", "dependencies" -> {
                    val ids = strings(key, value).map { reference ->
                        val obj = quest.questFile.get(resolve(reference)) ?: error("Missing $key object: $reference")
                        require(obj !== quest && obj.id != quest.id && obj !in quest.tasks && obj !is BaseQuestFile && obj !== quest.chapter) {
                            "Invalid self/parent $key reference"
                        }
                        if (key == "dependencies") require(!dependsOn(obj, quest.id, mutableSetOf())) { "Dependency cycle" }
                        obj.codeString
                    }.distinct()
                    tag.put(if (key == "mutex") "cq_mutex_ids" else "dependencies", list(ids))
                }
                "dependencyRequirement" -> tag.putString("dependency_requirement", enum(key, value, DependencyRequirement.NAME_MAP.keys))
                "dependencyControlPoints" -> {
                    val points = value as? Map<*, *> ?: error("dependencyControlPoints must be an ID -> [[x,y], ...] map")
                    val compound = CompoundTag()
                    val dependencies = tag.getList("dependencies", Tag.TAG_STRING.toInt()).map { it.asString }
                    points.forEach { (id, pointList) ->
                        val obj = quest.questFile.get(resolve(string("dependency ID", id ?: error("Null dependency ID"))))
                            ?: error("Missing dependency control-point object")
                        require(obj.codeString in dependencies) { "Control points need a configured dependency" }
                        val coordinates = pointList as? Collection<*> ?: error("Control points must be a list")
                        require(coordinates.size == 2) { "A native dependency curve requires two control points" }
                        compound.put(obj.codeString, ListTag().apply {
                            coordinates.forEach { point ->
                                val pair = point as? Collection<*> ?: error("A control point must be [x,y]")
                                require(pair.size == 2) { "A control point must have two coordinates" }
                                pair.forEach { add(DoubleTag.valueOf(number("coordinate", it!!))) }
                            }
                        })
                    }
                    tag.put("dep_control_pts", compound)
                }
                else -> common(tag, key, value, level)
            }
        }
        // 绝不要在临时草稿 Quest 对象上调用 readData：原生 addDependency 会产生副作用去修改线上关联任务的 dependants 列表。
        return Prepared(tag, locale, text)
    }

    fun chapter(level: ServerLevel, chapter: Chapter, supplied: Map<String, Any?>, resolve: (String) -> Long, creating: Boolean = false): Prepared {
        val allowed = chapterBooleans.keys + setOf("title", "subtitle", "locale", "icon", "tags", "customId",
            "defaultQuestShape", "defaultQuestSize", "defaultMinWidth", "defaultPreset", "progressionMode", "consumeItems", "autofocusId")
        require(supplied.keys.all { it in allowed }) { "Unknown graph settings: ${supplied.keys - allowed}" }
        val options = if (creating) creationDefaults(supplied) else supplied
        checkKeys(options, allowed)
        val tag = CompoundTag().also { chapter.writeData(it) }
        for ((key, nullable) in options) {
            val value = nullable!!
            when (key) {
                in chapterBooleans -> tag.putBoolean(chapterBooleans.getValue(key), boolean(key, value))
                "defaultQuestShape" -> tag.putString("default_quest_shape", shape(value))
                "defaultQuestSize" -> tag.putDouble("default_quest_size", number(key, value, 0.0625, 8.0))
                "defaultMinWidth" -> tag.putInt("default_min_width", integer(key, value, 0, 3000))
                "defaultPreset" -> tag.putString("preset", preset(chapter.questFile, value))
                "progressionMode" -> tag.putString("progression_mode", enum(key, value, ProgressionMode.NAME_MAP.keys))
                "consumeItems" -> tristate(tag, "consume_items", value)
                "autofocusId" -> {
                    val id = string(key, value)
                    if (id.isNotEmpty()) require(chapter.questFile.getQuest(resolve(id))?.chapter === chapter) {
                        "autofocusId must be a quest in this chapter"
                    }
                    tag.putString("autofocus_id", id)
                }
                else -> common(tag, key, value, level)
            }
        }
        Chapter(chapter.id, chapter.questFile, chapter.group, tag.getString("filename"))
            .readData(tag.copy())
        return Prepared(tag, locale(options, "en_us"), text(options, true))
    }

    fun applyText(obj: QuestObjectBase, prepared: Prepared) {
        prepared.text.forEach { (key, value) ->
            when (key) {
                TextKey.TITLE -> obj.setRawTitle(value as String)
                TextKey.QUEST_SUBTITLE -> (obj as Quest).setRawSubtitle(value as String)
                TextKey.QUEST_DESC -> {
                    val data = CompoundTag().also { obj.writeData(it) }
                    data.put("description", list(strings(key.name, value)))
                    obj.readData(data)
                }
                TextKey.CHAPTER_SUBTITLE -> {
                    val data = CompoundTag().also { obj.writeData(it) }
                    data.put("subtitle", list(strings(key.name, value)))
                    obj.readData(data)
                }
            }
        }
        obj.clearCachedData()
    }

    private fun common(tag: CompoundTag, key: String, value: Any, level: ServerLevel) {
        when (key) {
            "tags" -> tag.put("tags", list(strings(key, value).distinct()))
            "customId" -> tag.putString("custom_id", string(key, value).also {
                require(it.isEmpty() || it.matches(Regex("[a-zA-Z0-9_./:-]{1,128}"))) { "Invalid customId" }
            })
            "icon" -> tag.put("icon", icon(value, level))
        }
    }

    fun icon(value: Any, level: ServerLevel): Tag {
        if (value is ItemStack) return if (value.isEmpty) CompoundTag() else value.copyWithCount(1).save(CompoundTag())
        val tag = when (value) {
            is String -> if (value.isEmpty()) CompoundTag() else StringTag.valueOf(value)
            is StringTag -> if (value.asString.isEmpty()) CompoundTag() else value.copy()
            is CompoundTag -> value.copy()
            is Map<*, *> -> toTag(value)
            else -> error("icon must be an item ID, ItemStack or item NBT")
        }
        if (tag is CompoundTag && tag.isEmpty) return tag
        val parsed = item(tag)
        require(!parsed.isEmpty && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(parsed.item).toString() != "ftbquests:missing_item") {
            "Unknown or invalid icon item"
        }
        return parsed.copyWithCount(1).save(CompoundTag())
    }

    fun item(tag: Tag): ItemStack {
        if (tag is CompoundTag) return ItemStack.of(tag)
        require(tag is StringTag) { "Item must be an ID or compound" }
        val id = net.minecraft.resources.ResourceLocation.tryParse(tag.asString) ?: error("Invalid item ID")
        require(net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)) { "Unknown item" }
        return ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id))
    }

    fun toTag(value: Any): Tag = when (value) {
        is Tag -> value.copy()
        is String -> StringTag.valueOf(value)
        is Boolean -> ByteTag.valueOf(value)
        is Byte -> ByteTag.valueOf(value)
        is Short -> ShortTag.valueOf(value)
        is Int -> IntTag.valueOf(value)
        is Long -> LongTag.valueOf(value)
        is Number -> DoubleTag.valueOf(value.toDouble().also { require(it.isFinite()) { "Non-finite NBT value" } })
        is Map<*, *> -> CompoundTag().apply { value.forEach { (k, v) -> put(string("NBT key", k!!), toTag(v ?: error("Null NBT value"))) } }
        is Collection<*> -> ListTag().apply { value.forEach {
            val entry = toTag(it ?: error("Null NBT list value"))
            require(isEmpty() || elementType == entry.id) { "NBT list values must have the same type" }
            add(entry)
        } }
        else -> error("Unsupported NBT value")
    }

    /** 仅在创建对象时忽略空白可选字段；显式传入的空集合代表刻意清空原配置，予以保留。 */
    fun creationData(value: Any?): CompoundTag = when (value) {
        null -> CompoundTag()
        is CompoundTag -> value.copy()
        is Map<*, *> -> toTag(nullableCreationMap(value)) as CompoundTag
        else -> error("Creation data must be an options object or CompoundTag")
    }

    // 先保留全部字段交由目标 schema 逐一校验；null 转为空白创建占位值。
    private fun nullableCreationMap(value: Map<*, *>): Map<*, *> = value.mapValues { (_, v) -> when (v) {
        null -> ""
        is Map<*, *> -> nullableCreationMap(v)
        is Collection<*> -> v.map { if (it is Map<*, *>) nullableCreationMap(it) else it }
        else -> v
    } }

    fun tristate(tag: CompoundTag, key: String, value: Any) {
        val state = when (value) {
            is Boolean -> if (value) Tristate.TRUE else Tristate.FALSE
            else -> Tristate.valueOf(string(key, value).uppercase(Locale.ROOT))
        }
        tag.remove(key)
        state.write(tag, key)
    }
    fun number(key: String, value: Any, min: Double = -Double.MAX_VALUE, max: Double = Double.MAX_VALUE): Double =
        (value as? Number)?.toDouble()?.also { require(it.isFinite() && it in min..max) { "$key must be finite in $min..$max" } }
            ?: error("$key must be numeric")
    fun integer(key: String, value: Any, min: Int = 0, max: Int = Int.MAX_VALUE): Int =
        number(key, value, min.toDouble(), max.toDouble()).also { require(it == kotlin.math.floor(it)) { "$key must be integral" } }.toInt()
    fun string(key: String, value: Any): String = (value as? String ?: error("$key must be a string")).also {
        require(it.length <= 16384) { "$key is too long" }
    }
    fun boolean(key: String, value: Any): Boolean = value as? Boolean ?: error("$key must be boolean")
    fun strings(key: String, value: Any): List<String> = (value as? Collection<*> ?: error("$key must be a list"))
        .also { require(it.size <= 1024) { "$key list is too large" } }.map { string(key, it ?: error("Null list element")) }
    fun list(values: List<String>): ListTag = ListTag().apply { values.forEach { add(StringTag.valueOf(it)) } }
    private fun enum(key: String, value: Any, allowed: Collection<String>): String = string(key, value).let { input ->
        allowed.firstOrNull { it.equals(input, true) } ?: error("$key must be one of $allowed")
    }
    private fun shape(value: Any): String {
        val name = string("shape", value)
        if (name.isEmpty() || name == "default") return ""
        // FTB 1.38/39 的内置任务形状资源仅在客户端加载，服务端脚本创建任务时原生形状表可能尚未包含默认集合；此处固定兜底内置集合并兼容已注册扩展形状。
        val registered = setOf("circle", "diamond", "gear", "heart", "hexagon", "none", "octagon", "pentagon", "rsquare", "square")
        require(name.matches(Regex("[a-z0-9_./:-]{1,128}")) && name in registered) { "Unknown quest shape" }
        return name
    }
    private fun preset(file: BaseQuestFile, value: Any): String = string("preset", value).also {
        require(it.isEmpty() || false) { "Visual presets are unavailable in FTB Quests 2001.4.22" }
    }
    private fun locale(options: Map<String, Any?>, fallback: String): String =
        options["locale"]?.let { string("locale", it) }?.also {
            require(it.matches(Regex("[a-z]{2,3}_[a-z0-9]{2,8}"))) { "Invalid locale" }
        } ?: fallback
    private fun text(options: Map<String, Any?>, chapter: Boolean): Map<TextKey, Any> = buildMap {
        options["title"]?.let { put(TextKey.TITLE, string("title", it)) }
        options["subtitle"]?.let { put(if (chapter) TextKey.CHAPTER_SUBTITLE else TextKey.QUEST_SUBTITLE,
            if (chapter) strings("subtitle", it) else string("subtitle", it)) }
        options["description"]?.let { put(TextKey.QUEST_DESC, strings("description", it)) }
    }
    private fun checkKeys(options: Map<String, Any?>, allowed: Set<String>) {
        require(options.keys.all { it in allowed }) { "Unknown graph settings: ${options.keys - allowed}" }
        require(options.values.none { it == null }) { "Settings cannot be null" }
    }
    private fun dependsOn(obj: QuestObject, target: Long, visited: MutableSet<Long>): Boolean {
        if (obj.id == target) return true
        if (!visited.add(obj.id)) return false
        return when (obj) {
            is Quest -> obj.streamDependencies().anyMatch { dependsOn(it, target, visited) }
            is dev.ftb.mods.ftbquests.quest.task.Task -> dependsOn(obj.quest, target, visited)
            is Chapter -> obj.quests.any { dependsOn(it, target, visited) }
            else -> false
        }
    }
}
