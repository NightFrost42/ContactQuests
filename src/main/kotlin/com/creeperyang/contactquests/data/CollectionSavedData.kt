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
import kotlin.random.Random

class CollectionSavedData : SavedData() {

    data class ErrorData(
        val name: String,
        var triggerCount: Int,
        val itemStacks: MutableList<ItemStack>
    )

    private val dataMap: MutableMap<String, ErrorData> = Collections.synchronizedMap(HashMap())

    fun addItem(player: ServerPlayer, name: String, stack: ItemStack) {
        if (stack.isEmpty) return
        val solveType = NpcConfigManager.getErrorSolve(name, getTriggerCountByName(name) + 1)?.returnType
        when {
            solveType == ErrorSolveType.NOW -> {
                returnNow(player, name, stack)
                return
            }

            solveType != ErrorSolveType.DISCARD -> {
                returnDiscard(player, name)
                return
            }

            else -> synchronized(dataMap) {
                val data = dataMap.computeIfAbsent(name) {
                    ErrorData(it, 0, ArrayList())
                }

                data.triggerCount++

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

                setDirty()
            }
        }
    }

    fun returnNow(player: ServerPlayer, name: String, stack: ItemStack) {
        val npcData = NpcConfigManager.getErrorSolve(name, getTriggerCountByName(name) + 1) ?: return
        val message = NpcConfigManager.getMessage(npcData)
        val isEnder: Boolean = npcData.isAllEnder || message.isEnder
        val stacks = getStacksByName(name)
        stacks.add(stack.copy())
        if (stacks.first() == ItemStack.EMPTY) {
            stacks.removeFirst()
        }
        stacks.add(generatePostcard(name, npcData, message))
        for (item in stacks) {
            RewardDistributionManager.distribute(player, item, name, isEnder)
        }
        dataMap[name]?.itemStacks?.clear()
    }

    fun returnDiscard(player: ServerPlayer, name: String) {
        val npcData = NpcConfigManager.getErrorSolve(name, getTriggerCountByName(name)) ?: return
        val message = NpcConfigManager.getMessage(npcData)
        val isEnder: Boolean = npcData.isAllEnder || message.isEnder
        val stack = generatePostcard(name, npcData, message)
        RewardDistributionManager.distribute(player, stack, name, isEnder)
    }

    fun returnReward(player: ServerPlayer, name: String) {
        val solveType = NpcConfigManager.getErrorSolve(name, getTriggerCountByName(name) + 1)?.returnType
        if (solveType == ErrorSolveType.WITHREWARDS) {
            val npcData = NpcConfigManager.getErrorSolve(name, getTriggerCountByName(name)) ?: return
            val message = NpcConfigManager.getMessage(npcData)
            val isEnder = npcData.isAllEnder || message.isEnder
            val stacks = getStacksByName(name)
            if (stacks.first() == ItemStack.EMPTY) {
                stacks.removeFirst()
            }
            stacks.add(generatePostcard(name, npcData, message))
            for (item in stacks) {
                RewardDistributionManager.distribute(player, item, name, isEnder)
            }
            dataMap[name]?.itemStacks?.clear()
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
        if (messageData.text.isNotEmpty()) {
            val processedText = messageData.text.replace("\\n", "\n")
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

    fun removeData(name: String) {
        synchronized(dataMap) {
            if (dataMap.remove(name) != null) {
                setDirty()
            }
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

    companion object {
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