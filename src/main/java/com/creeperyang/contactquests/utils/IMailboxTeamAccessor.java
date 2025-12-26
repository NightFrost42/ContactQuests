package com.creeperyang.contactquests.utils;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface IMailboxTeamAccessor {
    void contactquests$setTeamId(@Nullable UUID id);

    @Nullable UUID contactquests$getTeamId();
}