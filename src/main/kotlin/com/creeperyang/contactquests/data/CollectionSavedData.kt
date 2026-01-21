package com.creeperyang.contactquests.data
import com.creeperyang.contactquests.config.*
import com.flechazo.contact.common.item.PostcardItem
import com.flechazo.contact.resourse.PostcardDataManager
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.saveddata.SavedData
import java.util.*
import java.util.regex.Pattern
import kotlin.random.Random

class CollectionSavedData : SavedData() {

    data class CollectionData(
        val name: String,
        var triggerCount: Int,
        val itemStacks: MutableList<ItemStack>
    )

    private val dataMap: MutableMap<UUID, MutableMap<String, CollectionData>> = Collections.synchronizedMap(HashMap())

    companion object {
        private val LEGACY_UUID = UUID(0L, 0L)

        private val defaultReplacers = mutableListOf<CollectionTextReplacer>()
        private val replacers = mutableListOf<CollectionTextReplacer>()

        private val ITEM_NAME_PATTERN = Pattern.compile("<item_(\\d+)_name>")
        private val ITEM_COUNT_PATTERN = Pattern.compile("<item_(\\d+)_count>")

        fun registerReplacer(replacer: CollectionTextReplacer) {
            replacers.add(replacer)
        }

        private fun registerDefault(replacer: CollectionTextReplacer) {
            defaultReplacers.add(replacer)
        }

        fun reset() {
            replacers.clear()
            replacers.addAll(defaultReplacers)
        }

        init {
            registerDefault { text, context ->
                text.replace("<trigger_count>", context.triggerCount.toString())
                    .replace("<triggerCount>", context.triggerCount.toString())
            }
            registerDefault { text, context ->
                val matcher = ITEM_NAME_PATTERN.matcher(text)
                val sb = StringBuilder()
                while (matcher.find()) {
                    val index = matcher.group(1).toIntOrNull() ?: return@registerDefault null
                    val listIndex = index - 1
                    if (listIndex < 0 || listIndex >= context.itemStacks.size) return@registerDefault null
                    matcher.appendReplacement(sb, context.itemStacks[listIndex].hoverName.string)
                }
                matcher.appendTail(sb)
                sb.toString()
            }
            registerDefault { text, context ->
                val matcher = ITEM_COUNT_PATTERN.matcher(text)
                val sb = StringBuilder()
                while (matcher.find()) {
                    val index = matcher.group(1).toIntOrNull() ?: return@registerDefault null
                    val listIndex = index - 1
                    if (listIndex < 0 || listIndex >= context.itemStacks.size) return@registerDefault null
                    matcher.appendReplacement(sb, context.itemStacks[listIndex].count.toString())
                }
                matcher.appendTail(sb)
                sb.toString()
            }
            registerDefault { text, context ->
                val total = context.itemStacks.sumOf { it.count }
                text.replace("<total_amount>", total.toString())
                    .replace("<total_count>", total.toString())
            }
            registerDefault { text, context ->
                val types = context.itemStacks.size
                text.replace("<total_types>", types.toString())
            }
            reset()
        }

        fun get(level: ServerLevel): CollectionSavedData {
            return level.dataStorage.computeIfAbsent(
                ::load,
                ::CollectionSavedData,
                "contactquests_collection_data"
            )
        }

        private fun load(tag: CompoundTag): CollectionSavedData {
            val data = CollectionSavedData()

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
                        npcMap[name] = CollectionData(name, count, itemStacks)
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
        if (stacks.isEmpty()) return
        val validStacks = stacks.filter { !it.isEmpty }
        if (validStacks.isEmpty()) return

        val uuid = player.uuid
        synchronized(dataMap) {
            val data = getOrCreateData(uuid, name)

            data.triggerCount++
            val currentCount = data.triggerCount
            val solveType = NpcConfigManager.getErrorSolve(name, currentCount)?.returnType

            when (solveType) {
                ErrorSolveType.NOW -> {
                    val itemsToSend = ArrayList(validStacks.map { it.copy() })
                    returnNow(player, name, itemsToSend, currentCount)
                }
                ErrorSolveType.DISCARD -> {
                    returnDiscard(player, name, currentCount)
                }
                else -> {
                    mergeItemsIntoData(data, validStacks)
                    setDirty()
                    returnSave(player, name, currentCount)
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
    private fun processAndDistribute(
        player: ServerPlayer,
        name: String,
        npcData: ErrorSolveData,
        itemsToSend: MutableList<ItemStack>,
        contextItems: List<ItemStack>
    ) {
        val (selectedMsgData, processedText) = selectValidMessage(player, name, npcData, contextItems)
        val finalMessageData = selectedMsgData.copy(text = processedText)
        val isEnder: Boolean = npcData.isAllEnder || finalMessageData.isEnder
        itemsToSend.add(generatePostcard(name, npcData, finalMessageData))
        for (item in itemsToSend) {
            RewardDistributionManager.distribute(player, item, name, isEnder)
        }
    }

    fun returnNow(player: ServerPlayer, name: String, stacks: MutableList<ItemStack>, countOverride: Int? = null) {
        val count = countOverride ?: getTriggerCount(player.uuid, name)
        val npcData = NpcConfigManager.getErrorSolve(name, count) ?: return
        processAndDistribute(player, name, npcData, stacks, stacks)
    }
    fun returnDiscard(
        player: ServerPlayer,
        name: String,
        countOverride: Int? = null,
        contextItems: List<ItemStack> = emptyList()
    ) {
        val count = countOverride ?: (getTriggerCount(player.uuid, name) + 1)
        val npcData = NpcConfigManager.getErrorSolve(name, count) ?: return
        val itemsToSend = mutableListOf<ItemStack>()
        val finalContext = contextItems.ifEmpty { getStacks(player.uuid, name) }
        processAndDistribute(player, name, npcData, itemsToSend, finalContext)
    }

    fun returnSave(player: ServerPlayer, name: String, countOverride: Int? = null) {
        val count = countOverride ?: getTriggerCount(player.uuid, name)
        val npcData = NpcConfigManager.getErrorSolve(name, count) ?: return
        val itemsToSend = mutableListOf<ItemStack>()
        processAndDistribute(player, name, npcData, itemsToSend, getStacks(player.uuid, name))
    }

    fun returnReward(player: ServerPlayer, name: String) {
        val uuid = player.uuid
        val currentCount = getTriggerCount(uuid, name) + 1
        val solveType = NpcConfigManager.getErrorSolve(name, currentCount)?.returnType

        if (solveType == ErrorSolveType.WITHREWARDS) {
            val npcData = NpcConfigManager.getErrorSolve(name, currentCount) ?: return

            val playerMap = dataMap[uuid] ?: return
            val data = playerMap[name] ?: return
            val savedStacks = data.itemStacks

            if (savedStacks.isNotEmpty() && savedStacks.first().isEmpty) {
                savedStacks.removeFirst()
            }
            val itemsToSend = ArrayList(savedStacks)

            val message = NpcConfigManager.getMessage(npcData)
            val isEnder: Boolean = npcData.isAllEnder || message.isEnder
            for (item in itemsToSend) {
                RewardDistributionManager.distribute(player, item, name, isEnder)
            }

            savedStacks.clear()
            setDirty()
        }
    }
    fun generatePostcard(name: String, npcData: ErrorSolveData, messageData: MessageData): ItemStack {
        val styleType = npcData.styleType
        val isEnder = npcData.isAllEnder || messageData.isEnder
        val styleId = when (styleType) {
            StyleType.RANDOM -> {
                val allStyles = PostcardDataManager.getPostcards().keys.map { it.toString() }.sorted().toMutableList()
                if (!allStyles.contains("")) allStyles.add(0, "")
                val num = Random.nextInt(allStyles.size)
                allStyles[num]
            }
            StyleType.SAME -> npcData.style
            StyleType.SPECIFIC -> messageData.style
        }
        val style = if (styleId.isNotEmpty()) ResourceLocation.tryParse(styleId) else null
        var postcard = if (style != null) {
            PostcardItem.getPostcard(style, isEnder)
        } else {
            PostcardItem.getPostcard(ResourceLocation("contact", "default"), isEnder)
        }

        var message = messageData.text.replace("\\n", "\n")
        if (messageData.text.isNotEmpty()) {
            val processedText = message
            postcard = PostcardItem.setText(postcard, processedText)
        }
        if (name.isNotEmpty()) {
            postcard.getOrCreateTag().putString("postcard_sender", name)
        }
        return postcard
    }

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
                    npcList.add(npcTag)
                }
                pTag.put("npcData", npcList)
                playerList.add(pTag)
            }
        }
        tag.put("playerDataMap", playerList)
        return tag
    }

    private fun selectValidMessage(
        player: ServerPlayer,
        name: String,
        npcData: ErrorSolveData,
        contextItems: List<ItemStack>
    ): Pair<MessageData, String> {
        val messages = ArrayList(npcData.message)
        val triggerCount = getTriggerCount(player.uuid, name)
        val context = ReplacerContext(player, name, triggerCount, contextItems)

        while (messages.isNotEmpty()) {
            val pickedMsg = pickWeightedMessage(messages)
            val processedText = applyReplacements(pickedMsg.text, context)
            if (processedText != null) {
                return Pair(pickedMsg, processedText)
            }
            messages.remove(pickedMsg)
        }
        val defaultMsg = MessageData(text = "Error: No valid message", weight = 1)
        return Pair(defaultMsg, defaultMsg.text)
    }

    private fun pickWeightedMessage(messages: List<MessageData>): MessageData {
        if (messages.isEmpty()) return MessageData()
        val totalWeight = messages.sumOf { it.weight }
        if (totalWeight <= 0) return messages.random()
        var randomVal = Random.nextInt(totalWeight)
        for (msg in messages) {
            randomVal -= msg.weight
            if (randomVal < 0) return msg
        }
        return messages.first()
    }

    private fun applyReplacements(text: String, context: ReplacerContext): String? {
        var currentText = text
        for (replacer in replacers) {
            val result = replacer.replace(currentText, context) ?: return null
            currentText = result
        }
        return currentText
    }
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