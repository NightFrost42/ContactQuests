package com.creeperyang.contactquests.quest.task

import com.creeperyang.contactquests.ContactQuests
import com.creeperyang.contactquests.client.gui.ValidRedPacketItemsScreen
import com.creeperyang.contactquests.data.DataManager
import com.creeperyang.contactquests.utils.ITeamDataExtension
import com.flechazo.contact.common.item.RedPacketItem
import dev.ftb.mods.ftblibrary.config.ConfigGroup
import dev.ftb.mods.ftblibrary.util.TooltipList
import dev.ftb.mods.ftbquests.client.ClientQuestFile
import dev.ftb.mods.ftbquests.integration.item_filtering.ItemMatchingSystem
import dev.ftb.mods.ftbquests.item.MissingItem
import dev.ftb.mods.ftbquests.quest.Quest
import dev.ftb.mods.ftbquests.quest.TeamData
import dev.ftb.mods.ftbquests.quest.task.TaskType
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemContainerContents
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn

class RedPacketTask(id: Long, quest: Quest) : ItemMatchingTask(id, quest) {

    @Transient
    private var tempContext: Pair<Player, TeamData>? = null

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

            var targetBlessing = blessing
            val (player, teamData) = tempContext!!
            var normalizedConfig = ""
            if (tempContext != null) {
                targetBlessing = getResolvedText(teamData, player)

                normalizedConfig = targetBlessing.replace("\\n", "\n").trim()
            }

            if (stackBlessing != targetBlessing && !player.level().isClientSide) {
                ContactQuests.info("--- Postcard Task Validation Failed ---")
                ContactQuests.info("Task ID: $id")
                ContactQuests.info("Config Raw: '$blessing'")
                ContactQuests.info("Config Resolved: '$targetBlessing'")
                ContactQuests.info("Config Normalized (Expected): '$normalizedConfig'")
                ContactQuests.info("Match Result: false")

                ContactQuests.info("---------------------------------------")
            }

            if (stackBlessing != normalizedConfig) return false
        }

        if (itemStack.isEmpty) return true

        val contentStack = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
            .stream().findFirst().orElse(ItemStack.EMPTY)

        return ItemMatchingSystem.INSTANCE.doesItemMatch(itemStack, contentStack, matchComponents)
    }

    fun getResolvedText(teamData: TeamData?, player: Player?): String {
        if (teamData != null) {
            val cached = (teamData as ITeamDataExtension).`contactQuests$getRedPacketBlessing`(this.id)
            if (!cached.isNullOrEmpty()) {
//                ContactQuests.info("[Debug Client] Task $id found cached text: $cached")
                return cached
            } else {
//                ContactQuests.info("[Debug Client] Task $id has NO cached text.")
            }
        }

        if (player != null && teamData != null) {
            return RedPacketPlaceholderSupport.replace(blessing, player, teamData)
        }
        return blessing
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
        tempContext = player to teamData
        ContactQuests.info(tempContext.toString())
        try {
            if (itemStack.item is MissingItem || submitItemStack.item is MissingItem) return submitItemStack
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


    @OnlyIn(Dist.CLIENT)
    override fun addMouseOverText(list: TooltipList, teamData: TeamData) {
        list.blankLine()
        list.add(
            Component.translatable("contactquest.task.parcel.recipient", targetAddressee).withStyle(ChatFormatting.GRAY)
        )
        val player = Minecraft.getInstance().player
        val resolvedBlessing =
            if (player != null) getResolvedText(ClientQuestFile.INSTANCE.selfTeamData, player) else blessing
        list.add(
            Component.translatable("contactquest.task.redpacket.req_text").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(resolvedBlessing).withStyle(ChatFormatting.GOLD))
        )

        super.addMouseOverText(list, teamData)
    }

    object RedPacketPlaceholderSupport {
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