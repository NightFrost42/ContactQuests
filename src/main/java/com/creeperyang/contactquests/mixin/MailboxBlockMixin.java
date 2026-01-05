package com.creeperyang.contactquests.mixin;

import com.creeperyang.contactquests.utils.IMailboxTeamAccessor;
import com.flechazo.contact.common.block.MailboxBlock;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.flechazo.contact.common.block.DoubleHorizontalBlock.HALF;

@Mixin(MailboxBlock.class)
public class MailboxBlockMixin {

    @Redirect(
            method = "use(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Objects;equals(Ljava/lang/Object;Ljava/lang/Object;)Z"
            ),
            require = 1
    )
    private boolean interceptOwnerCheck(Object ownerUuidObj, Object playerUuidObj, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (Objects.equals(ownerUuidObj, playerUuidObj)) {
            return true;
        }

        if (!(playerUuidObj instanceof UUID playerUUID)) {
            return false;
        }

        BlockPos topPos = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos : pos.above();

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