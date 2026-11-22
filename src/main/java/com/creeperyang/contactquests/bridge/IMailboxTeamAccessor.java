package com.creeperyang.contactquests.bridge;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 邮箱方块实体的 Mixin 桥接接口，用于在方块实体上记录并同步所属 FTB 队伍 UUID。
 */
public interface IMailboxTeamAccessor {
    /** 设置该邮箱关联的队伍 UUID；传 null 表示解除视觉绑定标记。 */
    void contactQuests$setTeamId(@Nullable UUID id);

    /** 返回当前邮箱记录的队伍 UUID；未绑定时为 null。 */
    @Nullable UUID contactQuests$getTeamId();
}
