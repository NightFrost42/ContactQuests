package com.creeperyang.contactquests.compat.kubejs

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.saveddata.SavedData

class KubeJSNpcSavedData : SavedData() {
    private val npcConfigs: MutableMap<String, NpcConfig> = mutableMapOf()

    data class NpcConfig(var deliveryTime: Int = 0, var autoFill: Boolean = false)

    companion object {
        private const val TAG_NPCS = "npcs"

        fun get(level: ServerLevel): KubeJSNpcSavedData {
            return level.server.overworld().dataStorage.computeIfAbsent(
                Factory(
                    ::KubeJSNpcSavedData,
                    ::load
                ), "contactquests_kubejs_npcs"
            )
        }

        private fun load(tag: CompoundTag, provider: HolderLookup.Provider): KubeJSNpcSavedData {
            val data = KubeJSNpcSavedData()
            val list = tag.getList(TAG_NPCS, Tag.TAG_COMPOUND.toInt())
            for (i in 0 until list.size) {
                val npcTag = list.getCompound(i)
                val name = npcTag.getString("name")
                val config = NpcConfig(
                    deliveryTime = npcTag.getInt("deliveryTime"),
                    autoFill = npcTag.getBoolean("autoFill")
                )
                data.npcConfigs[name] = config
            }
            return data
        }
    }

    override fun save(tag: CompoundTag, provider: HolderLookup.Provider): CompoundTag {
        val list = ListTag()
        npcConfigs.forEach { (name, config) ->
            val npcTag = CompoundTag()
            npcTag.putString("name", name)
            npcTag.putInt("deliveryTime", config.deliveryTime)
            npcTag.putBoolean("autoFill", config.autoFill)
            list.add(npcTag)
        }
        tag.put(TAG_NPCS, list)
        return tag
    }

    fun removeNpcData(name: String) {
        if (npcConfigs.containsKey(name)) {
            npcConfigs.remove(name)
            setDirty()
        }
    }

    fun setNpcData(name: String, deliveryTime: Int?, autoFill: Boolean?) {
        val config = npcConfigs.computeIfAbsent(name) { NpcConfig() }
        if (deliveryTime != null) config.deliveryTime = deliveryTime
        if (autoFill != null) config.autoFill = autoFill
        setDirty()
    }

    fun getNpcData(name: String): NpcConfig? {
        return npcConfigs[name]
    }

    fun getAllNpcs(): Map<String, NpcConfig> {
        return npcConfigs
    }

    fun clearAll() {
        npcConfigs.clear()
        setDirty()
    }
}