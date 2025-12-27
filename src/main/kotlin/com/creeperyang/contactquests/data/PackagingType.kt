package com.creeperyang.contactquests.data

import dev.ftb.mods.ftblibrary.config.NameMap
import net.minecraft.util.StringRepresentable

enum class PackagingType(val id: String) : StringRepresentable {
    PARCEL("parcel");

    override fun getSerializedName(): String = id

    companion object {
        val NAME_MAP: NameMap<PackagingType> = NameMap.of(PARCEL, entries.toTypedArray()).create()
    }
}