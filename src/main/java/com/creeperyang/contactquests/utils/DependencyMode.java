package com.creeperyang.contactquests.utils;

import dev.ftb.mods.ftblibrary.config.NameMap;

public enum DependencyMode {
    ALL, ANY;
    public static final NameMap<DependencyMode> NAME_MAP = NameMap.of(ALL, values()).create();
}