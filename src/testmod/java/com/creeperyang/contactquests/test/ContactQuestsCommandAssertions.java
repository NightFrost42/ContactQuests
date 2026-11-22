package com.creeperyang.contactquests.test;

import com.creeperyang.contactquests.config.NpcConfigManager;
import com.creeperyang.contactquests.data.CollectionSavedData;
import com.creeperyang.contactquests.data.DataManager;
import com.creeperyang.contactquests.registry.ModItems;
import com.creeperyang.contactquests.bridge.ITeamDataExtension;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;

import java.util.*;
import java.util.concurrent.TimeUnit;

/** Executes every registered production command and observes its actual effects. */
public final class ContactQuestsCommandAssertions {
    private ContactQuestsCommandAssertions() {}

    private static final class Messages implements CommandSource {
        final List<String> text = new ArrayList<>();
        public void sendSystemMessage(Component message) { text.add(message.getString()); }
        public boolean acceptsSuccess() { return true; }
        public boolean acceptsFailure() { return true; }
        public boolean shouldInformAdmins() { return false; }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }

    private static int cards(ServerPlayer player) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            if (stack.is(ModItems.INSTANCE.getTEAM_BINDING_CARD().get())) count += stack.getCount();
        }
        return count;
    }

    private static Set<String> suggestions(CommandDispatcher<CommandSourceStack> dispatcher,
                                            CommandSourceStack source, String input) throws Exception {
        Set<String> result = new HashSet<>();
        for (var suggestion : dispatcher.getCompletionSuggestions(dispatcher.parse(input, source)).get().getList()) {
            result.add(suggestion.getText());
        }
        return result;
    }

    public static void run(ServerPlayer player) {
        var dispatcher = player.server.getCommands().getDispatcher();
        var messages = new Messages();
        var admin = player.createCommandSourceStack().withPermission(2).withSource(messages);
        var ordinary = admin.withPermission(0);
        var data = CollectionSavedData.Companion.get(player.serverLevel());
        String npc = "CQ Command Stored NPC";
        UUID other = UUID.fromString("6a934e88-66ad-4a82-a102-079e3321c749");
        try {
            int before = cards(player);
            require(dispatcher.execute("contactquests admin givebinder @s", admin) == 1
                    && cards(player) == before + 1, "givebinder did not add exactly one actual binding card");
            boolean denied = false;
            try { dispatcher.execute("contactquests admin givebinder @s", ordinary); }
            catch (CommandSyntaxException expected) { denied = true; }
            require(denied && cards(player) == before + 1, "Non-admin givebinder changed inventory");
            require(dispatcher.execute("contactquests open not-a-hex-id", ordinary) == 0,
                    "Invalid open ID did not fail without opening anything");
            ContactQuestsTestServerController.recordCase("server.commands.binder_and_invalid_open", true,
                    "Admin givebinder adds one real card; ordinary player denied; malformed open returns zero");

            List<ItemStack> saved = new ArrayList<>();
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                saved.add(player.getInventory().getItem(slot).copy());
            }
            var oldEntities = player.serverLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(8))
                    .stream().map(ItemEntity::getUUID).collect(java.util.stream.Collectors.toSet());
            var previousMode = player.gameMode.getGameModeForPlayer();
            try {
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                    player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
                }
                require(dispatcher.execute("contactquests get_binder_internal", ordinary) == 1
                        && dispatcher.execute("contactquests admin givebinder @s", admin) == 1,
                        "Full inventory binder commands failed");
                var dropped = player.serverLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(8))
                        .stream().filter(e -> !oldEntities.contains(e.getUUID())
                                && e.getItem().is(ModItems.INSTANCE.getTEAM_BINDING_CARD().get())).toList();
                require(cards(player) == 0 && dropped.stream().mapToInt(e -> e.getItem().getCount()).sum() == 2,
                        "Full survival inventory did not drop exactly two binding cards: inventory=" + cards(player)
                                + "; drops=" + dropped.stream().map(e -> e.getItem().toString()).toList());
                dropped.forEach(ItemEntity::discard);
            } finally {
                for (int slot = 0; slot < saved.size(); slot++) player.getInventory().setItem(slot, saved.get(slot));
                player.setGameMode(previousMode);
                player.inventoryMenu.broadcastFullState();
            }
            ContactQuestsTestServerController.recordCase("server.commands.binder_full_inventory", true,
                    "Self/admin binder commands each drop exactly one card when every inventory slot is occupied");

            require(dispatcher.execute("contactquests admin setcount \"" + npc + "\" 0", admin) == 1
                    && data.getTriggerCount(player.getUUID(), npc) == 0, "Explicit count zero was not applied");
            var snapshot = data.save(new CompoundTag());
            denied = false;
            try { dispatcher.execute("contactquests admin setcount \"" + npc + "\" -1", admin); }
            catch (CommandSyntaxException expected) { denied = true; }
            require(denied && snapshot.equals(data.save(new CompoundTag())),
                    "Negative count mutated persisted state");
            data.setTriggerCount(player.getUUID(), npc, 11);
            data.setStacks(player.getUUID(), npc, List.of(new ItemStack(Items.APPLE, 3)));
            data.setTriggerCount(other, npc, 22);
            data.setStacks(other, npc, List.of(new ItemStack(Items.PAPER, 4)));
            Set<String> stored = suggestions(dispatcher, admin, "contactquests admin removedata ");
            require(stored.contains("\"" + npc + "\""), "Quoted stored NPC suggestion missing: " + stored);
            require(dispatcher.execute("contactquests admin removedata \"" + npc + "\" @s", admin) == 1
                    && data.getTriggerCount(player.getUUID(), npc) == 0
                    && data.getStacks(player.getUUID(), npc).stream().allMatch(ItemStack::isEmpty)
                    && data.getTriggerCount(other, npc) == 22
                    && data.getStacks(other, npc).stream().mapToInt(ItemStack::getCount).sum() == 4,
                    "Target removedata affected another player or left target attachments");
            require(dispatcher.execute("contactquests admin removedata \"" + npc + "\"", admin) == 1
                    && !data.getStoredNpcNames().contains(npc), "Global removedata left another player's NPC data");
            var restored = ContactQuestsTestUtil.roundTrip(data);
            require(restored.getTriggerCount(other, npc) == 0 && !restored.getStoredNpcNames().contains(npc),
                    "Deleted NPC was resurrected by SavedData reload");
            ContactQuestsTestServerController.recordCase("server.commands.remove_and_count_bounds", true,
                    "Quoted NPC self/target/global delete checks exact items, other-player isolation and reload; zero accepted/negative rejected");

            var team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElseThrow();
            var ext = (ITeamDataExtension) ServerQuestFile.INSTANCE.getOrCreateTeamData(team);
            String tag = "CQCommandManagedTag";
            var previousTags = new HashSet<>(ext.contactQuests$getTags());
            require(dispatcher.execute("contactquests admin tags add @s " + tag, admin) == 1
                    && ext.contactQuests$getTags().contains(tag), "tags add did not modify actual team data");
            require(dispatcher.execute("contactquests admin tags add @s " + tag, admin) == 0,
                    "Duplicate tag add reported success");
            require(dispatcher.execute("contactquests admin tags view @s", admin) == 1
                    && messages.text.stream().anyMatch(t -> t.contains(tag)), "tags view omitted the real team tag");
            require(suggestions(dispatcher, admin, "contactquests admin tags remove @s ").contains(tag),
                    "Existing team tag not suggested for removal");
            tagRemoveNoTeamCompletion(player, dispatcher, admin, ext, tag);
            require(dispatcher.execute("contactquests admin tags remove @s " + tag, admin) == 1
                    && dispatcher.execute("contactquests admin tags remove @s " + tag, admin) == 0
                    && new HashSet<>(ext.contactQuests$getTags()).equals(previousTags),
                    "Tag remove/missing result incorrect or unrelated team tags changed");
            int deniedCommands = 0;
            for (String command : List.of("removedata CQCommandSelf", "removedata CQCommandSelf @s",
                    "removedRedundantNPC", "tags view @s", "tags add @s Forbidden", "tags remove @s Forbidden")) {
                try { dispatcher.execute("contactquests admin " + command, ordinary); }
                catch (CommandSyntaxException expected) { deniedCommands++; }
            }
            require(deniedCommands == 6 && new HashSet<>(ext.contactQuests$getTags()).equals(previousTags),
                    "Admin command permission matrix allowed an ordinary player mutation");
            ContactQuestsTestServerController.recordCase("server.commands.tag_controls", true,
                    "Real view/add/duplicate/remove/missing and completion suggestions preserve unrelated team tags; all admin branches deny permission zero");
            consoleAndMissingTarget(player, dispatcher, admin, data, ext, other);
            prune(player, dispatcher, admin);
        } catch (Exception error) {
            ContactQuestsTestServerController.recordCase("server.commands.complete_matrix", false, error.toString());
        } finally {
            data.removeDataAll(npc);
        }
    }

    private static void consoleAndMissingTarget(ServerPlayer player,
                                                CommandDispatcher<CommandSourceStack> dispatcher,
                                                CommandSourceStack admin, CollectionSavedData data,
                                                ITeamDataExtension team, UUID offline) throws Exception {
        String caseId = "server.commands.console_and_missing_target";
        try {
            var original = RejectedCommandState.capture(player, data, team);
            var consoleMessages = new Messages();
            var source = player.server.createCommandSourceStack().withPermission(2).withSource(consoleMessages);
            require(source.getEntity() == null, "Console command fixture unexpectedly has a player entity");
            for (String command : List.of("contactquests get_binder_internal", "contactquests open 1",
                    "contactquests admin setcount CQConsole 3", "contactquests admin getcount CQConsole")) {
                require(dispatcher.execute(command, source) == 0 && consoleMessages.text.isEmpty(),
                        "Player-only console command returned success or unexpected feedback: " + command);
                original.requireUnchanged(player, data, team, command);
            }
            String missing = "CQAbsentCmd";
            require(player.server.getPlayerList().getPlayerByName(missing) == null
                            && player.server.getPlayerList().getPlayer(offline) == null
                            && !player.getUUID().equals(offline),
                    "Missing-name/offline-UUID fixtures unexpectedly resolve to an online player");
            require(suggestions(dispatcher, admin, "contactquests admin tags remove " + missing + " ").isEmpty(),
                    "Unresolvable player target suggested another team's tags");
            original.requireUnchanged(player, data, team, "Missing-target completion");

            String npc = "CQ Command Missing Target NPC";
            String tag = "CQMissingTargetSentinel";
            require(!data.getStoredNpcNames().contains(npc) && !team.contactQuests$getTags().contains(tag),
                    "Missing-target sentinel would overwrite existing NPC data or a team tag");
            var drop = new ItemEntity(player.serverLevel(), player.getX(), player.getY(), player.getZ(),
                    new ItemStack(Items.PAPER, 2));
            drop.setDefaultPickUpDelay();
            boolean addedTag = false;
            try {
                // Reuse run()'s offline record so cleanup does not leave a new UUID row.
                data.setTriggerCount(player.getUUID(), npc, 11);
                data.setStacks(player.getUUID(), npc, List.of(new ItemStack(Items.APPLE, 3)));
                data.setTriggerCount(offline, npc, 22);
                data.setStacks(offline, npc, List.of(new ItemStack(Items.PAPER, 4)));
                addedTag = team.contactQuests$unlockTag(tag);
                require(addedTag && player.serverLevel().addFreshEntity(drop),
                        "Could not create nonempty tag/drop controls for missing-target commands");
                var saved = RejectedCommandState.capture(player, data, team);
                require(saved.tags().contains(tag) && saved.drops().containsKey(drop.getUUID()),
                        "Missing-target snapshot omitted its nonempty tag/drop controls");
                var adminNode = dispatcher.getRoot().getChild("contactquests").getChild("admin");
                require(adminNode != null && admin.hasPermission(2) && !admin.hasPermission(3)
                                && admin.getEntity() == player && adminNode.canUse(admin),
                        "Command fixture is not an actual permission-two player source");

                int executions = 0;
                for (String target : List.of(missing, offline.toString())) {
                    for (String verb : List.of("givebinder", "removedata", "setcount", "getcount")) {
                        List<String> path = new ArrayList<>(List.of("contactquests", "admin", verb));
                        if (!verb.equals("givebinder")) path.add("npc");
                        if (verb.equals("setcount")) path.add("count");
                        path.add("target");
                        var targetNode = dispatcher.findNode(path);
                        require(targetNode instanceof ArgumentCommandNode<?, ?> argument
                                        && argument.getType() instanceof EntityArgument && targetNode.getCommand() != null,
                                "Missing registered production player-target handler: " + path);
                        String arguments = verb.equals("givebinder") ? "" : "\"" + npc + "\" ";
                        if (verb.equals("setcount")) arguments += "3 ";
                        String command = "contactquests admin " + verb + " " + arguments + target;
                        for (int permission : List.of(0, 1, 2)) {
                            String action = "permission=" + permission + ": " + command;
                            var feedback = new Messages();
                            var actor = admin.withPermission(permission).withSource(feedback);
                            require(actor.hasPermission(permission) && !actor.hasPermission(permission + 1)
                                            && adminNode.canUse(actor) == (permission == 2)
                                            && player.server.getPlayerList().getPlayerByName(missing) == null
                                            && player.server.getPlayerList().getPlayer(offline) == null,
                                    "Permission or absent-target precondition changed: " + action);
                            if (permission == 2) {
                                var parsed = dispatcher.parse(command, actor);
                                var nodes = parsed.getContext().getNodes();
                                if (target.equals(missing)) {
                                    require(!parsed.getReader().canRead() && parsed.getExceptions().isEmpty()
                                                    && !nodes.isEmpty() && nodes.get(nodes.size() - 1).getNode() == targetNode
                                                    && parsed.getContext().getCommand() == targetNode.getCommand(),
                                            "OP input did not reach the production target handler: " + action);
                                    // These production catches currently log the resolution error and return zero silently.
                                    require(dispatcher.execute(parsed) == 0,
                                            "Absent target reported command success: " + action);
                                } else {
                                    // Minecraft 1.21.1 marks bare UUIDs as includesEntities; player() rejects them
                                    // before resolution, resetting the exception cursor to zero for the full input.
                                    var rejection = parsed.getExceptions().get(targetNode);
                                    require(parsed.getReader().getRemaining().equals(target)
                                                    && parsed.getExceptions().size() == 1 && rejection != null
                                                    && nodes.stream().noneMatch(node -> node.getNode() == targetNode)
                                                    && parsed.getContext().getCommand() != targetNode.getCommand()
                                                    && rejection.getType() == EntityArgument.ERROR_ONLY_PLAYERS_ALLOWED
                                                    && command.equals(rejection.getInput()) && rejection.getCursor() == 0
                                                    && rejection.getRawMessage().getString().equals(EntityArgument.ERROR_ONLY_PLAYERS_ALLOWED
                                                            .create().getRawMessage().getString()),
                                            "Bare UUID did not fail at the native player-only argument parser: " + action
                                                    + "; " + rejection);
                                    boolean denied = false;
                                    try {
                                        dispatcher.execute(parsed);
                                    } catch (CommandSyntaxException expected) {
                                        require(expected == rejection,
                                                "Dispatcher did not propagate the exact UUID parse rejection: " + action
                                                        + "; " + expected);
                                        denied = true;
                                    }
                                    require(denied, "Bare UUID reached a handler instead of being rejected during parsing: " + action);
                                }
                            } else {
                                boolean denied = false;
                                try {
                                    dispatcher.execute(command, actor);
                                } catch (CommandSyntaxException expected) {
                                    var rejection = CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument();
                                    require(expected.getType() == rejection && command.equals(expected.getInput())
                                                    && expected.getCursor() == "contactquests ".length()
                                                    && expected.getRawMessage().getString().equals(rejection.create().getRawMessage().getString()),
                                            "Command failed somewhere other than the real admin permission gate: " + action
                                                    + "; " + expected);
                                    denied = true;
                                }
                                require(denied, "Insufficient permission reached an admin handler: " + action);
                            }
                            require(feedback.text.isEmpty(),
                                    "Rejected command emitted unexpected feedback: " + action + "; " + feedback.text);
                            require(data.getTriggerCount(player.getUUID(), npc) == 11
                                            && data.getTriggerCount(offline, npc) == 22,
                                    "Rejected command changed the online/offline NPC counts: " + action);
                            saved.requireUnchanged(player, data, team, action);
                            require(player.server.getPlayerList().getPlayerByName(missing) == null
                                            && player.server.getPlayerList().getPlayer(offline) == null,
                                    "Rejected command registered a missing/offline player: " + action);
                            executions++;
                        }
                    }
                }
                require(executions == 24, "Missing-target dispatcher matrix did not execute all 24 permission/target rows");
            } finally {
                drop.discard();
                if (addedTag) team.contactQuests$removeTag(tag);
                data.removeData(player.getUUID(), npc);
                data.removeData(offline, npc);
            }
            original.requireUnchanged(player, data, team, "Missing-target fixture cleanup");
            ContactQuestsTestServerController.recordCase(caseId, true,
                    "Real dispatcher: four console rejections; givebinder/removedata/setcount/getcount x absent name/offline UUID "
                            + "x permission 0/1/2 = 24 executions/rejections. OP absent names reach target handlers and return zero; "
                            + "OP bare UUIDs throw the exact native ERROR_ONLY_PLAYERS_ALLOWED parse error; lower permissions "
                            + "throw the exact admin-gate syntax error. Every row preserves inventory/cursor, nonempty team tags, "
                            + "online/offline counts 11/22, all SavedData attachments, and nearby dropped-entity NBT, with no feedback; fixtures cleaned up");
        } catch (Exception error) {
            ContactQuestsTestServerController.recordCase(caseId, false, error.toString());
            throw error;
        }
    }

    private record RejectedCommandState(CompoundTag saved, Set<String> tags, List<ItemStack> inventory,
                                        ItemStack carried, Map<UUID, CompoundTag> drops) {
        private static RejectedCommandState capture(ServerPlayer player, CollectionSavedData data,
                                                     ITeamDataExtension team) {
            List<ItemStack> inventory = new ArrayList<>();
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                inventory.add(player.getInventory().getItem(slot).copy());
            }
            return new RejectedCommandState(data.save(new CompoundTag()).copy(),
                    new HashSet<>(team.contactQuests$getTags()), inventory, player.containerMenu.getCarried().copy(),
                    droppedEntities(player));
        }

        private static Map<UUID, CompoundTag> droppedEntities(ServerPlayer player) {
            return player.serverLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(8))
                    .stream().collect(java.util.stream.Collectors.toMap(ItemEntity::getUUID,
                            entity -> entity.saveWithoutId(new CompoundTag()).copy()));
        }

        private void requireUnchanged(ServerPlayer player, CollectionSavedData data, ITeamDataExtension team,
                                      String action) {
            require(saved.equals(data.save(new CompoundTag())),
                    "Command changed saved NPC counts or attachments: " + action);
            require(tags.equals(new HashSet<>(team.contactQuests$getTags())),
                    "Command changed actual team tags: " + action);
            require(inventory.size() == player.getInventory().getContainerSize(),
                    "Command changed inventory size: " + action);
            for (int slot = 0; slot < inventory.size(); slot++) {
                require(ItemStack.matches(inventory.get(slot), player.getInventory().getItem(slot)),
                        "Command changed inventory item/count/components in slot " + slot + ": " + action);
            }
            require(ItemStack.matches(carried, player.containerMenu.getCarried()),
                    "Command changed the carried item/count/components: " + action);
            require(drops.equals(droppedEntities(player)),
                    "Command changed nearby dropped entities, stacks or entity NBT: " + action);
        }
    }

    public static void runNoTeamBoundary(ServerPlayer player) throws Exception {
        var team = (ITeamDataExtension) ServerQuestFile.INSTANCE.getOrCreateTeamData(player);
        String sentinel = "CQNoScriptCommandControl";
        var before = new HashSet<>(team.contactQuests$getTags());
        require(!before.contains(sentinel), "No-script command control tag already exists");
        try {
            require(team.contactQuests$unlockTag(sentinel), "Could not seed nonempty no-script command control");
            tagRemoveNoTeamCompletion(player, player.server.getCommands().getDispatcher(),
                    player.createCommandSourceStack().withPermission(2).withSource(new Messages()), team, sentinel);
        } finally {
            team.contactQuests$removeTag(sentinel);
            require(before.equals(new HashSet<>(team.contactQuests$getTags())), "No-script command control tags were not restored");
        }
    }

    private static void tagRemoveNoTeamCompletion(ServerPlayer player,
                                                   CommandDispatcher<CommandSourceStack> dispatcher,
                                                   CommandSourceStack admin, ITeamDataExtension existingTeam,
                                                   String existingTag) throws Exception {
        String caseId = "server.commands.tags_remove_no_team_completion";
        try {
            var manager = FTBTeamsAPI.api().getManager();
            var existingTeamId = manager.getTeamForPlayer(player).orElseThrow().getId();
            var previousTags = new HashSet<>(existingTeam.contactQuests$getTags());
            require(previousTags.contains(existingTag), "No-team completion needs a nonempty control team");

            UUID id = UUID.randomUUID();
            require(manager.getTeamForPlayerID(id).isEmpty(), "Independent player UUID already has an FTB team");
            // @s resolves the source entity itself. Do not add this player to the
            // world/PlayerList or log it into FTB Teams just to resolve an argument.
            ServerPlayer noTeamPlayer = new FakePlayer(player.serverLevel(), new GameProfile(id, "CQCmdNoTeam"));
            require(manager.getTeamForPlayer(noTeamPlayer).isEmpty()
                            && !player.server.getPlayerList().getPlayers().contains(noTeamPlayer)
                            && !player.serverLevel().players().contains(noTeamPlayer),
                    "Independent command source unexpectedly acquired team or online membership");
            var source = admin.withEntity(noTeamPlayer);
            String input = "contactquests admin tags remove @s ";
            var parsed = dispatcher.parse(input, source);
            var context = parsed.getContext().build(input);
            require(EntityArgument.getPlayer(context, "target") == noTeamPlayer,
                    "Real EntityArgument did not resolve the independent no-team player");
            var targetNode = dispatcher.getRoot().getChild("contactquests").getChild("admin")
                    .getChild("tags").getChild("remove").getChild("target");
            var suggestionContext = parsed.getContext().findSuggestionContext(input.length());
            require(targetNode instanceof ArgumentCommandNode<?, ?> targetArgument
                            && targetArgument.getType() instanceof EntityArgument
                            && suggestionContext.parent == targetNode
                            && suggestionContext.startPos == input.length()
                            && targetNode.getChildren().size() == 1
                            && targetNode.getChild("tag") instanceof ArgumentCommandNode<?, ?> tagArgument
                            && tagArgument.getCustomSuggestions() != null,
                    "Completion did not reach the registered production tag suggestion provider");

            var future = dispatcher.getCompletionSuggestions(parsed);
            require(future != null && future.isDone() && !future.isCancelled() && !future.isCompletedExceptionally(),
                    "No-team completion did not return a normally completed future");
            var result = future.get(1, TimeUnit.SECONDS);
            require(result != null && result.isEmpty() && result.getList().isEmpty(),
                    "No-team completion returned suggestions: " + result);
            var noTeamMessages = new Messages();
            var noTeamSource = source.withSource(noTeamMessages);
            require(dispatcher.execute("contactquests admin tags view @s", noTeamSource) == 0
                            && noTeamMessages.text.equals(List.of(Component.translatable("contactquests.command.error.no_team").getString()))
                            && dispatcher.execute("contactquests admin tags add @s CQNoTeamTag", noTeamSource) == 0
                            && dispatcher.execute("contactquests admin tags remove @s " + existingTag, noTeamSource) == 0,
                    "No-team tag view/add/remove returned success or omitted the exact no-team view error");
            require(manager.getTeamForPlayer(noTeamPlayer).isEmpty()
                            && manager.getTeamForPlayerID(id).isEmpty()
                            && manager.getTeamForPlayer(player).orElseThrow().getId().equals(existingTeamId)
                            && new HashSet<>(existingTeam.contactQuests$getTags()).equals(previousTags),
                    "No-team completion created a team or changed the existing player's team/tags");
            ContactQuestsTestServerController.recordCase("server.commands.tags_no_team_controls", true,
                    "Real dispatcher view/add/remove return zero for a no-team player; view emits the exact error and no team, tag or unrelated state is created");
            ContactQuestsTestServerController.recordCase(caseId, true,
                    "Real dispatcher/EntityArgument @s resolves an unregistered ServerPlayer with no FTB team; "
                            + "production tag provider returns a completed empty Suggestions future and preserves the nonempty control team");
        } catch (Exception error) {
            ContactQuestsTestServerController.recordCase(caseId, false, error.toString());
            throw error;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void prune(ServerPlayer player, CommandDispatcher<CommandSourceStack> dispatcher,
                              CommandSourceStack admin) throws Exception {
        var field = NpcConfigManager.class.getDeclaredField("npcMap"); field.setAccessible(true);
        Map map = (Map) field.get(NpcConfigManager.INSTANCE);
        Map snapshot = new HashMap(map);
        List<String> names = List.of("CQCommandActiveParcel", "CQCommandActiveRed", "CQCommandActiveCard", "CQCommandActiveReward");
        String unused = "CQ Command Config Only";
        try {
            DataManager.parcelReceiver.put(names.get(0), new HashSet<>());
            DataManager.redPacketReceiver.put(names.get(1), new HashSet<>());
            DataManager.postcardReceiver.put(names.get(2), new HashSet<>());
            DataManager.rewardSenders.add(names.get(3));
            var all = new HashSet<>(names); all.add(unused);
            NpcConfigManager.INSTANCE.syncWithQuests(all, Set.of(), Set.of(), Set.of());
            require(suggestions(dispatcher, admin, "contactquests admin getcount ").contains("\"" + unused + "\""),
                    "Config-only NPC not suggested");
            require(dispatcher.execute("contactquests admin removedRedundantNPC", admin) == 1,
                    "Prune command did not succeed");
            Set<String> kept = new HashSet<>(NpcConfigManager.INSTANCE.getAllNpcNames());
            require(kept.containsAll(names) && !kept.contains(unused), "Prune removed a live task/reward NPC or kept unused NPC");
            NpcConfigManager.INSTANCE.reload();
            require(kept.equals(NpcConfigManager.INSTANCE.getAllNpcNames()), "Prune changes were not saved to disk");
            ContactQuestsTestServerController.recordCase("server.commands.prune_npcs", true,
                    "Prune retains all three task receiver types plus reward sender, removes unused config, and persists across disk reload");
        } finally {
            DataManager.parcelReceiver.remove(names.get(0));
            DataManager.redPacketReceiver.remove(names.get(1));
            DataManager.postcardReceiver.remove(names.get(2));
            DataManager.rewardSenders.remove(names.get(3));
            map.clear(); map.putAll(snapshot);
            var save = NpcConfigManager.class.getDeclaredMethod("save"); save.setAccessible(true); save.invoke(NpcConfigManager.INSTANCE);
        }
    }
}
