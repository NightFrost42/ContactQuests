package com.creeperyang.contactquests.utils;

import java.util.Collection;

public interface ITeamDataExtension {
    boolean contactQuests$unlockTag(String tag);

    boolean contactQuests$removeTag(String tag); // 新增

    boolean contactQuests$hasTag(String tag);

    Collection<String> contactQuests$getTags();  // 新增

    boolean contactQuests$forceQuest(long questId);

    boolean contactQuests$unforceQuest(long questId);

    boolean contactQuests$isQuestForced(long questId);

    boolean contactQuests$blockQuest(long questId);

    boolean contactQuests$unblockQuest(long questId);

    boolean contactQuests$isQuestBlocked(long questId);

    Collection<Long> contactQuests$getForcedQuests();

    Collection<Long> contactQuests$getBlockedQuests();

    void contactQuests$setForcedQuests(Collection<Long> ids);

    void contactQuests$setBlockedQuests(Collection<Long> ids);
}