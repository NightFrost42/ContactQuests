package com.creeperyang.contactquests.llm

import com.creeperyang.contactquests.quest.QuestGraphService
import com.creeperyang.contactquests.quest.QuestStateEvents
import com.creeperyang.contactquests.bridge.ITeamDataExtension
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import dev.ftb.mods.ftbquests.quest.ServerQuestFile
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import java.security.MessageDigest

object NpcLlmTools {
    internal val taskTypes = setOf(
        "contactquests:parcel_task",
        "contactquests:red_packet_task",
        "contactquests:postcard_task",
        "ftbquests:checkmark"
    )
    internal val rewardTypes = setOf(
        "contactquests:parcel",
        "contactquests:red_packet",
        "contactquests:postcard",
        "contactquests:parcel_random",
        "contactquests:parcel_all_table",
        "contactquests:parcel_choice"
    )

    fun execute(level: ServerLevel, player: ServerPlayer, conversation: NpcLlmSavedData.Conversation,
                npc: NpcLlmConfig.Npc, provider: NpcLlmConfig.Provider, tool: NpcLlmTransport.Tool): String {
        require(tool.name in npc.tools && tool.name in provider.tools) {
            "This NPC/provider is not authorized for ${tool.name}"
        }
        val args = tool.arguments
        require(args.keySet().all {
            it in if (tool.name == "get_quest") setOf("key") else setOf("key", "options", "tasks", "rewards")
        }) { "Unknown tool arguments" }
        val key = args["key"]?.asString ?: error("Tool key is required")
        require(key.matches(Regex("[a-z0-9][a-z0-9_/-]{0,47}"))) { "Invalid tool key" }
        val sourceTeam = dev.ftb.mods.ftbteams.api.FTBTeamsAPI.api().manager.getTeamForPlayer(player).orElseThrow()
        require(
            sourceTeam.id == conversation.teamId && (conversation.owner == null || conversation.owner == player.uuid)
        ) {
            "Conversation identity changed"
        }
        if (tool.name == "get_quest") {
            val id = conversation.published[key] ?: error("Quest does not belong to this conversation")
            val quest = QuestGraphService.getQuest(level, id) ?: error("Quest was removed")
            return JsonObject().apply {
                addProperty("id", quest.codeString)
                addProperty("title", quest.rawTitle)
            }.toString()
        }
        require(tool.name == "publish_quest") { "Unknown structured tool" }
        // FTB 任务进度绑定到团队；personal 模式仅限个人团队，多人队伍必须使用 team 作用域。
        require(npc.scope == "team" || sourceTeam.members.size == 1) {
            "Personal quest publication requires a personal FTB team\u003b use team scope for a party"
        }
        require(conversation.published.containsKey(key) || conversation.published.size < npc.maxPublishedQuests) {
            "NPC quest quota reached"
        }
        require(args["options"] == null || args["options"].isJsonNull || args["options"].isJsonObject) {
            "options must be an object or null"
        }
        val options = (args["options"]?.let(::plain) as? Map<String, Any?>).orEmpty()
        require(options.keys.all { it in setOf("title", "subtitle", "description", "x", "y") }) {
            "Unauthorized quest options"
        }
        fun children(name: String, allowed: Set<String>): List<Map<String, Any?>> {
            val array = args.getAsJsonArray(name) ?: error("$name is required")
            require(array.size() in 1..16) { "$name must contain 1..16 objects" }
            return array.map { raw ->
                val item = plain(raw) as? Map<String, Any?> ?: error("Invalid $name object")
                require(item.keys.all { it in setOf("type", "data") } && item["type"] in allowed) {
                    "Unauthorized $name type/fields: type=${item["type"]}, fields=${item.keys}"
                }
                require(item["data"] == null || item["data"] is Map<*, *>) { "Child data must be an object or null" }
                val data = (item["data"] as? Map<String, Any?>).orEmpty()
                require(data.keys.none {
                    it in setOf(
                        "unlock_tags",
                        "team_reward",
                        "auto",
                        "custom_id",
                        "table_id",
                        "is_team_reward",
                        "team_reward_multiplier"
                    )
                }) { "Unauthorized child options" }
                if (data.containsKey("table_data")) {
                    require(name == "rewards" && item["type"] in setOf(
                        "contactquests:parcel_random",
                        "contactquests:parcel_all_table",
                        "contactquests:parcel_choice"
                    )) { "Inline table is not applicable" }
                    val table = data["table_data"] as? Map<*, *> ?: error("Inline table must be an object")
                    require(table.keys.all {
                        it in setOf("rewards", "empty_weight", "loot_size", "hide_tooltip", "use_title")
                    }) { "Unauthorized table options" }
                    val entries = table["rewards"] as? List<*> ?: error("Inline reward entries are required")
                    require(entries.size in 1..16) { "Inline reward table must have 1..16 entries" }
                    val scopedEntries = entries.map { entry ->
                        val fields = entry as? Map<*, *> ?: error("Invalid table entry")
                        require(fields["type"] in setOf(
                            "ftbquests:item",
                            "contactquests:parcel",
                            "contactquests:red_packet",
                            "contactquests:postcard"
                        ) && fields.keys.none {
                            it in setOf(
                                "id",
                                "unlock_tags",
                                "team_reward",
                                "auto",
                                "custom_id",
                                "table_id",
                                "table_data",
                                "is_team_reward",
                                "team_reward_multiplier"
                            )
                        }) { "Unauthorized table entry" }
                        if (fields["type"] == "ftbquests:item") fields
                        else fields + mapOf("target_addressee" to conversation.npc)
                    }
                    return@map item + mapOf("data" to (data + mapOf(
                        "target_addressee" to conversation.npc,
                        "table_data" to (table + mapOf("rewards" to scopedEntries))
                    )))
                }
                item + mapOf("data" to (data + when {
                    name == "tasks" && item["type"] != "ftbquests:checkmark" ->
                        mapOf("TargetAddressee" to conversation.npc)
                    name == "rewards" -> mapOf("target_addressee" to conversation.npc)
                    else -> emptyMap()
                }))
            }
        }
        val tasks = children("tasks", taskTypes)
        val rewards = children("rewards", rewardTypes)
        val identity = MessageDigest.getInstance("SHA-256").digest(conversation.key.toByteArray(Charsets.UTF_8))
            .take(12).joinToString("") { "%02x".format(it) }
        val tag = "cq_npc_$identity"
        val quest = QuestGraphService.publishQuest(
            level,
            npc.chapterId,
            "cqllm:${npc.questPrefix}/$identity/$key",
            options + mapOf("requiredTags" to listOf(tag), "tagMode" to "ALL", "hideWithoutTags" to true),
            tasks,
            rewards
        )
        val team = ServerQuestFile.INSTANCE.getNullableTeamData(conversation.teamId)
            ?: error("Original team was removed")
        QuestStateEvents.withActor(player, "npc_tool") { (team as ITeamDataExtension).`contactQuests$unlockTag`(tag) }
        conversation.published[key] = quest.codeString
        NpcLlmSavedData.get(level).setDirty()
        return quest.codeString
    }

    private fun plain(element: JsonElement): Any? = when {
        element.isJsonNull -> null
        element.isJsonObject -> element.asJsonObject.entrySet().associate { it.key to plain(it.value) }
        element.isJsonArray -> element.asJsonArray.map(::plain)
        element.asJsonPrimitive.isBoolean -> element.asBoolean
        element.asJsonPrimitive.isNumber -> element.asDouble.also { require(it.isFinite()) { "Nonfinite tool number" } }
        else -> element.asString
    }
}
