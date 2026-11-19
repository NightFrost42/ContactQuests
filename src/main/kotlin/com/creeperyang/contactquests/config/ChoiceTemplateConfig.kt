package com.creeperyang.contactquests.config

import com.creeperyang.contactquests.ContactQuests
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.util.RandomSource
import net.neoforged.fml.loading.FMLPaths

/** 服务端独占的自选奖励回邮信件模板配置，与常规 NPC 错信策略配置文件相互独立。 */
object ChoiceTemplateConfig {
    data class Template(val id: String = "request", val weight: Int = 1,
                        val text: String = "{recipient}，你好：\n这次的谢礼我想选{reward}，有空时请寄给我吧。谢谢！\n{player}")
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val path get() = FMLPaths.CONFIGDIR.get().resolve("contactquests/choice_templates.json")
    private val type = object : TypeToken<Map<String, List<Template>>>() {}.type
    private var groups: Map<String, List<Template>> = bundledDefaults()

    fun bundledDefaults(): Map<String, List<Template>> = ChoiceTemplateConfig::class.java
        .getResourceAsStream("/contactquests/choice_templates.json")!!.bufferedReader(Charsets.UTF_8).use {
            gson.fromJson<Map<String, List<Template>>>(it, type)
        }

    fun reload() {
        try {
            java.nio.file.Files.createDirectories(path.parent)
            if (!java.nio.file.Files.exists(path)) java.nio.file.Files.writeString(path, gson.toJson(groups))
            val loaded: Map<String, List<Template>> = gson.fromJson(java.nio.file.Files.readString(path), type)
            configure(loaded)
        } catch (e: Exception) {
            ContactQuests.warn("Choice template configuration rejected: %s", e.message)
        }
    }

    /** 先整体校验全部替换模板，确保合法后再切换当前生效配置。 */
    fun configure(replacement: Map<String, List<Template>>) {
        require(replacement.isNotEmpty() && replacement.size <= 128) { "Template groups must contain 1..128 entries" }
        replacement.forEach { (group, templates) ->
            require(group.matches(Regex("[a-z0-9_./:-]{1,128}"))) { "Invalid template group" }
            require(templates.size in 1..128 && templates.map { it.id }.distinct().size == templates.size) { "Invalid template list" }
            templates.forEach {
                require(it.id.matches(Regex("[a-z0-9_-]{1,64}")) && it.weight >= 0 && it.text.isNotBlank() && it.text.length <= 1500) { "Invalid letter template" }
                require(Regex("\\{([a-z_]+)}").findAll(it.text).all { match -> match.groupValues[1] in
                    setOf("recipient", "player", "quest", "task_goal", "reward") }) { "Unknown letter placeholder" }
            }
            require(templates.sumOf { it.weight.toLong() } in 1..Int.MAX_VALUE.toLong()) { "A template group needs positive total weight" }
        }
        groups = replacement.mapValues { it.value.toList() }
    }

    fun hasGroup(group: String) = group in groups
    fun current(): Map<String, List<Template>> = groups.mapValues { it.value.toList() }
    fun select(group: String, random: RandomSource): Template {
        val templates = groups[group] ?: error("Unknown choice template group: $group")
        var weight = random.nextInt(templates.sumOf { it.weight })
        return templates.first { weight -= it.weight; weight < 0 }
    }

    fun selectForLocale(group: String, locale: String?, random: RandomSource): Template {
        val resolvedGroup = if (group.isBlank() || group == "default") resolveLocaleGroup(locale) else group
        return select(resolvedGroup, random)
    }

    private fun resolveLocaleGroup(locale: String?): String {
        val normalized = locale?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return "default"
        if (normalized in groups) return normalized
        val lang = normalized.substringBefore('_')
        if (lang == "en" && "en_us" in groups) return "en_us"
        if (lang in groups) return lang
        val sameLang = groups.keys.firstOrNull { it.startsWith("${lang}_") }
        if (sameLang != null) return sameLang
        return "default"
    }

    fun fill(template: Template, values: Map<String, String>): String {
        val result = Regex("\\{([a-z_]+)}").replace(template.text) { values[it.groupValues[1]] ?: it.value }
        return result.also { require(it.length <= 2000) { "Filled postcard text exceeds 2000 characters" } }
    }
}
