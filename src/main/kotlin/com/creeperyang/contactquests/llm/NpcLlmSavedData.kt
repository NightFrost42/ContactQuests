package com.creeperyang.contactquests.llm

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID

/** 按 NPC、团队与可选玩家维度隔离保存对话历史；挂起工具/回复状态重启后直接续跑，避免重复调用写操作工具。 */
class NpcLlmSavedData : SavedData() {
    data class Turn(val role: String, val text: String)
    data class Mail(val id: UUID, val playerId: UUID, val text: String, val items: List<ItemStack>)
    data class RewardEscrow(val playerId: UUID, val items: MutableList<ItemStack> = mutableListOf())
    data class ToolState(val id: String, val name: String, val arguments: String, var result: String? = null)
    data class Conversation(
        val key: String,
        val npc: String,
        val teamId: UUID,
        val owner: UUID?,
        val turns: MutableList<Turn> = mutableListOf(),
        val published: MutableMap<String, String> = linkedMapOf(),
        var lastTick: Long = Long.MIN_VALUE,
        var pendingId: UUID? = null,
        var pendingPlayer: UUID? = null,
        var pendingText: String = "",
        var replyText: String = "",
        var retryAfterTick: Long = Long.MIN_VALUE,
        val inbox: MutableList<Mail> = mutableListOf(),
        val keptItems: MutableList<ItemStack> = mutableListOf(),
        val rewardItems: MutableList<RewardEscrow> = mutableListOf(),
        var mailPolicy: String = "",
        var resolved: Boolean = false,
        val pendingTools: MutableList<ToolState> = mutableListOf(),
        var toolReplyText: String = "",
        var toolMailPolicy: String = ""
    )
    private val conversations = linkedMapOf<String, Conversation>()

    fun conversation(npc: String, team: UUID, player: UUID, scope: String): Conversation {
        val key = "$npc|$team|${if (scope == "team") "team" else player}"
        return conversations.getOrPut(key) {
            Conversation(key, npc, team, player.takeUnless { scope == "team" }).also { setDirty() }
        }
    }

    fun hasActiveConversation(npc: String, team: UUID, player: UUID, scope: String): Boolean {
        val key = "$npc|$team|${if (scope == "team") "team" else player}"
        val c = conversations[key] ?: return false
        return c.turns.isNotEmpty() || c.inbox.isNotEmpty() || c.pendingId != null ||
            c.published.isNotEmpty() || c.rewardItems.isNotEmpty() || c.keptItems.isNotEmpty()
    }

    fun all(): List<Conversation> = conversations.values.toList()

    fun history(npc: String, team: UUID, player: UUID, scope: String): List<Turn> =
        conversation(npc, team, player, scope).turns.toList()

    fun trim(conversation: Conversation, turns: Int) {
        while (conversation.turns.size > turns * 2) conversation.turns.removeAt(0)
        setDirty()
    }

    override fun save(tag: CompoundTag, provider: HolderLookup.Provider): CompoundTag {
        tag.put("Conversations", ListTag().apply {
            conversations.values.forEach { c ->
                add(CompoundTag().apply {
                    putString("key", c.key)
                    putString("npc", c.npc)
                    putUUID("team", c.teamId)
                    c.owner?.let { putUUID("owner", it) }
                    c.pendingId?.let { putUUID("pending", it) }
                    c.pendingPlayer?.let { putUUID("player", it) }
                    putLong("last_tick", c.lastTick)
                    putString("pending_text", c.pendingText)
                    putString("reply", c.replyText)
                    putLong("retry_after_tick", c.retryAfterTick)
                    putString("mail_policy", c.mailPolicy)
                    putBoolean("resolved", c.resolved)
                    putString("tool_reply", c.toolReplyText)
                    putString("tool_mail_policy", c.toolMailPolicy)
                    put("pending_tools", ListTag().apply {
                        c.pendingTools.forEach { tool ->
                            add(CompoundTag().apply {
                                putString("id", tool.id)
                                putString("name", tool.name)
                                putString("arguments", tool.arguments)
                                tool.result?.let { putString("result", it) }
                            })
                        }
                    })
                    put("inbox", ListTag().apply {
                        c.inbox.forEach { mail ->
                            add(CompoundTag().apply {
                                putUUID("id", mail.id)
                                putUUID("player", mail.playerId)
                                putString("text", mail.text)
                                put("items", ListTag().apply { mail.items.forEach { add(it.save(provider)) } })
                            })
                        }
                    })
                    put("kept_items", ListTag().apply { c.keptItems.forEach { add(it.save(provider)) } })
                    put("reward_items", ListTag().apply {
                        c.rewardItems.forEach { escrow ->
                            add(CompoundTag().apply {
                                putUUID("player", escrow.playerId)
                                put("items", ListTag().apply { escrow.items.forEach { add(it.save(provider)) } })
                            })
                        }
                    })
                    put("turns", ListTag().apply {
                        c.turns.forEach { turn ->
                            add(CompoundTag().apply {
                                putString("role", turn.role)
                                putString("text", turn.text)
                            })
                        }
                    })
                    put("published", CompoundTag().apply {
                        c.published.forEach { (key, id) -> putString(key, id) }
                    })
                })
            }
        })
        return tag
    }

    companion object {
        fun get(level: ServerLevel): NpcLlmSavedData = level.server.overworld().dataStorage.computeIfAbsent(
            Factory(::NpcLlmSavedData, ::load),
            "contactquests_npc_llm"
        )

        fun load(tag: CompoundTag, provider: HolderLookup.Provider): NpcLlmSavedData = NpcLlmSavedData().apply {
            tag.getList("Conversations", Tag.TAG_COMPOUND.toInt()).forEach { raw ->
                val t = raw as CompoundTag
                runCatching {
                    val c = Conversation(
                        t.getString("key"),
                        t.getString("npc"),
                        t.getUUID("team"),
                        if (t.hasUUID("owner")) t.getUUID("owner") else null
                    )
                    t.getList("turns", Tag.TAG_COMPOUND.toInt()).forEach {
                        c.turns.add(Turn((it as CompoundTag).getString("role"), it.getString("text")))
                    }
                    t.getCompound("published").let { p ->
                        p.allKeys.forEach { c.published[it] = p.getString(it) }
                    }
                    c.lastTick = t.getLong("last_tick")
                    c.pendingText = t.getString("pending_text")
                    c.replyText = t.getString("reply")
                    c.retryAfterTick =
                        if (t.contains("retry_after_tick")) t.getLong("retry_after_tick") else Long.MIN_VALUE
                    c.mailPolicy = t.getString("mail_policy")
                    c.resolved = t.getBoolean("resolved")
                    c.toolReplyText = t.getString("tool_reply")
                    c.toolMailPolicy = t.getString("tool_mail_policy")
                    t.getList("pending_tools", Tag.TAG_COMPOUND.toInt()).forEach { rawTool ->
                        val tool = rawTool as CompoundTag
                        c.pendingTools.add(ToolState(
                            tool.getString("id"),
                            tool.getString("name"),
                            tool.getString("arguments"),
                            if (tool.contains("result")) tool.getString("result") else null
                        ))
                    }
                    t.getList("inbox", Tag.TAG_COMPOUND.toInt()).forEach { rawMail ->
                        val mail = rawMail as CompoundTag
                        c.inbox.add(Mail(
                            mail.getUUID("id"),
                            mail.getUUID("player"),
                            mail.getString("text"),
                            mail.getList("items", Tag.TAG_COMPOUND.toInt())
                                .map { ItemStack.parseOptional(provider, it as CompoundTag) }
                                .filter { !it.isEmpty }
                        ))
                    }
                    t.getList("kept_items", Tag.TAG_COMPOUND.toInt()).forEach {
                        c.keptItems.add(ItemStack.parseOptional(provider, it as CompoundTag))
                    }
                    t.getList("reward_items", Tag.TAG_COMPOUND.toInt()).forEach { rawEscrow ->
                        val escrow = rawEscrow as CompoundTag
                        if (escrow.hasUUID("player")) {
                            val items = escrow.getList("items", Tag.TAG_COMPOUND.toInt()).map {
                                ItemStack.parseOptional(provider, it as CompoundTag)
                            }.filter { !it.isEmpty }.toMutableList()
                            if (items.isNotEmpty()) c.rewardItems.add(RewardEscrow(escrow.getUUID("player"), items))
                        }
                    }
                    if (t.hasUUID("pending")) c.pendingId = t.getUUID("pending")
                    if (t.hasUUID("player")) c.pendingPlayer = t.getUUID("player")
                    conversations[c.key] = c
                }
            }
        }
    }
}
