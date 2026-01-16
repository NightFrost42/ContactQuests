package com.creeperyang.contactquests.quest.task

import com.creeperyang.contactquests.client.gui.ValidPostcardItemsScreen
import com.creeperyang.contactquests.data.DataManager
import com.creeperyang.contactquests.utils.ITeamDataExtension
import com.flechazo.contact.common.item.PostcardItem
import com.flechazo.contact.data.PostcardDataManager
import dev.ftb.mods.ftblibrary.config.ConfigCallback
import dev.ftb.mods.ftblibrary.config.ConfigGroup
import dev.ftb.mods.ftblibrary.config.ConfigValue
import dev.ftb.mods.ftblibrary.icon.Color4I
import dev.ftb.mods.ftblibrary.ui.Panel
import dev.ftb.mods.ftblibrary.ui.SimpleTextButton
import dev.ftb.mods.ftblibrary.ui.Widget
import dev.ftb.mods.ftblibrary.ui.input.MouseButton
import dev.ftb.mods.ftblibrary.ui.misc.AbstractButtonListScreen
import dev.ftb.mods.ftblibrary.util.TooltipList
import dev.ftb.mods.ftbquests.client.ClientQuestFile
import dev.ftb.mods.ftbquests.quest.Quest
import dev.ftb.mods.ftbquests.quest.TeamData
import dev.ftb.mods.ftbquests.quest.task.TaskType
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn

class PostcardTask(id: Long, quest: Quest) : ContactTask(id, quest) {

    var postcardStyle: String = ""
    var postcardText: String = ""

    @Transient
    private var tempContext: Pair<Player, TeamData>? = null

    override fun getType(): TaskType = TaskRegistry.POSTCARD

    override fun writeData(nbt: CompoundTag, provider: HolderLookup.Provider) {
        super.writeData(nbt, provider)
        nbt.putString("postcard_style", postcardStyle)
        nbt.putString("postcard_text", postcardText)
    }

    override fun readData(nbt: CompoundTag, provider: HolderLookup.Provider) {
        super.readData(nbt, provider)
        postcardStyle = nbt.getString("postcard_style")
        postcardText = nbt.getString("postcard_text")
    }

    override fun writeNetData(buffer: RegistryFriendlyByteBuf) {
        super.writeNetData(buffer)
        buffer.writeUtf(postcardStyle)
        buffer.writeUtf(postcardText)
    }

    override fun readNetData(buffer: RegistryFriendlyByteBuf) {
        super.readNetData(buffer)
        postcardStyle = buffer.readUtf()
        postcardText = buffer.readUtf()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getComponent(path: String): DataComponentType<T>? {
        val id = ResourceLocation.fromNamespaceAndPath("contact", path)
        return BuiltInRegistries.DATA_COMPONENT_TYPE[id] as? DataComponentType<T>
    }

    override fun test(stack: ItemStack): Boolean {
        if (stack.item !is PostcardItem) return false

        if (!checkStyleMatch(stack)) return false

        if (!checkTextMatch(stack)) return false

        return true
    }

    private fun checkStyleMatch(stack: ItemStack): Boolean {
        if (postcardStyle.isEmpty()) return true

        val styleType = getComponent<ResourceLocation>("postcard_style_id") ?: return false
        val itemStyle = stack[styleType]?.toString() ?: return false

        return itemStyle.contains(postcardStyle) || postcardStyle.contains(itemStyle)
    }

    private fun checkTextMatch(stack: ItemStack): Boolean {
        if (postcardText.isEmpty()) return true

        val textType = getComponent<String>("postcard_text") ?: return false
        val itemText = stack[textType] ?: return false

        if (tempContext != null) {
            val (player, teamData) = tempContext!!
            val normalizedItem = itemText.trim()

            val resolvedText = getResolvedText(teamData, player)

            val normalizedConfig = resolvedText.replace("\\n", "\n").trim()

            val result = normalizedItem.contains(normalizedConfig)

//            if (!result && !player.level().isClientSide) {
//                ContactQuests.info("--- Postcard Task Validation Failed ---")
//                ContactQuests.info("Task ID: $id")
//                ContactQuests.info("Config Raw: '$postcardText'")
//                ContactQuests.info("Config Resolved: '$resolvedText'")
//                ContactQuests.info("Config Normalized (Expected): '$normalizedConfig'")
//                ContactQuests.info("Item Text (Actual): '$normalizedItem'")
//                ContactQuests.info("Match Result: false")
//                ContactQuests.info("---------------------------------------")
//            }

            return result
        }
        return false
    }

    fun getResolvedText(teamData: TeamData?, player: Player?): String {
        if (teamData != null) {
            val cached = (teamData as ITeamDataExtension).`contactQuests$getPostcardText`(this.id)
            if (!cached.isNullOrEmpty()) {
//                ContactQuests.info("[Debug Client] Task $id found cached text: $cached")
                return cached
            } else {
//                ContactQuests.info("[Debug Client] Task $id has NO cached text.")
            }
        }

        if (player != null && teamData != null) {
            return PostcardPlaceholderSupport.replace(postcardText, player, teamData)
        }

        return postcardText
    }

    override fun getValidDisplayItems(): MutableList<ItemStack> {
        val displayList = mutableListOf<ItemStack>()
        val validItemIds = listOf("postcard", "ender_postcard")
        for (itemIdStr in validItemIds) {
            val itemId = ResourceLocation.fromNamespaceAndPath("contact", itemIdStr)
            val item = BuiltInRegistries.ITEM[itemId]
            displayList.add(ItemStack(item))
        }
        return displayList
    }

    @OnlyIn(Dist.CLIENT)
    override fun fillConfigGroup(config: ConfigGroup) {
        super.fillConfigGroup(config)

        val allStyles = PostcardDataManager.getPostcards().keys.map { it.toString() }.sorted().toMutableList()
        if (!allStyles.contains("")) allStyles.add(0, "")

        fun getStyleDisplayName(id: String?): Component {
            if (id.isNullOrEmpty()) {
                return Component.translatable("contactquest.postcard.any")
            }
            val rl = ResourceLocation.tryParse(id)
            if (rl != null) {
                return Component.translatable("tooltip.postcard.${rl.namespace}.${rl.path}")
            }
            return Component.literal(id)
        }

        val customValue = object : ConfigValue<String>() {

            init {
                this.value = postcardStyle
            }

            override fun getStringForGUI(v: String?): Component {
                return getStyleDisplayName(v)
            }

            override fun onClicked(widget: Widget, button: MouseButton, callback: ConfigCallback) {
                val self = this

                object : AbstractButtonListScreen() {
                    init {

                        title = Component.translatable("contactquest.task.postcard.style")

                        setHasSearchBox(true)

                        showBottomPanel(false)

                        showCloseButton(true)
                    }

                    override fun addButtons(panel: Panel) {
                        for (styleId in allStyles) {
                            val btnName = getStyleDisplayName(styleId)

                            panel.add(object : SimpleTextButton(panel, btnName, Color4I.empty()) {
                                override fun onClicked(btn: MouseButton?) {
                                    playClickSound()

                                    self.value = styleId

                                    callback.save(true)

                                    closeGui()
                                }
                            })
                        }
                    }

                    override fun doCancel() {
                        closeGui()
                    }

                    override fun doAccept() {
                        closeGui()
                    }
                }.openGui()
            }
        }

        config.add("postcard_style", customValue, postcardStyle, { postcardStyle = it }, "")
            .nameKey = "contactquest.task.postcard.style"

        config.addString("postcard_text", postcardText, { postcardText = it }, "").nameKey = "contactquest.task.postcard.text"
    }

    @OnlyIn(Dist.CLIENT)
    override fun openTaskGui(validItems: MutableList<ItemStack>) {
        ValidPostcardItemsScreen(this).openGui()
    }

    @OnlyIn(Dist.CLIENT)
    override fun addMouseOverText(list: TooltipList, teamData: TeamData) {
        list.blankLine()
        list.add(Component.translatable("contactquest.task.parcel.recipient", targetAddressee).withStyle(ChatFormatting.GRAY))

        if (postcardStyle.isNotEmpty()) {
            val rl = ResourceLocation.tryParse(postcardStyle)
            if (rl != null) {
                val styleName =
                    Component.translatable("tooltip.postcard.${rl.namespace}.${rl.path}").withStyle(ChatFormatting.AQUA)
                list.add(
                    Component.translatable("contactquest.task.postcard.req_style")
                        .append(styleName.withStyle(ChatFormatting.AQUA))
                )
            }
        }
        if (postcardText.isNotEmpty()) {

            val player = Minecraft.getInstance().player

            val resolvedText = getResolvedText(ClientQuestFile.INSTANCE.selfTeamData, player)
            val textToDraw = resolvedText.replace("\\n", "\n")

            list.add(
                Component.translatable("contactquest.task.postcard.req_text").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(textToDraw).withStyle(ChatFormatting.GOLD))
            )
        }

        super.addMouseOverText(list, teamData)
    }

    fun submitPostcardTask(teamData: TeamData, player: ServerPlayer, submitItemStack: ItemStack): ItemStack {
        if (teamData.isCompleted(this)){
            DataManager.completePostcardTask(this)
            return submitItemStack
        }
        tempContext = player to teamData
        try {
            return insert(teamData, submitItemStack, false)
        } finally {
            tempContext = null
        }
    }

    fun setContext(player: Player, teamData: TeamData) {
        tempContext = player to teamData
    }

    fun clearContext() {
        tempContext = null
    }

    object PostcardPlaceholderSupport {
        private val defaultReplacers = mutableMapOf<String, (Player, TeamData) -> String>()
        val replacers = mutableMapOf<String, (Player, TeamData) -> String>()

        init {
            registerDefault("<player_name>") { p, _ -> p.name.string }
            registerDefault("<team_name>") { _, t -> t.name }
            registerDefault("<team_size>") { p, t ->
                val teamId = t.teamId
                if (p.level().isClientSide) {
                    val team = FTBTeamsAPI.api().clientManager.getTeamByID(teamId).orElse(null)
                    team?.members?.size?.toString() ?: "1"
                } else {
                    val team = FTBTeamsAPI.api().manager.getTeamByID(teamId).orElse(null)
                    team?.members?.size?.toString() ?: "1"
                }
            }
            reset()
        }

        private fun registerDefault(key: String, func: (Player, TeamData) -> String) {
            defaultReplacers[key] = func
        }

        fun register(key: String, func: (Player, TeamData) -> String) {
            replacers[key] = func
        }

        fun reset() {
            replacers.clear()
            replacers.putAll(defaultReplacers)
        }

        fun replace(text: String, player: Player, team: TeamData): String {
            var result = text
            replacers.forEach { (key, func) ->
                if (result.contains(key)) {
                    result = result.replace(key, func(player, team))
                }
            }
            return result
        }
    }
}