package com.creeperyang.contactquests.mailbox

import com.flechazo.contact.common.handler.MailboxManager
import com.flechazo.contact.platform.PlatformHelper
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import java.util.UUID

/** 负责将任务奖励包裹或回信物品直接投递到指定 FTB 团队绑定的 Contact 邮箱，并通知在线成员。 */
object TeamMailHelper {
    @JvmOverloads
    fun sendParcelToTeam(
        level: ServerLevel,
        teamId: UUID,
        content: ItemStack,
        senderName: String = "Quest System",
        preserveSender: Boolean = false
    ): String? {
        val pos = PlatformHelper.getMailboxPos(teamId) ?: return "message.contact.mailbox.no_owner"

        if (PlatformHelper.isMailboxFull(teamId)) {
            return "message.contact.mailbox.full"
        }

        val toSend = content.copy()

        val senderComponentId = ResourceLocation.fromNamespaceAndPath("contact", "postcard_sender")
        val componentTypeOpt = BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(senderComponentId)

        // 当调用方（如自定义 NPC 回信或明信片文本奖励）已自行写入寄件人组件时，保留原署名不被系统名覆盖
        if (!preserveSender) componentTypeOpt.ifPresent { type ->
            try {
                @Suppress("UNCHECKED_CAST")
                val stringType = type as DataComponentType<String>
                toSend[stringType] = senderName
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        PlatformHelper.addMailboxContents(teamId, toSend)

        val mailboxLevel = level.server.getLevel(pos.dimension())
        if (mailboxLevel != null && mailboxLevel.hasChunkAt(pos.pos())) {
            MailboxManager.updateState(mailboxLevel, pos.pos())
        }

        if (!level.isClientSide) {
            val server = level.server
            val teamManager = FTBTeamsAPI.api().manager
            val team = teamManager.getTeamByID(teamId).orElse(null)

            if (team != null) {
                val message = Component.translatable("contactquests.message.parcel_received")
                    .withStyle(ChatFormatting.GREEN)

                val members = team.members
                for (player in server.playerList.players) {
                    if (members.contains(player.uuid)) {
                        player.sendSystemMessage(message)
                    }
                }
            }
        }

        return null
    }
}
