package com.creeperyang.contactquests.config

import com.creeperyang.contactquests.ContactQuests
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.neoforged.fml.loading.FMLPaths
import java.io.File
import java.io.FileReader
import java.io.FileWriter

data class NpcData(
    var deliveryTime: Int = 0
)

object NpcConfigManager {
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()
    private val CONFIG_DIR: File = FMLPaths.CONFIGDIR.get().resolve("contactquests").toFile()
    private val CONFIG_FILE: File = CONFIG_DIR.resolve("npc_config.json")

    private val npcMap: MutableMap<String, NpcData> = mutableMapOf()

    fun initFile() {
        if (!CONFIG_DIR.exists()) CONFIG_DIR.mkdirs()

        if (CONFIG_FILE.exists()) {
            try {
                FileReader(CONFIG_FILE).use { reader ->
                    val type = object : TypeToken<Map<String, NpcData>>() {}.type
                    val loaded: Map<String, NpcData>? = GSON.fromJson(reader, type)
                    if (loaded != null) {
                        npcMap.clear()
                        npcMap.putAll(loaded)
                    }
                }
                ContactQuests.info("Initialized NPC Config from disk.")
            } catch (e: Exception) {
                ContactQuests.error("Failed to read npc_config.json", e)
            }
        } else {
            save()
            ContactQuests.info("Created empty NPC Config file.")
        }
    }

    fun syncWithQuests(allNpcNames: Set<String>) {
        var hasChanges = false

        for (name in allNpcNames) {
            if (!npcMap.containsKey(name)) {
                npcMap[name] = NpcData(deliveryTime = 0)
                hasChanges = true
            }
        }

        if (hasChanges) {
            save()
            ContactQuests.info("Synced new NPCs to config.")
        }
    }

    fun getNpcData(name: String): NpcData {
        return npcMap.getOrDefault(name, NpcData())
    }

    fun getDeliveryTime(name: String): Int {
        return getNpcData(name).deliveryTime
    }

    private fun save() {
        try {
            FileWriter(CONFIG_FILE).use { writer ->
                GSON.toJson(npcMap, writer)
            }
        } catch (e: Exception) {
            ContactQuests.error("Failed to save npc_config.json", e)
        }
    }
}