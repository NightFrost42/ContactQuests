package com.creeperyang.contactquests.utils

import com.flechazo.contact.common.handler.MailboxManager
import com.flechazo.contact.common.storage.MailboxDataManager
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import java.util.*

object TeamMailHelper {
    fun sendParcelToTeam(
        level: ServerLevel,
        teamId: UUID,
        content: ItemStack,
        senderName: String = "Quest System"
    ): String? {
        val dataManager = MailboxDataManager.getData(level)

        val pos = dataManager.getMailboxPos(teamId) ?: return "message.contact.mailbox.no_owner"

        if (dataManager.isMailboxFull(teamId)) {
            return "message.contact.mailbox.full"
        }

        val toSend = content.copy()

        val tag = toSend.getOrCreateTag()
        tag.putString("Sender", senderName)

        dataManager.addMailboxContents(teamId, toSend)

        if (pos.dimension() == level.dimension() && level.isLoaded(pos.pos())) {
            MailboxManager.updateState(level, pos.pos())
        }

        dataManager.addMailboxContents(teamId, toSend)

        MailboxManager.updateState(level, pos.pos())


        if (!level.isClientSide) {
            val server = level.server
            val teamManager = FTBTeamsAPI.api().manager

            val team = teamManager.getTeamByID(teamId).orElse(null)

            if (team != null) {
                val message = Component.translatable("contactquests.message.parcel_received")
                    .withStyle(net.minecraft.ChatFormatting.GREEN)

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