package com.creeperyang.contactquests.data

import com.creeperyang.contactquests.config.ErrorSolveType
import com.creeperyang.contactquests.config.MessageData
import com.creeperyang.contactquests.config.NpcConfigManager
import com.creeperyang.contactquests.llm.NpcLlmConfig
import com.creeperyang.contactquests.llm.NpcLlmService
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.saveddata.SavedData
import java.util.Collections
import java.util.UUID

/** 管理按玩家与 NPC 隔离的暂存物品列表、交互触发次数、旧维度存档迁移与超量/退回策略分发。 */
class CollectionSavedData : SavedData() {

    data class CollectionData(
        val name: String,
        var triggerCount: Int,
        val itemStacks: MutableList<ItemStack>,
        var lastPostcardStyle: String = ""
    )

    private val dataMap: MutableMap<UUID, MutableMap<String, CollectionData>> = Collections.synchronizedMap(HashMap())
    private val migratedDimensions = mutableSetOf<String>()

    companion object {
        private val LEGACY_UUID = UUID(0L, 0L)

        // 保留在 Companion 上以便生成与历史版本一致的静态字段，供测试框架直接镜像与恢复占位符列表。
        private val defaultReplacers = mutableListOf<CollectionTextReplacer>()
        private val replacers = mutableListOf<CollectionTextReplacer>()

        init {
            defaultReplacers.addAll(CollectionReplyComposer.buildDefaultReplacers())
            reset()
        }

        fun registerReplacer(replacer: CollectionTextReplacer) {
            replacers.add(replacer)
        }

        fun reset() {
            replacers.clear()
            replacers.addAll(defaultReplacers)
        }

        internal fun currentReplacers(): List<CollectionTextReplacer> = replacers

        fun get(level: ServerLevel): CollectionSavedData {
            val overworld = level.server.overworld()
            val global = overworld.dataStorage.computeIfAbsent(
                ::load, ::CollectionSavedData,
                "contactquests_collection_data"
            )
            if (level !== overworld) {
                val dimension = level.dimension().location().toString()
                if (dimension !in global.migratedDimensions) {
                    // 早期版本曾按维度分开持久化玩家暂存物，此处在首次访问非主世界时惰性合并并保留旧文件兜底。
                    val legacy = level.dataStorage.get(::load, "contactquests_collection_data")
                    if (legacy != null && legacy !== global) global.mergeLegacy(legacy)
                    global.migratedDimensions.add(dimension)
                    global.setDirty()
                }
            }
            return global
        }

        private fun load(tag: CompoundTag): CollectionSavedData {
            val data = CollectionSavedData()
            tag.getList("MigratedDimensions", Tag.TAG_STRING.toInt()).forEach {
                data.migratedDimensions.add(it.asString)
            }

            if (tag.contains("playerDataMap")) {
                val playerList = tag.getList("playerDataMap", Tag.TAG_COMPOUND.toInt())
                for (i in 0 until playerList.size) {
                    val pTag = playerList.getCompound(i)
                    val uuid = pTag.getUUID("uuid")
                    val npcList = pTag.getList("npcData", Tag.TAG_COMPOUND.toInt())

                    val npcMap = Collections.synchronizedMap(HashMap<String, CollectionData>())
                    for (j in 0 until npcList.size) {
                        val npcTag = npcList.getCompound(j)
                        val name = npcTag.getString("name")
                        val count = npcTag.getInt("count")
                        val itemStacks = ArrayList<ItemStack>()
                        val itemsTag = npcTag.getList("itemStacks", Tag.TAG_COMPOUND.toInt())
                        for (k in 0 until itemsTag.size) {
                            val stack = ItemStack.of(itemsTag.getCompound(k))
                            if (!stack.isEmpty) itemStacks.add(stack)
                        }
                        val lastStyle = if (npcTag.contains("lastPostcardStyle")) npcTag.getString("lastPostcardStyle") else ""
                        npcMap[name] = CollectionData(name, count, itemStacks, lastStyle)
                    }
                    data.dataMap[uuid] = npcMap
                }
            }

            if (tag.contains("collectionDataList")) {
                val list = tag.getList("collectionDataList", Tag.TAG_COMPOUND.toInt())
                val legacyMap = data.dataMap.computeIfAbsent(LEGACY_UUID) { Collections.synchronizedMap(HashMap()) }

                for (i in 0 until list.size) {
                    val dataTag = list.getCompound(i)
                    val name = dataTag.getString("name")
                    val count = dataTag.getInt("count")
                    val itemStacks = ArrayList<ItemStack>()
                    val itemsListTag = dataTag.getList("itemStacks", Tag.TAG_COMPOUND.toInt())
                    for (j in 0 until itemsListTag.size) {
                        val stackTag = itemsListTag.getCompound(j)
                        val stack = ItemStack.of(stackTag)
                        if (!stack.isEmpty) itemStacks.add(stack)
                    }
                    legacyMap.putIfAbsent(name, CollectionData(name, count, itemStacks))
                }
            }

            return data
        }
    }

    private fun mergeLegacy(legacy: CollectionSavedData) {
        synchronized(dataMap) {
            legacy.dataMap.forEach { (uuid, npcMap) ->
                npcMap.forEach { (name, old) ->
                    val current = getOrCreateData(uuid, name)
                    current.triggerCount = (current.triggerCount.toLong() + old.triggerCount)
                        .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    mergeItemsIntoData(current, old.itemStacks)
                }
            }
        }
    }

    private fun getOrCreateData(uuid: UUID, name: String): CollectionData {
        val playerMap = dataMap.computeIfAbsent(uuid) { Collections.synchronizedMap(HashMap()) }

        return playerMap.computeIfAbsent(name) {
            val legacyData = dataMap[LEGACY_UUID]?.get(name)
            if (legacyData != null) {
                CollectionData(name, legacyData.triggerCount, ArrayList(legacyData.itemStacks.map { it.copy() }))
            } else {
                CollectionData(name, 0, ArrayList())
            }
        }
    }

    fun addItem(player: ServerPlayer, name: String, stack: ItemStack) {
        addItems(player, name, listOf(stack))
    }

    fun addItems(player: ServerPlayer, name: String, stacks: List<ItemStack>) {
        if (NpcLlmConfig.npc(name) != null) {
            NpcLlmService.receive(player, name, stacks)
            return
        }
        if (stacks.isEmpty()) return
        val validStacks = stacks.filter { !it.isEmpty }
        if (validStacks.isEmpty()) return

        val uuid = player.uuid
        synchronized(dataMap) {
            val data = getOrCreateData(uuid, name)

            data.triggerCount++
            // 立即退回与丢弃策略也会递增持久化交互次数，用于后续阶梯提示配置匹配。
            setDirty()
            val currentCount = data.triggerCount
            val incomingStyle = validStacks.firstNotNullOfOrNull { CollectionReplyComposer.extractPostcardStyle(it) }
            val solveType = NpcConfigManager.getErrorSolve(name, currentCount)?.returnType

            when (solveType) {
                ErrorSolveType.NOW -> {
                    val itemsToSend = ArrayList(validStacks.map { it.copy() })
                    returnNow(player, name, itemsToSend, currentCount, incomingStyle)
                }
                ErrorSolveType.DISCARD -> {
                    returnDiscard(player, name, currentCount, validStacks, incomingStyle)
                }
                ErrorSolveType.WITHREWARDS -> {
                    if (!incomingStyle.isNullOrEmpty()) {
                        data.lastPostcardStyle = incomingStyle
                    }
                    mergeItemsIntoData(data, validStacks)
                    setDirty()
                }
                else -> {
                    if (!incomingStyle.isNullOrEmpty()) {
                        data.lastPostcardStyle = incomingStyle
                    }
                    mergeItemsIntoData(data, validStacks)
                    setDirty()
                    returnSave(player, name, currentCount, incomingStyle)
                }
            }
        }
    }

    private fun mergeItemsIntoData(data: CollectionData, stacks: List<ItemStack>) {
        for (stack in stacks) {
            var merged = false
            for (existingStack in data.itemStacks) {
                if (ItemStack.isSameItemSameTags(existingStack, stack)) {
                    existingStack.grow(stack.count)
                    merged = true
                    break
                }
            }
            if (!merged) {
                data.itemStacks.add(stack.copy())
            }
        }
    }

    @JvmOverloads
    fun returnNow(
        player: ServerPlayer,
        name: String,
        stacks: MutableList<ItemStack>,
        countOverride: Int? = null,
        incomingStyle: String? = null
    ) {
        if (NpcLlmConfig.npc(name) != null) return
        val count = countOverride ?: getTriggerCount(player.uuid, name)
        val npcData = NpcConfigManager.getErrorSolve(name, count) ?: return
        CollectionReplyComposer.processAndDistribute(this, player, name, npcData, stacks, stacks, incomingStyle)
    }

    @JvmOverloads
    fun returnDiscard(
        player: ServerPlayer,
        name: String,
        countOverride: Int? = null,
        contextItems: List<ItemStack> = emptyList(),
        incomingStyle: String? = null
    ) {
        if (NpcLlmConfig.npc(name) != null) return
        val count = countOverride ?: (getTriggerCount(player.uuid, name) + 1)
        val npcData = NpcConfigManager.getErrorSolve(name, count) ?: return
        val itemsToSend = mutableListOf<ItemStack>()
        val finalContext = contextItems.ifEmpty { getStacks(player.uuid, name) }
        CollectionReplyComposer.processAndDistribute(this, player, name, npcData, itemsToSend, finalContext, incomingStyle)
    }

    @JvmOverloads
    fun returnSave(
        player: ServerPlayer,
        name: String,
        countOverride: Int? = null,
        incomingStyle: String? = null
    ) {
        if (NpcLlmConfig.npc(name) != null) return
        val count = countOverride ?: getTriggerCount(player.uuid, name)
        val npcData = NpcConfigManager.getErrorSolve(name, count) ?: return
        val itemsToSend = mutableListOf<ItemStack>()
        CollectionReplyComposer.processAndDistribute(this, player, name, npcData, itemsToSend, getStacks(player.uuid, name), incomingStyle)
    }

    @JvmOverloads
    fun returnReward(player: ServerPlayer, name: String, isEnder: Boolean = false) {
        if (NpcLlmConfig.npc(name) != null) {
            NpcLlmService.returnForReward(player, name, isEnder)
            return
        }
        val uuid = player.uuid
        synchronized(dataMap) {
            val playerMap = dataMap[uuid] ?: return
            val data = playerMap[name] ?: return
            val validSaved = data.itemStacks.filter { !it.isEmpty }
            if (validSaved.isEmpty()) return

            val currentCount = getTriggerCount(uuid, name)
            if (currentCount <= 0) return
            val npcData = NpcConfigManager.getErrorSolve(name, currentCount) ?: return
            if (npcData.returnType != ErrorSolveType.WITHREWARDS) return

            val itemsToSend = ArrayList(validSaved.map { it.copy() })
            CollectionReplyComposer.processAndDistribute(
                this, player, name, npcData, itemsToSend, itemsToSend, data.lastPostcardStyle, isEnder
            )

            data.itemStacks.clear()
            data.lastPostcardStyle = ""
            setDirty()
        }
    }

    @JvmOverloads
    fun generatePostcard(
        name: String,
        npcData: com.creeperyang.contactquests.config.ErrorSolveData,
        messageData: MessageData,
        incomingStyle: String? = null
    ): ItemStack = CollectionReplyComposer.generatePostcard(name, npcData, messageData, incomingStyle)

    fun getStacks(uuid: UUID, name: String): MutableList<ItemStack> {
        val pData = dataMap[uuid]?.get(name)
        if (pData != null) return pData.itemStacks

        val lData = dataMap[LEGACY_UUID]?.get(name)
        return lData?.itemStacks?.map { it.copy() }?.toMutableList() ?: MutableList(1) { ItemStack.EMPTY }
    }

    fun setStacks(uuid: UUID, name: String, stacks: List<ItemStack>) {
        synchronized(dataMap) {
            val data = getOrCreateData(uuid, name)
            data.itemStacks.clear()
            val validStacks = stacks.filter { !it.isEmpty }
            for (stack in validStacks) {
                data.itemStacks.add(stack.copy())
            }
            setDirty()
        }
    }

    fun addItemsSilent(player: ServerPlayer, name: String, stacks: List<ItemStack>) {
        if (stacks.isEmpty()) return
        val validStacks = stacks.filter { !it.isEmpty }
        if (validStacks.isEmpty()) return

        val uuid = player.uuid
        synchronized(dataMap) {
            val data = getOrCreateData(uuid, name)
            mergeItemsIntoData(data, validStacks)
            setDirty()
        }
    }

    fun getTriggerCount(uuid: UUID, name: String): Int {
        val pData = dataMap[uuid]?.get(name)
        if (pData != null) return pData.triggerCount

        val lData = dataMap[LEGACY_UUID]?.get(name)
        return lData?.triggerCount ?: 0
    }

    fun getTriggerCountByName(name: String): Int {
        return dataMap[LEGACY_UUID]?.get(name)?.triggerCount ?: 0
    }

    fun setTriggerCount(uuid: UUID, name: String, count: Int) {
        synchronized(dataMap) {
            val data = getOrCreateData(uuid, name)
            data.triggerCount = count
            setDirty()
        }
    }

    fun removeData(uuid: UUID, name: String) {
        synchronized(dataMap) {
            dataMap[uuid]?.remove(name)
            setDirty()
        }
    }

    fun removeDataAll(name: String) {
        synchronized(dataMap) {
            dataMap.values.forEach { it.remove(name) }
            setDirty()
        }
    }

    fun getStoredNpcNames(): Set<String> {
        synchronized(dataMap) {
            return dataMap.values.flatMap { it.keys }.toSet()
        }
    }

    override fun save(tag: CompoundTag): CompoundTag {
        val playerList = ListTag()
        synchronized(dataMap) {
            for ((uuid, npcMap) in dataMap) {
                val pTag = CompoundTag()
                pTag.putUUID("uuid", uuid)

                val npcList = ListTag()
                for (data in npcMap.values) {
                    val npcTag = CompoundTag()
                    npcTag.putString("name", data.name)
                    npcTag.putInt("count", data.triggerCount)

                    val itemsTag = ListTag()
                    for (stack in data.itemStacks) {
                        itemsTag.add(stack.save(CompoundTag()))
                    }
                    npcTag.put("itemStacks", itemsTag)
                    if (data.lastPostcardStyle.isNotEmpty()) {
                        npcTag.putString("lastPostcardStyle", data.lastPostcardStyle)
                    }
                    npcList.add(npcTag)
                }
                pTag.put("npcData", npcList)
                playerList.add(pTag)
            }
        }
        tag.put("playerDataMap", playerList)
        val dimensions = ListTag()
        migratedDimensions.forEach { dimensions.add(StringTag.valueOf(it)) }
        tag.put("MigratedDimensions", dimensions)
        return tag
    }

    // 保留此私有入口以兼容测试模块通过反射直接验证内置占位符替换逻辑
    private fun applyReplacements(text: String, context: ReplacerContext): String? =
        CollectionReplyComposer.applyReplacements(text, context, replacers)

    data class ReplacerContext(
        val player: ServerPlayer,
        val name: String,
        val triggerCount: Int,
        val itemStacks: List<ItemStack>
    )

    fun interface CollectionTextReplacer {
        fun replace(text: String, context: ReplacerContext): String?
    }
}
