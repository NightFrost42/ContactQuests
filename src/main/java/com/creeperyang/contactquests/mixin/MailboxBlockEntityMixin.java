package com.creeperyang.contactquests.mixin;

import com.creeperyang.contactquests.client.renderer.MailboxGlobalRenderer;
import com.creeperyang.contactquests.utils.IMailboxTeamAccessor;
import com.flechazo.contact.common.tileentity.MailboxBlockEntity;
import net.minecraft.core.BlockPos;
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
    public void contactquests$setTeamId(@Nullable UUID id) {
        this.contactQuestsTeamId = id;
    }

    @Override
    @Nullable
    public UUID contactquests$getTeamId() {
        return this.contactQuestsTeamId;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null && this.level.isClientSide) {
            MailboxGlobalRenderer.INSTANCE.track((MailboxBlockEntity) (Object) this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.level != null && this.level.isClientSide) {
            MailboxGlobalRenderer.INSTANCE.untrack((MailboxBlockEntity) (Object) this);
        }
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void onSaveAdditional(@NotNull CompoundTag tag, CallbackInfo ci) {
        if (contactQuestsTeamId != null) {
            tag.putUUID(TEAM_ID_NBT_KEY, contactQuestsTeamId);
        }
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void onLoad(@NotNull CompoundTag tag, CallbackInfo ci) {
        if (tag.hasUUID(TEAM_ID_NBT_KEY)) {
            contactQuestsTeamId = tag.getUUID(TEAM_ID_NBT_KEY);
        } else {
            contactQuestsTeamId = null;
        }
    }

    @Inject(method = "getUpdateTag", at = @At("RETURN"))
    private void onGetUpdateTag(CallbackInfoReturnable<CompoundTag> cir) {
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
    public void onDataPacket(@NotNull Connection net, @NotNull ClientboundBlockEntityDataPacket pkt) {
        super.onDataPacket(net, pkt);
        CompoundTag tag = pkt.getTag();
        if (tag != null && tag.hasUUID(TEAM_ID_NBT_KEY)) {
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