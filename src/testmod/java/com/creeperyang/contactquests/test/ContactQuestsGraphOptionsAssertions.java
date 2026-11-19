package com.creeperyang.contactquests.test;

import com.creeperyang.contactquests.compat.kubejs.QuestOverrideSavedData;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ftb.mods.ftblibrary.snbt.SNBT;
import dev.ftb.mods.ftbquests.quest.BaseQuestFile;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.preset.VisualPresetRecord;
import dev.ftb.mods.ftbquests.quest.preset.VisualPresets;
import dev.ftb.mods.ftbquests.quest.task.CheckmarkTask;
import dev.ftb.mods.ftbquests.quest.translation.TranslationKey;
import dev.ftb.mods.ftbquests.quest.translation.TranslationManager;
import dev.ftb.mods.ftbquests.quest.translation.TranslationTable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Observations of real Rhino calls; rejoin only reads disk/native objects in a different JVM. */
public final class ContactQuestsGraphOptionsAssertions {
    private static final String KEY = "cqtest:graph-options/";
    private static final String CASE = "kubejs.graph_options.";
    private static final List<String> QUESTS = List.of("prerequisite", "focus", "inherited", "chapter-consumer", "saved-description", "global-description");
    private static final List<String> NATIVE_DESC = List.of("Native first line", "Native second line");
    private static final List<String> SAVED_DESC = List.of("Saved first line", "Saved second line");
    private static final String TITLE = "Graph title sentinel";

    public static Path snapshotPath() { return Path.of(System.getProperty("contactquests.test.snapshot") + ".graph-options.json"); }

    /** Stable IDs let a client controller inspect the synchronized graph without server statics. */
    public static String id(String kind, String leaf) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(("contactquests:" + kind + ":" + KEY + leaf).getBytes(StandardCharsets.UTF_8));
            long value = 0;
            for (int i = 0; i < 8; i++) value = value << 8 | digest[i] & 255L;
            return QuestObjectBase.getCodeString((value & 0x0fffffffffffffffL) | 0x6000000000000000L);
        } catch (Exception error) { throw new IllegalStateException(error); }
    }

    private static long number(String kind, String leaf) { return Long.parseUnsignedLong(id(kind, leaf), 16); }
    private static Quest quest(BaseQuestFile file, String leaf) { return Objects.requireNonNull(file.getQuest(number("quest", leaf)), "Missing graph-options quest " + leaf); }
    private static Chapter chapter(BaseQuestFile file, String leaf) { return Objects.requireNonNull(file.getChapter(number("chapter", leaf)), "Missing graph-options chapter " + leaf); }
    private static CompoundTag tag(QuestObjectBase object, HolderLookup.Provider provider) {
        var result = new CompoundTag(); object.writeData(result, provider); return result;
    }

    public static List<String> locales() {
        var result = new LinkedHashSet<>(List.of("en_us", "zh_cn"));
        result.add(ServerQuestFile.INSTANCE.getLocale());
        return List.copyOf(result);
    }

    /** Native public root readData registers two presets while retaining every other root setting. */
    public static void prepare(ServerPlayer player) {
        var file = ServerQuestFile.INSTANCE;
        require(file.server == player.server && player.server.isSameThread() && !Boolean.getBoolean("contactquests.test.rejoin"), "Prepare only in the first real server JVM");
        require(!Files.exists(snapshotPath()) && file.getBase(number("chapter", "main")) == null
                && file.getBase(number("chapter", "dependency")) == null, "Do not reseed an existing graph-options fixture");
        var existing = file.getPresets().allPresets();
        require(!existing.containsKey(KEY + "default") && !existing.containsKey(KEY + "explicit"), "Graph-options preset key already belongs to another fixture");
        var presets = new LinkedHashMap<>(existing);
        presets.put(KEY + "default", new VisualPresetRecord("circle", 1.25));
        presets.put(KEY + "explicit", new VisualPresetRecord("square", 1.75));
        var before = tag(file, player.registryAccess());
        var registered = before.copy(); registered.put("presets", new VisualPresets(presets).serialize());
        file.readData(registered, player.registryAccess());
        require(tag(file, player.registryAccess()).equals(registered), "Registering native presets changed other root settings");
        file.clearCachedData(); file.markDirty(); file.saveNow();
    }

    public static void createdIds(ServerPlayer player) {
        var file = ServerQuestFile.INSTANCE;
        require(strings(tag(chapter(file, "main"), player.registryAccess()), "tags").equals(List.of(KEY + "chapter-created"))
                && strings(tag(quest(file, "focus"), player.registryAccess()), "tags").equals(List.of(KEY + "quest-created")),
                "Rhino createChapter/createQuest did not retain their explicit customIds");
    }

    public static void verifyOptions(ServerPlayer player) {
        verifyNativeFields(ServerQuestFile.INSTANCE, player.registryAccess());
        pass("custom_id_autofocus", "Native create/update Chapter and Quest customIds; same-chapter autofocus resolves to the real focus quest at 6,-4");
        pass("presets", "Registered native presets survive public option conversion: inherited circle/1.25 and explicit square/1.75");
        pass("icon_components", "Rhino Map icons preserve gold/bold custom names and exact custom data; native icon counts normalize from 9 to 1");
        pass("task_dependency", "Real quest dependency references the other quest's CheckmarkTask with exact native ID and graph object");
        pass("chapter_dependency", "Real quest dependency references another native Chapter with exact native ID and graph object");
    }

    /** Also callable with ClientQuestFile.INSTANCE; observes fields, not a claimed GUI rendering. */
    public static void verifyClientFields(BaseQuestFile file, HolderLookup.Provider provider) throws Exception {
        verifyNativeFields(file, provider);
        for (String leaf : List.of("saved-description", "global-description")) {
            var q = quest(file, leaf);
            require(q.getRawDescription().isEmpty() && q.getRawTitle().equals(TITLE), "Client description/title sentinel did not synchronize: " + leaf);
        }
        verifyTextState(file, null, "global");
    }

    private static void verifyNativeFields(BaseQuestFile file, HolderLookup.Provider provider) {
        var main = chapter(file, "main"); var other = chapter(file, "dependency");
        var focus = quest(file, "focus"); var inherited = quest(file, "inherited");
        var prerequisite = quest(file, "prerequisite"); var consumer = quest(file, "chapter-consumer");
        var task = file.getTask(number("task", "prerequisite-task"));
        var focusTask = file.getTask(number("task", "focus-task"));
        require(task instanceof CheckmarkTask && task.getQuest() == prerequisite && focusTask instanceof CheckmarkTask
                        && focusTask.getQuest() == focus && focus.getChapter() == main && prerequisite.getChapter() == other,
                "Task/quest/chapter membership is wrong");
        var c = tag(main, provider); var q = tag(focus, provider);
        // FTB 2101.1.38 migrates legacy custom_id into tags on read and only writes tags.
        require(strings(c, "tags").equals(List.of(KEY + "chapter-created", KEY + "chapter"))
                        && strings(q, "tags").equals(List.of(KEY + "quest-created", KEY + "quest"))
                        && main.hasTag(KEY + "chapter") && focus.hasTag(KEY + "quest")
                        && !c.contains("custom_id") && !q.contains("custom_id")
                        && c.getString("autofocus_id").equals(focus.getCodeString()) && main.getAutofocus().orElse(null) == focus
                        && main.isAutofocus(focus.getId()) && focus.getX() == 6 && focus.getY() == -4,
                "Legal customId/autofocus did not reach native NBT and resolved focus");
        require(c.getString("preset").equals(KEY + "default") && q.getString("preset").equals(KEY + "explicit")
                        && !tag(inherited, provider).contains("preset")
                        && focus.getShape().equals("square") && focus.getSize() == 1.75
                        && inherited.getShape().equals("circle") && inherited.getSize() == 1.25
                        && file.getPresets().allPresets().get(KEY + "default").equals(new VisualPresetRecord("circle", 1.25))
                        && file.getPresets().allPresets().get(KEY + "explicit").equals(new VisualPresetRecord("square", 1.75)),
                "Registered preset or chapter inheritance is wrong");
        verifyIcon(c, provider, true); verifyIcon(q, provider, false);
        require(focus.streamDependencies().toList().equals(List.of(task)) && focus.hasDependency(task)
                        && q.getList("dependencies", Tag.TAG_STRING).size() == 1
                        && q.getList("dependencies", Tag.TAG_STRING).getString(0).equals(task.getCodeString()), "Task dependency changed type/ID");
        var dependencies = tag(consumer, provider).getList("dependencies", Tag.TAG_STRING);
        require(consumer.streamDependencies().toList().equals(List.of(other)) && consumer.hasDependency(other)
                        && dependencies.size() == 1 && dependencies.getString(0).equals(other.getCodeString())
                        && prerequisite.streamDependencies().findAny().isEmpty(), "Chapter dependency or rejected cycle changed graph");
    }

    private static void verifyIcon(CompoundTag object, HolderLookup.Provider provider, boolean chapter) {
        var actual = ItemStack.parse(provider, object.getCompound("icon"))
                .orElseThrow(() -> new IllegalStateException("Cannot decode native " + (chapter ? "chapter" : "quest") + " component icon"));
        var expected = new ItemStack(chapter ? Items.DIAMOND : Items.EMERALD);
        expected.set(DataComponents.CUSTOM_NAME, Component.literal(chapter ? "Graph chapter icon" : "Graph quest icon").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        var custom = new CompoundTag(); custom.putString("cq_graph_options", chapter ? "chapter" : "quest"); custom.putString("sentinel", "keep-exact");
        expected.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        require(ItemStack.matches(actual, expected), "Rhino component icon differs from exact native ItemStack: " + actual.save(provider));
    }

    /** Extends the existing rejection snapshot with full native translation tables and in-memory overrides. */
    public static Map<String, String> snapshot(ServerLevel level) throws Exception {
        Map<String, String> values = new TreeMap<>(ContactQuestsRhinoBoundaryAssertions.snapshot(level));
        var file = ServerQuestFile.INSTANCE;
        for (var entry : tables(file).entrySet()) values.put("translation:" + entry.getKey(), entry.getValue().saveToNBT().toString());
        for (var chapter : file.getAllChapters()) for (var quest : chapter.getQuests()) {
            values.put(quest.getCodeString() + ":dependants", quest.getDependants().stream().map(object -> object.getCodeString()).sorted().toList().toString());
            for (String field : List.of("contactQuests$titleOverrides", "contactQuests$subtitleOverrides", "contactQuests$descOverrides"))
                values.put(quest.getCodeString() + ":" + field, new TreeMap<>(overrideMap(quest, field)).toString());
        }
        Path disk = overridePath(level);
        if (Files.isRegularFile(disk)) values.put("worldOverrideDisk", java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(disk))));
        return values;
    }

    public static void verifyDescriptions(ServerPlayer player, String phase) throws Exception {
        require(List.of("seeded", "saved", "global").contains(phase), "Unknown description phase");
        verifyTextState(ServerQuestFile.INSTANCE, QuestOverrideSavedData.Companion.get(player.serverLevel()), phase);
    }

    private static void verifyTextState(BaseQuestFile file, QuestOverrideSavedData saved, String phase) throws Exception {
        // Native client sync sends en_us, fallback and active locale, not every server table.
        var locales = new LinkedHashSet<>(List.of("en_us", file.getLocale()));
        if (saved != null) locales.add("zh_cn");
        for (String leaf : List.of("saved-description", "global-description")) {
            var q = quest(file, leaf);
            boolean savedClear = leaf.equals("saved-description") && !phase.equals("seeded");
            boolean globalClear = leaf.equals("global-description") && phase.equals("global");
            var memory = overrideMap(q, "contactQuests$descOverrides");
            var titles = overrideMap(q, "contactQuests$titleOverrides");
            var tag = saved == null ? null : Objects.requireNonNull(saved.getOverride(q.getId()));
            for (String locale : locales) {
                List<String> nativeExpected = globalClear ? List.of() : NATIVE_DESC;
                var table = Objects.requireNonNull(tables(file).get(locale), "Missing translation table " + locale);
                String descriptionKey = textKey(q, TranslationKey.QUEST_DESC);
                var nativeLines = table.getStringListTranslation(descriptionKey);
                // Native FTB omits empty translation lists on save. A fresh load has no
                // entry for a global clear; a saved override must retain the nonempty original.
                if (globalClear) require(nativeLines.orElse(List.of()).isEmpty()
                                && !table.saveToNBT().contains(descriptionKey),
                        "Globally cleared native description remains for " + leaf + "/" + locale);
                else require(nativeLines.isPresent() && nativeLines.get().equals(nativeExpected),
                        "Native description was changed/missing for " + leaf + "/" + locale);
                require(TITLE.equals(titles.get(locale)), "Description edit touched unrelated title override");
                if (globalClear) require(!memory.containsKey(locale), "Global [] left stale in-memory description override");
                else require(memory.containsKey(locale) && memory.get(locale).equals(savedClear ? List.of() : SAVED_DESC), "Saved [] must remain an explicit empty override");
                if (tag != null) {
                    require(tag.getCompound("title").getString(locale).equals(TITLE), "Description edit changed persisted title override");
                    var desc = tag.getCompound("description");
                    if (globalClear) require(!desc.contains(locale), "Global [] left a persisted locale override");
                    else require(desc.contains(locale, Tag.TAG_LIST) && strings(desc, locale).equals(savedClear ? List.of() : SAVED_DESC),
                            "Saved description override differs from native SavedData");
                }
            }
            require(q.getRawTitle().equals(TITLE) && q.getRawDescription().equals(savedClear || globalClear ? List.of() : SAVED_DESC),
                    "Actually resolved native quest text does not match description phase " + phase);
        }
    }

    public static void verifyAndCapture(ServerPlayer player) throws Exception {
        verifyNativeFields(ServerQuestFile.INSTANCE, player.registryAccess()); verifyDescriptions(player, "global");
        var file = ServerQuestFile.INSTANCE;
        file.markDirty(); file.saveNow(); player.server.saveEverything(false, true, true);
        var values = persistentState(player);
        var json = new JsonObject(); json.addProperty("pid", ProcessHandle.current().pid());
        json.addProperty("player", player.getUUID().toString()); json.addProperty("chapter", id("chapter", "main"));
        json.addProperty("focus", id("quest", "focus")); json.addProperty("savedDescription", id("quest", "saved-description"));
        json.addProperty("globalDescription", id("quest", "global-description"));
        var gson = new GsonBuilder().setPrettyPrinting().create(); json.add("state", gson.toJsonTree(values));
        Files.createDirectories(snapshotPath().toAbsolutePath().getParent()); Files.writeString(snapshotPath(), gson.toJson(json));
        pass("persisted", "Actual native chapter SNBT, registered presets, locale tables and compressed world SavedData match runtime; PID/IDs/exact state captured for new JVM");
    }

    /** Main thread calls this from verify_rejoin. Never seed, load overrides manually or save here. */
    public static void rejoin(ServerPlayer player) {
        try {
            var json = JsonParser.parseString(Files.readString(snapshotPath())).getAsJsonObject();
            require(Boolean.getBoolean("contactquests.test.rejoin") && json.get("pid").getAsLong() != ProcessHandle.current().pid(), "Graph-options rejoin must be a genuinely different JVM");
            require(json.get("player").getAsString().equals(player.getUUID().toString()), "Graph-options rejoin changed real player");
            verifyNativeFields(ServerQuestFile.INSTANCE, player.registryAccess()); verifyDescriptions(player, "global");
            Map<String, String> expected = new TreeMap<>();
            json.getAsJsonObject("state").entrySet().forEach(e -> expected.put(e.getKey(), e.getValue().getAsString()));
            Map<String, String> actual = persistentState(player);
            require(expected.equals(actual), "Graph-options state changed across JVM; differing keys=" + differingKeys(expected, actual));
            ContactQuestsTestServerController.recordCase("server.graph_options.rejoin", true,
                    "Different JVM restored exact native options/dependencies/icons/presets and both empty-description semantics from disk without reseeding");
        } catch (Exception error) { ContactQuestsTestServerController.recordCase("server.graph_options.rejoin", false, error.toString()); }
    }

    private static Map<String, String> persistentState(ServerPlayer player) throws Exception {
        var file = ServerQuestFile.INSTANCE; var provider = player.registryAccess();
        var saved = QuestOverrideSavedData.Companion.get(player.serverLevel());
        Map<String, String> result = new TreeMap<>();
        var rootDisk = Objects.requireNonNull(SNBT.read(file.getFolder().resolve("data.snbt")), "Missing native root data.snbt");
        var diskPresets = VisualPresets.deserialize(rootDisk.getCompound("presets"));
        for (String name : List.of("default", "explicit")) {
            var preset = file.getPresets().allPresets().get(KEY + name);
            require(preset != null && preset.equals(diskPresets.allPresets().get(KEY + name)), "Native preset did not reach disk: " + name);
            result.put("preset:" + name, preset.toString());
        }
        for (String leaf : List.of("main", "dependency")) {
            var chapter = chapter(file, leaf);
            var disk = Objects.requireNonNull(SNBT.read(file.getFolder().resolve(chapter.getPath()
                    .orElseThrow(() -> new IllegalStateException("Missing native chapter path: " + leaf)))), "Missing native chapter file");
            var chapterTag = tag(chapter, provider);
            require(disk.getString("id").equals(chapter.getCodeString()), "Native chapter disk ID mismatch");
            requireFields(chapterTag, disk, "chapter " + leaf);
            require(disk.getList("quests", Tag.TAG_COMPOUND).size() == chapter.getQuests().size(), "Native chapter lost/added a quest on disk");
            for (var q : chapter.getQuests()) {
                var questDisk = child(disk, "quests", q.getCodeString());
                requireFields(tag(q, provider), questDisk, "quest " + q.getCodeString());
                require(questDisk.getList("tasks", Tag.TAG_COMPOUND).size() == q.getTasks().size(), "Native task count differs on disk");
                for (var task : q.getTasks()) requireFields(tag(task, provider), child(questDisk, "tasks", task.getCodeString()), "task " + task.getCodeString());
            }
            // Chapter order is a global editor concern; other fixtures may insert/remove preceding chapters.
            disk.remove("order_index");
            result.put("diskChapter:" + leaf, disk.toString());
            result.put("nativeChapter:" + leaf, chapterTag.toString());
        }
        for (String leaf : QUESTS) {
            var q = quest(file, leaf); result.put("nativeQuest:" + leaf, tag(q, provider).toString());
            for (var task : q.getTasks()) result.put("nativeTask:" + task.getCodeString(), tag(task, provider).toString());
        }
        var world = NbtIo.readCompressed(overridePath(player.serverLevel()), NbtAccounter.unlimitedHeap()).getCompound("data");
        for (String leaf : List.of("saved-description", "global-description")) {
            var q = quest(file, leaf); var override = Objects.requireNonNull(saved.getOverride(q.getId()));
            CompoundTag diskOverride = null;
            for (Tag value : world.getList("QuestOverrides", Tag.TAG_COMPOUND)) {
                var entry = (CompoundTag) value;
                if (entry.getLong("id") == q.getId()) { require(diskOverride == null, "Duplicate persisted quest override"); diskOverride = entry.getCompound("data"); }
            }
            require(override.equals(diskOverride), "World compressed override differs from runtime for " + leaf);
            result.put("override:" + leaf, override.toString());
            result.put("resolvedDescription:" + leaf, q.getRawDescription().toString());
        }
        for (String locale : locales()) {
            var table = tables(file).get(locale);
            var disk = Objects.requireNonNull(SNBT.read(file.getFolder().resolve("lang").resolve(locale + ".snbt")), "Missing native locale file");
            var live = table.saveToNBT();
            for (var object : objects(file)) for (TranslationKey key : TranslationKey.values()) {
                String textKey = textKey(object, key);
                require(Objects.equals(live.get(textKey), disk.get(textKey)), "Native locale disk entry differs: " + locale + "/" + textKey);
                result.put("translation:" + locale + ":" + textKey, Objects.toString(live.get(textKey), "<absent>"));
            }
        }
        return result;
    }

    private static List<QuestObjectBase> objects(BaseQuestFile file) {
        List<QuestObjectBase> result = new ArrayList<>(); result.add(chapter(file, "main")); result.add(chapter(file, "dependency"));
        for (String leaf : QUESTS) { var q = quest(file, leaf); result.add(q); result.addAll(q.getTasks()); }
        return result;
    }
    private static void requireFields(CompoundTag expected, CompoundTag actual, String label) {
        for (String key : expected.getAllKeys()) require(Objects.equals(expected.get(key), actual.get(key)), "Native disk field differs: " + label + "/" + key);
    }
    private static CompoundTag child(CompoundTag parent, String key, String id) {
        CompoundTag found = null;
        for (Tag value : parent.getList(key, Tag.TAG_COMPOUND)) {
            var tag = (CompoundTag) value;
            if (tag.getString("id").equals(id)) { require(found == null, "Duplicate native disk ID " + id); found = tag; }
        }
        return Objects.requireNonNull(found, "Missing native disk object " + id);
    }
    private static Path overridePath(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve("data/contactquests_quest_overrides.dat");
    }
    private static List<String> strings(CompoundTag tag, String key) { return tag.getList(key, Tag.TAG_STRING).stream().map(Tag::getAsString).toList(); }
    private static String textKey(QuestObjectBase object, TranslationKey key) { return object.getObjectType().getId() + "." + object.getCodeString() + "." + key.getName(); }
    @SuppressWarnings("unchecked")
    private static Map<String, TranslationTable> tables(BaseQuestFile file) throws Exception {
        var field = TranslationManager.class.getDeclaredField("map"); field.setAccessible(true);
        return (Map<String, TranslationTable>) field.get(file.getTranslationManager());
    }
    @SuppressWarnings("unchecked")
    private static Map<String, Object> overrideMap(Quest quest, String name) throws Exception {
        var field = Quest.class.getDeclaredField(name); field.setAccessible(true); return (Map<String, Object>) field.get(quest);
    }
    private static List<String> differingKeys(Map<String, String> expected, Map<String, String> actual) {
        var keys = new java.util.TreeSet<>(expected.keySet()); keys.addAll(actual.keySet());
        return keys.stream().filter(k -> !Objects.equals(expected.get(k), actual.get(k))).toList();
    }
    private static void pass(String suffix, String message) { ContactQuestsTestServerController.recordCase(CASE + suffix, true, message); }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private ContactQuestsGraphOptionsAssertions() {}
}
