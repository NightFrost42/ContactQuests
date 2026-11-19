package com.creeperyang.contactquests.test;

import com.creeperyang.contactquests.item.TeamBindingItem;
import com.creeperyang.contactquests.registry.ModItems;
import com.creeperyang.contactquests.bridge.IMailboxTeamAccessor;
import com.flechazo.contact.common.block.MailboxBlock;
import com.flechazo.contact.common.registry.BlockRegistry;
import com.flechazo.contact.platform.PlatformHelper;
import com.google.gson.JsonObject;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.data.ServerTeam;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Integrated-server scenario action, dispatched by the controller to {@link #run(JsonObject)}.
 * For each variant (lower, upper, conflict_lower, conflict_upper), invoke:
 * prepare -> wait 80 ticks -> ready -> use -> wait 20 -> snapshot -> wait 10 -> verify.
 * Then restore -> wait 60 -> verify_restored. Waits are scenario ticks, not wall-clock sleeps.
 * Only prepare reads "variant"; supplying it on other case phases also checks identity.
 *
 * Case IDs: client.mailbox.binding_ui.{lower,upper,conflict_lower,conflict_upper,restored}.
 * A failed setup/action records the corresponding case as failed, never a setup-only PASS.
 * The normal variants start unbound independently, so both halves exercise first binding.
 * Conflicts also start with the acting team unbound: they must reach occupied-owner denial,
 * rather than pass merely because the acting team already has another mailbox.
 *
 * Requires the existing real player to be the only online member of a non-personal team,
 * no open container/cursor stack and no concurrent inventory, mail or team mutations.
 * Server submit calls only set up, snapshot and restore. The actual item use is exclusively
 * client gameMode.useItemOn, with real carried-item and crouch packets. A passive server-side
 * RightClickBlock observation and the native client denial/success message prove handling.
 * Fixed isolated area: x=4095..4101, z=-4098..-4094, y=159..162; every cell must be air first.
 * Restore preserves prior blocks, mailbox position/contents, team membership, inventory,
 * abilities, game mode and player location. Newly generated chunks are not deleted.
 * No automatic ticking/retry is installed: insufficient waits fail instead of fabricating PASS.
 */
@EventBusSubscriber(modid = ContactQuestsTestMod.MOD_ID, value = Dist.CLIENT)
public final class ContactQuestsMailboxBindingUiAssertions {
    private static final String PREFIX = "client.mailbox.binding_ui.";
    private static final BlockPos OWN_LOWER = new BlockPos(4096, 160, -4096);
    private static final BlockPos FOREIGN_LOWER = new BlockPos(4100, 160, -4096);
    private static final Vec3 STAND = new Vec3(4098.5, 160, -4097.5);
    private static final List<String> NATIVE_MESSAGES = List.of(
            "message.contactquests.binding.success", "message.contactquests.binding.occupied",
            "message.contactquests.binding.exists", "message.contactquests.binding.no_team");
    private static volatile Session session;

    private enum Variant {
        LOWER(false, false), UPPER(false, true), CONFLICT_LOWER(true, false), CONFLICT_UPPER(true, true);

        final boolean conflict;
        final boolean upper;

        Variant(boolean conflict, boolean upper) { this.conflict = conflict; this.upper = upper; }
        String key() { return name().toLowerCase(java.util.Locale.ROOT); }
        BlockPos hit() { return (conflict ? FOREIGN_LOWER : OWN_LOWER).offset(0, upper ? 1 : 0, 0); }
    }

    private enum Stage { PREPARING, READY, USED, SNAPSHOTTED, VERIFIED, RESTORING }

    private static final class Session {
        final UUID playerId;
        final net.minecraft.client.server.IntegratedServer server;
        final boolean oldShiftKey;
        final boolean oldClientShift;
        final boolean oldInputShift;
        final ConcurrentLinkedQueue<UseObservation> uses = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<String> messages = new ConcurrentLinkedQueue<>();
        final EnumSet<Variant> completed = EnumSet.noneOf(Variant.class);
        volatile ServerState saved;
        volatile boolean observing;
        volatile Variant variant;
        Stage stage;
        CompletableFuture<Snapshot> prepared;
        CompletableFuture<Snapshot> after;
        CompletableFuture<Snapshot> restored;
        Snapshot before;
        InteractionResult clientResult;

        Session(Minecraft mc) {
            playerId = mc.player.getUUID();
            server = mc.getSingleplayerServer();
            oldShiftKey = mc.options.keyShift.isDown();
            oldClientShift = mc.player.isShiftKeyDown();
            oldInputShift = mc.player.input.shiftKeyDown;
        }
    }

    /** Fields below are captured/mutated only by the integrated server thread. */
    private static final class ServerState {
        final ServerLevel level;
        final UUID team;
        final GlobalPos originalMailbox;
        final boolean hadContents;
        final SimpleContainer originalContents;
        final List<ItemStack> originalMail;
        final List<ItemStack> inventory;
        final int selected;
        final Vec3 position;
        final Vec3 velocity;
        final float yaw, pitch, fallDistance;
        final boolean shift, onGround;
        final GameType gameMode;
        final CompoundTag abilities = new CompoundTag();
        final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        ServerTeam foreign;
        List<ItemStack> foreignMail;
        boolean detached;

        ServerState(ServerPlayer player, UUID team) {
            this.level = player.serverLevel();
            this.team = team;
            originalMailbox = PlatformHelper.getMailboxPos(team);
            hadContents = PlatformHelper.getUuidToContents().containsKey(team);
            originalContents = PlatformHelper.getMailboxContents(team);
            originalMail = contents(originalContents);
            inventory = inventory(player);
            selected = player.getInventory().selected;
            position = player.position(); velocity = player.getDeltaMovement();
            yaw = player.getYRot(); pitch = player.getXRot(); fallDistance = player.fallDistance;
            shift = player.isShiftKeyDown(); onGround = player.onGround();
            gameMode = player.gameMode.getGameModeForPlayer();
            player.getAbilities().addSaveData(abilities);
        }
    }

    private record UseObservation(BlockPos pos, boolean sneaking, ItemStack held, boolean canceled) {}

    private record Snapshot(UUID team, GlobalPos ownLocation, UUID ownOwner, UUID ownMarker,
                            GlobalPos foreignLocation, UUID foreignOwner, UUID foreignMarker,
                            List<ItemStack> ownMail, List<ItemStack> foreignMail,
                            List<ItemStack> inventory, int selected, ItemStack carried,
                            List<UseObservation> uses) {}

    private ContactQuestsMailboxBindingUiAssertions() {}

    public static void run(JsonObject step) {
        String phase = step.has("phase") ? step.get("phase").getAsString() : "";
        String id = PREFIX + (phase.contains("restore") ? "restored"
                : step.has("variant") ? step.get("variant").getAsString()
                : session != null && session.variant != null ? session.variant.key() : "lower");
        try {
            var mc = Minecraft.getInstance();
            require(mc.player != null && mc.level != null && mc.gameMode != null && mc.getConnection() != null,
                    "Live client player, world and connection are required");
            require(mc.getSingleplayerServer() != null, "This fixture requires the integrated server");
            if (phase.equals("prepare")) {
                prepare(step, mc);
                return;
            }
            Session s = session;
            require(s != null && mc.player.getUUID().equals(s.playerId) && mc.getSingleplayerServer() == s.server,
                    "Wrong player/server or missing preparation");
            if (step.has("variant") && !phase.contains("restore"))
                require(step.get("variant").getAsString().equals(s.variant.key()), "Variant changed mid-case");
            switch (phase) {
                case "ready" -> ready(s, mc);
                case "use" -> use(s, mc);
                case "snapshot" -> {
                    require(s.stage == Stage.USED, "Use exactly once before requesting its snapshot");
                    s.after = s.server.submit(() -> snapshot(s));
                    s.stage = Stage.SNAPSHOTTED;
                }
                case "verify" -> verify(s, mc);
                case "restore" -> queueRestore(s, mc);
                case "verify_restored" -> verifyRestored(s, mc);
                default -> throw new IllegalArgumentException("Unknown mailbox-binding phase: " + phase);
            }
        } catch (Exception error) {
            Session s = session;
            if (s != null) {
                try {
                    queueRestore(s, Minecraft.getInstance());
                    // Finish only after cleanup has run; controller failure can close the world.
                    s.restored.whenComplete((ignored, cleanup) -> Minecraft.getInstance().execute(() -> {
                        if (cleanup != null) error.addSuppressed(cleanup);
                        ContactQuestsClientTestController.assertCase(id, false, phase + ": " + error
                                + (cleanup == null ? "; server cleanup completed" : "; cleanup failed: " + cleanup));
                    }));
                    return;
                }
                catch (Exception cleanup) { error.addSuppressed(cleanup); }
            }
            ContactQuestsClientTestController.assertCase(id, false, phase + ": " + error);
        }
    }

    private static void prepare(JsonObject step, Minecraft mc) {
        Variant variant = Variant.valueOf(step.get("variant").getAsString().toUpperCase(java.util.Locale.ROOT));
        if (session == null) {
            require(mc.player.containerMenu == mc.player.inventoryMenu && mc.player.inventoryMenu.getCarried().isEmpty(),
                    "Close real containers and clear the cursor before mailbox UI fixtures");
            session = new Session(mc);
        }
        Session s = session;
        require(mc.player.getUUID().equals(s.playerId) && mc.getSingleplayerServer() == s.server,
                "Fixture cannot cross players or worlds");
        require(s.stage == null || s.stage == Stage.VERIFIED, "Previous case must finish before preparing another");
        require(!s.completed.contains(variant), "Do not repeat an already verified binding case");
        mc.setScreen(null);
        s.variant = variant;
        s.observing = false;
        s.before = null; s.after = null; s.clientResult = null;
        s.uses.clear(); s.messages.clear();
        s.stage = Stage.PREPARING;
        s.prepared = s.server.submit(() -> {
            try { return prepareServer(s); }
            catch (Exception error) { throw new java.util.concurrent.CompletionException(error); }
        });
    }

    private static Snapshot prepareServer(Session s) throws Exception {
        ServerPlayer player = player(s);
        var manager = FTBTeamsAPI.api().getManager();
        var team = manager.getTeamForPlayerID(s.playerId).orElseThrow();
        require(!team.isPlayerTeam() && team.isValid() && team.getOnlineMembers().size() == 1,
                "Use an existing non-personal team with only this player online; membership is never changed");
        if (s.saved == null) {
            require(player.containerMenu == player.inventoryMenu && player.inventoryMenu.getCarried().isEmpty(),
                    "Server container/cursor must also be empty");
            require(!player.isPassenger() && !player.isSleeping(), "Player must be awake and unmounted");
            ServerState f = new ServerState(player, team.getId());
            s.saved = f; // Capture before any mutation so failure cleanup can restore partial preparation.
            require(f.level.getMinBuildHeight() <= 159 && f.level.getMaxBuildHeight() > 162,
                    "Isolated area is outside this world's build height");
            if (f.originalMailbox != null)
                require(f.team.equals(PlatformHelper.getMailboxOwner(f.originalMailbox.dimension(), f.originalMailbox.pos())),
                        "Pre-existing mailbox has inconsistent source ownership; leave it for its own fixture");
            for (int x = 4095; x <= 4101; x++) for (int z = -4098; z <= -4094; z++) for (int y = 159; y <= 162; y++) {
                BlockPos pos = new BlockPos(x, y, z);
                require(f.level.getBlockState(pos).isAir() && f.level.getBlockEntity(pos) == null,
                        "Reserved distant fixture area is occupied at " + pos);
                require(PlatformHelper.getMailboxOwner(f.level.dimension(), pos) == null,
                        "Reserved distant coordinate already has a Contact owner: " + pos);
                f.blocks.put(pos, f.level.getBlockState(pos));
            }
            if (f.originalMailbox != null) PlatformHelper.removeMailboxData(f.originalMailbox);
            f.detached = true;
            for (int x = 4095; x <= 4101; x++) for (int z = -4098; z <= -4094; z++)
                f.level.setBlock(new BlockPos(x, 159, z), Blocks.STONE.defaultBlockState(), 3);
            placeMailbox(f.level, OWN_LOWER);
            placeMailbox(f.level, FOREIGN_LOWER);
            UUID foreignId = UUID.randomUUID();
            f.foreign = (ServerTeam) manager.createServerTeam(s.server.createCommandSourceStack(),
                    "CQUIBind_" + foreignId.toString().substring(0, 8), "Temporary mailbox UI isolation fixture",
                    Color4I.WHITE, foreignId);
            PlatformHelper.setMailboxData(foreignId, f.level.dimension(), FOREIGN_LOWER.above());
            var foreignContents = new SimpleContainer(24);
            foreignContents.setItem(0, new ItemStack(Items.DIAMOND, 7));
            ItemStack named = new ItemStack(Items.PAPER, 3);
            named.set(DataComponents.CUSTOM_NAME, Component.literal("Keep original mail $ / \\"));
            foreignContents.setItem(7, named);
            foreignContents.setItem(23, new ItemStack(Items.DIRT, 11));
            PlatformHelper.setMailboxContents(foreignId, foreignContents);
            f.foreignMail = contents(foreignContents);
            player.setGameMode(GameType.SURVIVAL);
            player.getAbilities().invulnerable = true;
            player.getAbilities().mayfly = true;
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.teleportTo(f.level, STAND.x, STAND.y, STAND.z, 0, 0);
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0;
        }
        ServerState f = s.saved;
        require(team.getId().equals(f.team) && player.serverLevel() == f.level, "Team or dimension changed during fixture");
        GlobalPos current = PlatformHelper.getMailboxPos(f.team);
        if (current != null) {
            require(current.equals(GlobalPos.of(f.level.dimension(), OWN_LOWER.above())), "Unexpected new team mailbox");
            PlatformHelper.removeMailboxData(current);
        }
        require(sameItems(contents(PlatformHelper.getMailboxContents(f.team)), f.originalMail), "Original team mail changed");
        require(sameItems(contents(PlatformHelper.getMailboxContents(f.foreign.getId())), f.foreignMail), "Foreign mail changed");
        player.getInventory().clearContent();
        player.getInventory().selected = 0;
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.INSTANCE.getTEAM_BINDING_CARD().get()));
        player.getInventory().setItem(9, new ItemStack(Items.STICK, 5));
        player.inventoryMenu.broadcastChanges();
        player.connection.send(new ClientboundSetCarriedItemPacket(0));
        Snapshot before = snapshot(s);
        require(before.ownLocation == null && before.ownOwner == null && before.ownMarker == null,
                "Normal and conflict cases must start with an unbound acting team");
        require(f.foreign.getId().equals(before.foreignOwner) && f.foreign.getId().equals(before.foreignMarker),
                "Foreign fixture must have an actual valid owner and source marker");
        return before;
    }

    private static void ready(Session s, Minecraft mc) {
        require(s.stage == Stage.PREPARING && s.prepared.isDone(), "Server preparation is still pending; allow more ticks");
        s.before = s.prepared.join();
        require(mc.level.dimension().equals(s.saved.level.dimension()) && mc.player.position().distanceToSqr(STAND) < 0.25,
                "Actual teleport has not reached the client");
        require(sameItems(inventory(mc.player), s.before.inventory) && mc.player.inventoryMenu.getCarried().isEmpty(),
                "Seeded binder and inventory have not synchronized to the client");
        require(mc.player.getInventory().selected == 0 && !mc.player.getAbilities().instabuild,
                "Actual held slot and survival game-mode packet have not synchronized");
        require(clientMarker(mc, OWN_LOWER) == null && s.saved.foreign.getId().equals(clientMarker(mc, FOREIGN_LOWER)),
                "Real mailbox block entities and initial markers have not synchronized");
        s.stage = Stage.READY;
    }

    private static void use(Session s, Minecraft mc) {
        require(s.stage == Stage.READY, "Prepare and check synchronization before use");
        require(mc.screen == null && mc.player.containerMenu == mc.player.inventoryMenu, "Interact from ordinary gameplay");
        require(sameItems(inventory(mc.player), s.before.inventory), "Inventory changed after readiness");
        BlockPos hit = s.variant.hit();
        require(mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(hit)) < 16, "Mailbox is outside normal use reach");
        mc.player.getInventory().selected = 0;
        require(mc.player.getMainHandItem().getItem() instanceof TeamBindingItem && mc.player.getMainHandItem().getCount() == 1
                && mc.player.getOffhandItem().isEmpty(), "Hold exactly one genuine binder and an empty offhand");
        setShiftKey(mc, true);
        mc.player.input.shiftKeyDown = true;
        mc.player.setShiftKeyDown(true);
        s.uses.clear(); s.messages.clear(); s.observing = true;
        mc.getConnection().send(new ServerboundSetCarriedItemPacket(0));
        mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY));
        s.stage = Stage.USED;
        s.clientResult = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(hit), Direction.NORTH, hit, false));
        require(s.clientResult == InteractionResult.SUCCESS, "Native client binder use did not return SUCCESS: " + s.clientResult);
    }

    private static Snapshot snapshot(Session s) {
        ServerState f = s.saved;
        ServerPlayer p = player(s);
        var currentTeam = FTBTeamsAPI.api().getManager().getTeamForPlayerID(s.playerId).orElseThrow();
        return new Snapshot(currentTeam.getId(), PlatformHelper.getMailboxPos(f.team),
                PlatformHelper.getMailboxOwner(f.level.dimension(), OWN_LOWER.above()), marker(f.level, OWN_LOWER),
                PlatformHelper.getMailboxPos(f.foreign.getId()),
                PlatformHelper.getMailboxOwner(f.level.dimension(), FOREIGN_LOWER.above()), marker(f.level, FOREIGN_LOWER),
                contents(PlatformHelper.getMailboxContents(f.team)), contents(PlatformHelper.getMailboxContents(f.foreign.getId())),
                inventory(p), p.getInventory().selected, p.inventoryMenu.getCarried().copy(), List.copyOf(s.uses));
    }

    private static void verify(Session s, Minecraft mc) {
        require(s.stage == Stage.SNAPSHOTTED && s.after.isDone(), "Independent server snapshot is pending");
        Snapshot after = s.after.join();
        ServerState f = s.saved;
        require(after.team.equals(f.team), "Acting player's source team changed");
        require(after.uses.size() == 1, "Expected exactly one real server RightClickBlock, observed=" + after.uses.size());
        UseObservation use = after.uses.getFirst();
        require(use.pos.equals(s.variant.hit()) && use.sneaking && !use.canceled
                && ItemStack.matches(use.held, s.before.inventory.getFirst()), "Server did not receive the actual sneaking binder input");
        boolean conflict = s.variant.conflict;
        require(Objects.equals(after.ownLocation, conflict ? null : GlobalPos.of(f.level.dimension(), OWN_LOWER.above()))
                && Objects.equals(after.ownOwner, conflict ? null : f.team)
                && Objects.equals(after.ownMarker, conflict ? null : f.team), "Source ownership/marker disagrees with binding result");
        require(after.foreignLocation.equals(s.before.foreignLocation) && after.foreignOwner.equals(s.before.foreignOwner)
                && after.foreignMarker.equals(s.before.foreignMarker), "Interaction changed the foreign owner/location/marker");
        require(sameItems(after.ownMail, f.originalMail) && sameItems(after.foreignMail, f.foreignMail),
                "Mailbox contents changed, including exact slots, counts or item components");
        List<ItemStack> expected = new ArrayList<>(s.before.inventory);
        if (!conflict) expected.set(0, ItemStack.EMPTY);
        require(sameItems(after.inventory, expected) && sameItems(inventory(mc.player), expected)
                && after.selected == 0 && mc.player.getInventory().selected == 0
                && after.carried.isEmpty() && mc.player.inventoryMenu.getCarried().isEmpty(),
                "Client/server inventory must consume exactly one binder on success and preserve every item on conflict");
        require(Objects.equals(clientMarker(mc, OWN_LOWER), conflict ? null : f.team)
                && f.foreign.getId().equals(clientMarker(mc, FOREIGN_LOWER)), "Client team markers did not synchronize");
        String key = "message.contactquests.binding." + (conflict ? "occupied" : "success");
        require(List.copyOf(s.messages).equals(List.of(key)), "Expected exactly one native result message " + key + ", actual=" + s.messages);
        require(mc.screen == null, "Sneaking item use unexpectedly opened a mailbox menu");
        s.observing = false;
        s.completed.add(s.variant);
        s.stage = Stage.VERIFIED;
        ContactQuestsClientTestController.assertCase(PREFIX + s.variant.key(), true,
                "Real client MAIN_HAND+crouch use on " + s.variant.key() + ": one server input/native response; exact source owner, "
                        + "server/client team markers, " + (conflict ? "unchanged binder and original foreign mail" : "one binder consumed and original mail preserved"));
    }

    @SubscribeEvent
    public static void observeServerUse(PlayerInteractEvent.RightClickBlock event) {
        Session s = session;
        if (s == null || !s.observing || !(event.getEntity() instanceof ServerPlayer p)
                || !p.getUUID().equals(s.playerId) || event.getHand() != InteractionHand.MAIN_HAND
                || !event.getPos().equals(s.variant.hit())) return;
        s.uses.add(new UseObservation(event.getPos().immutable(), p.isShiftKeyDown(), p.getMainHandItem().copy(), event.isCanceled()));
    }

    @SubscribeEvent
    public static void observeNativeMessage(ClientChatReceivedEvent.System event) {
        Session s = session;
        if (s == null || !s.observing) return;
        for (String key : NATIVE_MESSAGES) if (containsKey(event.getMessage(), key)) { s.messages.add(key); break; }
    }

    private static void queueRestore(Session s, Minecraft mc) {
        s.observing = false;
        if (s.restored != null) return;
        setShiftKey(mc, s.oldShiftKey);
        if (mc.player != null && mc.player.getUUID().equals(s.playerId)) {
            mc.player.input.shiftKeyDown = s.oldInputShift;
            mc.player.setShiftKeyDown(s.oldClientShift);
            if (mc.getConnection() != null) mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player,
                    s.oldClientShift ? ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY : ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
        }
        s.stage = Stage.RESTORING;
        s.restored = s.server.submit(() -> restoreServer(s));
    }

    private static Snapshot restoreServer(Session s) {
        ServerState f = s.saved;
        if (f == null) return null;
        ServerPlayer p = player(s);
        // Remove only this fixture's locations; restore the real prior binding after blocks are gone.
        if (f.detached) for (BlockPos lower : List.of(OWN_LOWER, FOREIGN_LOWER))
            PlatformHelper.removeMailboxData(GlobalPos.of(f.level.dimension(), lower.above()));
        var entries = new ArrayList<>(f.blocks.entrySet());
        for (int i = entries.size() - 1; i >= 0; i--) {
            var entry = entries.get(i);
            f.level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        if (f.foreign != null) {
            PlatformHelper.getUuidToContents().remove(f.foreign.getId());
            f.foreign.delete(s.server.createCommandSourceStack());
        }
        if (f.detached && f.originalMailbox != null)
            PlatformHelper.setMailboxData(f.team, f.originalMailbox.dimension(), f.originalMailbox.pos());
        List<ItemStack> currentMail = contents(PlatformHelper.getMailboxContents(f.team));
        boolean mailUnchanged = sameItems(currentMail, f.originalMail);
        // Do not discard unexpected concurrent mail even when the fixture must fail.
        if (mailUnchanged) {
            if (f.hadContents) PlatformHelper.setMailboxContents(f.team, f.originalContents);
            else PlatformHelper.getUuidToContents().remove(f.team);
        }
        p.setGameMode(f.gameMode);
        p.getAbilities().loadSaveData(f.abilities);
        p.onUpdateAbilities();
        p.getInventory().clearContent();
        for (int i = 0; i < f.inventory.size(); i++) p.getInventory().setItem(i, f.inventory.get(i).copy());
        p.getInventory().selected = f.selected;
        p.setShiftKeyDown(f.shift);
        p.teleportTo(f.level, f.position.x, f.position.y, f.position.z, f.yaw, f.pitch);
        p.setDeltaMovement(f.velocity); p.fallDistance = f.fallDistance; p.setOnGround(f.onGround);
        p.inventoryMenu.broadcastChanges();
        p.connection.send(new ClientboundSetCarriedItemPacket(f.selected));
        require(mailUnchanged, "Concurrent team mail changed; it was preserved instead of overwritten during cleanup");
        require(Objects.equals(PlatformHelper.getMailboxPos(f.team), f.originalMailbox), "Original mailbox position did not restore");
        if (f.originalMailbox != null)
            require(f.team.equals(PlatformHelper.getMailboxOwner(f.originalMailbox.dimension(), f.originalMailbox.pos())),
                    "Original mailbox source owner did not restore");
        for (var entry : f.blocks.entrySet()) require(f.level.getBlockState(entry.getKey()).equals(entry.getValue()), "Fixture block remains");
        if (f.foreign != null) require(FTBTeamsAPI.api().getManager().getTeamByID(f.foreign.getId()).isEmpty()
                && PlatformHelper.getMailboxPos(f.foreign.getId()) == null
                && !PlatformHelper.getUuidToContents().containsKey(f.foreign.getId()), "Temporary foreign team/mailbox remains");
        return new Snapshot(f.team, PlatformHelper.getMailboxPos(f.team), null, null, null, null, null,
                contents(PlatformHelper.getMailboxContents(f.team)), List.of(), inventory(p), p.getInventory().selected,
                p.inventoryMenu.getCarried().copy(), List.of());
    }

    private static void verifyRestored(Session s, Minecraft mc) {
        require(s.stage == Stage.RESTORING && s.restored != null && s.restored.isDone(), "Server restoration is pending");
        Snapshot restored = s.restored.join();
        ServerState f = s.saved;
        require(restored != null && sameItems(restored.inventory, f.inventory) && sameItems(inventory(mc.player), f.inventory)
                && restored.selected == f.selected && mc.player.getInventory().selected == f.selected
                && restored.carried.isEmpty() && mc.player.inventoryMenu.getCarried().isEmpty(), "Original inventory/selection did not synchronize back");
        require(mc.level.dimension().equals(f.level.dimension()) && mc.player.position().distanceToSqr(f.position) < 0.25,
                "Original player location did not synchronize back");
        require(sameItems(restored.ownMail, f.originalMail), "Original team mail was not preserved");
        require(s.completed.equals(EnumSet.allOf(Variant.class)), "Run and verify all four half/owner cases before final cleanup PASS");
        session = null;
        ContactQuestsClientTestController.assertCase(PREFIX + "restored", true,
                "All four actual network interactions verified; original player/team mailbox, exact mail, inventory and world blocks restored; temporary foreign team removed");
    }

    private static ServerPlayer player(Session s) {
        ServerPlayer player = s.server.getPlayerList().getPlayer(s.playerId);
        require(player != null, "Original server player disconnected");
        return player;
    }

    private static void placeMailbox(ServerLevel level, BlockPos lower) {
        BlockState base = BlockRegistry.GREEN_MAILBOX.get().defaultBlockState();
        level.setBlock(lower, base.setValue(MailboxBlock.HALF, DoubleBlockHalf.LOWER), 3);
        level.setBlock(lower.above(), base.setValue(MailboxBlock.HALF, DoubleBlockHalf.UPPER), 3);
        require(level.getBlockState(lower).getBlock() instanceof MailboxBlock
                && level.getBlockEntity(lower.above()) instanceof IMailboxTeamAccessor, "Real two-block Contact mailbox did not survive placement");
    }

    private static UUID marker(ServerLevel level, BlockPos lower) {
        var entity = level.getBlockEntity(lower.above());
        require(entity instanceof IMailboxTeamAccessor, "Server mailbox team accessor is missing");
        return ((IMailboxTeamAccessor) entity).contactQuests$getTeamId();
    }

    private static UUID clientMarker(Minecraft mc, BlockPos lower) {
        require(mc.level.hasChunkAt(lower), "Client mailbox chunk has not arrived");
        var lowerState = mc.level.getBlockState(lower);
        var upperState = mc.level.getBlockState(lower.above());
        require(lowerState.getBlock() instanceof MailboxBlock && upperState.getBlock() instanceof MailboxBlock
                && lowerState.getValue(MailboxBlock.HALF) == DoubleBlockHalf.LOWER
                && upperState.getValue(MailboxBlock.HALF) == DoubleBlockHalf.UPPER, "Client two-block mailbox is missing");
        var entity = mc.level.getBlockEntity(lower.above());
        require(entity instanceof IMailboxTeamAccessor, "Client mailbox block entity has not arrived");
        return ((IMailboxTeamAccessor) entity).contactQuests$getTeamId();
    }

    private static List<ItemStack> inventory(Player player) {
        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) result.add(player.getInventory().getItem(i).copy());
        return List.copyOf(result);
    }

    private static List<ItemStack> contents(SimpleContainer container) {
        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) result.add(container.getItem(i).copy());
        return List.copyOf(result);
    }

    private static boolean sameItems(List<ItemStack> actual, List<ItemStack> expected) {
        if (actual.size() != expected.size()) return false;
        for (int i = 0; i < actual.size(); i++) if (!ItemStack.matches(actual.get(i), expected.get(i))) return false;
        return true;
    }

    private static boolean containsKey(Component component, String key) {
        if (component.getContents() instanceof TranslatableContents text) {
            if (text.getKey().equals(key)) return true;
            for (Object arg : text.getArgs()) if (arg instanceof Component child && containsKey(child, key)) return true;
        }
        return component.getSiblings().stream().anyMatch(child -> containsKey(child, key));
    }

    private static void setShiftKey(Minecraft mc, boolean down) {
        if (mc.options.keyShift.isDown() == down) return;
        mc.options.keyShift.setDown(down);
        // Toggle crouch mappings ignore setDown(false); one press toggles them back.
        if (mc.options.keyShift.isDown() != down) mc.options.keyShift.setDown(true);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
