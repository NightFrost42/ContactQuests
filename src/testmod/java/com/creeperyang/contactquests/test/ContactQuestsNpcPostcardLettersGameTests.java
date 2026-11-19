package com.creeperyang.contactquests.test;

import com.creeperyang.contactquests.data.RewardDistributionManager;
import com.creeperyang.contactquests.llm.NpcPostcardLetters;
import com.flechazo.contact.common.component.ContactDataComponents;
import com.flechazo.contact.common.item.PostcardItem;
import com.flechazo.contact.data.PostcardStyle;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Dedicated-server checks; real client Font/read-screen acceptance is supplied by the client fixture. */
@GameTestHolder(ContactQuestsTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ContactQuestsNpcPostcardLettersGameTests {
    private static final ResourceLocation STYLE = ResourceLocation.parse("contact:default");
    private static final String MARKER = "ContactQuestsNpcLetter";

    private static String body(List<ItemStack> cards) {
        return cards.stream().map(PostcardItem::getText).collect(Collectors.joining());
    }

    private static void intactCodePoints(GameTestHelper helper, String part) {
        for (int i = 0; i < part.length(); i++) {
            char value = part.charAt(i);
            if (Character.isHighSurrogate(value)) {
                helper.assertTrue(i + 1 < part.length() && Character.isLowSurrogate(part.charAt(i + 1)),
                        "A card ended inside a supplementary Unicode code point");
                i++;
            } else helper.assertTrue(!Character.isLowSurrogate(value), "A card began with an orphan low surrogate");
        }
    }

    /**
     * Independent conservative oracle using substring boundaries: LF ends a line, overflow rewinds
     * to the final ASCII space (including the overflowing space), and terminal LF adds no extra line.
     * Each code point is charged 12 pixels, exceeding the bundled default font's advances.
     */
    private static List<String> worstCaseLines(String text, int width) {
        List<String> lines = new ArrayList<>();
        int columns = width / 12;
        for (int start = 0; start < text.length();) {
            int limit = text.offsetByCodePoints(start, Math.min(columns, text.codePointCount(start, text.length())));
            int newline = text.indexOf('\n', start);
            int end;
            if (newline >= 0 && newline <= limit) end = newline + 1;
            else if (limit == text.length()) end = limit;
            else if (text.charAt(limit) == ' ') end = limit + 1;
            else {
                int space = text.lastIndexOf(' ', limit - 1);
                end = space >= start ? space + 1 : limit;
            }
            lines.add(text.substring(start, end));
            start = end;
        }
        return lines;
    }

    private static void fits(GameTestHelper helper, String part, int width, int height) {
        helper.assertTrue(!part.isEmpty() && part.length() < 1024, "Empty or oversized native postcard body");
        intactCodePoints(helper, part);
        var lines = worstCaseLines(part, width);
        helper.assertTrue(lines.size() * 12 <= height,
                "Conservative native wrap exceeds the postcard text area: " + lines.size() + " lines in " + height);
        for (String line : lines) {
            int end = line.length();
            while (end > 0 && (line.charAt(end - 1) == ' ' || line.charAt(end - 1) == '\n')) end--;
            String visible = line.substring(0, end);
            helper.assertTrue(visible.codePointCount(0, visible.length()) * 12 <= width,
                    "Conservative native line exceeds the postcard width");
        }
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void npcLongReplyPreservesExactTextAndDefaultStyle(GameTestHelper helper) {
        String npc = "长信邮差";
        String paragraph = "  中文和 English words remain exact.\n\n重复 段落\t原样\r\n署名：邮差 📬 e\u0301\n";
        String text = "界".repeat(119) + "📨" + paragraph.repeat(12) + "W".repeat(400) + "\n  结束\n\n";
        var cards = NpcPostcardLetters.create(npc, text);
        helper.assertTrue(cards.size() > 1 && body(cards).equals(text),
                "Long mixed-language reply was truncated, normalized, numbered or reordered");
        UUID group = NpcPostcardLetters.group(cards.getFirst());
        helper.assertTrue(group != null, "Reply has no hidden fragment group");
        var blank = PostcardItem.getPostcard(STYLE, false);
        for (int i = 0; i < cards.size(); i++) {
            var card = cards.get(i);
            var style = PostcardStyle.fromItemStack(card);
            helper.assertTrue(card.getItem() == blank.getItem() && card.getItem() instanceof PostcardItem
                            && card.getCount() == 1 && STYLE.equals(card.get(ContactDataComponents.POSTCARD_STYLE_ID.get()))
                            && npc.equals(card.get(ContactDataComponents.POSTCARD_SENDER.get()))
                            && group.equals(NpcPostcardLetters.group(card)) && NpcPostcardLetters.index(card) == i
                            && NpcPostcardLetters.count(card) == cards.size(),
                    "A fragment lost its real Contact item, default style, sender or consecutive hidden identity");
            fits(helper, PostcardItem.getText(card), style.textWidth(), style.textHeight());
        }
        helper.assertTrue(NpcPostcardLetters.create(npc, "").isEmpty(), "Empty reply generated a blank mail item");
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void npcPostcardLayoutRespectsNativeWrapBoundaries(GameTestHelper helper) {
        // Native StringSplitter first emits the leading space alone, then wraps the long word.
        // The previous token estimator incorrectly fitted this entire input into two rows.
        helper.assertTrue(NpcPostcardLetters.split(" xxxx", 36, 24).equals(List.of(" xxx", "x")),
                "Leading space plus a long word overflowed the native read screen");
        helper.assertTrue(NpcPostcardLetters.split("a\n", 12, 12).equals(List.of("a\n"))
                        && NpcPostcardLetters.split("\n\n", 12, 12).equals(List.of("\n", "\n")),
                "Terminal and consecutive newlines did not follow native line counting");
        for (String text : List.of("界".repeat(121), " " + "W".repeat(300), "   ".repeat(90),
                "\t\r\u00a0\u2003".repeat(50), "📨界 e\u0301\n\n".repeat(40), "\n".repeat(27))) {
            var parts = NpcPostcardLetters.split(text, 180, 96);
            helper.assertTrue(String.join("", parts).equals(text), "Layout boundary changed the exact source body");
            for (var part : parts) fits(helper, part, 180, 96);
        }
        String limit = "a".repeat(1023);
        helper.assertTrue(NpcPostcardLetters.split(limit, 24000, 12).equals(List.of(limit))
                        && NpcPostcardLetters.split(limit + "b", 24000, 12).equals(List.of(limit, "b")),
                "Contact's strict less-than-1024 body limit was not respected");
        String surrogateBoundary = "a".repeat(1022) + "📨z";
        var boundaryParts = NpcPostcardLetters.split(surrogateBoundary, 24000, 12);
        helper.assertTrue(boundaryParts.equals(List.of("a".repeat(1022), "📨z")),
                "The text-length boundary split a Unicode code point");
        for (var part : boundaryParts) intactCodePoints(helper, part);
        for (int[] area : List.of(new int[]{11, 96}, new int[]{180, 11})) {
            boolean rejected = false;
            try { NpcPostcardLetters.split("x", area[0], area[1]); }
            catch (IllegalArgumentException expected) { rejected = true; }
            helper.assertTrue(rejected, "An unusable text area silently emitted an overflowing postcard");
        }
        helper.succeed();
    }

    @SuppressWarnings("unchecked")
    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void npcRepeatedFragmentsKeepDeliveryOrderAndHiddenIdentity(GameTestHelper helper) throws Exception {
        var level = helper.getLevel().getServer().overworld();
        var blank = PostcardItem.getPostcard(STYLE, false);
        var style = PostcardStyle.fromItemStack(blank);
        int capacity = (style.textWidth() / 12) * (style.textHeight() / 12);
        String repeated = "甲".repeat(capacity);
        String text = repeated.repeat(3) + "乙".repeat(capacity) + repeated + "终";
        String npc = "重复片段邮差";
        var first = NpcPostcardLetters.create(npc, text);
        var second = NpcPostcardLetters.create(npc, text);
        helper.assertTrue(first.size() == 6 && PostcardItem.getText(first.get(0)).equals(PostcardItem.getText(first.get(4)))
                        && !ItemStack.isSameItemSameComponents(first.get(0), first.get(4))
                        && !NpcPostcardLetters.group(first.getFirst()).equals(NpcPostcardLetters.group(second.getFirst())),
                "Repeated fragments or separate replies can merge despite their hidden identities");
        List<ItemStack> originals = new ArrayList<>(first); originals.addAll(second);
        List<ItemStack> restored = new ArrayList<>();
        for (var original : originals) {
            var copy = ItemStack.parse(level.registryAccess(), original.save(level.registryAccess())).orElseThrow();
            helper.assertTrue(ItemStack.matches(original, copy)
                            && NpcPostcardLetters.group(original).equals(NpcPostcardLetters.group(copy))
                            && NpcPostcardLetters.index(original) == NpcPostcardLetters.index(copy)
                            && NpcPostcardLetters.count(original) == NpcPostcardLetters.count(copy),
                    "Card NBT round trip lost body or fragment identity");
            restored.add(copy);
            // Contact may supply native name/lore components; fragments must preserve those exact values.
            helper.assertTrue(java.util.Objects.equals(copy.get(DataComponents.CUSTOM_NAME), blank.get(DataComponents.CUSTOM_NAME))
                            && java.util.Objects.equals(copy.get(DataComponents.LORE), blank.get(DataComponents.LORE))
                            && copy.getHoverName().equals(blank.getHoverName()),
                    "Fragment changed the native same-style postcard name or lore");
            for (var flag : List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED)) {
                String tooltip = copy.getTooltipLines(Item.TooltipContext.of(level), null, flag).stream()
                        .map(component -> component.getString()).collect(Collectors.joining("\n"));
                helper.assertTrue(!tooltip.contains(MARKER) && !tooltip.contains(NpcPostcardLetters.group(copy).toString()),
                        "Hidden fragment ID appeared in a native tooltip");
            }
        }
        UUID recipient = UUID.randomUUID();
        var field = RewardDistributionManager.class.getDeclaredField("buffer"); field.setAccessible(true);
        var buffer = (Map<Object, Object>) field.get(RewardDistributionManager.INSTANCE);
        Object ownedKey = null;
        try {
            for (var card : restored) RewardDistributionManager.INSTANCE.distributeTo(recipient, null, card, npc, false);
            List<ItemStack> queued = new ArrayList<>();
            for (var entry : buffer.entrySet()) {
                var playerId = entry.getKey().getClass().getDeclaredMethod("getPlayerId"); playerId.setAccessible(true);
                if (!recipient.equals(playerId.invoke(entry.getKey()))) continue;
                ownedKey = entry.getKey();
                for (var values : ((Map<?, ?>) entry.getValue()).values()) queued.addAll((List<ItemStack>) values);
            }
            helper.assertTrue(queued.size() == originals.size() && queued.stream().allMatch(card -> card.getCount() == 1)
                            && body(queued).equals(text + text),
                    "Real reward merge combined repeated bodies or moved a later fragment ahead of intervening text");
            var pack = RewardDistributionManager.class.getDeclaredMethod("packItems", List.class, boolean.class, String.class);
            pack.setAccessible(true);
            var packed = (List<ItemStack>) pack.invoke(RewardDistributionManager.INSTANCE, queued, false, npc);
            helper.assertTrue(packed.size() == originals.size() && body(packed).equals(text + text),
                    "Real reward packing wrapped postcards or changed their consecutive body order");
            for (int i = 0; i < originals.size(); i++) helper.assertTrue(ItemStack.matches(originals.get(i), packed.get(i)),
                    "Real merge/packing changed a fragment's exact components or position");
        } finally {
            // Never flush or clear another test's/global delivery buffer.
            if (ownedKey != null) buffer.remove(ownedKey);
        }
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void npcFragmentMetadataRejectsMalformedMarkers(GameTestHelper helper) {
        var blank = PostcardItem.getPostcard(STYLE, false);
        for (var card : List.of(ItemStack.EMPTY, new ItemStack(Items.STICK), blank)) {
            helper.assertTrue(NpcPostcardLetters.group(card) == null && NpcPostcardLetters.index(card) == -1
                            && NpcPostcardLetters.count(card) == 0, "Ordinary item was mistaken for an NPC letter fragment");
        }
        for (int[] coordinates : List.of(new int[]{-1, 2}, new int[]{2, 2}, new int[]{0, 0}, new int[]{0, -1})) {
            var marker = new CompoundTag(); marker.putUUID("group", UUID.randomUUID());
            marker.putInt("index", coordinates[0]); marker.putInt("count", coordinates[1]);
            var data = new CompoundTag(); data.put(MARKER, marker);
            var card = blank.copy(); card.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
            var before = card.copy();
            helper.assertTrue(NpcPostcardLetters.group(card) == null && NpcPostcardLetters.index(card) == -1
                            && NpcPostcardLetters.count(card) == 0 && ItemStack.matches(before, card),
                    "Invalid fragment coordinates were accepted or the read-only metadata API mutated the item");
        }
        helper.succeed();
    }
}
