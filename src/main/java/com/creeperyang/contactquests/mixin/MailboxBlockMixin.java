package com.creeperyang.contactquests.mixin;

import com.creeperyang.contactquests.bridge.IMailboxTeamAccessor;
import com.creeperyang.contactquests.mailbox.TeamMailboxBinding;
import com.flechazo.contact.common.block.MailboxBlock;
import com.flechazo.contact.common.block.DoubleHorizontalBlock;
import com.flechazo.contact.platform.PlatformHelper;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** 拦截邮箱方块的破坏与交互事件，维护 FTB 队伍专属邮箱归属及跨队访问隔离。 */
@Mixin(MailboxBlock.class)
public abstract class MailboxBlockMixin extends DoubleHorizontalBlock {

    protected MailboxBlockMixin(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (!level.isClientSide && state.getBlock() != replacement.getBlock()) {
            BlockPos upper = state.getValue(DoubleHorizontalBlock.HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos;
            // 原版 Contact 的 playerWillDestroy 无法捕获爆炸、支撑方块脱落或脚本替换导致的销毁；只要物理方块移除就需同步清理绑定数据与视觉标记。
            PlatformHelper.removeMailboxData(GlobalPos.of(level.dimension(), upper));
            TeamMailboxBinding.INSTANCE.updateMarker(level, upper, null);
        }
        super.onRemove(state, level, pos, replacement, moved);
    }

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void checkTeamBinding(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                  BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        BlockPos top = state.getValue(DoubleHorizontalBlock.HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos;
        UUID teamId = TeamMailboxBinding.refresh(serverLevel, top);
        if (teamId == null) return;
        Team team = FTBTeamsAPI.api().getManager().getTeamByID(teamId).orElse(null);
        Team playerTeam = FTBTeamsAPI.api().getManager().getTeamForPlayerID(player.getUUID()).orElse(null);
        if (team != null && (playerTeam == null || !teamId.equals(playerTeam.getId()))) {
            player.sendSystemMessage(Component.translatable("message.contactquests.binding.team_owner", team.getName()));
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Redirect(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Objects;equals(Ljava/lang/Object;Ljava/lang/Object;)Z"
            ),
            require = 1
    )
    private boolean interceptOwnerCheck(Object ownerUuidObj, Object playerUuidObj,
                                        BlockState state, Level level, BlockPos pos,
                                        Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(playerUuidObj instanceof UUID playerUUID)) {
            return false;
        }

        BlockPos topPos;
        if (state.hasProperty(DoubleHorizontalBlock.HALF) &&
                state.getValue(DoubleHorizontalBlock.HALF) == DoubleBlockHalf.LOWER) {
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

        if (be instanceof IMailboxTeamAccessor accessor) {
            UUID storedTeamId = accessor.contactQuests$getTeamId();
            if (storedTeamId != null) {
                return storedTeamId.equals(currentPlayerTeamID)
                        && storedTeamId.equals(PlatformHelper.getMailboxOwner(level.dimension(), topPos));
            }
        }

        if (Objects.equals(ownerUuidObj, playerUuidObj)) return true;
        if (currentPlayerTeamID == null) return false;

        if (ownerUuidObj instanceof UUID mailboxOwnerUUID) {
            return mailboxOwnerUUID.equals(currentPlayerTeamID);
        }

        return false;
    }
}
