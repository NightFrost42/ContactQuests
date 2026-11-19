package com.creeperyang.contactquests.quest

import dev.ftb.mods.ftblibrary.config.NameMap

/** 互斥任务组触发锁定时的计数/范围判定模式（全量锁定、任一锁定或按数量上限锁定）。 */
enum class MutexMode {
    ALL, ANY, NUMBER;

    companion object {
        // 保留既有本地化键前缀，避免破坏既有翻译资源映射
        @JvmField
        val NAME_MAP: NameMap<MutexMode?> =
            NameMap.of(ANY, entries.toTypedArray())
                .baseNameKey("contactquests.utils.mutexmode")
                .create()
    }
}
