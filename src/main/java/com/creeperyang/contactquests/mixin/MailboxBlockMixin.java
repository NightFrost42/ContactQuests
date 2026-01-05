package com.creeperyang.contactquests.mixin;

import com.creeperyang.contactquests.ContactQuests;
import com.creeperyang.contactquests.utils.IMailboxTeamAccessor;
import com.flechazo.contact.common.block.MailboxBlock;
import com.flechazo.contact.common.storage.IMailboxDataProvider;
import com.flechazo.contact.common.storage.MailboxDataManager;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Mixin(MailboxBlock.class)
public class MailboxBlockMixin {

    @Redirect(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Objects;equals(Ljava/lang/Object;Ljava/lang/Object;)Z"
            )
    )
    private boolean interceptOwnerCheck(Object ownerUuidObj, Object playerUuidObj, BlockState state, Level level, BlockPos pos, Player player) {
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

    @Inject(method = "playerWillDestroy", at = @At("HEAD"))
    private void onPlayerDestroy(Level level, BlockPos pos, BlockState state, Player player, CallbackInfo ci) {
        if (!level.isClientSide) {
            try {
                IMailboxDataProvider dataManager = MailboxDataManager.getData(level);

                BlockPos targetPos = pos;
                if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
                    var half = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF);
                    if (half == DoubleBlockHalf.LOWER) {
                        targetPos = pos.above();
                    }
                }

                GlobalPos globalPos = GlobalPos.of(level.dimension(), targetPos);

                dataManager.removeMailboxData(globalPos);
            } catch (Exception e) {
                ContactQuests.error("Failed to remove mailbox data at " + pos, e);
            }
        }
    }
}