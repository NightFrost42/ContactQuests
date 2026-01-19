package com.creeperyang.contactquests.quest.task

import com.creeperyang.contactquests.config.ContactConfig
import dev.ftb.mods.ftblibrary.config.ConfigGroup
import dev.ftb.mods.ftblibrary.icon.Icon
import dev.ftb.mods.ftblibrary.icon.IconAnimation
import dev.ftb.mods.ftblibrary.icon.ItemIcon
import dev.ftb.mods.ftblibrary.ui.Button
import dev.ftb.mods.ftblibrary.util.TooltipList
import dev.ftb.mods.ftbquests.client.gui.CustomToast
import dev.ftb.mods.ftbquests.quest.Quest
import dev.ftb.mods.ftbquests.quest.TeamData
import dev.ftb.mods.ftbquests.quest.task.Task
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import java.util.function.Predicate
import kotlin.math.max
import kotlin.math.min

abstract class ContactTask(id: Long, quest: Quest) : Task(id, quest), Predicate<ItemStack> {

    var targetAddressee: String = ContactConfig.defaultTargetAddressee.get()
    var count: Long = 1

    override fun getMaxProgress(): Long = count

    fun getAmountNeeded(teamData: TeamData): Long {
        return max(0L, count - teamData.getProgress(this))
    }

    fun insert(teamData: TeamData, stack: ItemStack, simulate: Boolean, needTest: Boolean = true): ItemStack {
        val test = if (needTest) {
            test(stack)
        } else true
        if (!teamData.isCompleted(this) && test && teamData.canStartTasks(quest)) {
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

    override fun writeData(nbt: CompoundTag) {
        super.writeData(nbt)
        nbt.putString("TargetAddressee", targetAddressee)
        if (count > 1) nbt.putLong("count", count)
    }

    override fun readData(nbt: CompoundTag) {
        super.readData(nbt)
        targetAddressee = nbt.getString("TargetAddressee")
        count = max(nbt.getLong("count"), 1L)
    }

    override fun writeNetData(buffer: FriendlyByteBuf) {
        super.writeNetData(buffer)
        buffer.writeUtf(targetAddressee)
        buffer.writeVarLong(count)
    }

    override fun readNetData(buffer: FriendlyByteBuf) {
        super.readNetData(buffer)
        targetAddressee = buffer.readUtf()
        count = buffer.readVarLong()
    }

    @OnlyIn(Dist.CLIENT)
    override fun fillConfigGroup(config: ConfigGroup) {
        super.fillConfigGroup(config)
        config.addString("target_addressee", targetAddressee, { targetAddressee = it }, "Quest NPC")
            .nameKey = "contactquest.task.parcel.recipient"
        config.addLong("count", count, { v: Long? -> count = v!! }, 1, 1, Long.MAX_VALUE)
    }

    @OnlyIn(Dist.CLIENT)
    override fun onButtonClicked(button: Button?, canClick: Boolean) {
        button!!.playClickSound()

        val validItems = getValidDisplayItems().filter {
            !it.isEmpty && it.item != Items.AIR && it.count > 0
        }.toMutableList()

        if (validItems.isEmpty()) {
            Minecraft.getInstance().toasts.addToast(
                CustomToast(
                    Component.translatable("contactquest.task.no_valid_items"),
                    ItemIcon.getItemIcon("ftbquests:missing_item"),
                    Component.translatable("contactquest.task.error_check")
                )
            )
        } else {
            openTaskGui(validItems)
        }
    }

    abstract fun getValidDisplayItems(): MutableList<ItemStack>

    @OnlyIn(Dist.CLIENT)
    abstract fun openTaskGui(validItems: MutableList<ItemStack>)

    @OnlyIn(Dist.CLIENT)
    override fun addMouseOverText(list: TooltipList, teamData: TeamData) {
        list.blankLine()
        list.add(
            Component.translatable("contactquest.task.click_to_submit")
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.UNDERLINE)
        )
    }

    @OnlyIn(Dist.CLIENT)
    override fun getAltIcon(): Icon? {
        val icons = ArrayList<Icon?>()
        for (stack in getValidDisplayItems()) {
            val copy = stack.copy()
            copy.count = 1
            val icon = ItemIcon.getItemIcon(copy)
            if (!icon.isEmpty) icons.add(icon)
        }

        if (icons.isEmpty()) {
            return ItemIcon.getItemIcon("ftbquests:missing_item")
        }
        return IconAnimation.fromList(icons, false)
    }

}