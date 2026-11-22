package com.creeperyang.contactquests.mailbox

import com.flechazo.contact.common.handler.MailboxManager
import com.flechazo.contact.platform.PlatformHelper
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
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

        if (!preserveSender) toSend.orCreateTag.putString("Sender", senderName)
        if (!PlatformHelper.addMailboxContents(teamId, toSend)) return "message.contact.mailbox.full"

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
