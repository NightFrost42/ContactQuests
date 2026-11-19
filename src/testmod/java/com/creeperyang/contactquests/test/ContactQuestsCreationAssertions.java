package com.creeperyang.contactquests.test;

import com.creeperyang.contactquests.quest.reward.*;
import com.creeperyang.contactquests.quest.task.*;
import dev.ftb.mods.ftbquests.quest.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import java.nio.file.*;
import java.util.*;

/** Inspect actual server/client objects created by Rhino, then compare saved native fields in a new JVM. */
public final class ContactQuestsCreationAssertions {
    private static String chapterId = "", questId = "";
    private static String chapterNbt = "", questNbt = "";
    private static List<String> taskNbt = List.of(), rewardNbt = List.of();
    private static Path path() { return Path.of(System.getProperty("contactquests.test.snapshot") + ".creation.json"); }

    public static void capture(Chapter chapter, Quest quest) {
        var provider = ServerQuestFile.INSTANCE.server.registryAccess();
        require(verify(chapter, quest, provider), "Native fields from the Rhino creation bundle were ignored: chapter="
                + write(chapter, provider) + "; quest=" + write(quest, provider));
        chapterId = chapter.getCodeString(); questId = quest.getCodeString();
        chapterNbt = write(chapter, provider); questNbt = write(quest, provider);
        taskNbt = quest.getTasks().stream().map(t -> write(t, provider)).toList();
        rewardNbt = quest.getRewards().stream().map(r -> write(r, provider)).toList();
    }
    public static void save() throws Exception {
        var json = new com.google.gson.JsonObject();
        json.addProperty("chapter", chapterId); json.addProperty("quest", questId);
        json.addProperty("chapterNbt", chapterNbt); json.addProperty("questNbt", questNbt);
        json.add("tasks", new com.google.gson.Gson().toJsonTree(taskNbt));
        json.add("rewards", new com.google.gson.Gson().toJsonTree(rewardNbt));
        Files.writeString(path(), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json));
    }
    public static String questId() { return questId; }
    public static String diagnostic(Chapter chapter, Quest quest, net.minecraft.core.HolderLookup.Provider provider) {
        return "chapter=" + write(chapter, provider) + "; quest=" + write(quest, provider)
                + "; title=" + quest.getRawTitle() + "; subtitle=" + quest.getRawSubtitle() + "; desc=" + quest.getRawDescription()
                + "; chapterTitle=" + chapter.getRawTitle() + "; chapterSubtitle=" + chapter.getRawSubtitle()
                + "; tasks=" + quest.getTasks().stream().map(t -> t.getClass().getSimpleName() + ":" + t.getRawTitle() + ":" + write(t, provider)).toList()
                + "; rewards=" + quest.getRewards().stream().map(r -> r.getClass().getSimpleName() + ":" + write(r, provider)).toList();
    }
    public static void rejoin() throws Exception {
        var saved = com.google.gson.JsonParser.parseString(Files.readString(path())).getAsJsonObject();
        chapterId = saved.get("chapter").getAsString(); questId = saved.get("quest").getAsString();
        var file = ServerQuestFile.INSTANCE; var provider = file.server.registryAccess();
        var chapter = file.getChapter(Long.parseUnsignedLong(chapterId, 16)); var quest = file.getQuest(Long.parseUnsignedLong(questId, 16));
        require(verify(chapter, quest, provider) && write(chapter, provider).equals(saved.get("chapterNbt").getAsString())
                && write(quest, provider).equals(saved.get("questNbt").getAsString()), "New JVM changed full creation chapter/quest settings");
        require(quest.getTasks().size() == saved.getAsJsonArray("tasks").size() && quest.getRewards().size() == saved.getAsJsonArray("rewards").size(), "Creation children lost");
        int i = 0; for (var t : quest.getTasks()) require(write(t, provider).equals(saved.getAsJsonArray("tasks").get(i++).getAsString()), "Native creation task fields changed on disk");
        i = 0; for (var r : quest.getRewards()) require(write(r, provider).equals(saved.getAsJsonArray("rewards").get(i++).getAsString()), "Native creation reward fields changed on disk");
        ContactQuestsTestServerController.recordCase("server.features.creation_rejoin", true, "Full Rhino creation fields and children restored identically by a new native JVM");
    }
    public static boolean verify(Chapter chapter, Quest quest, net.minecraft.core.HolderLookup.Provider provider) {
        if (chapter == null || quest == null || quest.getChapter() != chapter || quest.getTasks().size() != 3 || quest.getRewards().size() != 4) return false;
        var c = tag(chapter, provider); var q = tag(quest, provider);
        boolean common = chapter.getRawTitle().equals("Creation chapter") && chapter.getRawSubtitle().equals(List.of("Creation subtitle"))
                && c.getString("default_quest_shape").equals("circle") && c.getDouble("default_quest_size") == 1.5
                && c.getInt("default_min_width") == 220 && c.getBoolean("disable_toast")
                && c.getString("progression_mode").equals("linear") && c.getBoolean("require_sequential_tasks")
                && c.getBoolean("hide_quest_until_deps_complete") && c.getBoolean("hide_quest_details_until_startable")
                && !c.contains("consume_items") && c.getList("tags",8).getString(0).equals("CreationChapter")
                && quest.getRawTitle().equals("Creation bundle") && quest.getRawSubtitle().equals("Creation quest subtitle")
                && quest.getRawDescription().equals(List.of("One","Two")) && quest.getX() == 6 && quest.getY() == 7
                && q.getDouble("size") == 2 && q.getDouble("icon_scale") == 1.2 && q.getInt("min_width") == 240
                && q.getString("shape").equals("circle") && q.getString("guide_page").equals("creation")
                && q.getList("cq_req_tags",8).getString(0).equals("CreationTag") && q.getString("cq_tag_mode").equals("ALL")
                && q.getString("cq_mutex_mode").equals("NUMBER") && q.getInt("cq_mutex_count") == 1
                && q.getBoolean("cq_hide_no_tags") && q.getBoolean("cq_hide_locked")
                && q.getList("dependencies",8).size() == 1 && q.getCompound("dep_control_pts").getList(q.getList("dependencies",8).getString(0),6).size() == 4
                && q.getBoolean("hide_details_until_startable") && q.getBoolean("hide_dependent_lines") && q.getBoolean("hide_lock_icon")
                && q.getInt("repeat_cooldown") == 8 && q.getBoolean("optional") && q.getInt("max_completable_dependents") == 2
                && q.getBoolean("ignore_reward_blocking") && q.getBoolean("disable_toast");
        var tasks = new ArrayList<>(quest.getTasks()); var rewards = new ArrayList<>(quest.getRewards());
        return common && tasks.get(0) instanceof ParcelTask p && p.getCount() == 5 && p.getItemStack().is(Items.DIAMOND)
                && p.getTargetAddressee().equals("CQCreation") && p.getRawTitle().equals("Parcel title")
                && tag(p,provider).getBoolean("optional_task") && tag(p,provider).getBoolean("disable_toast")
                && tasks.get(1) instanceof RedPacketTask red && red.getCount() == 2 && red.getItemStack().is(Items.GOLD_INGOT)
                && red.getBlessing().equals("Creation blessing")
                && red.getMatchComponents() == dev.ftb.mods.ftbquests.integration.item_filtering.ItemMatchingSystem.ComponentMatchType.STRICT
                && tasks.get(2) instanceof PostcardTask card && card.getPostcardText().equals("Creation text")
                && rewards.get(0) instanceof ParcelReward parcel && parcel.getItem().is(Items.APPLE) && parcel.getCount() == 3
                && parcel.getHideContents() && parcel.getExcludeFromClaimAll()
                && rewards.get(1) instanceof RedPacketReward rr && rr.getItem().is(Items.EMERALD) && rr.getCount() == 2 && rr.getHideContents()
                && rewards.get(2) instanceof PostcardReward cr && cr.getPostcardText().equals("Creation card") && cr.getHideContents()
                && rewards.get(3) instanceof ParcelChoiceReward choice && choice.getHideContents() && choice.getOptionLabels().equals(List.of("Alpha","Beta"))
                && choice.getTable() != null && choice.getTable().getWeightedRewards().size() == 2
                && choice.getTable().getWeightedRewards().get(0).getReward().getRawTitle().equals("Creation apple option")
                && choice.getTable().getWeightedRewards().get(1).getReward().getRawTitle().equals("Creation diamond option");
    }
    private static CompoundTag tag(QuestObjectBase object, net.minecraft.core.HolderLookup.Provider provider) { var t = new CompoundTag(); object.writeData(t, provider); return t; }
    private static String write(QuestObjectBase object, net.minecraft.core.HolderLookup.Provider provider) { return tag(object, provider).toString(); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
