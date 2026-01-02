package com.creeperyang.contactquests.quest.task

import dev.ftb.mods.ftblibrary.config.ConfigGroup
import dev.ftb.mods.ftblibrary.config.Tristate
import dev.ftb.mods.ftbquests.api.FTBQuestsTags
import dev.ftb.mods.ftbquests.integration.item_filtering.ItemMatchingSystem
import dev.ftb.mods.ftbquests.quest.Quest
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.item.ItemStack
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn

abstract class ItemMatchingTask(id: Long, quest: Quest) : ContactTask(id, quest) {

    var itemStack: ItemStack = ItemStack.EMPTY
    var matchNBT: Tristate = Tristate.DEFAULT
    var weakNBTmatch: Boolean = false

    private val checkNNTItemFilters = TagKey.create(Registries.ITEM, ResourceLocation("itemfilters", "check_nbt"))

    override fun writeData(nbt: CompoundTag) {
        super.writeData(nbt)
        val itemTag = CompoundTag()
        val toSave = itemStack.copy()
        toSave.count = 1
        toSave.save(itemTag)
        nbt.put(getItemNbtKey(), itemTag)

        matchNBT.write(nbt, "match_nbt")
        if (weakNBTmatch) {
            nbt.putBoolean("weak_nbt_match", true)
        }
    }

    override fun readData(nbt: CompoundTag) {
        super.readData(nbt)
        itemStack = if (nbt.contains(getItemNbtKey())) {
            ItemStack.of(nbt.getCompound(getItemNbtKey()))
        } else {
            ItemStack.EMPTY
        }

        matchNBT = Tristate.read(nbt, "match_nbt")
        weakNBTmatch = nbt.getBoolean("weak_nbt_match")
    }

    override fun writeNetData(buffer: FriendlyByteBuf) {
        super.writeNetData(buffer)
        buffer.writeItem(itemStack)
        matchNBT.write(buffer)
        buffer.writeBoolean(weakNBTmatch)
    }

    override fun readNetData(buffer: FriendlyByteBuf) {
        super.readNetData(buffer)
        itemStack = buffer.readItem()
        matchNBT = Tristate.read(buffer)
        weakNBTmatch = buffer.readBoolean()
    }

    @OnlyIn(Dist.CLIENT)
    override fun fillConfigGroup(config: ConfigGroup) {
        super.fillConfigGroup(config)
        config.addItemStack(
            getItemNbtKey(),
            itemStack,
            { v: ItemStack -> itemStack = v },
            ItemStack.EMPTY,
            true,
            false
        ).nameKey =
            getConfigNameKey()

        config.addTristate("match_nbt", matchNBT, { v: Tristate -> matchNBT = v }, Tristate.DEFAULT)
        config.addBool("weak_nbt_match", weakNBTmatch, { v: Boolean -> weakNBTmatch = v }, false)
    }

    override fun getValidDisplayItems(): MutableList<ItemStack> {
        return ItemMatchingSystem.INSTANCE.getAllMatchingStacks(itemStack)
    }

    fun isTargetItem(stack: ItemStack): Boolean {
        if (itemStack.isEmpty) return false
        return ItemMatchingSystem.INSTANCE.doesItemMatch(itemStack, stack, shouldMatchNBT(), weakNBTmatch)
    }

    fun shouldMatchNBT(): Boolean {
        return when (matchNBT) {
            Tristate.TRUE -> true
            Tristate.FALSE -> false
            Tristate.DEFAULT -> hasNBTCheckTag()
        }
    }

    private fun hasNBTCheckTag(): Boolean {
        return itemStack.`is`(FTBQuestsTags.Items.CHECK_NBT)
                || itemStack.`is`(checkNNTItemFilters)
    }

    open fun getItemNbtKey() = "item"
    open fun getConfigNameKey() = "contactquest.task.ftbquests.parcel.valid_for"
}