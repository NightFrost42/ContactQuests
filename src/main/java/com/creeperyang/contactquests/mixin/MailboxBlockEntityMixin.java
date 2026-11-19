package com.creeperyang.contactquests.mixin;

import com.creeperyang.contactquests.client.renderer.MailboxGlobalRenderer;
import com.creeperyang.contactquests.bridge.IMailboxTeamAccessor;
import com.creeperyang.contactquests.mailbox.TeamMailboxBinding;
import com.flechazo.contact.common.tileentity.MailboxBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/** 为 Contact 邮箱方块实体附加队伍 UUID 绑定标记并同步至客户端及全局渲染器。 */
@Mixin(MailboxBlockEntity.class)
public abstract class MailboxBlockEntityMixin extends BlockEntity implements IMailboxTeamAccessor {

    @Unique
    private static final String TEAM_ID_NBT_KEY = "ContactQuestsTeamID";

    @Unique
    @Nullable
    private UUID contactQuestsTeamId = null;

    protected MailboxBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }


    @Override
    public void contactQuests$setTeamId(@Nullable UUID id) {
        this.contactQuestsTeamId = id;
    }

    @Override
    @Nullable
    public UUID contactQuests$getTeamId() {
        return this.contactQuestsTeamId;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null && this.level.isClientSide) {
            MailboxGlobalRenderer.INSTANCE.track((MailboxBlockEntity) (Object) this);
        } else if (this.level instanceof ServerLevel serverLevel) {
            // 队伍可能在区块未加载期间被解散，延迟 1 tick 等团队数据就绪后重新校对归属标记。
            serverLevel.getServer().tell(new net.minecraft.server.TickTask(serverLevel.getServer().getTickCount() + 1,
                    () -> TeamMailboxBinding.refresh(serverLevel, this.worldPosition)));
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.level != null && this.level.isClientSide) {
            MailboxGlobalRenderer.INSTANCE.untrack((MailboxBlockEntity) (Object) this);
        }
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"), remap = false)
    private void onSaveAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries, CallbackInfo ci) {
        if (contactQuestsTeamId != null) {
            tag.putUUID(TEAM_ID_NBT_KEY, contactQuestsTeamId);
        }
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"), remap = false)
    private void onLoadAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries, CallbackInfo ci) {
        if (tag.hasUUID(TEAM_ID_NBT_KEY)) {
            contactQuestsTeamId = tag.getUUID(TEAM_ID_NBT_KEY);
        } else {
            contactQuestsTeamId = null;
        }
    }

    @Inject(method = "getUpdateTag", at = @At("RETURN"), remap = false)
    private void onGetUpdateTag(@NotNull HolderLookup.Provider registries, CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag tag = cir.getReturnValue();
        if (tag != null && contactQuestsTeamId != null) {
            tag.putUUID(TEAM_ID_NBT_KEY, contactQuestsTeamId);
        }
    }

    @Override
    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(@NotNull Connection net, @NotNull ClientboundBlockEntityDataPacket pkt, HolderLookup.@NotNull Provider lookupProvider) {
        super.onDataPacket(net, pkt, lookupProvider);
        CompoundTag tag = pkt.getTag();
        if (tag.hasUUID(TEAM_ID_NBT_KEY)) {
            this.contactQuestsTeamId = tag.getUUID(TEAM_ID_NBT_KEY);
            if (this.level != null && this.level.isClientSide) {
                MailboxGlobalRenderer.INSTANCE.track((MailboxBlockEntity) (Object) this);
            }
        } else {
            this.contactQuestsTeamId = null;
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (this.level != null && this.level.isClientSide) {
            MailboxGlobalRenderer.INSTANCE.untrack((MailboxBlockEntity) (Object) this);
        }
    }

}
