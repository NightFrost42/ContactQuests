package com.creeperyang.contactquests.mixin;

import com.creeperyang.contactquests.utils.IMailboxTeamAccessor;
import com.flechazo.contact.common.block.MailboxBlock;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Mixin(MailboxBlock.class)
public class MailboxBlockMixin {

    @Redirect(
            method = "useWithoutItem",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Objects;equals(Ljava/lang/Object;Ljava/lang/Object;)Z"
            ),
            remap = false
    )
    private boolean interceptOwnerCheck(Object ownerUuidObj, Object playerUuidObj,
                                        @Local(argsOnly = true) Level level,
                                        @Local(argsOnly = true) BlockState state,
                                        @Local(argsOnly = true) BlockPos pos) {
        if (Objects.equals(ownerUuidObj, playerUuidObj)) {
            return true;
        }

        if (!(playerUuidObj instanceof UUID playerUUID)) {
            return false;
        }

        BlockPos topPos;
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) &&
                state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
            topPos = pos.above();
        } else {
            topPos = pos;
        }

        BlockEntity be = level.getBlockEntity(topPos);

        UUID currentPlayerTeamID = null;
        try {
            Optional<Team> teamOpt = FTBTeamsAPI.api().getManager().getTeamForPlayerID(playerUUID);
            if (teamOpt.isPresent()) {
                currentPlayerTeamID = teamOpt.get().getId();
            }
        } catch (Exception ignored) {
        }

        if (currentPlayerTeamID == null) return false;

        if (be instanceof IMailboxTeamAccessor accessor) {
            UUID storedTeamId = accessor.contactquests$getTeamId();
            if (storedTeamId != null && storedTeamId.equals(currentPlayerTeamID)) {
                return true;
            }
        }

        if (ownerUuidObj instanceof UUID mailboxOwnerUUID) {
            return mailboxOwnerUUID.equals(currentPlayerTeamID);
        }

        return false;
    }
}