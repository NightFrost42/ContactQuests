package com.creeperyang.contactquests.utils;

import dev.ftb.mods.ftbquests.quest.TeamData;

import java.util.List;

public interface IQuestExtension {
    List<String> contactQuests$getRequiredTags();

    boolean contactQuests$areTagsMet(TeamData data);
}