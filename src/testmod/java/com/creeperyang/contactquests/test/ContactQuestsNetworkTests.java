package com.creeperyang.contactquests.test;

import com.creeperyang.contactquests.network.SyncQuestTextMessage;
import com.creeperyang.contactquests.network.SyncTeamExtensionMessage;
import com.creeperyang.contactquests.network.SyncTeamTagsMessage;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;

@GameTestHolder(ContactQuestsTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ContactQuestsNetworkTests {
    private ContactQuestsNetworkTests() {}

    private static <T> T roundTrip(GameTestHelper helper, BiConsumer<T, FriendlyByteBuf> encoder,
                                   Function<FriendlyByteBuf, T> decoder, T message) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            encoder.accept(message, buffer);
            T decoded = decoder.apply(buffer);
            helper.assertTrue(!buffer.isReadable(), "Payload left unread bytes");
            return decoded;
        } finally {
            buffer.release();
        }
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void queuedTeamExtensionRetainsExactSnapshot(GameTestHelper helper) {
        UUID team = UUID.randomUUID();
        Set<String> tags = new HashSet<>(Set.of("收信标签", "CQNetworkTag"));
        Set<Long> forced = new HashSet<>(Set.of(3_000_000_005L));
        Set<Long> blocked = new HashSet<>(Set.of(4_000_000_006L));
        Map<Long, String> postcards = new HashMap<>(Map.of(5_000_000_007L, "已排队明信片\n第二行"));
        Map<Long, String> blessings = new HashMap<>(Map.of(6_000_000_008L, "已排队红包祝福"));
        SyncTeamExtensionMessage queued = new SyncTeamExtensionMessage(team, tags, forced, blocked, postcards, blessings);

        // Reproduce the real interval between server send and Netty encode.
        tags.clear(); tags.add("later tag");
        forced.clear(); blocked.add(7L);
        postcards.clear(); postcards.put(8L, "later text");
        blessings.clear(); blessings.put(9L, "later blessing");
        SyncTeamExtensionMessage decoded = roundTrip(helper, SyncTeamExtensionMessage::encode, SyncTeamExtensionMessage::new, queued);
        assertTeamSnapshot(helper, decoded, team);

        // Keep updating the source while another thread repeatedly encodes the
        // same queued payload. Each decode must retain the original quantities/text.
        CompletableFuture<Void> encoding = CompletableFuture.runAsync(() -> {
            for (int i = 0; i < 128; i++) {
                assertTeamSnapshot(helper, roundTrip(helper, SyncTeamExtensionMessage::encode, SyncTeamExtensionMessage::new, queued), team);
            }
        });
        for (int i = 0; i < 1024; i++) {
            tags.clear(); tags.add("tick-" + i);
            forced.clear(); forced.add((long) i);
            blocked.clear(); blocked.add((long) i + 1);
            postcards.clear(); postcards.put((long) i, "tick-" + i);
            blessings.clear(); blessings.put((long) i, "blessing-" + i);
        }
        try {
            encoding.get(5, TimeUnit.SECONDS);
        } catch (Exception error) {
            throw new IllegalStateException("Concurrent payload encode failed", error);
        }
        helper.succeed();
    }

    private static void assertTeamSnapshot(GameTestHelper helper, SyncTeamExtensionMessage message, UUID team) {
        helper.assertTrue(message.getTeamId().equals(team), "Team ID changed during encode");
        helper.assertTrue(message.getTags().equals(Set.of("收信标签", "CQNetworkTag")), "Queued tags were changed by later updates");
        helper.assertTrue(message.getForcedQuests().equals(Set.of(3_000_000_005L)), "Queued forced quests changed");
        helper.assertTrue(message.getBlockedQuests().equals(Set.of(4_000_000_006L)), "Queued blocked quests changed");
        helper.assertTrue(message.getPostcardData().equals(Map.of(5_000_000_007L, "已排队明信片\n第二行")), "Queued postcard text changed");
        helper.assertTrue(message.getRedPacketData().equals(Map.of(6_000_000_008L, "已排队红包祝福")), "Queued blessing changed");
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void queuedTeamTagsRetainExactSnapshot(GameTestHelper helper) {
        UUID team = UUID.randomUUID();
        Set<String> tags = new HashSet<>(Set.of("OriginalTag", "原始标签"));
        SyncTeamTagsMessage queued = new SyncTeamTagsMessage(team, tags);
        tags.clear(); tags.add("LaterTag");
        SyncTeamTagsMessage decoded = roundTrip(helper, SyncTeamTagsMessage::encode, SyncTeamTagsMessage::decode, queued);
        helper.assertTrue(decoded.getTeamId().equals(team) && decoded.getTags().equals(Set.of("OriginalTag", "原始标签")),
                "Queued tag message followed the team's later changes");
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void queuedQuestDescriptionRetainsExactSnapshot(GameTestHelper helper) {
        List<String> lines = new ArrayList<>(List.of("原始描述", "second line"));
        SyncQuestTextMessage queued = new SyncQuestTextMessage(3_000_000_005L, "zh_cn", 2, lines);
        lines.clear(); lines.add("later description");
        SyncQuestTextMessage decoded = roundTrip(helper, SyncQuestTextMessage::encode, SyncQuestTextMessage.Companion::decode, queued);
        helper.assertTrue(decoded.getId() == 3_000_000_005L && decoded.getLocale().equals("zh_cn") && decoded.getType() == 2
                        && !decoded.getGlobal() && List.of("原始描述", "second line").equals(decoded.getContent()),
                "Queued description changed before encode");
        SyncQuestTextMessage global = roundTrip(helper, SyncQuestTextMessage::encode, SyncQuestTextMessage.Companion::decode,
                new SyncQuestTextMessage(queued.getId(), "zh_cn", 0, "新的全局标题", true));
        helper.assertTrue(global.getGlobal() && global.getContent().equals("新的全局标题"), "Global translation mode was lost in the codec");
        helper.succeed();
    }
}
