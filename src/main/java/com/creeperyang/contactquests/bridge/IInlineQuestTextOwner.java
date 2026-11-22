package com.creeperyang.contactquests.bridge;

/**
 * 标记内嵌奖励表所属虚拟任务，避免与全局翻译管理器产生共享假 ID 冲突。
 */
public interface IInlineQuestTextOwner {
    /** 返回当前对象是否仅在本地保存内嵌标题文本。 */
    boolean contactQuests$usesLocalInlineText();

    /** 设置当前对象是否启用本地内嵌标题文本模式。 */
    void contactQuests$setLocalInlineText(boolean enabled);
}
