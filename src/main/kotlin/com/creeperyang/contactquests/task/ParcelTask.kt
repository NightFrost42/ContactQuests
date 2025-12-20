package com.creeperyang.contactquests.task

import com.creeperyang.contactquests.client.gui.ValidParcelItemsScreen
import com.creeperyang.contactquests.data.DataManager
import dev.ftb.mods.ftblibrary.config.ConfigGroup
import dev.ftb.mods.ftblibrary.icon.Icon
import dev.ftb.mods.ftblibrary.icon.IconAnimation
import dev.ftb.mods.ftblibrary.icon.ItemIcon
import dev.ftb.mods.ftblibrary.math.Bits
import dev.ftb.mods.ftblibrary.ui.Button
import dev.ftb.mods.ftblibrary.util.TooltipList
import dev.ftb.mods.ftbquests.FTBQuests
import dev.ftb.mods.ftbquests.client.FTBQuestsClient
import dev.ftb.mods.ftbquests.client.gui.CustomToast
import dev.ftb.mods.ftbquests.integration.item_filtering.ItemMatchingSystem
import dev.ftb.mods.ftbquests.integration.item_filtering.ItemMatchingSystem.ComponentMatchType
import dev.ftb.mods.ftbquests.item.MissingItem
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
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.TooltipFlag
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import java.util.function.Consumer
import java.util.function.Predicate
import kotlin.math.max
import kotlin.math.min

class ParcelTask(id: Long, quest: Quest) : Task(id, quest), Predicate<ItemStack> {

    var targetAddressee: String = "QuestNPC"
    var itemStack: ItemStack = ItemStack.EMPTY
    var count: Long = 1
    private var matchComponents: ComponentMatchType? = ComponentMatchType.NONE
//    var sendTime: Int = 0

    override fun getType(): TaskType = TaskRegistry.PARCEL

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
        nbt.put("item", saveItemSingleLine(itemStack.copyWithCount(1)))
        if (count > 1) {
            nbt.putLong("count", count)
        }
        if (matchComponents != ComponentMatchType.NONE) {
            nbt.putString("match_components", ComponentMatchType.NAME_MAP.getName(matchComponents))
        }
    }

    override fun readData(nbt: CompoundTag, provider: HolderLookup.Provider) {
        super.readData(nbt, provider)
        targetAddressee = nbt.getString("TargetAddressee")
        itemStack = itemOrMissingFromNBT(nbt["item"], provider)
        count = max(nbt.getLong("count"), 1L)
        matchComponents = ComponentMatchType.NAME_MAP[nbt.getString("match_components")]
//        sendTime = nbt.getInt("sendTime")
    }

    override fun writeNetData(buffer: RegistryFriendlyByteBuf) {
        super.writeNetData(buffer)
        buffer.writeUtf(targetAddressee)
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, itemStack)
        buffer.writeVarLong(count)
        var flags = 0
        flags = Bits.setFlag(flags, 0x20, matchComponents != ComponentMatchType.NONE)
        flags = Bits.setFlag(flags, 0x40, matchComponents == ComponentMatchType.STRICT)
        buffer.writeVarInt(flags)
    }

    override fun readNetData(buffer: RegistryFriendlyByteBuf) {
        super.readNetData(buffer)
        targetAddressee = buffer.readUtf()
        itemStack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer)
        count = buffer.readVarLong()
        val flags = buffer.readVarInt()
        matchComponents = if (Bits.getFlag(flags, 0x20))
            if (Bits.getFlag(flags, 0x40))
                ComponentMatchType.STRICT else ComponentMatchType.FUZZY
        else ComponentMatchType.NONE
    }

    fun getValidDisplayItems(): MutableList<ItemStack> {
        return ItemMatchingSystem.INSTANCE.getAllMatchingStacks(itemStack)
    }

//    @OnlyIn(Dist.CLIENT)
    override fun test(stack: ItemStack): Boolean {
        if (itemStack.isEmpty) {
            return false
        }

        return ItemMatchingSystem.INSTANCE.doesItemMatch(itemStack, stack, matchComponents)
    }

    @OnlyIn(Dist.CLIENT)
    override fun fillConfigGroup(config: ConfigGroup) {
        super.fillConfigGroup(config)
        config.addString("target_addressee", targetAddressee, { targetAddressee = it }, "Quest NPC")
        config.addItemStack("item", itemStack, { v: ItemStack -> itemStack = v }, ItemStack.EMPTY, true, false).nameKey =
            "ftbquests.task.ftbquests.item"
        config.addLong("count", count, { v: Long? -> count = v!! }, 1, 1, Long.MAX_VALUE)
        config.addEnum<ComponentMatchType?>("match_components", matchComponents,
            { v: ComponentMatchType? -> matchComponents = v }, ComponentMatchType.NAME_MAP)
    }

    @OnlyIn(Dist.CLIENT)
    override fun onButtonClicked(button: Button?, canClick: Boolean) {
        button!!.playClickSound()

        val validItems = getValidDisplayItems().filter {
            !it.isEmpty && it.item != Items.AIR && it.count > 0
        }.toMutableList()

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
        if (rawTitle.isNotEmpty()) {
            list.add(title)
        } else {
            val stack = if (icon is ItemIcon) (icon as ItemIcon).stack else itemStack
            val lines = stack.getTooltipLines(
                Item.TooltipContext.of(
                    FTBQuestsClient.getClientLevel()
                ),
                FTBQuestsClient.getClientPlayer(),
                if (advanced) TooltipFlag.Default.ADVANCED else TooltipFlag.Default.NORMAL
            )
            if (lines.isNotEmpty()) {
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
                Component.translatable("contactquest.task.ftbquests.item.view_items")
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.UNDERLINE)
            )
        } else if (FTBQuests.getRecipeModHelper().isRecipeModAvailable) {
            list.blankLine()
            list.add(
                Component.translatable("contactquest.task.ftbquests.item.click_recipe")
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

    fun submitParcelTask (teamData: TeamData, player: ServerPlayer, submitItemStack: ItemStack): ItemStack{
        if (teamData.isCompleted(this)){
            DataManager.completeTask(this)
            return itemStack
        }
        if (!checkTaskSequence(teamData) || itemStack.item is MissingItem || submitItemStack.item is MissingItem) {
            return itemStack
        }
        val item = insert(teamData, submitItemStack, false)
        val returnItem = if (item.isEmpty) ItemStack.EMPTY else item

        return returnItem
    }

    fun insert(teamData: TeamData, stack: ItemStack, simulate: Boolean): ItemStack{
        if (!teamData.isCompleted(this) && test(stack) && teamData.canStartTasks(quest)) {
            val add = min(stack.count.toLong(), count - teamData.getProgress(this))

            if (add > 0L) {
                if (!simulate && teamData.file.isServerSide) {
                    teamData.addProgress(this, add)
                }

                val copy = stack.copy()
                copy.count = (stack.count - add).toInt()
                return copy
            }
        }

        return stack
    }
}