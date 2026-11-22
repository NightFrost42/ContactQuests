package com.creeperyang.contactquests.compat.kubejs

import com.creeperyang.contactquests.ContactQuests
import com.creeperyang.contactquests.bridge.ITeamDataExtension
import com.creeperyang.contactquests.compat.kubejs.KubeJSQuestSync.LOGGER
import com.creeperyang.contactquests.data.DataManager
import com.creeperyang.contactquests.data.RewardDistributionManager
import com.creeperyang.contactquests.network.NetworkHandler
import com.creeperyang.contactquests.network.OpenQuestMessage
import com.creeperyang.contactquests.quest.task.PostcardTask
import com.creeperyang.contactquests.quest.task.RedPacketTask
import com.creeperyang.contactquests.utils.RedPacketUtils
import com.flechazo.contact.common.item.PostcardItem
import dev.ftb.mods.ftbquests.quest.ServerQuestFile
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack

/** 负责 KubeJS 侧邮递物品构建、直接发信、任务界面跳转以及团队动态文案缓存刷新。 */
internal object KubeJSMailHelper {
    fun sendParcel(player: ServerPlayer, senderName: String, items: List<ItemStack>, isEnder: Boolean) {
        items.forEach { stack ->
            RewardDistributionManager.distribute(player, stack, senderName, isEnder)
        }
    }

    fun sendMail(player: ServerPlayer, senderName: String, item: ItemStack, isEnder: Boolean) {
        RewardDistributionManager.distribute(player, item, senderName, isEnder)
    }

    fun createRedPacket(sender: String, blessing: String, item: ItemStack): ItemStack {
        val container = SimpleContainer(1)
        container.setItem(0, item.copy())
        return RedPacketUtils.getRedPacket(container, blessing, sender)
    }

    fun createPostcard(
        sender: String,
        text: String,
        unlockTags: List<String>,
        style: String?,
        isEnder: Boolean
    ): ItemStack {
        val styleId = if (!style.isNullOrEmpty()) {
            ResourceLocation.tryParse(style) ?: ResourceLocation("contact", "default")
        } else {
            ResourceLocation("contact", "default")
        }

        var postcardStack = PostcardItem.getPostcard(styleId, isEnder)

        if (text.isNotEmpty()) {
            val processedText = text.replace("\\n", "\n")
            postcardStack = PostcardItem.setText(postcardStack, processedText)
        }

        if (sender.isNotEmpty()) {
            postcardStack.orCreateTag.putString("Sender", sender)
        }

        if (unlockTags.isNotEmpty()) {
            val tagList = ListTag()
            unlockTags.forEach { tag -> tagList.add(StringTag.valueOf(tag)) }
            postcardStack.orCreateTag.put("ContactQuestsUnlockTags", tagList)
        }

        return postcardStack
    }

    fun openQuest(player: ServerPlayer, questIdStr: String) {
        try {
            val id = try {
                java.lang.Long.parseUnsignedLong(questIdStr, 16)
            } catch (_: NumberFormatException) {
                questIdStr.toLong()
            }
            // 玩家登录过程中 FTB 可能仍在执行增量同步并持有临时锁，跳过不存在的任务对象以避免触发无关提示弹窗。
            val file = ServerQuestFile.INSTANCE ?: return
            if (file.get(id) == null) return
            NetworkHandler.sendToPlayer(OpenQuestMessage(id), player)
        } catch (e: Exception) {
            LOGGER.error("为玩家 ${player.name.string} 打开任务 $questIdStr 失败", e)
        }
    }

    fun refreshQuests() {
        val server = ServerQuestFile.INSTANCE.server
        if (server != null) {
            val count = updateAllTeamsTaskCache(server)
            LOGGER.info("ContactQuests: 已为 $count 名玩家重算团队动态明信片与红包文案缓存。")
        }

        ServerQuestFile.INSTANCE.refreshGui()
        LOGGER.info("ContactQuests: 已为所有玩家刷新任务界面。")
    }

    fun updatePlayerTaskCache(player: ServerPlayer) {
        try {
            val team = FTBTeamsAPI.api().manager.getTeamForPlayerID(player.uuid).orElse(null) ?: return
            val teamData = ServerQuestFile.INSTANCE.getOrCreateTeamData(team)
            val ext = teamData as ITeamDataExtension

            for (task in DataManager.postcardTasks.values) {
                val resolvedText = PostcardTask.PostcardPlaceholderSupport.replaceForTeamCache(task.postcardText, player, teamData)
                val cached = ext.`contactQuests$getPostcardText`(task.id)
                if (cached != resolvedText) {
                    ext.`contactQuests$setPostcardText`(task.id, resolvedText)
                    ContactQuests.LOGGER.debug("已更新玩家 ${player.name.string} 的明信片缓存（任务 ${task.id}）")
                }
            }

            for (task in DataManager.redPacketTasks.values) {
                val resolvedText = RedPacketTask.RedPacketPlaceholderSupport.replaceForTeamCache(task.blessing, player, teamData)
                val cached = ext.`contactQuests$getRedPacketBlessing`(task.id)
                if (cached != resolvedText) {
                    ext.`contactQuests$setRedPacketBlessing`(task.id, resolvedText)
                    ContactQuests.LOGGER.debug("已更新玩家 ${player.name.string} 的红包祝福语缓存（任务 ${task.id}）")
                }
            }
        } catch (e: Exception) {
            ContactQuests.LOGGER.error("刷新玩家 ${player.name.string} 任务文案缓存失败", e)
        }
    }

    private fun updateAllTeamsTaskCache(server: MinecraftServer): Int {
        var count = 0
        server.playerList.players.forEach { player ->
            updatePlayerTaskCache(player)
            count++
        }
        return count
    }
}
