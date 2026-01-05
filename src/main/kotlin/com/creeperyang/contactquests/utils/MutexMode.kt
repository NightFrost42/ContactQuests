package com.creeperyang.contactquests.utils

import dev.ftb.mods.ftblibrary.config.NameMap

enum class MutexMode {
    ALL, ANY, NUMBER;

    companion object {
        @JvmField
        val NAME_MAP: NameMap<MutexMode?> =
            NameMap.of(ANY, MutexMode.entries.toTypedArray()).create()
    }
}