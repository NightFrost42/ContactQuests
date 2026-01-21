package com.creeperyang.contactquests.utils;

import java.util.Collection;
import java.util.Map;

public interface ITeamDataExtension {
    boolean contactQuests$unlockTag(String tag);

    boolean contactQuests$removeTag(String tag);

    boolean contactQuests$hasTag(String tag);

    Collection<String> contactQuests$getTags();

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

    String contactQuests$getPostcardText(long taskId);

    void contactQuests$setPostcardText(long taskId, String text);

    Map<Long, String> contactQuests$getAllPostcardTexts();

    void contactQuests$setAllPostcardTexts(Map<Long, String> texts);

    String contactQuests$getRedPacketBlessing(long taskId);

    void contactQuests$setRedPacketBlessing(long taskId, String text);

    Map<Long, String> contactQuests$getAllRedPacketBlessings();

    void contactQuests$setAllRedPacketBlessings(Map<Long, String> texts);
}