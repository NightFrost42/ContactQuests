package com.creeperyang.contactquests.task

import com.creeperyang.contactquests.client.gui.ValidParcelItemsScreen
import dev.ftb.mods.ftblibrary.config.ConfigGroup
import dev.ftb.mods.ftblibrary.icon.Icon
import dev.ftb.mods.ftblibrary.icon.IconAnimation
import dev.ftb.mods.ftblibrary.icon.ItemIcon
import dev.ftb.mods.ftblibrary.ui.Button
import dev.ftb.mods.ftblibrary.util.TooltipList
import dev.ftb.mods.ftbquests.FTBQuests
import dev.ftb.mods.ftbquests.client.FTBQuestsClient
import dev.ftb.mods.ftbquests.client.gui.CustomToast
import dev.ftb.mods.ftbquests.events.QuestProgressEventData
import dev.ftb.mods.ftbquests.integration.item_filtering.ItemMatchingSystem
import dev.ftb.mods.ftbquests.quest.Quest
import dev.ftb.mods.ftbquests.quest.TeamData
import dev.ftb.mods.ftbquests.quest.task.Task
import dev.ftb.mods.ftbquests.quest.task.TaskType
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import java.util.function.Consumer
import kotlin.math.max

class ParcelTask(id: Long, quest: Quest) : Task(id, quest) {

    var targetAddressee: String = "Quest NPC"
    var itemStack: ItemStack = ItemStack.EMPTY
    var count: Long = 1
    var returnPostcardStyleId: ResourceLocation? = null

    override fun getType(): TaskType = TaskRegistry.PARCEL_TASK

    override fun getMaxProgress(): Long {
        return count
    }

    fun setStackAndCount(stack: ItemStack, count: Int): ParcelTask {
        itemStack = stack.copy()
        this.count = count.toLong()
        return this
    }

    override fun writeData(nbt: CompoundTag, provider: HolderLookup.Provider) {
        super.writeData(nbt, provider)
        nbt.putString("TargetAddressee", targetAddressee)
        returnPostcardStyleId?.let { nbt.putString("ReturnStyle", it.toString()) }
        nbt.put("item", saveItemSingleLine(itemStack.copyWithCount(1)))
        if (count > 1) {
            nbt.putLong("count", count)
        }
    }

    override fun readData(nbt: CompoundTag, provider: HolderLookup.Provider) {
        super.readData(nbt, provider)
        targetAddressee = nbt.getString("TargetAddressee")
        if (nbt.contains("ReturnStyle")) {
            returnPostcardStyleId = ResourceLocation.tryParse(nbt.getString("ReturnStyle"))
        }
        itemStack = itemOrMissingFromNBT(nbt.get("item"), provider)
        count = max(nbt.getLong("count"), 1L)
    }

    override fun writeNetData(buffer: RegistryFriendlyByteBuf) {
        super.writeNetData(buffer)
        buffer.writeUtf(targetAddressee)
        buffer.writeBoolean(returnPostcardStyleId != null)
        returnPostcardStyleId?.let { buffer.writeResourceLocation(it) }
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, itemStack)
        buffer.writeVarLong(count)
    }

    override fun readNetData(buffer: RegistryFriendlyByteBuf) {
        super.readNetData(buffer)
        targetAddressee = buffer.readUtf()
        if (buffer.readBoolean()) returnPostcardStyleId = buffer.readResourceLocation()
        itemStack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer)
        count = buffer.readVarLong()
    }

    fun getValidDisplayItems(): MutableList<ItemStack> {
        return ItemMatchingSystem.INSTANCE.getAllMatchingStacks(itemStack)
    }

    @OnlyIn(Dist.CLIENT)
    override fun fillConfigGroup(config: ConfigGroup) {
        super.fillConfigGroup(config)
        config.addString("target_addressee", targetAddressee, { targetAddressee = it }, "Quest NPC")
        config.addString("return_style", returnPostcardStyleId?.toString() ?: "", {
            returnPostcardStyleId = if (it.isEmpty()) null else ResourceLocation.tryParse(it)
        }, "")
        config.addItemStack("item", itemStack, { v: ItemStack -> itemStack = v }, ItemStack.EMPTY, true, false).nameKey =
            "ftbquests.task.ftbquests.item"
        config.addLong("count", count, { v: Long? -> count = v!! }, 1, 1, Long.MAX_VALUE)
    }

    @OnlyIn(Dist.CLIENT)
    override fun onButtonClicked(button: Button?, canClick: Boolean) {
        button!!.playClickSound()

        val validItems: MutableList<ItemStack> = getValidDisplayItems()

        if (validItems.size == 1 && FTBQuests.getRecipeModHelper().isRecipeModAvailable) {
            FTBQuests.getRecipeModHelper().showRecipes(validItems[0])
        } else if (validItems.isEmpty()) {
            Minecraft.getInstance().toasts.addToast(
                CustomToast(
                    Component.literal("No valid items!"),
                    ItemIcon.getItemIcon("ftbquests:missing_item"),
                    Component.literal("Report this bug to modpack author!")
                )
            )
        } else {
            ValidParcelItemsScreen(this, validItems).openGui()
        }
    }

    override fun addMouseOverHeader(list: TooltipList, teamData: TeamData?, advanced: Boolean) {
        if (!rawTitle.isEmpty()) {
            // task has had a custom title set, use that in preference to the item's tooltip
            list.add(title)
        } else {
            // use item's tooltip, but include a count with the item name (e.g. "3 x Stick") if appropriate
            val stack = if (icon is ItemIcon) (icon as ItemIcon).stack else itemStack
            val lines = stack.getTooltipLines(
                Item.TooltipContext.of(
                    FTBQuestsClient.getClientLevel()
                ),
                FTBQuestsClient.getClientPlayer(),
                if (advanced) TooltipFlag.Default.ADVANCED else TooltipFlag.Default.NORMAL
            )
            if (!lines.isEmpty()) {
                lines[0] = title
            } else {
                lines.add(title)
            }
            lines.forEach(Consumer { component: Component? -> list.add(component) })
        }
    }

    @OnlyIn(Dist.CLIENT)
    override fun addMouseOverText(list: TooltipList, teamData: TeamData) {
        if (getValidDisplayItems().size > 1) {
            list.blankLine()
            list.add(
                Component.translatable("ftbquests.task.ftbquests.item.view_items")
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.UNDERLINE)
            )
        } else if (FTBQuests.getRecipeModHelper().isRecipeModAvailable) {
            list.blankLine()
            list.add(
                Component.translatable("ftbquests.task.ftbquests.item.click_recipe")
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.UNDERLINE)
            )
        }
    }

    @OnlyIn(Dist.CLIENT)
    override fun getAltIcon(): Icon? {
        val icons = ArrayList<Icon?>()

        for (stack in getValidDisplayItems()) {
            val copy = stack.copy()
            copy.count = 1
            val icon = ItemIcon.getItemIcon(copy)

            if (!icon.isEmpty) {
                icons.add(icon)
            }
        }

        if (icons.isEmpty()) {
            return ItemIcon.getItemIcon("ftbquests:missing_item")
        }

        return IconAnimation.fromList(icons, false)
    }

    @OnlyIn(Dist.CLIENT)
    override fun getAltTitle(): MutableComponent {
        if (count > 1) {
            return Component.literal(count.toString() + "x ").append(itemStack.hoverName)
        }

        return Component.literal("").append(itemStack.hoverName)
    }

    override fun onStarted(data: QuestProgressEventData<*>) {
        super.onStarted(data)
    }
}