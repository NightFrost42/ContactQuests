package com.creeperyang.contactquests.mixin;

import com.creeperyang.contactquests.mailbox.TeamMailboxBinding;
import com.flechazo.contact.common.storage.PlayerMailboxData;
import com.flechazo.contact.platform.PlatformHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;
import java.util.Map;
import java.util.UUID;

/** 保护已绑定邮箱不被他人覆写归属，并在数据变更或读盘时同步清理旧迁移别名与方块视觉标记。 */
@Mixin(PlayerMailboxData.class)
public abstract class PlayerMailboxDataMixin {
    @Shadow(remap = false)
    public abstract UUID getMailboxOwner(ResourceKey<Level> dimension, BlockPos pos);

    @Shadow(remap = false) @Final
    private Map<UUID, GlobalPos> uuidToLocation;
    @Shadow(remap = false) @Final
    private Map<GlobalPos, UUID> locationToPlayer;

    @Unique
    private GlobalPos contactQuests$previousLocation;

    @Inject(method = "setMailboxData", at = @At("HEAD"), cancellable = true, remap = false)
    private void preventChangingOwner(UUID owner, ResourceKey<Level> dimension, BlockPos pos, CallbackInfo ci) {
        UUID existing = getMailboxOwner(dimension, pos);
        contactQuests$previousLocation = null;
        if (owner == null || existing != null && !Objects.equals(existing, owner)) {
            ci.cancel();
        } else {
            // 在 Contact 覆写坐标映射前先记录原邮箱位置，便于随后刷新旧邮箱的团队视觉标记。
            contactQuests$previousLocation = uuidToLocation.get(owner);
        }
    }

    @Inject(method = "setMailboxData", at = @At("TAIL"), remap = false)
    private void syncSetMarker(UUID owner, ResourceKey<Level> dimension, BlockPos pos, CallbackInfo ci) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || (Object) this != PlatformHelper.data()) return;
        GlobalPos previous = contactQuests$previousLocation;
        contactQuests$previousLocation = null;
        if (previous != null && !previous.equals(GlobalPos.of(dimension, pos))) {
            var previousLevel = server.getLevel(previous.dimension());
            if (previousLevel != null && previousLevel.hasChunkAt(previous.pos())) {
                TeamMailboxBinding.refresh(previousLevel, previous.pos());
            }
        }
        var level = server.getLevel(dimension);
        if (level != null && level.hasChunkAt(pos)) TeamMailboxBinding.refresh(level, pos);
    }

    @Inject(method = "removeMailboxData", at = @At("TAIL"), remap = false)
    private void syncRemovedMarker(GlobalPos pos, CallbackInfo ci) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || (Object) this != PlatformHelper.data()) return;
        var level = server.getLevel(pos.dimension());
        if (level != null && level.hasChunkAt(pos.pos())) TeamMailboxBinding.refresh(level, pos.pos());
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"), remap = false)
    private void removeLegacyAliases(CompoundTag tag, CallbackInfo ci) {
        // 旧版本可能留下个人与队伍同时指向同一邮箱的双向别名；读盘时仅保留 Contact 真实反向拥有者（邮件内容不受影响）。
        uuidToLocation.entrySet().removeIf(entry ->
                !Objects.equals(locationToPlayer.get(entry.getValue()), entry.getKey()));
    }
}
