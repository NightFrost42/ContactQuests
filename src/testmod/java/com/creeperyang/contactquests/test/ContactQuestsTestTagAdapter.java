package com.creeperyang.contactquests.test;

import dev.ftb.mods.ftbquests.api.ItemFilterAdapter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Representative FTB adapter for exercising the conversion UI without an optional filter mod. */
final class ContactQuestsTestTagAdapter implements ItemFilterAdapter {
    static final String NAME = "ContactQuests test tag adapter";
    private static final String KEY = "CQFixtureItemTag";

    @Override public String getName() { return NAME; }
    @Override public boolean hasItemTagFilter() { return true; }

    private String tagName(ItemStack stack) {
        var data = stack.getTag();
        return data == null ? "" : data.getString(KEY);
    }

    @Override public boolean isFilterStack(ItemStack stack) { return !tagName(stack).isEmpty(); }

    @Override public boolean doesItemMatch(ItemStack filter, ItemStack candidate) {
        String id = tagName(filter);
        return !id.isEmpty() && candidate.is(TagKey.create(Registries.ITEM, new ResourceLocation(id)));
    }

    @Override public Matcher getMatcher(ItemStack filter) {
        return candidate -> doesItemMatch(filter, candidate);
    }

    @Override public ItemStack makeTagFilterStack(TagKey<Item> tag) {
        var stack = new ItemStack(Items.PAPER);
        stack.getOrCreateTag().putString(KEY, tag.location().toString());
        return stack;
    }
}
