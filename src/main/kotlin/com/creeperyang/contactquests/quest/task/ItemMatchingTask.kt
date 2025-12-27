package com.creeperyang.contactquests.quest.task

import dev.ftb.mods.ftblibrary.config.ConfigGroup
import dev.ftb.mods.ftblibrary.math.Bits
import dev.ftb.mods.ftbquests.integration.item_filtering.ItemMatchingSystem
import dev.ftb.mods.ftbquests.integration.item_filtering.ItemMatchingSystem.ComponentMatchType
import dev.ftb.mods.ftbquests.quest.Quest
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.world.item.ItemStack
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn

abstract class ItemMatchingTask(id: Long, quest: Quest) : ContactTask(id, quest) {

    var itemStack: ItemStack = ItemStack.EMPTY
    var matchComponents: ComponentMatchType? = ComponentMatchType.NONE

    override fun writeData(nbt: CompoundTag, provider: HolderLookup.Provider) {
        super.writeData(nbt, provider)
        nbt.put(getItemNbtKey(), saveItemSingleLine(itemStack.copyWithCount(1)))
        if (matchComponents != ComponentMatchType.NONE) {
            nbt.putString("match_components", ComponentMatchType.NAME_MAP.getName(matchComponents))
        }
    }

    override fun readData(nbt: CompoundTag, provider: HolderLookup.Provider) {
        super.readData(nbt, provider)
        itemStack = itemOrMissingFromNBT(nbt[getItemNbtKey()], provider)
        matchComponents = ComponentMatchType.NAME_MAP[nbt.getString("match_components")]
    }

    override fun writeNetData(buffer: RegistryFriendlyByteBuf) {
        super.writeNetData(buffer)
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, itemStack)
        var flags = 0
        flags = Bits.setFlag(flags, 0x20, matchComponents != ComponentMatchType.NONE)
        flags = Bits.setFlag(flags, 0x40, matchComponents == ComponentMatchType.STRICT)
        buffer.writeVarInt(flags)
    }

    override fun readNetData(buffer: RegistryFriendlyByteBuf) {
        super.readNetData(buffer)
        itemStack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer)
        val flags = buffer.readVarInt()
        matchComponents = if (Bits.getFlag(flags, 0x20))
            if (Bits.getFlag(flags, 0x40))
                ComponentMatchType.STRICT else ComponentMatchType.FUZZY
        else ComponentMatchType.NONE
    }

    @OnlyIn(Dist.CLIENT)
    override fun fillConfigGroup(config: ConfigGroup) {
        super.fillConfigGroup(config)
        config.addItemStack("item", itemStack, { v: ItemStack -> itemStack = v }, ItemStack.EMPTY, true, false)
            .nameKey = getConfigNameKey()
        config.addEnum<ComponentMatchType?>("match_components", matchComponents,
            { v: ComponentMatchType? -> matchComponents = v }, ComponentMatchType.NAME_MAP)
    }

    override fun getValidDisplayItems(): MutableList<ItemStack> {
        return ItemMatchingSystem.INSTANCE.getAllMatchingStacks(itemStack)
    }

    fun isTargetItem(stack: ItemStack): Boolean {
        if (itemStack.isEmpty) return false
        return ItemMatchingSystem.INSTANCE.doesItemMatch(itemStack, stack, matchComponents)
    }

    open fun getItemNbtKey() = "item"
    open fun getConfigNameKey() = "contactquest.task.parcel.item"
}