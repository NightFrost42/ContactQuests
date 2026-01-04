package com.creeperyang.contactquests.data

import com.creeperyang.contactquests.config.*
import com.flechazo.contact.common.item.PostcardItem
import com.flechazo.contact.data.PostcardDataManager
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.saveddata.SavedData
import java.util.*
import java.util.regex.Pattern
import kotlin.random.Random

class CollectionSavedData : SavedData() {

    data class ErrorData(
        val name: String,
        var triggerCount: Int,
        val itemStacks: MutableList<ItemStack>
    )

    private val dataMap: MutableMap<String, ErrorData> = Collections.synchronizedMap(HashMap())

    fun addItem(player: ServerPlayer, name: String, stack: ItemStack) {
        addItems(player, name, listOf(stack))
    }

    fun addItems(player: ServerPlayer, name: String, stacks: List<ItemStack>) {
        if (stacks.isEmpty()) return

        val validStacks = stacks.filter { !it.isEmpty }
        if (validStacks.isEmpty()) return

        synchronized(dataMap) {
            val data = dataMap.computeIfAbsent(name) {
                ErrorData(it, 0, ArrayList())
            }

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

    private fun mergeItemsIntoData(data: ErrorData, stacks: List<ItemStack>) {
        for (stack in stacks) {
            var merged = false
            for (existingStack in data.itemStacks) {
                if (ItemStack.isSameItemSameComponents(existingStack, stack)) {
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
        val count = countOverride ?: getTriggerCountByName(name)
        val npcData = NpcConfigManager.getErrorSolve(name, count) ?: return
        processAndDistribute(player, name, npcData, stacks, stacks)
    }

    fun returnDiscard(
        player: ServerPlayer,
        name: String,
        countOverride: Int? = null,
        contextItems: List<ItemStack> = emptyList()
    ) {
        val count = countOverride ?: (getTriggerCountByName(name) + 1)
        val npcData = NpcConfigManager.getErrorSolve(name, count) ?: return
        val itemsToSend = mutableListOf<ItemStack>()
        val finalContext = contextItems.ifEmpty { getStacksByName(name) }
        processAndDistribute(player, name, npcData, itemsToSend, finalContext)
    }

    fun returnSave(player: ServerPlayer, name: String, countOverride: Int? = null) {
        val count = countOverride ?: getTriggerCountByName(name)
        val npcData = NpcConfigManager.getErrorSolve(name, count) ?: return

        val itemsToSend = mutableListOf<ItemStack>()

        processAndDistribute(player, name, npcData, itemsToSend, getStacksByName(name))
    }

    fun returnReward(player: ServerPlayer, name: String) {
        val currentCount = getTriggerCountByName(name) + 1
        val solveType = NpcConfigManager.getErrorSolve(name, currentCount)?.returnType
        if (solveType == ErrorSolveType.WITHREWARDS) {
            val npcData = NpcConfigManager.getErrorSolve(name, currentCount) ?: return

            val savedStacks = getStacksByName(name)
            if (savedStacks.isNotEmpty() && savedStacks.first().isEmpty) {
                savedStacks.removeFirst()
            }

            val itemsToSend = ArrayList(savedStacks)

            val isEnder = npcData.isAllEnder

            for (item in itemsToSend) {
                RewardDistributionManager.distribute(player, item, name, isEnder)
            }

            dataMap[name]?.itemStacks?.clear()
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

            StyleType.SAME -> {
                npcData.style
            }

            StyleType.SPECIFIC -> {
                messageData.style
            }
        }
        val style = if (styleId.isNotEmpty()) ResourceLocation.tryParse(styleId) else null
        var postcard = if (style != null) {
            PostcardItem.getPostcard(style, isEnder)
        } else {
            PostcardItem.getPostcard(ResourceLocation.fromNamespaceAndPath("contact", "default"), isEnder)
        }

        var message = messageData.text.replace("\\n", "\n")
//        message += "\n现在次数：" + getTriggerCountByName(name).toString()
        if (messageData.text.isNotEmpty()) {
            val processedText = message
            postcard = PostcardItem.setText(postcard, processedText)
        }

        if (name.isNotEmpty()) {
            val componentId = ResourceLocation.fromNamespaceAndPath("contact", "postcard_sender")
            val rawComponentType = BuiltInRegistries.DATA_COMPONENT_TYPE[componentId]

            @Suppress("UNCHECKED_CAST")
            val senderComponentType = rawComponentType as? DataComponentType<String>

            if (senderComponentType != null) {
                postcard[senderComponentType] = name
            }
        }

        return postcard
    }

    fun getStacksByName(name: String): MutableList<ItemStack> {
        return dataMap[name]?.itemStacks ?: MutableList(1) { ItemStack.EMPTY }
    }

    fun getTriggerCountByName(name: String): Int {
        return dataMap[name]?.triggerCount ?: 0
    }

    fun setTriggerCount(name: String, count: Int) {
        synchronized(dataMap) {
            val data = dataMap.computeIfAbsent(name) {
                ErrorData(it, 0, ArrayList())
            }
            data.triggerCount = count
            setDirty()
        }
    }

    fun removeData(name: String) {
        synchronized(dataMap) {
            if (dataMap.remove(name) != null) {
                setDirty()
            }
        }
    }

    fun getStoredNpcNames(): Set<String> {
        synchronized(dataMap) {
            return HashSet(dataMap.keys)
        }
    }

    override fun save(tag: CompoundTag, provider: HolderLookup.Provider): CompoundTag {
        val list = ListTag()
        synchronized(dataMap) {
            for (data in dataMap.values) {
                val dataTag = CompoundTag()
                dataTag.putString("name", data.name)
                dataTag.putInt("count", data.triggerCount)

                val itemsTag = ListTag()
                for (stack in data.itemStacks) {
                    itemsTag.add(stack.save(provider))
                }
                dataTag.put("itemStacks", itemsTag)

                list.add(dataTag)
            }
        }
        tag.put("collectionDataList", list)
        return tag
    }

    private fun selectValidMessage(
        player: ServerPlayer,
        name: String,
        npcData: ErrorSolveData,
        contextItems: List<ItemStack>
    ): Pair<MessageData, String> {
        val messages = ArrayList(npcData.message)
        val triggerCount = getTriggerCountByName(name)
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

    companion object {
        private val replacers = mutableListOf<CollectionTextReplacer>()
        private val ITEM_NAME_PATTERN = Pattern.compile("<item_(\\d+)_name>")
        private val ITEM_COUNT_PATTERN = Pattern.compile("<item_(\\d+)_count>")

        fun registerReplacer(replacer: CollectionTextReplacer) {
            replacers.add(replacer)
        }

        init {
            registerReplacer { text, context ->
                text.replace("<trigger_count>", context.triggerCount.toString())
                    .replace("<triggerCount>", context.triggerCount.toString()) // 兼容两种写法
            }

            registerReplacer { text, context ->
                val matcher = ITEM_NAME_PATTERN.matcher(text)
                val sb = StringBuilder()
                while (matcher.find()) {
                    val index = matcher.group(1).toIntOrNull() ?: return@registerReplacer null
                    val listIndex = index - 1
                    if (listIndex < 0 || listIndex >= context.itemStacks.size) {
                        return@registerReplacer null
                    }
                    matcher.appendReplacement(sb, context.itemStacks[listIndex].hoverName.string)
                }
                matcher.appendTail(sb)
                sb.toString()
            }

            registerReplacer { text, context ->
                val matcher = ITEM_COUNT_PATTERN.matcher(text)
                val sb = StringBuilder()
                while (matcher.find()) {
                    val index = matcher.group(1).toIntOrNull() ?: return@registerReplacer null
                    val listIndex = index - 1
                    if (listIndex < 0 || listIndex >= context.itemStacks.size) {
                        return@registerReplacer null
                    }
                    matcher.appendReplacement(sb, context.itemStacks[listIndex].count.toString())
                }
                matcher.appendTail(sb)
                sb.toString()
            }

            registerReplacer { text, context ->
                val total = context.itemStacks.sumOf { it.count }
                text.replace("<total_amount>", total.toString())
                    .replace("<total_count>", total.toString())
            }

            registerReplacer { text, context ->
                val types = context.itemStacks.size
                text.replace("<total_types>", types.toString())
            }
        }

        fun get(level: ServerLevel): CollectionSavedData {
            return level.dataStorage.computeIfAbsent(
                Factory(::CollectionSavedData, ::load, DataFixTypes.LEVEL),
                "contactquests_collection_data"
            )
        }

        private fun load(tag: CompoundTag, provider: HolderLookup.Provider): CollectionSavedData {
            val data = CollectionSavedData()
            val list = tag.getList("collectionDataList", Tag.TAG_COMPOUND.toInt())

            for (i in 0 until list.size) {
                val dataTag = list.getCompound(i)
                val name = dataTag.getString("name")
                val count = dataTag.getInt("count")

                val itemStacks = ArrayList<ItemStack>()
                val itemsListTag = dataTag.getList("itemStacks", Tag.TAG_COMPOUND.toInt())

                for (j in 0 until itemsListTag.size) {
                    val stackTag = itemsListTag.getCompound(j)
                    val stack = ItemStack.parseOptional(provider, stackTag)
                    if (!stack.isEmpty) {
                        itemStacks.add(stack)
                    }
                }

                data.dataMap[name] = ErrorData(name, count, itemStacks)
            }
            return data
        }
    }
}