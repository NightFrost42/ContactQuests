package com.creeperyang.contactquests.util

import com.flechazo.contact.common.handler.MailboxManager
import com.flechazo.contact.common.storage.MailboxDataManager
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries
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
        val dataManager = MailboxDataManager.getData(level)

        val pos = dataManager.getMailboxPos(teamId) ?: return "message.contact.mailbox.no_owner"

        if (dataManager.isMailboxFull(teamId)) {
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

        dataManager.addMailboxContents(teamId, toSend)

        if (pos.dimension() == level.dimension() && level.isLoaded(pos.pos())) {
            MailboxManager.updateState(level, pos.pos())
        }

        return null
    }
}