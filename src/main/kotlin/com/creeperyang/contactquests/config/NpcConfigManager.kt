package com.creeperyang.contactquests.config

import com.creeperyang.contactquests.ContactQuests
import com.creeperyang.contactquests.compat.kubejs.KubeJSNpcSavedData
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.neoforged.fml.loading.FMLPaths
import java.io.File
import kotlin.random.Random

enum class ErrorSolveType {
    @SerializedName("NOW")
    NOW,

    @SerializedName("Save")
    SAVE,

    @SerializedName("WithReward")
    WITHREWARDS,

    @SerializedName("Discard")
    DISCARD;
}

enum class StyleType {
    @SerializedName("Random")
    RANDOM,

    @SerializedName("Specific")
    SPECIFIC,

    @SerializedName("Same")
    SAME;
}

data class MessageData(
    var text: String = "Hello World!",
    var style: String = "default",
    var isEnder: Boolean = false,
    var weight: Int = 1
)

data class ErrorSolveData(
    var count: Int = 1,

    var returnType: ErrorSolveType = ErrorSolveType.NOW,

    var styleType: StyleType = StyleType.RANDOM,

    var style: String = "default",

    var isAllEnder: Boolean = false,

    var message: MutableList<MessageData> = mutableListOf(MessageData())
)

data class NpcData(
    var deliveryTime: Int = 0,
    var errorSolve: MutableList<ErrorSolveData> = mutableListOf(ErrorSolveData())
)

object NpcConfigManager {
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()
    private val CONFIG_DIR: File = FMLPaths.CONFIGDIR.get().resolve("contactquests").toFile()
    private val CONFIG_FILE: File = CONFIG_DIR.resolve("npc_config.json")

    private val npcMap: MutableMap<String, NpcData> = mutableMapOf()

    fun initFile() {
        if (!CONFIG_DIR.exists()) CONFIG_DIR.mkdirs()
        loadFromDisk()
    }

    fun reload() {
        loadFromDisk()
        ContactQuests.info("从硬盘重载NPC数据")
    }

    private fun loadFromDisk() {
        if (CONFIG_FILE.exists()) {
            try {
                CONFIG_FILE.reader(Charsets.UTF_8).use { reader ->
                    val type = object : TypeToken<Map<String, NpcData>>() {}.type
                    val loaded: Map<String, NpcData>? = GSON.fromJson(reader, type)
                    if (loaded != null) {
                        npcMap.clear()
                        npcMap.putAll(loaded)
                    }
                }
            } catch (e: Exception) {
                ContactQuests.error("读取npc_config.json失败，可能是JSON结构不匹配", e)
            }
        } else {
            save()
        }
    }

    fun syncWithQuests(
        parcelNpcs: Set<String>,
        redPacketNpcs: Set<String> = emptySet(),
        postcardNpcs: Set<String> = emptySet(),
        rewardNpcs: Set<String> = emptySet()
    ) {
        var hasChanges = false
        val allNpcs = parcelNpcs + redPacketNpcs + postcardNpcs + rewardNpcs

        for (name in allNpcs) {
            if (!npcMap.containsKey(name)) {
                npcMap[name] = NpcData(deliveryTime = 0)
                hasChanges = true
            }
        }

        if (hasChanges) {
            save()
            ContactQuests.info("同步新NPC到配置文件")
        }
    }

    fun getAllNpcNames(): Set<String> {
        return npcMap.keys
    }

    fun getNpcData(name: String): NpcData {
        return npcMap.getOrDefault(name, NpcData())
    }

    fun getDeliveryTime(name: String, level: Level? = null): Int {
        if (level is ServerLevel) {
            val kubeConfig = KubeJSNpcSavedData.get(level).getNpcData(name)
            if (kubeConfig != null) {
                return kubeConfig.deliveryTime
            }
        }
        return getNpcData(name).deliveryTime
    }

    fun getErrorSolve(name: String, limit: Int): ErrorSolveData? {
        val data = getNpcData(name)
        return data.errorSolve
            .filter { it.count <= limit }
            .maxByOrNull { it.count }
    }

    fun getMessage(data: ErrorSolveData): MessageData {
        if (data.message.isEmpty()) return MessageData()

        val totalWeight = data.message.sumOf { it.weight }
        if (totalWeight <= 0) return data.message.random()

        var randomNum = Random.nextInt(totalWeight)
        for (msg in data.message) {
            randomNum -= msg.weight
            if (randomNum < 0) return msg
        }
        return data.message.last()
    }

    private fun save() {
        try {
            CONFIG_FILE.writer(Charsets.UTF_8).use { writer ->
                GSON.toJson(npcMap, writer)
            }
        } catch (e: Exception) {
            ContactQuests.error("保存npc_config.json失败", e)
        }
    }
}