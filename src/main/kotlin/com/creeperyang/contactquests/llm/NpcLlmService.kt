package com.creeperyang.contactquests.llm

import com.creeperyang.contactquests.ContactQuests
import com.creeperyang.contactquests.data.RewardDistributionManager
import com.flechazo.contact.common.component.ContactDataComponents
import com.flechazo.contact.common.item.PostcardItem
import com.flechazo.contact.common.item.ParcelItem
import com.flechazo.contact.common.item.RedPacketItem
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.component.ItemContainerContents
import com.google.gson.JsonParser
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** 已启用的 LLM NPC 全权接管自身交互反馈；即便模型调用失败也绝不回退到旧版 NPC 错信规则。 */
object NpcLlmService {
    private val executor = ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(64),
        { task -> Thread(task, "ContactQuests-NPC-LLM").apply { isDaemon = true } }, ThreadPoolExecutor.AbortPolicy())
    private val running = ConcurrentHashMap<String, UUID>()

    /** 将整封来信（含真实明信片正文与剩余附件）加入待处理收件箱。 */
    fun receive(player: ServerPlayer, recipient: String, stacks: List<ItemStack>): List<ItemStack> {
        val npc = NpcLlmConfig.npc(recipient) ?: return stacks
        check(player.server.isSameThread)
        val team = FTBTeamsAPI.api().manager.getTeamForPlayer(player).orElse(null) ?: return stacks
        val valid = stacks.filter { !it.isEmpty }.map { it.copy() }
        if (valid.isEmpty()) return emptyList()
        val store = NpcLlmSavedData.get(player.serverLevel())
        val c = store.conversation(recipient, team.id, player.uuid, npc.scope)
        val text = valid.filter { it.item is PostcardItem }.mapNotNull { it[ContactDataComponents.POSTCARD_TEXT.get()] }
            .filter { it.isNotBlank() }.joinToString("\n").take(2000)
        val attachments = valid.filter { it.item !is PostcardItem }.joinToString(", ", transform = ::describe).take(1000)
        val input = (text + if (attachments.isEmpty()) "" else "\n[Attachments: $attachments]").ifBlank { "[Blank postcard]" }.take(2000)
        enqueue(player, c, npc, store, input, valid)
        return emptyList()
    }

    private fun enqueue(player: ServerPlayer, c: NpcLlmSavedData.Conversation, npc: NpcLlmConfig.Npc,
                        store: NpcLlmSavedData, input: String, valid: List<ItemStack>) {
        if (c.inbox.size >= 64 || store.all().sumOf { it.inbox.size } >= 2048) {
            // 触发背压时原样退回未受理信件，不额外合成 NPC 假回复。
            valid.forEach { RewardDistributionManager.distributeTo(player.uuid, c.teamId, it, c.npc, false, true) }
            RewardDistributionManager.flush(player.serverLevel())
            player.sendSystemMessage(Component.translatable("contactquests.llm.queue_full"))
            return
        }
        c.inbox.add(NpcLlmSavedData.Mail(UUID.randomUUID(), player.uuid, input, valid))
        store.setDirty(); pump(player.serverLevel(), c, npc, store)
    }

    fun submit(player: ServerPlayer, npcName: String, text: String): Boolean {
        if (NpcLlmConfig.npc(npcName) == null || text.isBlank() || text.length > 2000) return false
        receive(player, npcName, listOf(PostcardItem.setText(
            PostcardItem.getPostcard(ResourceLocation.parse("contact:default"), false), text)))
        return true
    }

    /** 前序任务物品分配已经完成，向模型暴露真实的消耗与退回明细而不再经由旧版规则路由。 */
    fun receiveAfterTasks(player: ServerPlayer, recipient: String, originals: List<ItemStack>, remaining: List<ItemStack>) {
        val consumed = originals.sumOf(::units) - remaining.sumOf(::units)
        if (consumed <= 0) { receive(player, recipient, remaining); return }
        val originalText = originals.filter { it.item is PostcardItem }.joinToString("\n") { it[ContactDataComponents.POSTCARD_TEXT.get()].orEmpty() }
        val attachments = remaining.filter { it.item !is PostcardItem }.joinToString(", ", transform = ::describe).take(1000)
        val receipt = "[Task submission: $consumed units accepted]" + (if (originalText.isBlank()) "" else "\n$originalText") +
            (if (attachments.isBlank()) "" else "\n[Remaining attachments: $attachments]")
        val npc = NpcLlmConfig.npc(recipient) ?: return
        val team = FTBTeamsAPI.api().manager.getTeamForPlayer(player).orElse(null) ?: return
        val store = NpcLlmSavedData.get(player.serverLevel()); val c = store.conversation(recipient, team.id, player.uuid, npc.scope)
        enqueue(player, c, npc, store, receipt.take(2000), remaining.filter { !it.isEmpty }.map { it.copy() })
    }

    fun tick(level: ServerLevel) {
        val store = NpcLlmSavedData.get(level)
        store.all().forEach { c -> NpcLlmConfig.npc(c.npc)?.let { pump(level, c, it, store) } }
    }

    /** 仅当玩家真实领取了同一 NPC 的随奖附带物品时，才将 AI 标记为随奖返还的寄存物品一并寄出。 */
    fun returnForReward(player: ServerPlayer, npcName: String, isEnder: Boolean) {
        check(player.server.isSameThread)
        val teamId = FTBTeamsAPI.api().manager.getTeamForPlayer(player).orElse(null)?.id ?: return
        val store = NpcLlmSavedData.get(player.serverLevel())
        val conversations = store.all().filter { conversation ->
            conversation.npc == npcName && conversation.teamId == teamId &&
                (conversation.owner == null || conversation.owner == player.uuid) &&
                conversation.rewardItems.any { it.playerId == player.uuid && it.items.isNotEmpty() }
        }
        if (conversations.isEmpty()) return

        conversations.forEach { conversation ->
            val escrow = conversation.rewardItems.filter { it.playerId == player.uuid }
            escrow.forEach { entry -> entry.items.forEach { stack ->
                val preserveSender = stack.item is PostcardItem || stack.item is RedPacketItem || stack.item is ParcelItem
                RewardDistributionManager.distributeTo(player.uuid, teamId, stack, npcName, isEnder, preserveSender)
            } }
            conversation.rewardItems.removeIf { it.playerId == player.uuid }
        }
        store.setDirty()
        // 领奖物品已在进入本钩子前写入发奖缓冲区，此处立即刷盘入队以合并同批邮件。
        RewardDistributionManager.flush(player.serverLevel())
    }

    private fun pump(level: ServerLevel, c: NpcLlmSavedData.Conversation, npc: NpcLlmConfig.Npc, store: NpcLlmSavedData) {
        if (c.pendingId != null) {
            if (c.resolved) {
                if (level.gameTime >= c.retryAfterTick) deliver(level, c, store)
            }
            else if (level.gameTime >= c.retryAfterTick && !running.containsKey(runtimeKey(level, c))) start(level, c, npc)
            return
        }
        val mail = c.inbox.firstOrNull() ?: return
        if (c.lastTick != Long.MIN_VALUE && level.gameTime - c.lastTick in 0 until npc.cooldownTicks.toLong()) return
        c.pendingId = mail.id; c.pendingPlayer = mail.playerId; c.pendingText = mail.text
        c.replyText = ""; c.resolved = false; c.mailPolicy = ""; c.lastTick = level.gameTime
        c.pendingTools.clear(); c.toolReplyText = ""; c.toolMailPolicy = ""
        store.setDirty(); start(level, c, npc)
    }

    private fun start(level: ServerLevel, c: NpcLlmSavedData.Conversation, npc: NpcLlmConfig.Npc) {
        val provider = NpcLlmConfig.current().providers[npc.provider] ?: return
        val id = c.pendingId ?: return
        val key = runtimeKey(level, c)
        if (running.putIfAbsent(key, id) != null) return
        val kept = c.keptItems.joinToString(", ", transform = ::describe).take(2000)
        val input = NpcLlmTransport.Input(c.npc, npc.persona, npc.model.ifBlank { provider.model },
            npc.reasoning.ifBlank { provider.reasoning }, c.turns.toList(), c.pendingText +
                (if (kept.isBlank()) "" else "\n[Previously kept mail: $kept]"), npc.tools.intersect(provider.tools.toSet()))
        if (c.pendingTools.isNotEmpty()) {
            executeAndFollowUp(level, c, npc, provider, input, key, id)
            return
        }
        try {
            CompletableFuture.supplyAsync({ NpcLlmTransport.call(provider, input) }, executor).whenComplete { response, error ->
                level.server.execute responseWork@{
                    if (!level.server.isRunning || c.pendingId != id) { running.remove(key, id); return@responseWork }
                    if (error != null) { fail(level, c, npc, key, id, error); return@responseWork }
                    if (!authorized(c, npc, provider)) { running.remove(key, id); return@responseWork }
                    if (response.tools.isEmpty()) { complete(level, c, npc, provider, response, key, id); return@responseWork }
                    c.toolReplyText = response.text; c.toolMailPolicy = response.mailPolicy.orEmpty()
                    response.tools.forEach { c.pendingTools.add(NpcLlmSavedData.ToolState(it.id, it.name, it.arguments.toString())) }
                    // 在修改任何任务图谱前先将工具调用计划落盘，确保崩溃重启后按原工具参数/结果继续。
                    NpcLlmSavedData.get(level).setDirty(); level.server.overworld().dataStorage.save()
                    executeAndFollowUp(level, c, npc, provider, input, key, id)
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            running.remove(key, id) // 队列已持久化，待工作线程池空闲时自动重试
        }
    }

    private fun executeAndFollowUp(level: ServerLevel, c: NpcLlmSavedData.Conversation, npc: NpcLlmConfig.Npc,
                                   provider: NpcLlmConfig.Provider, input: NpcLlmTransport.Input, key: String, id: UUID) {
        if (!authorized(c, npc, provider)) { running.remove(key, id); return }
        val player = c.pendingPlayer?.let { level.server.playerList.getPlayer(it) }
        val originalTeam = player?.let { FTBTeamsAPI.api().manager.getTeamForPlayer(it).orElse(null)?.id } == c.teamId
        if (player == null || !originalTeam) { running.remove(key, id); return }
        val tools = runCatching { c.pendingTools.map { NpcLlmTransport.Tool(it.id, it.name, JsonParser.parseString(it.arguments).asJsonObject) } }
            .getOrElse { fail(level, c, npc, key, id, it); return }
        c.pendingTools.zip(tools).forEach { (state, tool) -> if (state.result == null) {
            state.result = runCatching {
                NpcLlmTools.execute(level, player, c, npc, provider, tool)
            }.fold({ it }, { "rejected: ${it.message.orEmpty().take(240)}" })
            NpcLlmSavedData.get(level).setDirty(); level.server.overworld().dataStorage.save()
        } }
        val results = c.pendingTools.map { it.result.orEmpty() }
        val response = NpcLlmTransport.Response(c.toolReplyText, tools, c.toolMailPolicy.takeUnless { it.isBlank() })
        try {
            CompletableFuture.supplyAsync({ NpcLlmTransport.followUp(provider, input, response, results) }, executor)
                .whenComplete { finalResponse, finalError -> level.server.execute followupWork@{
                    if (!level.server.isRunning || c.pendingId != id) { running.remove(key, id); return@followupWork }
                    if (finalError != null) fail(level, c, npc, key, id, finalError)
                    else complete(level, c, npc, provider, finalResponse, key, id)
                } }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            running.remove(key, id) // 工具结果已持久化，仅需在空闲时重试最终回复轮次
        }
    }

    private fun authorized(c: NpcLlmSavedData.Conversation, npc: NpcLlmConfig.Npc, provider: NpcLlmConfig.Provider) =
        NpcLlmConfig.npc(c.npc) == npc && NpcLlmConfig.current().providers[npc.provider] == provider

    private fun fail(level: ServerLevel, c: NpcLlmSavedData.Conversation, npc: NpcLlmConfig.Npc, key: String, id: UUID,
                     cause: Throwable? = null) {
        running.remove(key, id)
        val detail = cause?.let { error ->
            val chain = generateSequence(error) { it.cause }.toList()
            val useful = chain.firstOrNull { it is IllegalArgumentException && !it.message.isNullOrBlank() }
                ?: chain.last()
            " (${useful::class.simpleName}: ${useful.message.orEmpty().take(400)})"
        }.orEmpty()
        ContactQuests.warn("NPC provider request failed for %s%s", c.npc, detail)
        // 未得到服务方决策时不生成伪回复也不处置物品，保留原始待处理信封等待重试。
        c.replyText = ""; c.resolved = false
        c.retryAfterTick = level.gameTime + maxOf(20, npc.cooldownTicks.toLong())
        c.pendingPlayer?.let { level.server.playerList.getPlayer(it) }?.sendSystemMessage(Component.translatable("contactquests.llm.connection_failed"))
        NpcLlmSavedData.get(level).setDirty()
    }

    private fun complete(level: ServerLevel, c: NpcLlmSavedData.Conversation, npc: NpcLlmConfig.Npc,
                         provider: NpcLlmConfig.Provider, response: NpcLlmTransport.Response, key: String, id: UUID) {
        running.remove(key, id)
        if (!authorized(c, npc, provider)) return
        c.replyText = response.text.take(2000); c.mailPolicy = response.mailPolicy ?: run {
            fail(level, c, npc, key, id, IllegalArgumentException("NPC response did not decide item disposition"))
            return
        }; c.resolved = true
        c.retryAfterTick = Long.MIN_VALUE
        c.turns.add(NpcLlmSavedData.Turn("user", c.pendingText))
        if (c.replyText.isNotBlank()) c.turns.add(NpcLlmSavedData.Turn("assistant", c.replyText))
        val store = NpcLlmSavedData.get(level); store.trim(c, npc.historyTurns); deliver(level, c, store)
    }

    private fun deliver(level: ServerLevel, c: NpcLlmSavedData.Conversation, store: NpcLlmSavedData) {
        if (c.mailPolicy !in setOf("keep", "return", "withReward", "with_rewards", "with_reward", "discard")) {
            c.resolved = false
            c.retryAfterTick = level.gameTime + 20
            store.setDirty()
            return
        }
        val playerId = c.pendingPlayer ?: return
        val mail = c.inbox.firstOrNull { it.id == c.pendingId }
        if (c.mailPolicy in setOf("withReward", "with_rewards", "with_reward") &&
            c.rewardItems.sumOf { it.items.size } + mail?.items.orEmpty().size > 256) {
            // 寄存仓库暂时满载时原封保留已受理信件与模型决策：后续玩家领取对应奖励会释放配额，绝不擅自把 withReward 改判为立即退回。
            return
        }
        val keptPlan = if (c.mailPolicy == "keep") planKeptItems(c.keptItems, mail?.items.orEmpty()) else null
        if (c.mailPolicy == "keep" && keptPlan == null) {
            // 寄存容量上限不得篡改模型做出的收下决定；预先整体校验整封来信容量，防止重试时重复合并其中物品。
            if (c.retryAfterTick == Long.MIN_VALUE) {
                level.server.playerList.getPlayer(playerId)?.sendSystemMessage(Component.translatable("contactquests.llm.storage_full"))
            }
            c.retryAfterTick = level.gameTime + 20
            store.setDirty()
            return
        }
        when (c.mailPolicy) {
            "return" -> {
                (c.keptItems + mail?.items.orEmpty()).forEach { RewardDistributionManager.distributeTo(playerId, c.teamId, it, c.npc, false, true) }
                c.keptItems.clear()
            }
            "discard" -> Unit
            "withReward", "with_rewards", "with_reward" -> mail?.items?.forEach { stack ->
                val escrow = c.rewardItems.firstOrNull { it.playerId == mail.playerId }
                    ?: NpcLlmSavedData.RewardEscrow(mail.playerId).also(c.rewardItems::add)
                escrow.items.add(stack.copy())
            }
            else -> {
                c.keptItems.clear()
                c.keptItems.addAll(checkNotNull(keptPlan))
            }
        }
        if (c.replyText.isNotBlank()) {
            NpcPostcardLetters.create(c.npc, c.replyText).forEach { card ->
                RewardDistributionManager.distributeTo(playerId, c.teamId, card, c.npc, false)
            }
        }
        RewardDistributionManager.flush(level)
        c.inbox.removeIf { it.id == c.pendingId }
        c.pendingId = null; c.pendingPlayer = null; c.pendingText = ""; c.replyText = ""; c.resolved = false
        c.retryAfterTick = Long.MIN_VALUE
        c.pendingTools.clear(); c.toolReplyText = ""; c.toolMailPolicy = ""
        store.setDirty()
    }

    private fun planKeptItems(kept: List<ItemStack>, incoming: List<ItemStack>): List<ItemStack>? {
        val result = kept.map { it.copy() }.toMutableList()
        incoming.forEach { stack ->
            val remaining = stack.copy()
            result.forEach { stored -> if (!remaining.isEmpty && ItemStack.isSameItemSameComponents(stored, remaining)) {
                val count = minOf(remaining.count, (stored.maxStackSize - stored.count).coerceAtLeast(0))
                stored.grow(count); remaining.shrink(count)
            } }
            if (!remaining.isEmpty) {
                if (result.size >= 256) return null
                result.add(remaining)
            }
        }
        return result
    }

    private fun runtimeKey(level: ServerLevel, c: NpcLlmSavedData.Conversation) = "${System.identityHashCode(level.server)}:${c.key}"
    private fun units(stack: ItemStack): Long = if (stack.item is RedPacketItem) {
        stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItems().sumOf { it.count.toLong() }
    } else stack.count.toLong()
    private fun describe(stack: ItemStack): String {
        val contents = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItems()
            .joinToString(", ") { "${BuiltInRegistries.ITEM.getKey(it.item)} x${it.count}" }
        val text = if (stack.item is PostcardItem) stack[ContactDataComponents.POSTCARD_TEXT.get()].orEmpty().take(240) else
            stack[ContactDataComponents.RED_PACKET_BLESSING.get()].orEmpty().take(240)
        return "${BuiltInRegistries.ITEM.getKey(stack.item)} x${stack.count}" +
            (if (contents.isBlank()) "" else " [$contents]") + (if (text.isBlank()) "" else " text=$text")
    }
}
