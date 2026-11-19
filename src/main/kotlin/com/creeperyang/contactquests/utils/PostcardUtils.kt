package com.creeperyang.contactquests.utils

import net.minecraft.resources.ResourceLocation

object PostcardUtils {
    val DEFAULT_STYLE_ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath("contact", "stripes")

    fun resolveStyleId(style: String): ResourceLocation = ResourceLocation.tryParse(style) ?: DEFAULT_STYLE_ID

    fun normalizeStyleId(style: String?): ResourceLocation? {
        val trimmed = style?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return if (':' in trimmed) ResourceLocation.tryParse(trimmed)
        else ResourceLocation.tryBuild("contact", trimmed)
    }
}
