package com.creeperyang.contactquests.test;

import com.creeperyang.contactquests.client.gui.ContactConfigScreen;
import com.creeperyang.contactquests.client.gui.ValidParcelItemsScreen;
import com.creeperyang.contactquests.client.gui.ValidPostcardItemsScreen;
import com.creeperyang.contactquests.client.gui.ValidRedPacketItemsScreen;
import com.creeperyang.contactquests.client.util.TooltipUtils;
import com.creeperyang.contactquests.quest.reward.*;
import com.creeperyang.contactquests.quest.task.*;
import com.creeperyang.contactquests.quest.DependencyMode;
import com.creeperyang.contactquests.quest.MutexMode;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.client.gui.quests.RewardButton;
import dev.ftb.mods.ftbquests.client.gui.quests.TaskButton;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

final class ContactQuestsLocalizationTests {
    private static final Pattern PLACEHOLDER = Pattern.compile("%(?:(\\d+)\\$)?s");
    private static String originalLanguage;
    private static Preview pendingPreview;

    private record Preview(String locale, net.minecraft.client.gui.screens.Screen previousScreen,
                           dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen nativeScreen,
                           Quest quest, ParcelTask parcel, RedPacketTask packet, PostcardTask postcard) {}

    private ContactQuestsLocalizationTests() {}

    static void setLanguage(String locale) {
        var mc = Minecraft.getInstance();
        if (originalLanguage == null) originalLanguage = mc.getLanguageManager().getSelected();
        if (locale.equals("restore")) locale = originalLanguage;
        mc.getLanguageManager().setSelected(locale);
        mc.options.languageCode = locale;
        mc.getLanguageManager().onResourceManagerReload(mc.getResourceManager());
        mc.gui.getChat().rescaleChat();
    }

    static boolean run(String locale) {
        String id = "client.localization." + locale;
        var mc = Minecraft.getInstance();
        var previousScreen = pendingPreview == null ? mc.screen : pendingPreview.previousScreen();
        boolean completed = false;
        try {
            if (pendingPreview == null) {
            setLanguage(locale);
            JsonObject english = language("en_us"), chinese = language("zh_cn");
            require(english.keySet().equals(chinese.keySet()), "English and Chinese translation keys differ");
            JsonObject active = locale.equals("zh_cn") ? chinese : english;
            for (var entry : active.entrySet()) {
                String template = entry.getValue().getAsString();
                int sequential = 0, count = 0;
                var matcher = PLACEHOLDER.matcher(template);
                while (matcher.find()) {
                    int argument = matcher.group(1) == null ? ++sequential : Integer.parseInt(matcher.group(1));
                    count = Math.max(count, argument);
                }
                Object[] arguments = new Object[count];
                for (int i = 0; i < count; i++) arguments[i] = "CQ-argument-" + (i + 1);
                String expected = String.format(Locale.ROOT, template, arguments);
                String actual = Component.translatable(entry.getKey(), arguments).getString();
                require(actual.equals(expected), "Translation or argument mismatch for " + entry.getKey() + ": " + actual);
            }

            var config = new ContactConfigScreen(previousScreen == null
                    ? new net.minecraft.client.gui.screens.TitleScreen() : previousScreen);
            mc.setScreen(config);
            List<String> fields = config.children().stream().filter(c -> c instanceof EditBox)
                    .map(c -> ((EditBox) c).getMessage().getString()).toList();
            require(fields.contains(Component.translatable("contactquests.config.auto_fill_speed").getString())
                    && fields.contains(Component.translatable("contactquests.config.retry_interval").getString()),
                    "Actual configuration text fields have untranslated labels: " + fields);

            var file = ClientQuestFile.INSTANCE;
            var chapter = new Chapter(0x7e00000000000001L, file, file.getDefaultChapterGroup(), "cq_localization");
            file.getDefaultChapterGroup().addChapter(chapter);
            var quest = new Quest(0x7e00000000000002L, chapter);
            chapter.addQuest(quest);
            var parcel = new ParcelTask(0x7e00000000000003L, quest).setStackAndCount(new ItemStack(Items.DIAMOND), 2);
            var packet = new RedPacketTask(0x7e00000000000004L, quest).setStackAndCount(new ItemStack(Items.GOLD_INGOT), 2);
            var postcard = new PostcardTask(0x7e00000000000005L, quest);
            String longRecipient = "CQ-long-recipient-".repeat(14);
            parcel.setTargetAddressee(longRecipient);
            packet.setTargetAddressee(longRecipient);
            packet.setBlessing("CQ-long-blessing-".repeat(16));
            postcard.setTargetAddressee(longRecipient);
            postcard.setPostcardStyle("contact:stripes");
            postcard.setPostcardText("CQ-long-postcard-text-".repeat(18));
            for (var task : List.of(parcel, packet, postcard)) {
                quest.addTask(task);
                var group = new ConfigGroup("ftbquests.task.contactquests." + task.getType().getTypeForNBT().split(":")[1]);
                task.fillConfigGroup(group);
                checkConfigNames(group);
            }
            List<ParcelRewardBase> rewards = List.of(new ParcelReward(0x7e00000000000006L, quest),
                    new ParcelRandomReward(0x7e00000000000007L, quest), new ParcelAllTableReward(0x7e00000000000008L, quest),
                    new RedPacketReward(0x7e00000000000009L, quest), new PostcardReward(0x7e0000000000000aL, quest));
            for (var reward : rewards) {
                quest.addReward(reward);
                var group = new ConfigGroup("ftbquests.reward.contactquests." + reward.getType().getTypeForNBT().split(":")[1]);
                reward.fillConfigGroup(group);
                checkConfigNames(group);
            }
            var questGroup = new ConfigGroup("ftbquests.quest");
            quest.fillConfigGroup(questGroup);
            checkConfigNames(questGroup);
            require(DependencyMode.NAME_MAP.getDisplayName(DependencyMode.ALL).getString().equals(
                            Component.translatable("contactquests.utils.dependencymode.all").getString())
                    && DependencyMode.NAME_MAP.getDisplayName(DependencyMode.ANY).getString().equals(
                            Component.translatable("contactquests.utils.dependencymode.any").getString()),
                    "Tag mode options are not localized");
            for (var mode : MutexMode.values()) {
                require(MutexMode.NAME_MAP.getDisplayName(mode).getString().equals(Component.translatable(
                        "contactquests.utils.mutexmode." + mode.name().toLowerCase(Locale.ROOT)).getString()),
                        "Mutex mode option is not localized: " + mode);
            }
            ContactQuestsClientTestController.assertCase(id, true, "Loaded " + active.size()
                    + " translations with exact argument formatting; actual config labels, 3 task/5 reward editor groups and quest mode options localized");

            ContactQuestsTestUtil.refreshIndex(file);
            file.clearCachedData();
            var nativeScreen = ClientQuestFile.openGui(quest, false);
            nativeScreen.viewQuest(quest);
            pendingPreview = new Preview(locale, previousScreen, nativeScreen, quest, parcel, packet, postcard);
            // ViewQuestPanel rebuilds its buttons on the next rendered frame.
            return false;
            }
            completed = true;
            require(pendingPreview.locale().equals(locale), "Unexpected localization preview sequence");
            var nativeScreen = pendingPreview.nativeScreen();
            int taskButtons = 0, rewardButtons = 0;
            for (Widget widget : flatten(nativeScreen.viewQuestPanel)) {
                var list = new TooltipList();
                if (widget instanceof TaskButton button) {
                    button.addMouseOverText(list);
                    checkWrapped(list);
                    taskButtons++;
                } else if (widget instanceof RewardButton button) {
                    button.addMouseOverText(list);
                    checkWrapped(list);
                    rewardButtons++;
                }
            }
            require(taskButtons == 3 && rewardButtons == 5, "Actual native preview buttons missing: tasks="
                    + taskButtons + ", rewards=" + rewardButtons);
            var previews = List.of(new ValidParcelItemsScreen(pendingPreview.parcel(), new ArrayList<>(List.of(new ItemStack(Items.DIAMOND)))),
                    new ValidRedPacketItemsScreen(pendingPreview.packet(), new ArrayList<>(List.of(new ItemStack(Items.GOLD_INGOT)))),
                    new ValidPostcardItemsScreen(pendingPreview.postcard()));
            for (var preview : previews) {
                preview.openGui();
                var list = new TooltipList();
                preview.getSubmitButton().addMouseOverText(list);
                checkWrapped(list);
            }

            var styled = Component.translatable("contactquests.quest.locked.missing_tags", "CQ-tag-".repeat(80))
                    .withStyle(ChatFormatting.RED).withStyle(style -> style.withClickEvent(
                            new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/contactquests open 7e00000000000002")));
            var list = new TooltipList();
            list.add(styled);
            TooltipUtils.wrap(list);
            require(list.getLines().size() > 1, "Long tooltip was left on one line");
            checkWrapped(list);
            String reconstructed = list.getLines().stream().map(Component::getString).collect(java.util.stream.Collectors.joining());
            require(reconstructed.replaceAll("\\s", "").equals(styled.getString().replaceAll("\\s", "")),
                    "Wrapping dropped tooltip characters");
            for (Component line : list.getLines()) {
                line.visit((style, text) -> {
                    require(text.isEmpty() || (style.getClickEvent() != null && style.getColor() != null),
                            "Wrapping dropped component style or quest link");
                    return java.util.Optional.empty();
                }, Style.EMPTY);
            }
            ContactQuestsClientTestController.assertCase("client.tooltips.wrap." + locale, true,
                    "Actual 3 task/5 reward buttons and 3 submission tooltips fit the viewport; long colored quest link preserves every character over "
                            + list.getLines().size() + " lines");
        } catch (Exception error) {
            completed = true;
            ContactQuestsClientTestController.assertCase(id, false, error.toString());
        } finally {
            if (completed) {
                if (pendingPreview != null) {
                    pendingPreview.quest().getChapter().getGroup().removeChapter(pendingPreview.quest().getChapter());
                    ContactQuestsTestUtil.refreshIndex(ClientQuestFile.INSTANCE);
                    ClientQuestFile.INSTANCE.clearCachedData();
                }
                mc.setScreen(previousScreen);
                pendingPreview = null;
            }
        }
        return true;
    }

    private static JsonObject language(String locale) throws Exception {
        var resource = Minecraft.getInstance().getResourceManager().getResourceOrThrow(
                new ResourceLocation("contactquests", "lang/" + locale + ".json"));
        try (var reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static void checkConfigNames(ConfigGroup group) {
        for (var value : group.getValues()) {
            String key = value.getNameKey();
            if (key.startsWith("contactquest") || key.startsWith("contact.quest") || key.startsWith("ftbquests.task.contactquests")
                    || key.startsWith("ftbquests.reward.contactquests") || key.startsWith("ftbquests.reward.parcel")
                    || key.equals("ftbquests.reward.random_bonus")) {
                require(net.minecraft.client.resources.language.I18n.exists(key) && !value.getName().equals(key),
                        "Actual editor field has no localized name: " + key);
            }
        }
        for (var subgroup : group.getSubgroups()) checkConfigNames(subgroup);
    }

    private static List<Widget> flatten(Panel panel) {
        var widgets = new ArrayList<Widget>();
        for (Widget widget : panel.getWidgets()) {
            widgets.add(widget);
            if (widget instanceof Panel child) widgets.addAll(flatten(child));
        }
        return widgets;
    }

    private static void checkWrapped(TooltipList list) {
        int width = Math.min(260, Minecraft.getInstance().getWindow().getGuiScaledWidth() - 24);
        require(!list.getLines().isEmpty() && list.maxWidth > 0 && list.maxWidth <= width,
                "Actual tooltip has no width limit");
        require(list.getLines().stream().allMatch(line -> Minecraft.getInstance().font.width(line) <= width),
                "Actual tooltip still exceeds the screen width");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
