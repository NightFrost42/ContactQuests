package com.creeperyang.contactquests.compat.kubejs

import com.creeperyang.contactquests.quest.QuestStateEvents
import dev.latvian.mods.kubejs.event.KubeEvent

/** 所有对象 ID 均以完整十六进制字符串暴露（避免 JS 浮点数精度丢失），操作者身份由服务端下发。 */
class QuestStateEventJS(private val change: QuestStateEvents.Change) : KubeEvent {
    val kind get() = change.kind
    val questId get() = change.questId
    val tag get() = change.tag
    val teamId get() = change.teamId.toString()
    val playerId get() = change.playerId?.toString()
    val player get() = change.player
    val members get() = change.members.map { it.toString() }
    val before get() = change.before
    val after get() = change.after
    val cause get() = change.cause
    val sequence get() = change.sequence.toString()
}

object QuestStateEventBridge {
    fun post(change: QuestStateEvents.Change) {
        val event = when (change.kind) {
            "tagUnlocked" -> ContactKubeJSPlugin.TAG_UNLOCKED
            "mutexChanged" -> ContactKubeJSPlugin.MUTEX_CHANGED
            else -> ContactKubeJSPlugin.SCRIPT_BLOCK_CHANGED
        }
        event.post(QuestStateEventJS(change))
    }
}
