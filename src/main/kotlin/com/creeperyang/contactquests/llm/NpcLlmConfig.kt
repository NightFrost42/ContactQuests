package com.creeperyang.contactquests.llm

import com.creeperyang.contactquests.ContactQuests
import com.google.gson.GsonBuilder
import net.neoforged.fml.loading.FMLPaths
import java.net.URI

/** 仅服务端读取的 AI NPC 私人配置：鉴权凭据只保存环境变量名，绝不下发至客户端。 */
object NpcLlmConfig {
    data class Provider(val transport: String = "mock", val endpoint: String = "", val apiKeyEnv: String = "",
                        val model: String = "fixed-mock", val reasoning: String = "default",
                        val timeoutSeconds: Int = 30, val maxTokens: Int = 1024, val tools: List<String> = emptyList())
    data class Npc @JvmOverloads constructor(
        val enabled: Boolean = false,
        val provider: String = "mock",
        val persona: String = "You are a helpful quest NPC.",
        val scope: String = "player",
        val historyTurns: Int = 12,
        val model: String = "",
        val reasoning: String = "",
        val tools: List<String> = emptyList(),
        val chapterId: String = "",
        val questPrefix: String = "npc",
        val maxPublishedQuests: Int = 16,
        val cooldownTicks: Int = 40,
        val autoFill: Boolean = false
    )
    data class Config(val enabled: Boolean = false, val providers: Map<String, Provider> = mapOf("mock" to Provider()),
                      val npcs: Map<String, Npc> = emptyMap())
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val path get() = FMLPaths.CONFIGDIR.get().resolve("contactquests/npc_llm.json")
    @Volatile private var config = Config()
    fun current() = config
    fun names(): Set<String> = if (config.enabled) config.npcs.filterValues { it.enabled }.keys else emptySet()
    fun npc(name: String): Npc? = config.npcs[name]?.takeIf { config.enabled && it.enabled }

    fun reload() {
        try {
            java.nio.file.Files.createDirectories(path.parent)
            if (!java.nio.file.Files.exists(path)) java.nio.file.Files.writeString(path, gson.toJson(Config()))
            configure(gson.fromJson(java.nio.file.Files.readString(path), Config::class.java))
        } catch (e: Exception) { ContactQuests.warn("NPC LLM configuration rejected: %s", e.message) }
    }
    fun configure(replacement: Config) {
        require(replacement.providers.size <= 64 && replacement.npcs.size <= 256) { "Too many LLM configurations" }
        replacement.providers.forEach { (id, p) ->
            require(id.matches(Regex("[a-z0-9_-]{1,64}")) && p.transport in setOf("mock", "openai_compatible")) { "Invalid LLM provider" }
            require(p.model.length in 1..128 && p.apiKeyEnv.matches(Regex("[A-Za-z_][A-Za-z0-9_]{0,127}|"))) { "Invalid model/credential variable" }
            require(p.reasoning in setOf("default", "none", "minimal", "low", "medium", "high", "xhigh", "max")) { "Invalid reasoning level" }
            require(p.timeoutSeconds in 1..180 && p.maxTokens in 64..8192 && p.tools.all { it in toolNames }) { "Invalid provider limits/tools" }
            if (p.transport == "openai_compatible") {
                val uri = URI(p.endpoint)
                require(uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null &&
                    (uri.scheme == "https" || uri.scheme == "http" && uri.host in setOf("localhost", "127.0.0.1", "::1"))) { "Invalid provider endpoint" }
            }
        }
        replacement.npcs.forEach { (name, npc) ->
            require(name.isNotBlank() && name.length <= 128 && npc.provider in replacement.providers) { "Invalid NPC/provider" }
            require(npc.persona.isNotBlank() && npc.persona.length <= 8192 && npc.scope in setOf("player", "team")) { "Invalid persona/scope" }
            require(npc.historyTurns in 1..64 && npc.maxPublishedQuests in 0..128 && npc.cooldownTicks in 0..12000) { "Invalid NPC limits" }
            require(npc.tools.all { it in toolNames } && npc.questPrefix.matches(Regex("[a-z0-9_/-]{1,48}"))) { "Invalid NPC tools/prefix" }
            if (npc.tools.contains("publish_quest")) require(npc.chapterId.matches(Regex("[0-9a-fA-F]{16}"))) { "Publication needs a configured chapter ID" }
            require(npc.model.length <= 128 && (npc.reasoning.isBlank() || npc.reasoning in setOf("default", "none", "minimal", "low", "medium", "high", "xhigh", "max"))) { "Invalid NPC model settings" }
        }
        config = replacement.copy(providers = replacement.providers.mapValues { it.value.copy(tools = it.value.tools.toList()) },
            npcs = replacement.npcs.mapValues { it.value.copy(tools = it.value.tools.toList()) })
    }
    val toolNames = setOf("publish_quest", "get_quest")
}
