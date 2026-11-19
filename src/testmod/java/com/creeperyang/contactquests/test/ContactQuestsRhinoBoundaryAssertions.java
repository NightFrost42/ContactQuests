package com.creeperyang.contactquests.test;

import com.creeperyang.contactquests.compat.kubejs.QuestOverrideSavedData;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.util.Map;
import java.util.TreeMap;

/** Independent native graph, persistent override and on-disk observations around rejected Rhino calls. */
public final class ContactQuestsRhinoBoundaryAssertions {
    private ContactQuestsRhinoBoundaryAssertions() {}

    public static Map<String, String> snapshot(ServerLevel level) throws Exception {
        var result = new TreeMap<String, String>();
        var file = ServerQuestFile.INSTANCE;
        for (var chapter : file.getAllChapters()) {
            add(level, result, chapter);
            for (var quest : chapter.getQuests()) {
                add(level, result, quest);
                for (var task : quest.getTasks()) add(level, result, task);
                for (var reward : quest.getRewards()) add(level, result, reward);
            }
        }
        var overrides = QuestOverrideSavedData.Companion.get(level).save(new CompoundTag(), level.registryAccess());
        result.put("overrides", overrides.toString());
        var directory = FMLPaths.CONFIGDIR.get().resolve("ftbquests/quests");
        if (Files.isDirectory(directory)) {
            try (var paths = Files.walk(directory)) {
                for (var path : paths.filter(Files::isRegularFile).toList())
                    result.put("disk:" + directory.relativize(path), java.util.HexFormat.of().formatHex(
                            java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
            }
        }
        return result;
    }

    private static void add(ServerLevel level, Map<String, String> result, QuestObjectBase object) {
        if (ServerQuestFile.INSTANCE.getBase(object.id) != object)
            throw new IllegalStateException("Native ID index differs from actual graph: " + object.getCodeString());
        var data = new CompoundTag();
        object.writeData(data, level.registryAccess());
        result.put(object.getCodeString(), object.getClass().getName() + ":" + data);
        if (object instanceof dev.ftb.mods.ftbquests.quest.Quest quest) {
            result.put(object.getCodeString() + ":nativeText",
                    quest.getRawTitle() + "\n" + quest.getRawSubtitle() + "\n" + quest.getRawDescription());
        }
    }
}
