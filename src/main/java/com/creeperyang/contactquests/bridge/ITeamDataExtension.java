package com.creeperyang.contactquests.bridge;

import java.util.Collection;
import java.util.Map;

/**
 * FTB Quests 队伍进度（TeamData）的 Mixin 扩展接口，负责 Tag 解锁、脚本强开/屏蔽及信件文本占位符缓存。
 */
public interface ITeamDataExtension {
    /** 为当前队伍解锁一个 Tag 并同步给在线队友。 */
    boolean contactQuests$unlockTag(String tag);

    /** 移除当前队伍已解锁的指定 Tag 并同步给在线队友。 */
    boolean contactQuests$removeTag(String tag);

    /** 查询当前队伍是否已拥有指定 Tag。 */
    boolean contactQuests$hasTag(String tag);

    /** 返回当前队伍已解锁的所有 Tag 集合。 */
    Collection<String> contactQuests$getTags();

    /** 由脚本强制开放指定任务（优先级低于屏蔽状态）。 */
    boolean contactQuests$forceQuest(long questId);

    /** 取消对指定任务的脚本强制开放状态。 */
    boolean contactQuests$unforceQuest(long questId);

    /** 查询指定任务是否处于强制开放状态。 */
    boolean contactQuests$isQuestForced(long questId);

    /** 由脚本强制屏蔽指定任务。 */
    boolean contactQuests$blockQuest(long questId);

    /** 解除对指定任务的脚本屏蔽状态。 */
    boolean contactQuests$unblockQuest(long questId);

    /** 查询指定任务是否处于脚本屏蔽状态。 */
    boolean contactQuests$isQuestBlocked(long questId);

    /** 返回所有被强制开放的任务 ID 集合。 */
    Collection<Long> contactQuests$getForcedQuests();

    /** 返回所有被脚本屏蔽的任务 ID 集合。 */
    Collection<Long> contactQuests$getBlockedQuests();

    /** 整体覆写强制开放任务集合（用于客户端网络同步）。 */
    void contactQuests$setForcedQuests(Collection<Long> ids);

    /** 整体覆写脚本屏蔽任务集合（用于客户端网络同步）。 */
    void contactQuests$setBlockedQuests(Collection<Long> ids);

    /** 获取指定明信片任务按当前上下文解析并缓存的匹配文本。 */
    String contactQuests$getPostcardText(long taskId);

    /** 更新指定明信片任务的缓存文本（空字符串视为清除缓存）。 */
    void contactQuests$setPostcardText(long taskId, String text);

    /** 返回全部已缓存的明信片任务文本映射。 */
    Map<Long, String> contactQuests$getAllPostcardTexts();

    /** 批量替换全部明信片任务缓存文本（用于客户端同步）。 */
    void contactQuests$setAllPostcardTexts(Map<Long, String> texts);

    /** 获取指定红包任务按当前上下文解析并缓存的祝福语。 */
    String contactQuests$getRedPacketBlessing(long taskId);

    /** 更新指定红包任务的缓存祝福语（空字符串视为清除缓存）。 */
    void contactQuests$setRedPacketBlessing(long taskId, String text);

    /** 返回全部已缓存的红包任务祝福语映射。 */
    Map<Long, String> contactQuests$getAllRedPacketBlessings();

    /** 批量替换全部红包任务缓存祝福语（用于客户端同步）。 */
    void contactQuests$setAllRedPacketBlessings(Map<Long, String> texts);
}
