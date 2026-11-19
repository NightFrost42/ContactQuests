package com.creeperyang.contactquests.test;

import com.creeperyang.contactquests.compat.kubejs.ContactKubeJSPlugin;
import com.creeperyang.contactquests.compat.kubejs.QuestOverrideSavedData;
import com.creeperyang.contactquests.quest.reward.*;
import com.creeperyang.contactquests.quest.task.*;
import com.flechazo.contact.common.component.ContactDataComponents;
import com.flechazo.contact.common.item.PostcardItem;
import com.google.gson.*;
import dev.ftb.mods.ftbquests.quest.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;

import java.nio.file.*;
import java.util.*;

/** Independent observations of public calls made by the real Rhino script. */
public final class ContactQuestsPublicApiAssertions {
    private ContactQuestsPublicApiAssertions() {}
    private static Path snapshot() {
        return Path.of(System.getProperty("contactquests.test.snapshot") + ".public-api.json");
    }
    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
    private static CompoundTag tag(QuestObjectBase object, ServerPlayer player) {
        CompoundTag tag = new CompoundTag(); object.writeData(tag, player.registryAccess()); return tag;
    }
    public static boolean factories(ItemStack defaults, ItemStack ender) {
        return defaults.getItem() instanceof PostcardItem && ender.getItem() instanceof PostcardItem
                && defaults.getOrDefault(ContactDataComponents.POSTCARD_STYLE_ID.get(), net.minecraft.resources.ResourceLocation.parse("contact:default"))
                        .equals(net.minecraft.resources.ResourceLocation.parse("contact:default"))
                && defaults.getOrDefault(ContactDataComponents.POSTCARD_TEXT.get(), "").isEmpty()
                && defaults.getOrDefault(ContactDataComponents.POSTCARD_SENDER.get(), "").isEmpty()
                && !defaults.getOrDefault(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY)
                        .copyTag().contains("ContactQuestsUnlockTags")
                && ender.getOrDefault(ContactDataComponents.POSTCARD_TEXT.get(), "").equals("one\ntwo")
                && ender.getOrDefault(ContactDataComponents.POSTCARD_SENDER.get(), "").equals("CQApiSender")
                && ender.getItem() != defaults.getItem();
    }
    public static CompoundTag questTag(ServerPlayer player, String id) {
        return tag(ServerQuestFile.INSTANCE.getQuest(Long.parseUnsignedLong(id, 16)), player);
    }
    public static void verifyAndCapture(ServerPlayer player, String questId) throws Exception {
        var quest = ServerQuestFile.INSTANCE.getQuest(Long.parseUnsignedLong(questId, 16));
        check(quest != null && quest.getTasks().size() == 3 && quest.getRewards().size() == 3, "Public API graph missing children");
        // Change live values, then require SavedData to restore all six independently.
        for (var task : quest.getTasks()) {
            ((ContactTask) task).setTargetAddressee("MustBeRestored");
            if (task instanceof PostcardTask card) card.setPostcardStyle("contact:default");
        }
        for (var reward : quest.getRewards()) {
            ((ParcelRewardBase) reward).setTargetAddressee("MustBeRestored");
            if (reward instanceof PostcardReward card) card.setPostcardStyle("contact:default");
        }
        ContactKubeJSPlugin.loadPersistentData(player.server);
        assertGraph(quest);
        var saved = QuestOverrideSavedData.Companion.get(player.serverLevel());
        var data = new JsonObject(); data.addProperty("pid", ProcessHandle.current().pid()); data.addProperty("quest", questId);
        data.addProperty("player", player.getUUID().toString()); data.add("objects", new JsonArray());
        List<QuestObjectBase> objects = new ArrayList<>(); objects.add(quest);
        objects.addAll(quest.getTasks()); objects.addAll(quest.getRewards());
        for (var object : objects) {
            JsonObject value = new JsonObject(); value.addProperty("id", object.getCodeString());
            value.addProperty("type", object.getClass().getName()); value.addProperty("nbt", tag(object, player).toString());
            var override = saved.getOverride(object.getId());
            value.addProperty("override", override == null ? "" : override.toString());
            data.getAsJsonArray("objects").add(value);
        }
        Files.writeString(snapshot(), new GsonBuilder().setPrettyPrinting().create().toJson(data));
        ContactQuestsTestServerController.recordCase("kubejs.public_api.saved_reload", true,
                "Real Rhino changed all six objects; loader restored recipients/styles/text/count/bonus; exact NBT saved for a different JVM");
    }
    private static void assertGraph(Quest quest) {
        var parcel = (ParcelTask) quest.getTasks().stream().filter(ParcelTask.class::isInstance).findFirst().orElseThrow();
        var red = (RedPacketTask) quest.getTasks().stream().filter(RedPacketTask.class::isInstance).findFirst().orElseThrow();
        var card = (PostcardTask) quest.getTasks().stream().filter(PostcardTask.class::isInstance).findFirst().orElseThrow();
        check(parcel.getCount() == 5 && parcel.getItemStack().is(Items.EMERALD)
                && parcel.getTargetAddressee().equals("CQApiSavedParcel")
                && red.getCount() == 6 && red.getItemStack().is(Items.IRON_INGOT)
                && red.getBlessing().equals("saved blessing") && red.getTargetAddressee().equals("CQApiSavedRed")
                && card.getPostcardStyle().equals("contact:creeper") && card.getPostcardText().equals("saved postcard")
                && card.getTargetAddressee().equals("CQApiSavedCard"), "Saved task values not restored");
        var parcelReward = (ParcelReward) quest.getRewards().stream().filter(r -> r.getClass() == ParcelReward.class).findFirst().orElseThrow();
        var redReward = (RedPacketReward) quest.getRewards().stream().filter(RedPacketReward.class::isInstance).findFirst().orElseThrow();
        var cardReward = (PostcardReward) quest.getRewards().stream().filter(PostcardReward.class::isInstance).findFirst().orElseThrow();
        check(parcelReward.getItem().is(Items.BREAD) && parcelReward.getCount() == 7 && parcelReward.getRandomBonus() == 2
                && parcelReward.getTargetAddressee().equals("CQApiSavedParcel")
                && redReward.getItem().is(Items.LAPIS_LAZULI) && redReward.getCount() == 8 && redReward.getRandomBonus() == 3
                && redReward.getBlessing().equals("saved reward blessing") && redReward.getTargetAddressee().equals("CQApiSavedRed")
                && cardReward.getPostcardStyle().equals("contact:creeper") && cardReward.getPostcardText().equals("saved reward card")
                && cardReward.getTargetAddressee().equals("CQApiSavedCard"), "Saved reward values not restored");
    }
    public static void rejoin(ServerPlayer player) {
        try {
            var json = JsonParser.parseString(Files.readString(snapshot())).getAsJsonObject();
            check(json.get("pid").getAsLong() != ProcessHandle.current().pid(), "Not a different JVM");
            check(json.get("player").getAsString().equals(player.getUUID().toString()), "API persistence changed player");
            var file = ServerQuestFile.INSTANCE;
            assertGraph(file.getQuest(Long.parseUnsignedLong(json.get("quest").getAsString(), 16)));
            var saved = QuestOverrideSavedData.Companion.get(player.serverLevel());
            for (var element : json.getAsJsonArray("objects")) {
                var expected = element.getAsJsonObject();
                var object = file.getBase(Long.parseUnsignedLong(expected.get("id").getAsString(), 16));
                check(object != null && object.getClass().getName().equals(expected.get("type").getAsString())
                        && tag(object, player).toString().equals(expected.get("nbt").getAsString()), "Public API object changed across restart: " + expected);
                var override = saved.getOverride(object.getId());
                check((override == null ? "" : override.toString()).equals(expected.get("override").getAsString()),
                        "Saved override changed across restart: " + object.getCodeString());
            }
            ContactQuestsTestServerController.recordCase("server.public_api.rejoin", true,
                    "New JVM restored all six real Rhino edited tasks/rewards and exact native NBT/overrides without reseeding");
        } catch (Exception error) {
            ContactQuestsTestServerController.recordCase("server.public_api.rejoin", false, error.toString());
        }
    }
}
