package com.creeperyang.contactquests.compat.jade

import com.creeperyang.contactquests.bridge.IMailboxTeamAccessor
import com.flechazo.contact.common.tileentity.MailboxBlockEntity
import com.flechazo.contact.common.block.MailboxBlock
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI
import net.minecraft.client.Minecraft
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.ResourceLocation
import snownee.jade.api.*
import snownee.jade.api.config.IPluginConfig
import snownee.jade.api.config.IWailaConfig
import java.util.Optional
import kotlin.math.floor

@WailaPlugin
class ContactJadePlugin : IWailaPlugin {

    override fun register(registration: IWailaCommonRegistration) {
        registration.registerBlockDataProvider(MailboxComponentProvider, MailboxBlockEntity::class.java)
    }

    override fun registerClient(registration: IWailaClientRegistration) {
        registration.registerBlockComponent(MailboxComponentProvider, MailboxBlock::class.java)
    }
}

object MailboxComponentProvider : IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    private val UID = ResourceLocation("contactquests", "mailbox_team_info")
    private const val NBT_KEY_TEAM_ID = "ContactQuestsTeamID"

    override fun appendServerData(data: CompoundTag, accessor: BlockAccessor) {
        val be = accessor.blockEntity
        if (be is IMailboxTeamAccessor) {
            val teamId = be.`contactQuests$getTeamId`()
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

            val api = FTBTeamsAPI.api()
            val team = if (api.isClientManagerLoaded) api.clientManager.getTeamByID(teamId).orElse(null) else null

            val textComponent = if (team != null) {
                Component.translatable("jade.contactquests.team_owner", team.name)
                    .withStyle(net.minecraft.ChatFormatting.GOLD)
            } else {
                Component.translatable("jade.contactquests.unknown_team")
                    .withStyle(net.minecraft.ChatFormatting.RED)
            }

            // Jade 文本组件本身不会自动换行；需按主题内边距、方块图标与屏幕安全边距预留空间后再按字体实际宽度折行。
            val minecraft = Minecraft.getInstance()
            val overlay = IWailaConfig.get().overlay
            val scale = overlay.overlayScale.coerceAtLeast(0.1f)
            val theme = overlay.theme
            val padding = theme.padding[3] + theme.padding[1]
            val iconWidth = if (overlay.shouldShowIcon()) 32 else 0
            val width = (floor((minecraft.window.guiScaledWidth - 8) / scale).toInt() - padding - iconWidth - 2)
                .coerceIn(1, 220)
            minecraft.font.splitter.splitLines(textComponent, width, Style.EMPTY).forEach { text ->
                val line = Component.empty()
                text.visit({ textStyle, value ->
                    line.append(Component.literal(value).withStyle(textStyle))
                    Optional.empty<Unit>()
                }, Style.EMPTY)
                tooltip.add(line, UID)
            }
        }
    }

    override fun getUid(): ResourceLocation {
        return UID
    }
}
