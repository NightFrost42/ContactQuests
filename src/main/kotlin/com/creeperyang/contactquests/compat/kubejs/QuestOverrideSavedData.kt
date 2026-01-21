package com.creeperyang.contactquests.compat.kubejs

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.saveddata.SavedData


class QuestOverrideSavedData : SavedData() {
    val overrides = HashMap<Long, CompoundTag>()


    companion object {
        private const val TAG_OVERRIDES = "QuestOverrides"


        fun get(level: ServerLevel): QuestOverrideSavedData {
            return level.server.overworld().dataStorage.computeIfAbsent(
                { tag -> load(tag) },
                { QuestOverrideSavedData() },
                "contactquests_quest_overrides"
            )
        }


        private fun load(tag: CompoundTag): QuestOverrideSavedData {
            val data = QuestOverrideSavedData()
            val list = tag.getList(TAG_OVERRIDES, Tag.TAG_COMPOUND.toInt())
            for (i in 0 until list.size) {
                val entry = list.getCompound(i)
                val id = entry.getLong("id")
                val overrideTag = entry.getCompound("data")
                data.overrides[id] = overrideTag
            }
            return data
        }
    }


    override fun save(tag: CompoundTag): CompoundTag {
        val list = ListTag()
        for ((id, overrideTag) in overrides) {
            val entry = CompoundTag()
            entry.putLong("id", id)
            entry.put("data", overrideTag)
            list.add(entry)
        }
        tag.put(TAG_OVERRIDES, list)
        return tag
    }


    fun setOverride(id: Long, key: String, value: Any) {
        val tag = overrides.computeIfAbsent(id) { CompoundTag() }


        when (value) {
            is Int -> tag.putInt(key, value)
            is Long -> tag.putLong(key, value)
            is Double -> tag.putDouble(key, value)
            is String -> tag.putString(key, value)
            is Boolean -> tag.putBoolean(key, value)
            is ItemStack -> {
                val itemTag = CompoundTag()
                value.save(itemTag)
                tag.put(key, itemTag)
            }

            is Collection<*> -> {
                val listTag = ListTag()
                value.forEach { item ->
                    if (item != null) {
                        listTag.add(StringTag.valueOf(item.toString()))
                    }
                }
                tag.put(key, listTag)
            }


            is Map<*, *> -> {
                val mapTag = CompoundTag()
                value.forEach { (k, v) ->
                    if (k is String) {
                        if (v is Collection<*>) {
                            val listTag = ListTag()
                            v.forEach { item -> if (item != null) listTag.add(StringTag.valueOf(item.toString())) }
                            mapTag.put(k, listTag)
                        } else if (v is String) {
                            mapTag.putString(k, v)
                        }
                    }
                }
                tag.put(key, mapTag)
            }
        }
        setDirty()
    }


    fun getOverride(id: Long): CompoundTag? {
        return overrides[id]
    }


    fun clearOverride(id: Long, key: String? = null) {
        if (key == null) {
            overrides.remove(id)
        } else {
            overrides[id]?.remove(key)
            if (overrides[id]?.isEmpty == true) {
                overrides.remove(id)
            }
        }
        setDirty()
    }
}