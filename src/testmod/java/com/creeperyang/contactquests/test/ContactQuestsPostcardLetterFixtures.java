package com.creeperyang.contactquests.test;

import com.creeperyang.contactquests.llm.NpcLlmConfig;
import com.creeperyang.contactquests.llm.NpcLlmSavedData;
import com.creeperyang.contactquests.llm.NpcPostcardLetters;
import com.creeperyang.contactquests.utils.ContactMailNbt;
import com.flechazo.contact.common.item.PostcardItem;
import com.flechazo.contact.platform.PlatformHelper;
import com.google.gson.*;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import java.nio.file.*;
import java.util.*;

/** Long replies travel through the real postbox/mailbox and persist as real cards in a vanilla barrel. */
public final class ContactQuestsPostcardLetterFixtures {
    public static final String NPC = "CQTravelLetters";
    private static final String PERSONA = ("亲爱的旅人，沿河的花开得正好，赶路时也记得歇歇脚。\n"
            + "The river is quiet today; I hope the road brings you a pleasant surprise.\n").repeat(10);
    private static NpcLlmConfig.Config original;
    private static SimpleContainer originalMailbox;
    private static UUID team;
    private static BlockPos barrel;
    private static List<ItemStack> letters = List.of();
    private static String reply = "";
    private static Path path() { return Path.of(System.getProperty("contactquests.test.snapshot") + ".letters.json"); }
    private ContactQuestsPostcardLetterFixtures() {}

    public static void addResultFields(JsonObject result) {
        result.addProperty("postcardLetterCount", letters.size()); result.addProperty("postcardLetterText", reply);
    }

    public static void run(ServerPlayer player, String phase) {
        try {
            switch (phase) {
                case "prepare" -> prepare(player);
                case "verify" -> verify(player);
                case "finish" -> finish(player);
                case "rejoin" -> rejoin(player);
                default -> throw new IllegalArgumentException(phase);
            }
        } catch (Exception error) {
            ContactQuestsTestServerController.recordCase("server.postcard.letters." + phase, false, error.toString());
        }
    }

    private static void prepare(ServerPlayer player) {
        original = NpcLlmConfig.INSTANCE.current();
        team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElseThrow().getId();
        require(PlatformHelper.getMailboxPos(team) != null, "Real bound team mailbox required");
        var contents = PlatformHelper.getMailboxContents(team);
        originalMailbox = new SimpleContainer(contents.getContainerSize());
        for (int i = 0; i < contents.getContainerSize(); i++) originalMailbox.setItem(i, contents.getItem(i).copy());
        PlatformHelper.resetMailboxContents(team);
        var provider = new NpcLlmConfig.Provider("mock", "", "", "fixed-mock", "default", 30, 2048, List.of());
        var npc = new NpcLlmConfig.Npc(true, "letters", PERSONA, "player", 12, "", "", List.of(), "", "letters", 0, 0);
        NpcLlmConfig.INSTANCE.configure(new NpcLlmConfig.Config(true, Map.of("letters", provider), Map.of(NPC, npc)));
        var card = PostcardItem.getPostcard(new ResourceLocation("contact:creeper"), false);
        PostcardItem.setText(card, "路上可还安好？");
        var sender = ContactMailNbt.INSTANCE.getSender(card);
        require(sender == null || sender.isEmpty(), "Inquiry must remain an unsent native card");
        player.getInventory().clearContent(); player.getInventory().setItem(0, card); player.getInventory().selected = 0;
        player.inventoryMenu.broadcastFullState();
        record("prepare", "Real Contact inquiry ready; fixed provider must send a mixed Chinese/English reply longer than one card");
    }

    private static NpcLlmSavedData.Conversation conversation(ServerPlayer player) {
        return NpcLlmSavedData.Companion.get(player.serverLevel()).conversation(NPC, team, player.getUUID(), "player");
    }

    private static void verify(ServerPlayer player) {
        var c = conversation(player);
        require(c.getPendingId() == null && c.getInbox().isEmpty() && c.getTurns().size() == 2,
                "Fixed long response did not complete exactly once: turns=" + c.getTurns().size()
                        + ", inbox=" + c.getInbox().size() + ", pending=" + c.getPendingId());
        reply = c.getTurns().get(c.getTurns().size() - 1).getText();
        require(reply.contains(PERSONA) && reply.length() > 1024, "Long provider reply was truncated before mailing");
        var mailbox = PlatformHelper.getMailboxContents(team);
        var actual = new ArrayList<ItemStack>();
        for (int i = 0; i < mailbox.getContainerSize(); i++) {
            var stack = mailbox.getItem(i);
            if (!stack.isEmpty()) {
                require(stack.getItem() instanceof PostcardItem && stack.getCount() == 1
                        && NPC.equals(ContactMailNbt.INSTANCE.getSender(stack)), "Unexpected, stacked or wrongly addressed response card");
                actual.add(stack.copy());
            }
        }
        letters = List.copyOf(actual);
        require(letters.size() > 1 && letters.size() <= 27 && join(letters).equals(reply), "Delivered card order or complete original reply was lost");
        var group = NpcPostcardLetters.group(letters.get(0));
        require(group != null, "Long reply has no hidden group");
        for (int i = 0; i < letters.size(); i++) require(group.equals(NpcPostcardLetters.group(letters.get(i)))
                && NpcPostcardLetters.index(letters.get(i)) == i && NpcPostcardLetters.count(letters.get(i)) == letters.size(),
                "Delivered card hidden sequence differs from actual mailbox order");
        var level = player.serverLevel();
        var column = player.blockPosition().offset(12, 0, 12);
        // The no-script suite creates a fresh normal world; the adjacent terrain need
        // not be level with the player. Reserve an actual empty cell above its surface.
        int y = Math.max(column.getY(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                column.getX(), column.getZ()) + 2);
        barrel = new BlockPos(column.getX(), y, column.getZ());
        while (barrel.getY() < level.getMaxBuildHeight() && !level.getBlockState(barrel).isAir()) barrel = barrel.above();
        require(barrel.getY() < level.getMaxBuildHeight(), "No empty test barrel location in the chosen column");
        require(player.serverLevel().getBlockState(barrel).isAir(), "Private test barrel location is occupied");
        player.serverLevel().setBlockAndUpdate(barrel, Blocks.BARREL.defaultBlockState());
        var container = (Container) player.serverLevel().getBlockEntity(barrel);
        for (int i = 0; i < letters.size(); i++) container.setItem(i, letters.get(i).copy());
        container.setChanged();
        var saved = new JsonObject(); saved.addProperty("pid", ProcessHandle.current().pid());
        saved.addProperty("team", team.toString()); saved.addProperty("reply", reply);
        saved.addProperty("barrel", barrel.asLong());
        var cards = new JsonArray();
        for (var card : letters) cards.add(card.save(new CompoundTag()).toString());
        saved.add("cards", cards);
        try { Files.writeString(path(), new GsonBuilder().setPrettyPrinting().create().toJson(saved)); }
        catch (Exception error) { throw new IllegalStateException(error); }
        player.server.saveEverything(false, true, true);
        record("verify", "Real postbox/mailbox delivered " + letters.size() + " separate cards, exact full reply and native sender; real barrel saved for another JVM");
    }

    public static void collect(ServerPlayer player, int index) {
        try {
            require(index >= 0 && index < letters.size(), "Missing letter index " + index);
            var actual = ((Container) player.serverLevel().getBlockEntity(barrel)).getItem(index);
            require(ItemStack.matches(actual, letters.get(index)), "Persisted actual letter differs");
            player.getInventory().setItem(0, actual.copy()); player.getInventory().selected = 0;
            player.getInventory().setChanged(); player.containerMenu.broadcastFullState(); player.inventoryMenu.broadcastFullState();
            record("collected." + index, "Actual delivered card " + index + " synchronized for native item-use reader");
        } catch (Exception error) { ContactQuestsTestServerController.recordCase("server.postcard.letters.collected." + index, false, error.toString()); }
    }

    private static void finish(ServerPlayer player) {
        require(originalMailbox != null && original != null, "Original state missing");
        PlatformHelper.setMailboxContents(team, originalMailbox); NpcLlmConfig.INSTANCE.configure(original);
        player.server.saveEverything(false, true, true);
        record("finish", "Original NPC configuration and team mailbox restored; real long reply remains saved in its vanilla barrel");
    }

    private static void rejoin(ServerPlayer player) throws Exception {
        var saved = JsonParser.parseString(Files.readString(path())).getAsJsonObject();
        require(saved.get("pid").getAsLong() != ProcessHandle.current().pid(), "Persistence check requires another JVM");
        team = UUID.fromString(saved.get("team").getAsString()); reply = saved.get("reply").getAsString();
        require(team.equals(FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElseThrow().getId()), "Restart changed recipient team");
        barrel = BlockPos.of(saved.get("barrel").getAsLong());
        var container = (Container) player.serverLevel().getBlockEntity(barrel);
        require(container != null, "Real saved letter barrel is missing");
        var actual = new ArrayList<ItemStack>(); var expected = saved.getAsJsonArray("cards");
        for (int i = 0; i < expected.size(); i++) {
            var card = ItemStack.of(TagParser.parseTag(expected.get(i).getAsString()));
            require(ItemStack.matches(card, container.getItem(i)), "New JVM changed letter body/order/hidden data " + i);
            actual.add(container.getItem(i).copy());
        }
        letters = List.copyOf(actual);
        require(join(letters).equals(reply) && conversation(player).getTurns().size() == 2
                && conversation(player).getTurns().get(conversation(player).getTurns().size() - 1).getText().equals(reply), "Long reply context or card sequence changed after restart");
        record("rejoin", "New JVM restored every real Contact card/NBT in order and the identical full NPC transcript with no replay");
    }
    private static String join(List<ItemStack> cards) {
        var text = new StringBuilder(); cards.forEach(card -> text.append(PostcardItem.getText(card))); return text.toString();
    }
    private static void record(String phase, String text) { ContactQuestsTestServerController.recordCase("server.postcard.letters." + phase, true, text); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
