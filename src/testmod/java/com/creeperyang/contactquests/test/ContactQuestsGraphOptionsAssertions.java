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
import dev.ftb.mods.ftbquests.quest.task.CheckmarkTask;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
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
    private static CompoundTag tag(QuestObjectBase object) {
        var result = new CompoundTag(); object.writeData(result); return result;
    }

    public static List<String> locales() {
        return List.of("en_us", "zh_cn");
    }

    /** Validate a fresh fixture; 2001.4.22 has no visual presets to register. */
    public static void prepare(ServerPlayer player) {
        var file = ServerQuestFile.INSTANCE;
        require(file.server == player.server && player.server.isSameThread() && !Boolean.getBoolean("contactquests.test.rejoin"), "Prepare only in the first real server JVM");
        require(!Files.exists(snapshotPath()) && file.getBase(number("chapter", "main")) == null
                && file.getBase(number("chapter", "dependency")) == null, "Do not reseed an existing graph-options fixture");
    }

    public static void createdIds(ServerPlayer player) {
        var file = ServerQuestFile.INSTANCE;
        require(customIdMatches(tag(chapter(file, "main")), "chapter-created", "chapter-created")
                && customIdMatches(tag(quest(file, "focus")), "quest-created", "quest-created"),
                "Rhino createChapter/createQuest did not retain their explicit customIds");
    }

    public static void verifyOptions(ServerPlayer player) {
        verifyNativeFields(ServerQuestFile.INSTANCE);
        pass("custom_id_autofocus", "Native create/update Chapter and Quest customIds; same-chapter autofocus resolves to the real focus quest at 6,-4");
        pass("presets", "Direct chapter defaults inherit circle/1.25 and explicit quest options retain square/1.75; unsupported presets leave no native fields");
        pass("icon_components", "Rhino Map icons preserve gold/bold custom names and exact NBT; native icon counts normalize from 9 to 1");
        pass("task_dependency", "Real quest dependency references the other quest's CheckmarkTask with exact native ID and graph object");
        pass("chapter_dependency", "Real quest dependency references another native Chapter with exact native ID and graph object");
    }

    /** Also callable with ClientQuestFile.INSTANCE; observes fields, not a claimed GUI rendering. */
    public static void verifyClientFields(BaseQuestFile file, RegistryAccess registryAccess) throws Exception {
        verifyNativeFields(file);
        verifyTextState(file, null, "global");
    }

    private static void verifyNativeFields(BaseQuestFile file) {
        var main = chapter(file, "main"); var other = chapter(file, "dependency");
        var focus = quest(file, "focus"); var inherited = quest(file, "inherited");
        var prerequisite = quest(file, "prerequisite"); var consumer = quest(file, "chapter-consumer");
        var task = file.getTask(number("task", "prerequisite-task"));
        var focusTask = file.getTask(number("task", "focus-task"));
        require(task instanceof CheckmarkTask && task.getQuest() == prerequisite && focusTask instanceof CheckmarkTask
                        && focusTask.getQuest() == focus && focus.getChapter() == main && prerequisite.getChapter() == other,
                "Task/quest/chapter membership is wrong");
        var c = tag(main); var q = tag(focus);
        require(customIdMatches(c, "chapter-created", "chapter")
                        && customIdMatches(q, "quest-created", "quest")
                        && c.getString("autofocus_id").equals(focus.getCodeString())
                        && file.getQuest(Long.parseUnsignedLong(c.getString("autofocus_id"), 16)) == focus
                        && focus.getX() == 6 && focus.getY() == -4,
                "Legal customId/autofocus did not reach native NBT and resolved focus");
        var inheritedTag = tag(inherited);
        require(!c.contains("preset") && !q.contains("preset") && !inheritedTag.contains("preset")
                        && c.getString("default_quest_shape").equals("circle") && c.getDouble("default_quest_size") == 1.25
                        && q.getString("shape").equals("square") && q.getDouble("size") == 1.75
                        && focus.getShape().equals("square") && focus.getSize() == 1.75
                        && inherited.getShape().equals("circle") && inherited.getSize() == 1.25,
                "Direct shape/size options or chapter inheritance is wrong, or unsupported preset was retained");
        verifyIcon(c, true); verifyIcon(q, false);
        require(file.getBase(task.getId()) == task && q.getList("dependencies", Tag.TAG_STRING).size() == 1
                        && q.getList("dependencies", Tag.TAG_STRING).getString(0).equals(task.getCodeString()), "Task dependency changed type/ID");
        var dependencies = tag(consumer).getList("dependencies", Tag.TAG_STRING);
        require(file.getBase(other.getId()) == other && dependencies.size() == 1 && dependencies.getString(0).equals(other.getCodeString())
                        && strings(tag(prerequisite), "dependencies").isEmpty(), "Chapter dependency or rejected cycle changed graph");
    }

    private static void verifyIcon(CompoundTag object, boolean chapter) {
        var actual = ItemStack.of(object.getCompound("icon"));
        var expected = new ItemStack(chapter ? Items.DIAMOND : Items.EMERALD);
        // Match the fixture's literal display.Name JSON as well as its custom NBT.
        expected.getOrCreateTagElement("display").putString("Name", "{\"text\":\""
                + (chapter ? "Graph chapter icon" : "Graph quest icon") + "\",\"color\":\"gold\",\"bold\":true}");
        var custom = expected.getOrCreateTag();
        custom.putString("cq_graph_options", chapter ? "chapter" : "quest"); custom.putString("sentinel", "keep-exact");
        require(ItemStack.matches(actual, expected), "Rhino NBT icon differs from exact native ItemStack: " + actual.save(new CompoundTag()));
    }

    /** Extends rejection snapshots with native raw text and synchronized per-locale overrides. */
    public static Map<String, String> snapshot(ServerLevel level) throws Exception {
        Map<String, String> values = new TreeMap<>(ContactQuestsRhinoBoundaryAssertions.snapshot(level));
        var file = ServerQuestFile.INSTANCE;
        for (var chapter : file.getAllChapters()) values.put(chapter.getCodeString() + ":rawText", rawText(chapter).toString());
        for (var chapter : file.getAllChapters()) for (var quest : chapter.getQuests()) {
            values.put(quest.getCodeString() + ":rawText", rawText(quest).toString());
            values.put(quest.getCodeString() + ":dependants", file.getAllChapters().stream()
                    .flatMap(ch -> ch.getQuests().stream())
                    .filter(q -> strings(tag(q), "dependencies").contains(quest.getCodeString()))
                    .map(q -> q.getCodeString()).sorted().toList().toString());
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
        // 2001 stores native text on each object; locale overrides are synchronized separately.
        for (String leaf : List.of("saved-description", "global-description")) {
            var q = quest(file, leaf);
            boolean savedClear = leaf.equals("saved-description") && !phase.equals("seeded");
            boolean globalClear = leaf.equals("global-description") && phase.equals("global");
            var memory = overrideMap(q, "contactQuests$descOverrides");
            var titles = overrideMap(q, "contactQuests$titleOverrides");
            var tag = saved == null ? null : Objects.requireNonNull(saved.getOverride(q.getId()));
            var nativeText = rawText(q);
            require(strings(nativeText, "description").equals(globalClear ? List.of() : NATIVE_DESC),
                    "Native rawDescription differs for " + leaf + "/" + phase);
            for (String locale : locales()) {
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
            for (String locale : locales()) {
                require(TITLE.equals(titles.getOrDefault(locale, nativeText.getString("title")))
                                && memory.getOrDefault(locale, strings(nativeText, "description"))
                                .equals(savedClear || globalClear ? List.of() : SAVED_DESC),
                        "Resolved per-locale quest text differs for " + leaf + "/" + locale + "/" + phase);
            }
        }
    }

    public static void verifyAndCapture(ServerPlayer player) throws Exception {
        verifyNativeFields(ServerQuestFile.INSTANCE); verifyDescriptions(player, "global");
        var file = ServerQuestFile.INSTANCE;
        file.markDirty(); file.saveNow(); player.server.saveEverything(false, true, true);
        var values = persistentState(player);
        var json = new JsonObject(); json.addProperty("pid", ProcessHandle.current().pid());
        json.addProperty("player", player.getUUID().toString()); json.addProperty("chapter", id("chapter", "main"));
        json.addProperty("focus", id("quest", "focus")); json.addProperty("savedDescription", id("quest", "saved-description"));
        json.addProperty("globalDescription", id("quest", "global-description"));
        var gson = new GsonBuilder().setPrettyPrinting().create(); json.add("state", gson.toJsonTree(values));
        Files.createDirectories(snapshotPath().toAbsolutePath().getParent()); Files.writeString(snapshotPath(), gson.toJson(json));
        pass("persisted", "Actual native chapter SNBT, direct shape/size defaults, raw text, per-locale overrides and compressed world SavedData match runtime; PID/IDs/exact state captured for new JVM");
    }

    /** Main thread calls this from verify_rejoin. Never seed, load overrides manually or save here. */
    public static void rejoin(ServerPlayer player) {
        try {
            var json = JsonParser.parseString(Files.readString(snapshotPath())).getAsJsonObject();
            require(Boolean.getBoolean("contactquests.test.rejoin") && json.get("pid").getAsLong() != ProcessHandle.current().pid(), "Graph-options rejoin must be a genuinely different JVM");
            require(json.get("player").getAsString().equals(player.getUUID().toString()), "Graph-options rejoin changed real player");
            verifyNativeFields(ServerQuestFile.INSTANCE); verifyDescriptions(player, "global");
            Map<String, String> expected = new TreeMap<>();
            json.getAsJsonObject("state").entrySet().forEach(e -> expected.put(e.getKey(), e.getValue().getAsString()));
            Map<String, String> actual = persistentState(player);
            require(expected.equals(actual), "Graph-options state changed across JVM; differing keys=" + differingKeys(expected, actual));
            ContactQuestsTestServerController.recordCase("server.graph_options.rejoin", true,
                    "Different JVM restored exact native options/dependencies/icons/shape/size and both empty-description semantics from disk without reseeding");
        } catch (Exception error) { ContactQuestsTestServerController.recordCase("server.graph_options.rejoin", false, error.toString()); }
    }

    private static Map<String, String> persistentState(ServerPlayer player) throws Exception {
        var file = ServerQuestFile.INSTANCE;
        var saved = QuestOverrideSavedData.Companion.get(player.serverLevel());
        Map<String, String> result = new TreeMap<>();
        var rootDisk = Objects.requireNonNull(SNBT.read(file.getFolder().resolve("data.snbt")), "Missing native root data.snbt");
        require(!rootDisk.contains("presets") && !tag(file).contains("presets"), "Unsupported visual presets reached native root data");
        // Retain the shared snapshot keys, recording the supported effective visuals.
        result.put("preset:default", "shape=" + quest(file, "inherited").getShape() + ",size=" + quest(file, "inherited").getSize());
        result.put("preset:explicit", "shape=" + quest(file, "focus").getShape() + ",size=" + quest(file, "focus").getSize());
        for (String leaf : List.of("main", "dependency")) {
            var chapter = chapter(file, leaf);
            var disk = chapterDisk(file, chapter);
            var chapterTag = tag(chapter);
            require(disk.getString("id").equals(chapter.getCodeString()), "Native chapter disk ID mismatch");
            requireFields(chapterTag, disk, "chapter " + leaf);
            require(disk.getList("quests", Tag.TAG_COMPOUND).size() == chapter.getQuests().size(), "Native chapter lost/added a quest on disk");
            for (var q : chapter.getQuests()) {
                var questDisk = child(disk, "quests", q.getCodeString());
                requireFields(tag(q), questDisk, "quest " + q.getCodeString());
                require(questDisk.getList("tasks", Tag.TAG_COMPOUND).size() == q.getTasks().size(), "Native task count differs on disk");
                for (var task : q.getTasks()) requireFields(tag(task), child(questDisk, "tasks", task.getCodeString()), "task " + task.getCodeString());
            }
            // Chapter order is a global editor concern; other fixtures may insert/remove preceding chapters.
            disk.remove("order_index");
            result.put("diskChapter:" + leaf, disk.toString());
            result.put("nativeChapter:" + leaf, chapterTag.toString());
        }
        for (String leaf : QUESTS) {
            var q = quest(file, leaf); result.put("nativeQuest:" + leaf, tag(q).toString());
            for (var task : q.getTasks()) result.put("nativeTask:" + task.getCodeString(), tag(task).toString());
        }
        var world = NbtIo.readCompressed(overridePath(player.serverLevel()).toFile()).getCompound("data");
        for (String leaf : List.of("saved-description", "global-description")) {
            var q = quest(file, leaf); var override = Objects.requireNonNull(saved.getOverride(q.getId()));
            CompoundTag diskOverride = null;
            for (Tag value : world.getList("QuestOverrides", Tag.TAG_COMPOUND)) {
                var entry = (CompoundTag) value;
                if (entry.getLong("id") == q.getId()) { require(diskOverride == null, "Duplicate persisted quest override"); diskOverride = entry.getCompound("data"); }
            }
            require(override.equals(diskOverride), "World compressed override differs from runtime for " + leaf);
            result.put("override:" + leaf, override.toString());
            result.put("resolvedDescription:" + leaf, overrideMap(q, "contactQuests$descOverrides")
                    .getOrDefault("en_us", strings(rawText(q), "description")).toString());
        }
        // Preserve the snapshot's string-valued translation keys without nonexistent lang tables.
        for (var object : objects(file)) {
            var nativeText = rawText(object);
            for (String locale : locales()) for (String key : List.of("title", "subtitle", "description")) {
                Object value = nativeText.get(key);
                if (object instanceof Quest q) {
                    String field = switch (key) {
                        case "title" -> "contactQuests$titleOverrides";
                        case "subtitle" -> "contactQuests$subtitleOverrides";
                        default -> "contactQuests$descOverrides";
                    };
                    var overrides = overrideMap(q, field);
                    if (overrides.containsKey(locale)) value = overrides.get(locale);
                }
                result.put("translation:" + locale + ":" + object.getObjectType().getId() + "."
                        + object.getCodeString() + "." + key, Objects.toString(value, "<absent>"));
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
    private static boolean customIdMatches(CompoundTag tag, String created, String current) {
        if (tag.contains("custom_id", Tag.TAG_STRING)) return tag.getString("custom_id").equals(KEY + current);
        // Native readers which migrate custom_id to tags preserve both create/update values.
        return strings(tag, "tags").equals(created.equals(current)
                ? List.of(KEY + current) : List.of(KEY + created, KEY + current));
    }
    private static List<String> strings(CompoundTag tag, String key) { return tag.getList(key, Tag.TAG_STRING).stream().map(Tag::getAsString).toList(); }
    private static CompoundTag chapterDisk(ServerQuestFile file, Chapter chapter) throws Exception {
        CompoundTag found = null;
        try (var paths = Files.list(file.getFolder().resolve("chapters"))) {
            for (Path path : paths.filter(p -> p.getFileName().toString().endsWith(".snbt")).toList()) {
                var disk = SNBT.read(path);
                if (disk != null && disk.getString("id").equals(chapter.getCodeString())) {
                    require(found == null, "Duplicate native chapter disk ID " + chapter.getCodeString());
                    found = disk;
                }
            }
        }
        return Objects.requireNonNull(found, "Missing native chapter file " + chapter.getCodeString());
    }
    private static CompoundTag rawText(QuestObjectBase object) throws Exception {
        var result = new CompoundTag();
        for (String key : List.of("title", "subtitle", "description")) {
            String name = "raw" + Character.toUpperCase(key.charAt(0)) + key.substring(1);
            Object value = readField(object, name, key.equals("title") || object instanceof Quest);
            if (value instanceof String text) result.putString(key, text);
            else if (value instanceof List<?> lines) {
                var list = new ListTag();
                for (Object line : lines) list.add(StringTag.valueOf((String) line));
                result.put(key, list);
            }
        }
        return result;
    }
    private static Object readField(Object object, String name, boolean required) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object);
            } catch (NoSuchFieldException ignored) { }
        }
        if (required) throw new NoSuchFieldException(object.getClass().getName() + "." + name);
        return null;
    }
    @SuppressWarnings("unchecked")
    private static Map<String, Object> overrideMap(Quest quest, String name) throws Exception {
        // Observe the per-locale maps synchronized by ServerExtensionData and saved overrides.
        return (Map<String, Object>) readField(quest, name, true);
    }
    private static List<String> differingKeys(Map<String, String> expected, Map<String, String> actual) {
        var keys = new java.util.TreeSet<>(expected.keySet()); keys.addAll(actual.keySet());
        return keys.stream().filter(k -> !Objects.equals(expected.get(k), actual.get(k))).toList();
    }
    private static void pass(String suffix, String message) { ContactQuestsTestServerController.recordCase(CASE + suffix, true, message); }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private ContactQuestsGraphOptionsAssertions() {}
}
