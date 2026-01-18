package com.creeperyang.contactquests.utils

import com.flechazo.contact.common.handler.MailboxManager
import com.flechazo.contact.platform.PlatformHelper
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
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
        val pos = PlatformHelper.getMailboxPos(teamId) ?: return "message.contact.mailbox.no_owner"

        if (PlatformHelper.isMailboxFull(teamId)) {
            return "message.contact.mailbox.full"
        }

        val toSend = content.copy()

        val senderComponentId = ResourceLocation.fromNamespaceAndPath("contact", "postcard_sender")
        val componentTypeOpt = BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(senderComponentId)

        componentTypeOpt.ifPresent { type ->
            try {
                @Suppress("UNCHECKED_CAST")
                val stringType = type as DataComponentType<String>
                toSend[stringType] = senderName
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        PlatformHelper.addMailboxContents(teamId, toSend)

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