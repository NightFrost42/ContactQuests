package com.creeperyang.contactquests.test;

import dev.ftb.mods.ftbquests.api.ItemFilterAdapter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/** Representative FTB adapter for exercising the conversion UI without an optional filter mod. */
final class ContactQuestsTestTagAdapter implements ItemFilterAdapter {
    static final String NAME = "ContactQuests test tag adapter";
    private static final String KEY = "CQFixtureItemTag";

    @Override public String getName() { return NAME; }
    @Override public boolean hasItemTagFilter() { return true; }

    private String tagName(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getString(KEY);
    }

    @Override public boolean isFilterStack(ItemStack stack) { return !tagName(stack).isEmpty(); }

    @Override public boolean doesItemMatch(ItemStack filter, ItemStack candidate, HolderLookup.Provider registries) {
        String id = tagName(filter);
        return !id.isEmpty() && candidate.is(TagKey.create(Registries.ITEM, ResourceLocation.parse(id)));
    }

    @Override public Matcher getMatcher(ItemStack filter, HolderLookup.Provider registries) {
        return candidate -> doesItemMatch(filter, candidate, registries);
    }

    @Override public ItemStack makeTagFilterStack(TagKey<Item> tag) {
        var stack = new ItemStack(Items.PAPER);
        var data = new CompoundTag();
        data.putString(KEY, tag.location().toString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return stack;
    }
}
