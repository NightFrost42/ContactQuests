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
        itemsToSend: MutableList<ItemStack>
    ) {
        val message = NpcConfigManager.getMessage(npcData)
        val isEnder: Boolean = npcData.isAllEnder || message.isEnder

        itemsToSend.add(generatePostcard(name, npcData, message))

        for (item in itemsToSend) {
            RewardDistributionManager.distribute(player, item, name, isEnder)
        }
    }

    fun returnNow(player: ServerPlayer, name: String, stacks: MutableList<ItemStack>, countOverride: Int? = null) {
        val count = countOverride ?: getTriggerCountByName(name)
        val npcData = NpcConfigManager.getErrorSolve(name, count) ?: return
        processAndDistribute(player, name, npcData, stacks)
    }

    fun returnDiscard(player: ServerPlayer, name: String, countOverride: Int? = null) {
        val count = countOverride ?: (getTriggerCountByName(name) + 1)
        val npcData = NpcConfigManager.getErrorSolve(name, count) ?: return
        val itemsToSend = mutableListOf<ItemStack>()
        processAndDistribute(player, name, npcData, itemsToSend)
    }

    fun returnSave(player: ServerPlayer, name: String, countOverride: Int? = null) {
        val count = countOverride ?: getTriggerCountByName(name)
        val npcData = NpcConfigManager.getErrorSolve(name, count) ?: return

        val itemsToSend = mutableListOf<ItemStack>()

        processAndDistribute(player, name, npcData, itemsToSend)
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

            val message = NpcConfigManager.getMessage(npcData)
            val isEnder: Boolean = npcData.isAllEnder || message.isEnder

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
            PostcardItem.getPostcard(ResourceLocation("contact", "default"), isEnder)
        }

        var message = messageData.text.replace("\\n", "\n")
//        message += "\n现在次数：" + getTriggerCountByName(name).toString()
        if (messageData.text.isNotEmpty()) {
            val processedText = message
            postcard = PostcardItem.setText(postcard, processedText)
        }

        if (name.isNotEmpty()) {
            postcard.getOrCreateTag().putString("Sender", name)
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

    override fun save(tag: CompoundTag): CompoundTag {
        val list = ListTag()
        synchronized(dataMap) {
            for (data in dataMap.values) {
                val dataTag = CompoundTag()
                dataTag.putString("name", data.name)
                dataTag.putInt("count", data.triggerCount)

                val itemsTag = ListTag()
                for (stack in data.itemStacks) {
                    val itemTag = CompoundTag()
                    stack.save(itemTag)
                    itemsTag.add(itemTag)
                }
                dataTag.put("itemStacks", itemsTag)

                list.add(dataTag)
            }
        }
        tag.put("collectionDataList", list)
        return tag
    }

    companion object {
        fun get(level: ServerLevel): CollectionSavedData {
            return level.dataStorage.computeIfAbsent(
                ::load,
                ::CollectionSavedData,
                "contactquests_collection_data"
            )
        }

        private fun load(tag: CompoundTag): CollectionSavedData {
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
                    val stack = ItemStack.of(stackTag)
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