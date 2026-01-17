package com.creeperyang.contactquests.utils;

import dev.ftb.mods.ftbquests.quest.TeamData;

import java.util.List;

public interface IQuestExtension {
    List<String> contactQuests$getRequiredTags();

    List<String> contactQuests$getMutexTasks();
    boolean contactQuests$areTagsMet(TeamData data);

    boolean contactQuests$isLockedByMutex(TeamData data);

    void contactQuests$setDescriptionOverride(String locale, List<String> description);

    void contactQuests$setTitleOverride(String locale, String title);

    void contactQuests$setSubtitleOverride(String locale, String subtitle);
}