package com.creeperyang.contactquests.quest

import dev.ftb.mods.ftblibrary.config.NameMap

/** 任务在多条邮寄前置或子任务条件下的判定模式（全部满足或任一满足）。 */
enum class DependencyMode {
    ALL, ANY;

    companion object {
        // 保留既有本地化键前缀，避免破坏任务编辑器与历史配置文本映射
        @JvmField
        val NAME_MAP: NameMap<DependencyMode> = NameMap.of(ALL, entries.toTypedArray())
            .baseNameKey("contactquests.utils.dependencymode")
            .create()
    }
}
