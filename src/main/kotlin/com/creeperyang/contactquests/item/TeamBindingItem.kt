package com.creeperyang.contactquests.item

import com.creeperyang.contactquests.utils.IMailboxTeamAccessor
import com.flechazo.contact.common.block.MailboxBlock
import com.flechazo.contact.common.handler.MailboxManager
import com.flechazo.contact.platform.PlatformHelper
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.GlobalPos
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf
import java.util.*

class TeamBindingItem(properties: Properties) : Item(properties) {

    override fun useOn(context: UseOnContext): InteractionResult {
        val level = context.level
        val player = context.player
        val pos = context.clickedPos
        val state = level.getBlockState(pos)

        if (player == null || !player.isShiftKeyDown || state.block !is MailboxBlock) {
            return InteractionResult.PASS
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS
        }

        val team = FTBTeamsAPI.api().manager.getTeamForPlayerID(player.uuid).orElse(null)
        if (team == null) {
            player.sendSystemMessage(
                Component.translatable("message.contactquests.binding.no_team")
                    .withStyle(ChatFormatting.RED)
            )
            return InteractionResult.FAIL
        }

        val teamId = team.id

        if (!checkAndHandleExistingMailbox(teamId, level, pos, player)) {
            return InteractionResult.FAIL
        }

        val targetPos = if (state.getValue(MailboxBlock.HALF) == DoubleBlockHalf.UPPER) {
            pos
        } else {
            pos.above()
        }

        PlatformHelper.setMailboxData(teamId, level.dimension(), targetPos)
        PlatformHelper.setMailboxContents(teamId, PlatformHelper.getMailboxContents(teamId))
        MailboxManager.updateState(level, targetPos)

        updateMailboxVisuals(level, targetPos, teamId)

        player.sendSystemMessage(
            Component.translatable("message.contactquests.binding.success", team.name)
                .withStyle(ChatFormatting.GREEN)
        )

        consumeItem(context, player)

        return InteractionResult.SUCCESS
    }

    private fun checkAndHandleExistingMailbox(
        teamId: UUID,
        level: Level,
        currentPos: BlockPos,
        player: Player
    ): Boolean {
        val existingPos: GlobalPos = PlatformHelper.getMailboxPos(teamId) ?: return true

        val isSameDimension = existingPos.dimension() == level.dimension()
        val isSamePos = existingPos.pos() == currentPos ||
                existingPos.pos() == currentPos.above() ||
                existingPos.pos() == currentPos.below()

        if (isSameDimension && isSamePos) {
            return true
        }

        sendExistingMailboxError(player, existingPos)
        return false
    }

    private fun sendExistingMailboxError(player: Player, existingPos: GlobalPos) {
        val posStr = "[${existingPos.pos().x}, ${existingPos.pos().y}, ${existingPos.pos().z}]"
        val dimStr = existingPos.dimension().location().toString()

        player.sendSystemMessage(
            Component.translatable("message.contactquests.binding.exists", posStr, dimStr)
                .withStyle(ChatFormatting.RED)
        )

        if (player.hasPermissions(2)) {
            player.sendSystemMessage(
                Component.literal("[Teleport]")
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.UNDERLINE)
                    .withStyle { style ->
                        style.withClickEvent(
                            ClickEvent(
                                ClickEvent.Action.RUN_COMMAND,
                                "/execute in $dimStr run tp ${existingPos.pos().x} ${existingPos.pos().y} ${existingPos.pos().z}"
                            )
                        )
                            .withHoverEvent(
                                HoverEvent(
                                    HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("Click to teleport")
                                )
                            )
                    }
            )
        }
    }

    private fun updateMailboxVisuals(level: Level, targetPos: BlockPos, teamId: UUID) {
        val newBe = level.getBlockEntity(targetPos)
        if (newBe is IMailboxTeamAccessor) {
            newBe.`contactquests$setTeamId`(teamId)
            newBe.setChanged()
            level.sendBlockUpdated(targetPos, newBe.blockState, newBe.blockState, 3)
        }
    }

    private fun consumeItem(context: UseOnContext, player: Player) {
        val itemStack: ItemStack = context.itemInHand
        itemStack.shrink(1)
        player.setItemInHand(context.hand, if (itemStack.isEmpty) ItemStack.EMPTY else itemStack)
    }
}