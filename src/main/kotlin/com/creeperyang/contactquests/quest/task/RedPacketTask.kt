package com.creeperyang.contactquests.quest.task

import com.creeperyang.contactquests.client.gui.ValidRedPacketItemsScreen
import com.creeperyang.contactquests.data.DataManager
import com.flechazo.contact.common.item.RedPacketItem
import dev.ftb.mods.ftblibrary.config.ConfigGroup
import dev.ftb.mods.ftbquests.integration.item_filtering.ItemMatchingSystem
import dev.ftb.mods.ftbquests.item.MissingItem
import dev.ftb.mods.ftbquests.quest.Quest
import dev.ftb.mods.ftbquests.quest.TeamData
import dev.ftb.mods.ftbquests.quest.task.TaskType
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn

class RedPacketTask(id: Long, quest: Quest) : ItemMatchingTask(id, quest) {

    var blessing: String = ""

    override fun getType(): TaskType = TaskRegistry.RED_PACKET

    override fun getItemNbtKey() = "inner_item"
    override fun getConfigNameKey() = "contactquest.task.ftbquests.redpacket.valid_for"

    fun setStackAndCount(stack: ItemStack, count: Int): RedPacketTask {
        this.itemStack = stack.copy()
        this.count = count.toLong()
        return this
    }

    override fun writeData(nbt: CompoundTag) {
        super.writeData(nbt)
        nbt.putString("blessing", blessing)
    }

    override fun readData(nbt: CompoundTag) {
        super.readData(nbt)
        blessing = nbt.getString("blessing")
    }

    override fun writeNetData(buffer: FriendlyByteBuf) {
        super.writeNetData(buffer)
        buffer.writeUtf(blessing)
    }

    override fun readNetData(buffer: FriendlyByteBuf) {
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
            val tag = stack.tag ?: return false

            val stackBlessing = when {
                tag.contains("red_packet_blessing") -> tag.getString("red_packet_blessing")
                tag.contains("blessing") -> tag.getString("blessing")
                tag.contains("blessings") -> tag.getString("blessings")
                else -> ""
            }

            if (stackBlessing != blessing) return false
        }

        if (itemStack.isEmpty) return true

        val contentStack = getFirstItemFromRedPacket(stack)

        return ItemMatchingSystem.INSTANCE.doesItemMatch(itemStack, contentStack, shouldMatchNBT(), weakNBTmatch)
    }

    private fun getFirstItemFromRedPacket(stack: ItemStack): ItemStack {
        val tag = stack.tag ?: return ItemStack.EMPTY

        if (tag.contains("parcel", Tag.TAG_LIST.toInt())) {
            val items = tag.getList("parcel", Tag.TAG_COMPOUND.toInt())
            if (items.isNotEmpty()) {
                return ItemStack.of(items.getCompound(0))
            }
        }

        val tagListType = Tag.TAG_LIST.toInt()
        val tagCompoundType = Tag.TAG_COMPOUND.toInt()

        if (tag.contains("Items", tagListType)) {
            val items = tag.getList("Items", tagCompoundType)
            if (items.isNotEmpty()) {
                return ItemStack.of(items.getCompound(0))
            }
        } else if (tag.contains("Item", tagCompoundType)) {
            return ItemStack.of(tag.getCompound("Item"))
        }

        return ItemStack.EMPTY
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
        val contentStack = getFirstItemFromRedPacket(submitItemStack)
        return insert(teamData, contentStack, false, needTest = false)
    }
}