package com.creeperyang.contactquests.quest

import com.creeperyang.contactquests.ContactQuests
import com.creeperyang.contactquests.bridge.IQuestExtension
import com.creeperyang.contactquests.bridge.ITeamDataExtension
import dev.ftb.mods.ftbquests.quest.ServerQuestFile
import dev.ftb.mods.ftbquests.quest.TeamData
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI
import net.minecraft.server.level.ServerPlayer
import net.neoforged.fml.ModList
import java.util.UUID
import java.util.function.Consumer

/** 仅在服务端发生真实状态变更时触发事件；只读查询、状态快照、网络同步与读盘加载绝不发射。 */
object QuestStateEvents {
    data class State(val tags: List<String>, val mutex: Boolean, val scriptBlocked: Boolean, val startable: Boolean)
    data class Change(val kind: String, val questId: String, val tag: String, val teamId: UUID,
                      val playerId: UUID?, val player: ServerPlayer?, val members: List<UUID>,
                      val before: State, val after: State, val cause: String, val sequence: Long)
    private data class Transaction(var depth: Int, val before: Map<String, State>, val beforeTags: List<String>, val actor: ServerPlayer?, val actorId: UUID?,
                                   val cause: String, val tag: String)
    private val transactions = java.util.IdentityHashMap<TeamData, Transaction>()
    private val actors = ThreadLocal<ServerPlayer?>()
    private val actorIds = ThreadLocal<UUID?>()
    private val causes = ThreadLocal<String?>()
    private val listeners = mutableListOf<Consumer<Change>>()
    private val queue = java.util.ArrayDeque<Change>()
    private var dispatching = false
    private var sequence = 0L

    @JvmStatic fun withActor(player: ServerPlayer?, cause: String, action: Runnable) {
        withIdentity(player?.uuid, player, cause, action)
    }
    @JvmStatic fun withIdentity(playerId: UUID?, player: ServerPlayer?, cause: String, action: Runnable) {
        val oldActor = actors.get(); val oldId = actorIds.get(); val oldCause = causes.get()
        actors.set(player); actorIds.set(playerId); causes.set(cause)
        try { action.run() } finally { actors.set(oldActor); actorIds.set(oldId); causes.set(oldCause) }
    }
    @JvmStatic fun addListener(listener: Consumer<Change>) { listeners.add(listener) }
    @JvmStatic fun removeListener(listener: Consumer<Change>) { listeners.remove(listener) }

    @JvmStatic @JvmOverloads fun begin(team: TeamData, cause: String, tag: String = "") {
        if (team.file !is ServerQuestFile || !(team.file as ServerQuestFile).server.isSameThread) return
        val previous = transactions[team]
        if (previous != null) { previous.depth++; return }
        val file = team.file as ServerQuestFile
        transactions[team] = Transaction(1, snapshot(team), tags(team), actors.get() ?: file.currentPlayer,
            actorIds.get() ?: file.currentPlayer?.uuid,
            causes.get() ?: cause, tag)
    }

    @JvmStatic fun end(team: TeamData) {
        val tx = transactions[team] ?: return
        if (--tx.depth != 0) return
        transactions.remove(team)
        val after = snapshot(team)
        val members = FTBTeamsAPI.api().manager.getTeamByID(team.teamId).orElse(null)?.members.orEmpty().sortedBy { it.toString() }
        fun emit(kind: String, questId: String, tag: String, before: State, now: State) {
            queue.add(Change(kind, questId, tag, team.teamId, tx.actorId, tx.actor, members,
                before, now, tx.cause, ++sequence))
        }
        val beforeTags = tx.beforeTags
        val afterTags = tags(team)
        (afterTags - beforeTags.toSet()).forEach { tag ->
            val matching = (team.file as ServerQuestFile).allChapters.flatMap { it.quests }
                .filter { ((it as Any) as? IQuestExtension)?.`contactQuests$getRequiredTags`()?.contains(tag) == true }
            if (matching.isEmpty()) emit("tagUnlocked", "", tag, State(beforeTags, false, false, false), State(afterTags, false, false, false))
            else matching.forEach { q -> emit("tagUnlocked", q.codeString, tag,
                tx.before[q.codeString] ?: State(beforeTags, false, false, false), after.getValue(q.codeString)) }
        }
        after.forEach { (id, state) ->
            val before = tx.before[id] ?: return@forEach
            if (before.mutex != state.mutex) emit("mutexChanged", id, "", before, state)
            if (before.scriptBlocked != state.scriptBlocked) emit("scriptBlockChanged", id, "", before, state)
        }
        drain()
    }

    private fun snapshot(team: TeamData): Map<String, State> {
        val ext = team as? ITeamDataExtension
        val tags = tags(team)
        return (team.file as ServerQuestFile).allChapters.flatMap { it.quests }.associate { q ->
            q.codeString to State(tags, ((q as Any) as? IQuestExtension)?.`contactQuests$isLockedByMutex`(team) == true,
                ext?.`contactQuests$isQuestBlocked`(q.id) == true, team.canStartTasks(q))
        }
    }
    private fun tags(team: TeamData) = (team as? ITeamDataExtension)?.`contactQuests$getTags`()?.sorted().orEmpty()
    private fun drain() {
        if (dispatching) return
        dispatching = true
        try {
            var count = 0
            while (queue.isNotEmpty()) {
                val change = queue.removeFirst()
                if (++count > 1024) { queue.clear(); ContactQuests.warn("Quest state event chain exceeded 1024 changes"); break }
                listeners.toList().forEach { listener -> runCatching { listener.accept(change) }.onFailure { ContactQuests.warn("Quest state listener failed: %s", it.message) } }
                if (ModList.get().isLoaded("kubejs")) {
                    // 仅在加载 KubeJS 的运行环境下反射解析可选桥接类发送事件。
                    runCatching { com.creeperyang.contactquests.compat.kubejs.QuestStateEventBridge.post(change) }
                        .onFailure { ContactQuests.warn("Quest script event failed: %s", it.message) }
                }
            }
        } finally { dispatching = false }
    }
}
