package com.creeperyang.contactquests.compat.kubejs

import com.creeperyang.contactquests.data.CollectionSavedData
import com.creeperyang.contactquests.data.RewardDistributionManager
import com.creeperyang.contactquests.network.OpenQuestMessage
import com.creeperyang.contactquests.quest.reward.ParcelReward
import com.creeperyang.contactquests.quest.reward.PostcardReward
import com.creeperyang.contactquests.quest.task.ParcelTask
import com.creeperyang.contactquests.quest.task.PostcardTask
import com.creeperyang.contactquests.quest.task.RedPacketTask
import com.creeperyang.contactquests.utils.ITeamDataExtension
import com.flechazo.contact.common.item.PostcardItem
import dev.ftb.mods.ftbquests.quest.ServerQuestFile
import dev.ftb.mods.ftbquests.quest.TeamData
import dev.ftb.mods.ftbquests.quest.reward.Reward
import dev.ftb.mods.ftbquests.quest.task.Task
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI
import dev.latvian.mods.kubejs.event.EventGroup
import dev.latvian.mods.kubejs.event.KubeEvent
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.neoforged.neoforge.network.PacketDistributor
import org.apache.logging.log4j.LogManager
import java.util.function.BiFunction

object ContactKubeJSPlugin {
    val GROUP = EventGroup.of("ContactEvents")

    val REGISTER_REPLACERS = GROUP.server("registerReplacers") { RegisterReplacersEvent::class.java }

    val MAIL_RECEIVED = GROUP.server("mailReceived") { MailReceivedEventJS::class.java }

    private val LOGGER = LogManager.getLogger("contactquests-kubejs")

    private fun getTask(id: Any): Task? {
        val longId = if (id is Number) id.toLong() else try {
            java.lang.Long.parseUnsignedLong(id.toString(), 16)
        } catch (e: Exception) {
            return null
        }
        return ServerQuestFile.INSTANCE.getTask(longId)
    }

    private fun getReward(id: Any): Reward? {
        val longId = if (id is Number) id.toLong() else try {
            java.lang.Long.parseUnsignedLong(id.toString(), 16)
        } catch (e: Exception) {
            return null
        }
        return ServerQuestFile.INSTANCE.getReward(longId)
    }

    @JvmStatic
    fun init() {
        LOGGER.info("Initializing ContactQuests KubeJS Plugin Helper...")
    }

    @JvmStatic
    fun reload() {
        REGISTER_REPLACERS.post(RegisterReplacersEvent())
        LOGGER.info("Reloaded ContactQuests KubeJS integration")
    }

    @JvmStatic
    fun getData(player: Player): TeamData {
        val team = FTBTeamsAPI.api().manager.getTeamForPlayerID(player.uuid).orElseThrow {
            RuntimeException("ContactQuests: Could not find FTB Team for player ${player.name.string}")
        }
        return ServerQuestFile.INSTANCE.getOrCreateTeamData(team)
    }

    @JvmStatic
    fun addTag(team: TeamData, tag: String): Boolean {
        return (team as ITeamDataExtension).`contactQuests$unlockTag`(tag)
    }

    @JvmStatic
    fun removeTag(team: TeamData, tag: String): Boolean {
        return (team as ITeamDataExtension).`contactQuests$removeTag`(tag)
    }

    @JvmStatic
    fun hasTag(team: TeamData, tag: String): Boolean {
        return (team as ITeamDataExtension).`contactQuests$hasTag`(tag)
    }

    @JvmStatic
    fun getTags(team: TeamData): Collection<String> {
        return (team as ITeamDataExtension).`contactQuests$getTags`()
    }

    @JvmStatic
    fun setNpcConfig(level: ServerLevel, name: String, deliveryTime: Int, autoFill: Boolean) {
        KubeJSNpcSavedData.get(level).setNpcData(name, deliveryTime, autoFill)
    }

    @JvmStatic
    fun clearNpcConfigs(level: ServerLevel) {
        KubeJSNpcSavedData.get(level).clearAll()
    }

    @JvmStatic
    fun removeNpcConfig(level: ServerLevel, name: String) {
        KubeJSNpcSavedData.get(level).removeNpcData(name)
    }

    @JvmStatic
    fun setTriggerCount(player: ServerPlayer, name: String, count: Int) {
        CollectionSavedData.get(player.serverLevel()).setTriggerCount(player.uuid, name, count)
    }

    @JvmStatic
    fun setTriggerCount(level: ServerLevel, uuid: java.util.UUID, name: String, count: Int) {
        CollectionSavedData.get(level).setTriggerCount(uuid, name, count)
    }

    @JvmStatic
    fun updateNpcDeliveryTime(level: ServerLevel, name: String, deliveryTime: Int) {
        KubeJSNpcSavedData.get(level).setNpcData(name, deliveryTime, null)
    }

    @JvmStatic
    fun updateNpcAutoFill(level: ServerLevel, name: String, autoFill: Boolean) {
        KubeJSNpcSavedData.get(level).setNpcData(name, null, autoFill)
    }

    @JvmStatic
    fun sendParcel(player: ServerPlayer, senderName: String, items: List<ItemStack>, isEnder: Boolean) {
        ItemStack.EMPTY

        items.forEach { stack ->
            RewardDistributionManager.distribute(player, stack, senderName, isEnder)
        }
    }

    @JvmStatic
    fun sendMail(player: ServerPlayer, senderName: String, item: ItemStack, isEnder: Boolean) {
        RewardDistributionManager.distribute(player, item, senderName, isEnder)
    }

    @JvmStatic
    fun createPostcard(
        sender: String,
        text: String,
        unlockTags: List<String>,
        style: String?,
        isEnder: Boolean
    ): ItemStack {
        val styleId =
            if (!style.isNullOrEmpty()) ResourceLocation.tryParse(style) else ResourceLocation.fromNamespaceAndPath(
                "contact",
                "default"
            )

        var postcardStack = PostcardItem.getPostcard(styleId, isEnder)

        if (text.isNotEmpty()) {
            val processedText = text.replace("\\n", "\n")
            postcardStack = PostcardItem.setText(postcardStack, processedText)
        }

        if (sender.isNotEmpty()) {
            try {
                val componentId = ResourceLocation.fromNamespaceAndPath("contact", "postcard_sender")
                val rawComponentType = BuiltInRegistries.DATA_COMPONENT_TYPE.get(componentId)

                @Suppress("UNCHECKED_CAST")
                val senderComponentType = rawComponentType as? DataComponentType<String>

                if (senderComponentType != null) {
                    postcardStack.set(senderComponentType, sender)
                }
            } catch (e: Exception) {
                LOGGER.error("Failed to set postcard sender component", e)
            }
        }

        if (unlockTags.isNotEmpty()) {
            val componentType = net.minecraft.core.component.DataComponents.CUSTOM_DATA
            val customData = postcardStack.getOrDefault(componentType, CustomData.EMPTY).copyTag()

            val tagList = ListTag()
            unlockTags.forEach { t -> tagList.add(StringTag.valueOf(t)) }
            customData.put("ContactQuestsUnlockTags", tagList)

            postcardStack.set(componentType, CustomData.of(customData))
        }

        return postcardStack
    }

    @JvmStatic
    fun openQuest(player: ServerPlayer, questIdStr: String) {
        try {
            // 支持传入十六进制字符串 (如 "1A2B3C") 或十进制字符串
            val id = try {
                java.lang.Long.parseUnsignedLong(questIdStr, 16)
            } catch (e: NumberFormatException) {
                questIdStr.toLong()
            }
            PacketDistributor.sendToPlayer(player, OpenQuestMessage(id))
        } catch (e: Exception) {
            LOGGER.error("Failed to open quest $questIdStr for ${player.name.string}", e)
        }
    }

    @JvmStatic
    fun forceQuest(team: TeamData, questIdStr: String): Boolean {
        val id = parseId(questIdStr) ?: return false
        return (team as ITeamDataExtension).`contactQuests$forceQuest`(id)
    }

    @JvmStatic
    fun unforceQuest(team: TeamData, questIdStr: String): Boolean {
        val id = parseId(questIdStr) ?: return false
        return (team as ITeamDataExtension).`contactQuests$unforceQuest`(id)
    }

    @JvmStatic
    fun blockQuest(team: TeamData, questIdStr: String): Boolean {
        val id = parseId(questIdStr) ?: return false
        return (team as ITeamDataExtension).`contactQuests$blockQuest`(id)
    }

    @JvmStatic
    fun unblockQuest(team: TeamData, questIdStr: String): Boolean {
        val id = parseId(questIdStr) ?: return false
        return (team as ITeamDataExtension).`contactQuests$unblockQuest`(id)
    }

    @JvmStatic
    fun resetQuestState(team: TeamData, questIdStr: String) {
        val id = parseId(questIdStr) ?: return
        val ext = team as ITeamDataExtension
        ext.`contactQuests$unforceQuest`(id)
        ext.`contactQuests$unblockQuest`(id)
    }

    private fun parseId(idStr: String): Long? {
        return try {
            java.lang.Long.parseUnsignedLong(idStr, 16)
        } catch (e: Exception) {
            try {
                idStr.toLong()
            } catch (e2: Exception) {
                null
            }
        }
    }

    @JvmStatic
    fun editParcelTask(id: Any, item: ItemStack?, count: Number?): Boolean {
        val task = getTask(id) as? ParcelTask ?: return false
        if (item != null) task.itemStack = item
        if (count != null) task.count = count.toLong()
        task.clearCachedData()
        return true
    }

    @JvmStatic
    fun editPostcardTask(id: Any, style: String?, text: String?): Boolean {
        val task = getTask(id) as? PostcardTask ?: return false
        if (style != null) task.postcardStyle = style
        if (text != null) task.postcardText = text
        task.clearCachedData()
        return true
    }

    @JvmStatic
    fun editParcelReward(id: Any, item: ItemStack?, count: Number?, randomBonus: Number?): Boolean {
        val reward = getReward(id) as? ParcelReward ?: return false
        if (item != null) reward.item = item
        if (count != null) reward.count = count.toInt()
        if (randomBonus != null) reward.randomBonus = randomBonus.toInt()
        reward.clearCachedData()
        return true
    }

    @JvmStatic
    fun editPostcardReward(id: Any, style: String?, text: String?, sender: String?): Boolean {
        val reward = getReward(id) as? PostcardReward ?: return false
        if (style != null) reward.postcardStyle = style
        if (text != null) reward.postcardText = text
        if (sender != null) reward.targetAddressee = sender
        reward.clearCachedData()
        return true
    }

    @JvmStatic
    fun editRedPacketTask(id: Any, item: ItemStack?, count: Number?, blessing: String?): Boolean {
        val task = getTask(id) as? RedPacketTask ?: return false

        if (item != null) {
            task.itemStack = item
        }

        if (count != null) {
            task.count = count.toLong()
        }

        if (blessing != null) {
            task.blessing = blessing
        }

        task.clearCachedData()
        return true
    }

    @JvmStatic
    fun refreshQuests() {
        ServerQuestFile.INSTANCE.refreshGui()
    }

    class RegisterReplacersEvent : KubeEvent {
        fun registerPostcardTask(key: String, callback: BiFunction<Player, TeamData, String>) {
            PostcardTask.PostcardPlaceholderSupport.register(key) { p, t -> callback.apply(p, t) }
        }

        fun registerPostcardReward(callback: BiFunction<String, ServerPlayer, String>) {
            PostcardReward.registerReplacer { text, player -> callback.apply(text, player) }
        }

        fun registerNpcReply(callback: BiFunction<String, CollectionSavedData.ReplacerContext, String?>) {
            CollectionSavedData.registerReplacer { text, ctx -> callback.apply(text, ctx) }
        }
    }
}