package com.creeperyang.contactquests.config

import net.neoforged.neoforge.common.ModConfigSpec

object ContactConfig {
    private val BUILDER = ModConfigSpec.Builder()

    val SPEC: ModConfigSpec

    val enableDeliveryTime: ModConfigSpec.BooleanValue

    init {
        BUILDER.comment("Contact Quests General Configuration").push("general")

        enableDeliveryTime = BUILDER
            .comment("是否启用物流延迟功能 (Enable parcel delivery delay)")
            .comment("如果设置为 false，所有包裹将立即送达。")
            .define("enable_delivery_time", false)

        BUILDER.pop()

        SPEC = BUILDER.build()
    }
}