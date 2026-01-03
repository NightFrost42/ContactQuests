package com.creeperyang.contactquests.mixin;

import com.creeperyang.contactquests.ContactQuests;
import com.creeperyang.contactquests.utils.IMailboxTeamAccessor;
import com.flechazo.contact.common.block.MailboxBlock;
import com.flechazo.contact.common.storage.IMailboxDataProvider;
import com.flechazo.contact.common.storage.MailboxDataManager;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamManager;
import dev.ftb.mods.ftbteams.api.TeamRank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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
        if (Objects.equals(ownerUuidObj, playerUuidObj) || level.isClientSide) {
            return true;
        }

        if (!(ownerUuidObj instanceof UUID mailboxOwnerUUID) || !(playerUuidObj instanceof UUID playerUUID)) {
            return false;
        }

        return checkTeamPermission(level, pos, mailboxOwnerUUID, playerUUID);
    }

    @Unique
    private boolean checkTeamPermission(Level level, BlockPos pos, UUID mailboxOwnerUUID, UUID playerUUID) {
        try {
            TeamManager manager = FTBTeamsAPI.api().getManager();
            if (manager == null) return false;

            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof IMailboxTeamAccessor accessor) {
                UUID boundTeamId = accessor.contactquests$getTeamId();
                if (boundTeamId != null) {
                    Optional<Team> teamOptional = manager.getTeamByID(boundTeamId);
                    if (teamOptional.isPresent() && teamOptional.get().getRankForPlayer(playerUUID).isAtLeast(TeamRank.MEMBER)) {
                        return true;
                    }
                }
            }

            return manager.arePlayersInSameTeam(mailboxOwnerUUID, playerUUID);

        } catch (Exception e) {
            ContactQuests.error("Error checking team permissions for mailbox at " + pos, e);
            return false;
        }
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