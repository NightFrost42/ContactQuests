package com.creeperyang.contactquests.llm

import com.google.gson.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Flow

object NpcLlmTransport {
    private const val MAGPIE_USER_AGENT = "Magpie/0.1.3933"
    data class Tool(val id: String, val name: String, val arguments: JsonObject)
    data class Response(val text: String, val tools: List<Tool>, val mailPolicy: String? = null)
    data class Input(val npc: String, val persona: String, val model: String, val reasoning: String,
                     val history: List<NpcLlmSavedData.Turn>, val text: String, val tools: Set<String>)
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
        // 本地 OpenAI 兼容网关通常仅提供 HTTP/1.1，避免长耗时 POST 请求触发 h2c 协议升级协商。
        .version(HttpClient.Version.HTTP_1_1).followRedirects(HttpClient.Redirect.NEVER).build()

    fun call(provider: NpcLlmConfig.Provider, input: Input): Response {
        if (provider.transport == "mock") {
            val command = input.text.lineSequence().firstOrNull { it.startsWith("CQ_MOCK_") }.orEmpty()
            if (command == "CQ_MOCK_FAIL") error("Fixed mock connection failure")
            val tools = if (command == "CQ_MOCK_PUBLISH" && "publish_quest" in input.tools) listOf(
                Tool("fixed_publish", "publish_quest", JsonParser.parseString("""{"key":"intro","options":{"title":"Mock NPC quest","x":4,"y":5},"tasks":[{"type":"contactquests:parcel_task","data":{"item":"minecraft:diamond","count":2}}],"rewards":[{"type":"contactquests:parcel","data":{"item":"minecraft:apple","count":3}}]}""").asJsonObject)) else emptyList()
            return Response("${input.text}\nhistory=${input.history.size}, model=${input.model}, reasoning=${input.reasoning}\n${input.persona}", tools,
                when {
                    command == "CQ_MOCK_RETURN" -> "return"
                    command == "CQ_MOCK_WITH_REWARDS" -> "withReward"
                    command == "CQ_MOCK_DISCARD" -> "discard"
                    else -> "keep"
                })
        }
        return send(provider, input, messages(input), true)
    }

    fun followUp(provider: NpcLlmConfig.Provider, input: Input, response: Response, results: List<String>): Response {
        require(results.size == response.tools.size)
        if (provider.transport == "mock") return Response(response.text + "\n" + results.joinToString("\n"), emptyList(), response.mailPolicy)
        val messages = messages(input)
        messages.add(JsonObject().apply {
            addProperty("role", "assistant"); addProperty("content", response.text)
            add("tool_calls", JsonArray().apply { response.tools.forEach { tool -> add(JsonObject().apply {
                addProperty("id", tool.id); addProperty("type", "function")
                add("function", JsonObject().apply { addProperty("name", tool.name); addProperty("arguments", tool.arguments.toString()) })
            }) } })
        })
        response.tools.zip(results).forEach { (tool, result) -> messages.add(JsonObject().apply {
            addProperty("role", "tool"); addProperty("tool_call_id", tool.id); addProperty("content", result)
        }) }
        return send(provider, input, messages, false)
    }

    private fun messages(input: Input) = JsonArray().apply {
        add(JsonObject().apply {
            addProperty("role", "system")
            addProperty("content", input.persona + "\nYou are writing a Contact postcard to the player. Your final response MUST be a JSON object with exactly two string fields: " +
                "reply and mail_policy. reply is the exact text that will be written on the outgoing Contact postcard. " +
                "Write a short, natural letter, usually three to six lines; keep internal identifiers and diagnostic metadata out of normal conversation. " +
                "Longer letters are sent on consecutive cards without losing their words. " +
                "mail_policy is mandatory and is your decision about the received items: keep retains them for this NPC's future conversation, " +
                "return sends current and previously kept items back unchanged, withReward stores the current items until this NPC's quest reward is claimed and returns them in the same reward delivery, " +
                "and discard discards the current items. Choose the policy yourself; never omit it. " +
                "Do not invent tool results. Quest tools are restricted by server authorization.")
        })
        input.history.forEach { add(JsonObject().apply { addProperty("role", it.role); addProperty("content", it.text) }) }
        add(JsonObject().apply { addProperty("role", "user"); addProperty("content", input.text) })
    }

    private fun send(provider: NpcLlmConfig.Provider, input: Input, messages: JsonArray, allowTools: Boolean): Response {
        val hasTools = allowTools && input.tools.isNotEmpty()
        val body = JsonObject().apply {
            addProperty("model", input.model); addProperty("max_tokens", provider.maxTokens); add("messages", messages)
            if (!hasTools) add("response_format", JsonObject().apply { addProperty("type", "json_object") })
            if (input.reasoning != "default") addProperty("reasoning_effort", input.reasoning)
            if (hasTools) add("tools", JsonArray().apply { input.tools.sorted().forEach { name -> add(schema(name)) } })
        }
        val key = if (provider.apiKeyEnv.isBlank()) "" else System.getenv(provider.apiKeyEnv) ?: error("Configured API credential is absent")
        val endpoint = provider.endpoint.trimEnd('/').let { if (it.endsWith("/chat/completions")) it else "$it/chat/completions" }
        val builder = HttpRequest.newBuilder(URI(endpoint)).timeout(Duration.ofSeconds(provider.timeoutSeconds.toLong()))
            .header("Content-Type", "application/json")
            // 本机网关会拒绝默认 Java HttpClient User-Agent，显式指定客户端标识以保障稳定连通。
            .header("User-Agent", MAGPIE_USER_AGENT)
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
        if (key.isNotBlank()) builder.header("Authorization", "Bearer $key")
        // 结构化工具规划耗时可能达数十秒；直接在工作线程同步读取完整响应体，使超时涵盖数据传输全过程而非仅等待响应头。
        val result = client.send(builder.build(), boundedBodyHandler(262144))
        val text = String(result.body(), Charsets.UTF_8)
        require(result.statusCode() in 200..299) { "Provider request failed (${result.statusCode()})" }
        val envelope = parseObject(text, "HTTP response")
        val choices = envelope["choices"]?.takeIf { it.isJsonArray }?.asJsonArray
            ?: error("Provider HTTP response has no choices array")
        require(!choices.isEmpty) { "Provider HTTP response has no choices" }
        val message = choices.first().takeIf { it.isJsonObject }?.asJsonObject?.get("message")
            ?.takeIf { it.isJsonObject }?.asJsonObject
            ?: error("Provider HTTP response has no message object")
        val content = message["content"]?.takeUnless { it.isJsonNull }?.asString.orEmpty()
        val tools = message.getAsJsonArray("tool_calls")?.map { raw ->
            val t = raw.takeIf { it.isJsonObject }?.asJsonObject ?: error("Provider tool call must be an object")
            val f = t.getAsJsonObject("function") ?: error("Provider tool call has no function object")
            val arguments = f["arguments"]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                ?: error("Provider tool arguments must be a JSON string")
            Tool(t["id"]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                ?: error("Provider tool call ID must be a string"),
                f["name"]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                    ?: error("Provider tool name must be a string"),
                parseToolArguments(arguments))
        }.orEmpty()
        require(tools.size <= 4 && content.length <= 8192 && (allowTools || tools.isEmpty())) { "Provider output exceeds limit" }
        if (tools.isNotEmpty()) return Response(content, tools)
        val reaction = parseReaction(content)
        require(reaction.keySet().all { it in setOf("reply", "mail_policy") }) { "Unknown NPC response fields" }
        val replyValue = reaction["reply"] ?: error("NPC reply is required")
        require(replyValue.isJsonPrimitive && replyValue.asJsonPrimitive.isString) { "NPC reply must be a string" }
        val reply = replyValue.asString
        require(reply.isNotBlank()) { "NPC postcard reply must not be blank" }
        val policyValue = reaction["mail_policy"] ?: error("NPC mail_policy is required; item disposition must be decided by the NPC")
        require(policyValue.isJsonPrimitive && policyValue.asJsonPrimitive.isString) { "NPC mail_policy must be a string" }
        val policy = normalizeMailPolicy(policyValue.asString)
        require(reply.length <= 2000 && policy in setOf("keep", "return", "withReward", "discard")) { "Invalid NPC reaction" }
        return Response(reply, emptyList(), policy)
    }

    private fun boundedBodyHandler(limit: Int): HttpResponse.BodyHandler<ByteArray> =
        HttpResponse.BodyHandler { _ -> object : HttpResponse.BodySubscriber<ByteArray> {
            private val body = ByteArrayOutputStream()
            private val result = CompletableFuture<ByteArray>()
            private lateinit var subscription: Flow.Subscription

            override fun getBody(): CompletionStage<ByteArray> = result
            override fun onSubscribe(value: Flow.Subscription) {
                subscription = value
                value.request(1)
            }
            override fun onNext(buffers: List<ByteBuffer>) {
                try {
                    for (buffer in buffers) {
                        val size = buffer.remaining()
                        require(body.size() + size <= limit) { "Provider response exceeds limit" }
                        val bytes = ByteArray(size)
                        buffer.get(bytes)
                        body.write(bytes)
                    }
                    subscription.request(1)
                } catch (error: Throwable) {
                    subscription.cancel()
                    result.completeExceptionally(error)
                }
            }
            override fun onError(error: Throwable) { result.completeExceptionally(error) }
            override fun onComplete() { result.complete(body.toByteArray()) }
        } }

    private fun normalizeMailPolicy(policy: String): String = when (policy) {
        "with_rewards", "with_reward" -> "withReward"
        else -> policy
    }

    private fun parseObject(json: String, stage: String): JsonObject = try {
        JsonParser.parseString(json).takeIf { it.isJsonObject }?.asJsonObject
            ?: throw IllegalArgumentException("Provider $stage must be a JSON object")
    } catch (error: RuntimeException) {
        if (error is IllegalArgumentException && error.message == "Provider $stage must be a JSON object") throw error
        throw IllegalArgumentException("Malformed provider $stage", error)
    }

    /** 部分 OpenAI 兼容网关会用一对单引号包裹合法的 JSON 工具参数文本。 */
    private fun parseToolArguments(raw: String): JsonObject {
        val trimmed = raw.trim()
        return try {
            parseObject(trimmed, "tool arguments")
        } catch (direct: IllegalArgumentException) {
            if (trimmed.length >= 2 && trimmed.first() == '\'' && trimmed.last() == '\'') {
                parseObject(trimmed.substring(1, trimmed.length - 1), "tool arguments")
            } else throw direct
        }
    }

    /** 部分本地网关会在 JSON 回信决策外层额外包裹一对单引号。 */
    private fun parseReaction(raw: String): JsonObject {
        val trimmed = raw.trim()
        return try {
            parseObject(trimmed, "NPC reaction")
        } catch (direct: IllegalArgumentException) {
            if (trimmed.length >= 2 && trimmed.first() == '\'' && trimmed.last() == '\'') {
                parseObject(trimmed.substring(1, trimmed.length - 1), "NPC reaction")
            } else if (trimmed.startsWith("```") && trimmed.endsWith("```") && trimmed.length >= 6) {
                val inner = trimmed.indexOf('\n')
                    .let { if (it >= 0) trimmed.substring(it + 1) else trimmed.removePrefix("```") }
                    .removeSuffix("```").trim()
                parseObject(inner, "NPC reaction")
            } else throw direct
        }
    }

    fun schema(name: String): JsonObject = JsonParser.parseString(if (name == "get_quest") """
        {"type":"function","function":{"name":"get_quest","description":"Read a quest published in this conversation.","parameters":{"type":"object","properties":{"key":{"type":"string"}},"required":["key"],"additionalProperties":false}}}
    """ else """
        {"type":"function","function":{"name":"publish_quest","description":"Publish one quest in the configured chapter, scoped to this player or team. Reusing key is idempotent.","parameters":{"type":"object","properties":{"key":{"type":"string"},"options":{"type":"object","properties":{"title":{"type":"string"},"subtitle":{"type":"string"},"description":{"type":"array","items":{"type":"string"}},"x":{"type":"number"},"y":{"type":"number"}},"additionalProperties":false},"tasks":{"type":"array","items":{"type":"object","properties":{"type":{"type":"string"},"data":{"type":"object"}},"required":["type"],"additionalProperties":false}},"rewards":{"type":"array","items":{"type":"object","properties":{"type":{"type":"string"},"data":{"type":"object"}},"required":["type"],"additionalProperties":false}}},"required":["key","tasks","rewards"],"additionalProperties":false}}}
    """).asJsonObject.also { schema ->
        if (name == "publish_quest") {
            val properties = schema.getAsJsonObject("function").getAsJsonObject("parameters").getAsJsonObject("properties")
            for ((field, allowed) in mapOf("tasks" to NpcLlmTools.taskTypes, "rewards" to NpcLlmTools.rewardTypes)) {
                val type = properties.getAsJsonObject(field).getAsJsonObject("items").getAsJsonObject("properties").getAsJsonObject("type")
                type.add("enum", JsonArray().apply { allowed.sorted().forEach { add(it) } })
            }
        }
    }
}
