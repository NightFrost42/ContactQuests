package com.creeperyang.contactquests.test;

import dev.ftb.mods.ftbquests.quest.BaseQuestFile;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

final class ContactQuestsTestUtil {
    private ContactQuestsTestUtil() {}

    static void refreshIndex(BaseQuestFile file) {
        // FTB 1.38 made this protected. Only synthetic test graphs need it.
        try {
            var method = BaseQuestFile.class.getDeclaredMethod("refreshIDMap");
            method.setAccessible(true);
            method.invoke(file);
            file.clearCachedData();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not register the test quest graph", exception);
        }
    }

    static <T extends SavedData> T roundTrip(T original, HolderLookup.Provider registries) {
        CompoundTag saved = original.save(new CompoundTag(), registries);
        try {
            Object companion = original.getClass().getField("Companion").get(null);
            var load = companion.getClass().getDeclaredMethod("load", CompoundTag.class, HolderLookup.Provider.class);
            load.setAccessible(true);
            @SuppressWarnings("unchecked") T restored = (T) load.invoke(companion, saved.copy(), registries);
            return restored;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Could not reload " + original.getClass().getSimpleName(), error);
        }
    }
}
