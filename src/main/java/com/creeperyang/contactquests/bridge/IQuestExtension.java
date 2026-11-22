package com.creeperyang.contactquests.bridge;

import dev.ftb.mods.ftbquests.quest.TeamData;

import java.util.List;

/**
 * FTB Quests 任务对象的 Mixin 扩展接口，补充前置 Tag、互斥任务与按存档覆盖的本地化文本。
 */
public interface IQuestExtension {
    /** 返回解锁本任务所需的全部 Tag 名称列表。 */
    List<String> contactQuests$getRequiredTags();

    /** 返回配置为互斥关系的关联任务显示标题列表。 */
    List<String> contactQuests$getMutexTasks();

    /** 判断指定队伍是否尚未满足所需 Tag 条件（返回 true 表示当前被 Tag 条件拦截）。 */
    boolean contactQuests$areTagsMet(TeamData data);

    /** 判断指定队伍是否已触发与本任务互斥的解锁/完成条件。 */
    boolean contactQuests$isLockedByMutex(TeamData data);

    /** 设置或清除指定语言下的任务描述存档级覆盖文本；传 null 表示移除覆盖。 */
    void contactQuests$setDescriptionOverride(String locale, List<String> description);

    /** 设置或清除指定语言下的任务标题存档级覆盖文本；传 null 表示移除覆盖。 */
    void contactQuests$setTitleOverride(String locale, String title);

    /** 设置或清除指定语言下的任务副标题存档级覆盖文本；传 null 表示移除覆盖。 */
    void contactQuests$setSubtitleOverride(String locale, String subtitle);
    /** 返回当前语言的存档标题覆盖；null 表示使用原生标题。 */
    default String contactQuests$getTitleOverride() { return null; }

    /** 直接更新任务原生描述列表，绕过本地化覆盖注入。 */
    default void contactQuests$setNativeDescription(List<String> description) {}
}
