package com.creeperyang.contactquests.config

import net.minecraftforge.common.ForgeConfigSpec

object ContactConfig {
    private val BUILDER = ForgeConfigSpec.Builder()

    val SPEC: ForgeConfigSpec

    val enableDeliveryTime: ForgeConfigSpec.BooleanValue

    val autoFillSpeed: ForgeConfigSpec.IntValue

    val retryInterval: ForgeConfigSpec.IntValue

    val defaultTargetAddressee: ForgeConfigSpec.ConfigValue<String>

    init {
        BUILDER.comment("Contact Quests General Configuration").push("general")

        enableDeliveryTime = BUILDER
            .comment("是否启用物流延迟功能 (Enable parcel delivery delay)")
            .comment("如果设置为 false，所有包裹将立即送达。")
            .define("enable_delivery_time", true)

        autoFillSpeed = BUILDER
            .comment("自动填充明信片的打字速度 (Auto-fill typing speed)")
            .comment("单位：Tick (1 tick = 0.05秒)")
            .comment("0 = 立即填充 (关闭打字机效果 / Instant fill)")
            .comment("1 = 极快 (Very Fast), 2 = 正常 (Normal), >2 = 慢速 (Slow)")
            .defineInRange("auto_fill_speed", 1, 0, 100)

        retryInterval = BUILDER
            .comment("邮箱已满或发送失败时的重试检测间隔 (Retry interval when mailbox is full)")
            .comment("单位：Tick")
            .defineInRange("retry_interval", 10, 1, 12000)

        defaultTargetAddressee = BUILDER
            .comment("新创建的任务和奖励默认的收件人 (Default target addressee for new rewards)")
            .comment("这将作为默认值填入，你可以随时在任务编辑器中修改它。")
            .define("default_target_addressee", "QuestNPC")


        BUILDER.pop()

        SPEC = BUILDER.build()
    }
}