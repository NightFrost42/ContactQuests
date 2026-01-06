package com.creeperyang.contactquests.quest.task

import com.creeperyang.contactquests.client.gui.ValidRedPacketItemsScreen
import com.creeperyang.contactquests.data.DataManager
import com.flechazo.contact.common.item.RedPacketItem
import dev.ftb.mods.ftblibrary.config.ConfigGroup
import dev.ftb.mods.ftblibrary.util.TooltipList
import dev.ftb.mods.ftbquests.integration.item_filtering.ItemMatchingSystem
import dev.ftb.mods.ftbquests.item.MissingItem
import dev.ftb.mods.ftbquests.quest.Quest
import dev.ftb.mods.ftbquests.quest.TeamData
import dev.ftb.mods.ftbquests.quest.task.TaskType
import net.minecraft.ChatFormatting
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemContainerContents
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn

class RedPacketTask(id: Long, quest: Quest) : ItemMatchingTask(id, quest) {

    var blessing: String = ""

    override fun getType(): TaskType = TaskRegistry.RED_PACKET

    override fun getItemNbtKey() = "inner_item"
    override fun getConfigNameKey() = "contactquest.task.red_packet.item"

    fun setStackAndCount(stack: ItemStack, count: Int): RedPacketTask {
        this.itemStack = stack.copy()
        this.count = count.toLong()
        return this
    }

    override fun writeData(nbt: CompoundTag, provider: HolderLookup.Provider) {
        super.writeData(nbt, provider)
        nbt.putString("blessing", blessing)
    }

    override fun readData(nbt: CompoundTag, provider: HolderLookup.Provider) {
        super.readData(nbt, provider)
        blessing = nbt.getString("blessing")
    }

    override fun writeNetData(buffer: RegistryFriendlyByteBuf) {
        super.writeNetData(buffer)
        buffer.writeUtf(blessing)
    }

    override fun readNetData(buffer: RegistryFriendlyByteBuf) {
        super.readNetData(buffer)
        blessing = buffer.readUtf()
    }

    @OnlyIn(Dist.CLIENT)
    override fun fillConfigGroup(config: ConfigGroup) {
        super.fillConfigGroup(config)
        config.addString("blessing", blessing, { blessing = it }, "").nameKey = "contactquest.task.red_packet.blessing"
    }

    override fun test(stack: ItemStack): Boolean {
        if (stack.isEmpty || stack.item !is RedPacketItem) return false

        if (blessing.isNotEmpty()) {
            val id = ResourceLocation.fromNamespaceAndPath("contact", "red_packet_blessing")
            val compType = BuiltInRegistries.DATA_COMPONENT_TYPE[id]

            val stackBlessing: String? = if (compType != null) {
                stack[compType] as? String
            } else {
                null
            }
            if (stackBlessing != blessing) return false
        }

        if (itemStack.isEmpty) return true

        val contentStack = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
            .stream().findFirst().orElse(ItemStack.EMPTY)

        return ItemMatchingSystem.INSTANCE.doesItemMatch(itemStack, contentStack, matchComponents)
    }

    fun checkContent(stack: ItemStack): Boolean {
        return isTargetItem(stack)
    }

    @OnlyIn(Dist.CLIENT)
    override fun openTaskGui(validItems: MutableList<ItemStack>) {
        ValidRedPacketItemsScreen(this, validItems).openGui()
    }

    fun submitRedPacketTask(teamData: TeamData, player: ServerPlayer, submitItemStack: ItemStack): ItemStack {
        if (teamData.isCompleted(this)){
            DataManager.completeRedPacketTask(this)
            return submitItemStack
        }
        if (itemStack.item is MissingItem || submitItemStack.item is MissingItem) return submitItemStack
        return insert(teamData, submitItemStack, false)
    }

    @OnlyIn(Dist.CLIENT)
    override fun addMouseOverText(list: TooltipList, teamData: TeamData) {
        list.blankLine()
        list.add(
            Component.translatable("contactquest.task.parcel.recipient", targetAddressee).withStyle(ChatFormatting.GRAY)
        )

        super.addMouseOverText(list, teamData)
    }
}