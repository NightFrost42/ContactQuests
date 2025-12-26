package com.creeperyang.contactquests.compat.jade

import com.creeperyang.contactquests.utils.IMailboxTeamAccessor
import com.flechazo.contact.common.block.MailboxBlock
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import snownee.jade.api.*
import snownee.jade.api.config.IPluginConfig

@WailaPlugin
class ContactJadePlugin : IWailaPlugin {

    override fun register(registration: IWailaCommonRegistration) {
        registration.registerBlockDataProvider(MailboxComponentProvider, MailboxBlock::class.java)
    }

    override fun registerClient(registration: IWailaClientRegistration) {
        registration.registerBlockComponent(MailboxComponentProvider, MailboxBlock::class.java)
    }
}

object MailboxComponentProvider : IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    private val UID = ResourceLocation.fromNamespaceAndPath("contactquests", "mailbox_team_info")
    private const val NBT_KEY_TEAM_ID = "ContactQuestsTeamID"

    override fun appendServerData(data: CompoundTag, accessor: BlockAccessor) {
        val be = accessor.blockEntity
        if (be is IMailboxTeamAccessor) {
            val teamId = be.`contactquests$getTeamId`()
            if (teamId != null) {
                data.putUUID(NBT_KEY_TEAM_ID, teamId)
            }
        }
    }

    override fun appendTooltip(
        tooltip: ITooltip,
        accessor: BlockAccessor,
        config: IPluginConfig
    ) {
        val serverData = accessor.serverData
        if (serverData.hasUUID(NBT_KEY_TEAM_ID)) {
            val teamId = serverData.getUUID(NBT_KEY_TEAM_ID)

            val clientManager = FTBTeamsAPI.api().clientManager
            val team = clientManager.getTeamByID(teamId).orElse(null)

            val textComponent = if (team != null) {
                Component.translatable("jade.contactquests.team_owner", team.name)
                    .withStyle(net.minecraft.ChatFormatting.GOLD)
            } else {
                val shortId = teamId.toString().substring(0, 8)
                Component.translatable("jade.contactquests.unknown_team", shortId)
                    .withStyle(net.minecraft.ChatFormatting.RED)
            }

            tooltip.add(textComponent)
        }
    }

    override fun getUid(): ResourceLocation {
        return UID
    }
}