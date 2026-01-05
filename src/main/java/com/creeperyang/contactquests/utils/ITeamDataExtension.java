package com.creeperyang.contactquests.utils;

import java.util.Collection;

public interface ITeamDataExtension {
    boolean contactQuests$unlockTag(String tag);

    boolean contactQuests$removeTag(String tag); // 新增

    boolean contactQuests$hasTag(String tag);

    Collection<String> contactQuests$getTags();  // 新增
}