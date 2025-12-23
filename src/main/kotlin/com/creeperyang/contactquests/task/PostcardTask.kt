package com.creeperyang.contactquests.task

import com.creeperyang.contactquests.ContactQuests
import com.creeperyang.contactquests.client.gui.ValidPostcardItemsScreen
import com.creeperyang.contactquests.data.DataManager
import com.flechazo.contact.common.item.PostcardItem
import com.flechazo.contact.data.PostcardDataManager
import dev.ftb.mods.ftblibrary.config.ConfigGroup
import dev.ftb.mods.ftblibrary.config.NameMap
import dev.ftb.mods.ftblibrary.icon.Icon
import dev.ftb.mods.ftblibrary.icon.IconAnimation
import dev.ftb.mods.ftblibrary.icon.ItemIcon
import dev.ftb.mods.ftblibrary.ui.Button
import dev.ftb.mods.ftblibrary.util.TooltipList
import dev.ftb.mods.ftbquests.client.gui.CustomToast
import dev.ftb.mods.ftbquests.item.MissingItem
import dev.ftb.mods.ftbquests.quest.Quest
import dev.ftb.mods.ftbquests.quest.TeamData
import dev.ftb.mods.ftbquests.quest.task.Task
import dev.ftb.mods.ftbquests.quest.task.TaskType
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import java.util.function.Predicate
import kotlin.math.max
import kotlin.math.min

class PostcardTask(id: Long, quest: Quest) : Task(id, quest), Predicate<ItemStack> {

    var targetAddressee: String = "QuestNPC"
    var count: Long = 1

    var postcardStyle: String = ""
    var postcardText: String = ""

    @Suppress("UNCHECKED_CAST")
    private fun <T> getComponent(path: String): DataComponentType<T>? {
        val id = ResourceLocation.fromNamespaceAndPath("contact", path)
        return BuiltInRegistries.DATA_COMPONENT_TYPE.get(id) as? DataComponentType<T>
    }

    override fun getType(): TaskType = TaskRegistry.POSTCARD

    override fun getMaxProgress(): Long = count

    override fun writeData(nbt: CompoundTag, provider: HolderLookup.Provider) {
        super.writeData(nbt, provider)
        nbt.putString("TargetAddressee", targetAddressee)
        nbt.putString("postcard_style", postcardStyle)
        nbt.putString("postcard_text", postcardText)
        if (count > 1) nbt.putLong("count", count)
    }

    override fun readData(nbt: CompoundTag, provider: HolderLookup.Provider) {
        super.readData(nbt, provider)
        targetAddressee = nbt.getString("TargetAddressee")
        postcardStyle = nbt.getString("postcard_style")
        postcardText = nbt.getString("postcard_text")
        count = max(nbt.getLong("count"), 1L)
    }

    override fun writeNetData(buffer: RegistryFriendlyByteBuf) {
        super.writeNetData(buffer)
        buffer.writeUtf(targetAddressee)
        buffer.writeUtf(postcardStyle)
        buffer.writeUtf(postcardText)
        buffer.writeVarLong(count)
    }

    override fun readNetData(buffer: RegistryFriendlyByteBuf) {
        super.readNetData(buffer)
        targetAddressee = buffer.readUtf()
        postcardStyle = buffer.readUtf()
        postcardText = buffer.readUtf()
        count = buffer.readVarLong()
    }

    fun getValidDisplayItems(): MutableList<ItemStack> {
        val displayList = mutableListOf<ItemStack>()

        val validItemIds = listOf("postcard", "ender_postcard")

        for (itemIdStr in validItemIds) {
            val itemId = ResourceLocation.fromNamespaceAndPath("contact", itemIdStr)
            val item = BuiltInRegistries.ITEM.get(itemId)
            val stack = ItemStack(item)

            displayList.add(stack)
        }

        return displayList
    }

    override fun test(stack: ItemStack): Boolean {

        if (stack.item !is PostcardItem) return false

        val styleType = getComponent<ResourceLocation>("postcard_style_id")
        val textType = getComponent<String>("postcard_text")

        val itemStyle = if (styleType != null) stack.get(styleType)?.toString() else null
        val itemText = if (textType != null) stack.get(textType) else null

        if (ContactQuests.LOGGER.isDebugEnabled) {
            ContactQuests.debug("=== 明信片匹配调试 [Task ID: $id] ===")
            ContactQuests.debug("目标样式: '$postcardStyle' | 物品样式: '$itemStyle'")

            val configTextDebug = postcardText.replace("\n", "[LF]").replace("\\n", "[\\n]")
            val itemTextDebug = itemText?.replace("\n", "[LF]")?.replace("\\n", "[\\n]") ?: "null"
            ContactQuests.debug("目标文本: '$configTextDebug' | 物品文本: '$itemTextDebug'")
        }

        if (postcardStyle.isNotEmpty()) {
            if (itemStyle == null) return false
            if (!itemStyle.contains(postcardStyle) && !postcardStyle.contains(itemStyle)) {
                return false
            }
        }

        if (postcardText.isNotEmpty()) {
            if (itemText == null) return false

            val normalizedConfig = postcardText.replace("\\n", "\n").trim()
            val normalizedItem = itemText.trim()

            if (normalizedConfig == normalizedItem) return true

            if (normalizedItem.contains(normalizedConfig)) return true

            return false
        }

        return true
    }

    @OnlyIn(Dist.CLIENT)
    override fun fillConfigGroup(config: ConfigGroup) {
        super.fillConfigGroup(config)

        config.addString("target_addressee", targetAddressee, { targetAddressee = it }, "Quest NPC").nameKey = "contactquest.task.parcel.recipient"

        val allStyles = PostcardDataManager.getPostcards().keys.map { it.toString() }.sorted().toMutableList()

        if (!allStyles.contains("")) {
            allStyles.add(0, "")
        }

        config.addEnum("postcard_style", postcardStyle, { postcardStyle = it },
            NameMap.of("", allStyles)
                .nameKey { id ->
                    id.ifEmpty { "Any" }
                }
                .create()
        ).nameKey = "contactquest.task.postcard.style"

        config.addString("postcard_text", postcardText, { postcardText = it }, "").nameKey = "contactquest.task.postcard.text"

        config.addLong("count", count, { v: Long? -> count = v!! }, 1, 1, Long.MAX_VALUE)
    }

    @OnlyIn(Dist.CLIENT)
    override fun onButtonClicked(button: Button?, canClick: Boolean) {
        button!!.playClickSound()

        val validItems = getValidDisplayItems()

        if (validItems.isEmpty()) {
            Minecraft.getInstance().toasts.addToast(
                CustomToast(
                    Component.literal("Postcard Task"),
                    ItemIcon.getItemIcon("ftbquests:missing_item"),
                    Component.literal("Contact Mod items not found!")
                )
            )
        } else {
            ValidPostcardItemsScreen(this).openGui()
        }
    }

    override fun addMouseOverHeader(list: TooltipList, teamData: TeamData?, advanced: Boolean) {
        list.add(title)
    }

    @OnlyIn(Dist.CLIENT)
    override fun addMouseOverText(list: TooltipList, teamData: TeamData) {
        list.blankLine()

        list.add(Component.translatable("contactquest.task.parcel.recipient", targetAddressee)
            .withStyle(ChatFormatting.GRAY))

        if (postcardStyle.isNotEmpty()) {
            list.add(Component.translatable("contactquest.task.postcard.req_style")
                .append(Component.literal(postcardStyle).withStyle(ChatFormatting.AQUA)))
        }

        if (postcardText.isNotEmpty()) {
            list.add(Component.translatable("contactquest.task.postcard.req_text")
                .append(Component.literal(postcardText).withStyle(ChatFormatting.GOLD)))
        }

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

        if (icons.isEmpty()) return null
        return IconAnimation.fromList(icons, false)
    }

    @OnlyIn(Dist.CLIENT)
    override fun getAltTitle(): MutableComponent {
        if (count > 1) {
            return Component.literal(count.toString() + "x ").append(Component.translatable("item.contact.postcard"))
        }
        return super.getAltTitle() as MutableComponent
    }

    fun submitPostcardTask(teamData: TeamData, player: ServerPlayer, submitItemStack: ItemStack): ItemStack {
        if (teamData.isCompleted(this)){
            DataManager.completePostcardTask(this)
            return submitItemStack
        }

        if (!checkTaskSequence(teamData) || submitItemStack.item is MissingItem) {
            return submitItemStack
        }

        return insert(teamData, submitItemStack, false)
    }

    fun insert(teamData: TeamData, stack: ItemStack, simulate: Boolean): ItemStack {
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