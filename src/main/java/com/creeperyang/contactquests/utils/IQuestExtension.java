package com.creeperyang.contactquests.utils;

import dev.ftb.mods.ftbquests.quest.TeamData;

import java.util.List;

public interface IQuestExtension {
    List<String> contactQuests$getRequiredTags();

    List<String> contactQuests$getMutexTasks();
    boolean contactQuests$areTagsMet(TeamData data);

    boolean contactQuests$isLockedByMutex(TeamData data);
}