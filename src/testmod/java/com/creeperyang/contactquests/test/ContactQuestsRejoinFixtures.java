package com.creeperyang.contactquests.test;

import com.creeperyang.contactquests.compat.kubejs.ContactKubeJSPlugin;
import com.creeperyang.contactquests.compat.kubejs.KubeJSNpcSavedData;
import com.creeperyang.contactquests.compat.kubejs.QuestGraphApi;
import com.creeperyang.contactquests.data.CollectionSavedData;
import com.creeperyang.contactquests.data.TaskDeliverySavedData;
import com.creeperyang.contactquests.item.TeamBindingItem;
import com.creeperyang.contactquests.quest.task.ParcelTask;
import com.creeperyang.contactquests.registry.ModItems;
import com.creeperyang.contactquests.utils.ContactMailNbt;
import com.creeperyang.contactquests.bridge.IMailboxTeamAccessor;
import com.creeperyang.contactquests.bridge.ITeamDataExtension;
import com.flechazo.contact.common.block.MailboxBlock;
import com.flechazo.contact.common.item.ParcelItem;
import com.flechazo.contact.common.registry.BlockRegistry;
import com.flechazo.contact.platform.PlatformHelper;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/** A fresh native Java process reads the saved world without reseeding it. */
public final class ContactQuestsRejoinFixtures {
    private ContactQuestsRejoinFixtures() {}

    public static Path snapshotPath() { return Path.of(System.getProperty("contactquests.test.snapshot")); }

    public static void prepare(ServerPlayer player) {
        try {
            if (Boolean.getBoolean("contactquests.test.rejoin") || Files.exists(snapshotPath())) {
                throw new IllegalStateException("Persistence fixture may be seeded only once");
            }
            ServerLevel level = player.serverLevel();
            var manager = (TeamManagerImpl) FTBTeamsAPI.api().getManager();
            var team = manager.getTeamForPlayer(player).orElseThrow();
            if (!team.isPartyTeam()) throw new IllegalStateException("Expected live party team");
            BlockPos teamLower = player.blockPosition().offset(3, 0, 3);
            BlockPos removedLower = player.blockPosition().offset(7, 0, 3);
            BlockPos personalLower = player.blockPosition().offset(11, 0, 3);
            BlockPos personalRemovedLower = player.blockPosition().offset(15, 0, 3);
            if (PlatformHelper.getMailboxPos(team.getId()) != null) PlatformHelper.removeMailboxData(PlatformHelper.getMailboxPos(team.getId()));
            place(level, removedLower);
            require(bind(player, removedLower).consumesAction(), "Party binding failed");
            // Actual player-like block destruction and a fresh block placement.
            level.destroyBlock(removedLower, false);
            place(level, removedLower);
            require(PlatformHelper.getMailboxPos(team.getId()) == null && owner(level, removedLower) == null,
                    "Party replacement inherited a position binding");
            place(level, teamLower);
            require(bind(player, teamLower).consumesAction(), "Fresh party binding failed");
            PlatformHelper.resetMailboxContents(team.getId());
            PlatformHelper.addMailboxContents(team.getId(), ParcelItem.getParcel(
                    new SimpleContainer(new ItemStack(Items.DIAMOND, 7)), false, "CQRejoinParty"));

            UUID personalId = UUID.randomUUID();
            manager.playerLoggedIn(null, personalId, "CQRejoinPersonal");
            ServerPlayer personal = FakePlayerFactory.get(level, new GameProfile(personalId, "CQRejoinPersonal"));
            place(level, personalRemovedLower);
            require(bind(personal, personalRemovedLower).consumesAction(), "Personal binding failed");
            level.destroyBlock(personalRemovedLower.above(), false);
            place(level, personalRemovedLower);
            require(PlatformHelper.getMailboxPos(personalId) == null && owner(level, personalRemovedLower) == null,
                    "Personal replacement inherited a position binding");
            place(level, personalLower);
            require(bind(personal, personalLower).consumesAction(), "Fresh personal binding failed");
            PlatformHelper.addMailboxContents(personalId, ParcelItem.getParcel(
                    new SimpleContainer(new ItemStack(Items.EMERALD, 5)), false, "CQRejoinPersonal"));

            var api = QuestGraphApi.INSTANCE;
            var chapter = api.createChapter(level, "cqtest:persistence/chapter", "Persistent chapter");
            var quest = api.createQuest(level, chapter.getCodeString(), "cqtest:persistence/quest", "Persistent quest");
            var rule = api.createQuest(level, chapter.getCodeString(), "cqtest:persistence/mutex", "Persistent mutex");
            // FTB completes empty quests on login. A mutex fixture needs a real,
            // deliberately unfinished task to keep the main quest available.
            rule.addTask(new dev.ftb.mods.ftbquests.quest.task.CheckmarkTask(ServerQuestFile.INSTANCE.newID(), rule));
            api.updateQuest(level, quest.getCodeString(), Map.of("requiredTags", java.util.List.of("CQPersistTag"),
                    "tagMode", "ALL", "hideWithoutTags", true, "mutex", java.util.List.of(rule.getCodeString()),
                    "mutexMode", "ANY", "hideIfLocked", true));
            CompoundTag taskData = new CompoundTag(); taskData.putString("item", "minecraft:diamond");
            taskData.putLong("count", 10); taskData.putString("TargetAddressee", "CQPersistentNpc");
            var task = (ParcelTask) api.addTask(level, quest.getCodeString(), "cqtest:persistence/task", "contactquests:parcel_task", taskData);
            var data = ServerQuestFile.INSTANCE.getOrCreateTeamData(team);
            ((ITeamDataExtension) data).contactQuests$unlockTag("CQPersistTag");
            data.setProgress(task, 3);
            ContactKubeJSPlugin.editQuestTitleSaved(level, quest.getCodeString(), "重进后任务标题", "zh_cn");
            ContactKubeJSPlugin.editQuestSubtitleSaved(level, quest.getCodeString(), "重进后任务副标题", "zh_cn");
            ContactKubeJSPlugin.editQuestDescriptionSaved(level, quest.getCodeString(), java.util.List.of("重进后任务描述"), "zh_cn");
            ContactKubeJSPlugin.editQuestTitleSaved(level, quest.getCodeString(), "重进后任务标题", "en_us");
            ContactKubeJSPlugin.editQuestSubtitleSaved(level, quest.getCodeString(), "重进后任务副标题", "en_us");
            ContactKubeJSPlugin.editQuestDescriptionSaved(level, quest.getCodeString(), java.util.List.of("重进后任务描述"), "en_us");
            ContactKubeJSPlugin.setNpcConfig(level, "CQPersistentNpc", 8765, true);
            TaskDeliverySavedData.Companion.get(level).addParcel(player, task.id, 12000, "CQPersistentNpc", 2, team.getId());
            var doomed = api.createQuest(level, chapter.getCodeString(), "cqtest:persistence/deleted", "Must stay deleted");
            String deleted = doomed.getCodeString(); api.deleteQuest(level, deleted);

            JsonObject snapshot = new JsonObject();
            snapshot.addProperty("seedPid", ProcessHandle.current().pid());
            snapshot.addProperty("player", player.getUUID().toString()); snapshot.addProperty("team", team.getId().toString());
            snapshot.addProperty("personal", personalId.toString()); snapshot.addProperty("dimension", level.dimension().location().toString());
            snapshot.addProperty("chapter", chapter.getCodeString()); snapshot.addProperty("quest", quest.getCodeString());
            snapshot.addProperty("mutex", rule.getCodeString()); snapshot.addProperty("task", task.getCodeString()); snapshot.addProperty("deleted", deleted);
            var editor = ServerQuestFile.INSTANCE.getQuest(ContactQuestsTestServerController.getEditorQuestId());
            if (editor == null || !ContactQuestsEditorAssertions.taskFields(editor)
                    || !ContactQuestsEditorAssertions.rewardFields(editor, ContactQuestsTestServerController.getEditorEditedTableId())) {
                throw new IllegalStateException("Edited task/reward graph must be verified before persistence snapshot");
            }
            snapshot.addProperty("editorQuest", editor.getCodeString());
            snapshot.addProperty("editorTable", Long.toUnsignedString(ContactQuestsTestServerController.getEditorEditedTableId(), 16));
            snapshot.addProperty("editorMutex", Long.toUnsignedString(ContactQuestsTestServerController.getEditorMutexId(), 16));
            var collection = CollectionSavedData.Companion.get(level);
            require(collection.getTriggerCount(player.getUUID(), "CQCommandSelf") == 7
                    && collection.getTriggerCount(player.getUUID(), "CQCommandTarget") == 9
                    && collection.getTriggerCount(player.getUUID(), "CQTest_CountApi") == 9,
                    "Command and Rhino count assertions must run before persistence snapshot");
            snapshot.addProperty("countSelf", 7);
            snapshot.addProperty("countTarget", 9);
            snapshot.addProperty("countApi", 9);
            // An unknown NPC uses the default NOW policy: returning the items
            // immediately still records one mistake and must survive shutdown.
            String nowNpc = "CQRejoinWrongNow";
            collection.addItems(player, nowNpc, java.util.List.of(new ItemStack(Items.DIRT, 12)));
            require(collection.getTriggerCount(player.getUUID(), nowNpc) == 1
                    && collection.getStacks(player.getUUID(), nowNpc).isEmpty(),
                    "NOW must count one returned mail and keep no confiscated items");
            snapshot.addProperty("countNow", 1);
            putPos(snapshot, "teamPos", teamLower.above()); putPos(snapshot, "personalPos", personalLower.above());
            putPos(snapshot, "teamRemoved", removedLower.above()); putPos(snapshot, "personalRemoved", personalRemovedLower.above());
            ServerQuestFile.INSTANCE.saveNow(); manager.saveNow(); player.server.saveEverything(false, true, true);
            Files.writeString(snapshotPath(), new GsonBuilder().setPrettyPrinting().create().toJson(snapshot));
            ContactQuestsTestServerController.recordCase("server.persistence.prepared", true,
                    "Seeded once: party/personal mailboxes, broken replacements, exact mail, dynamic graph, tags, progress, text overrides and pending amount");
        } catch (Exception error) {
            ContactQuestsTestServerController.recordCase("server.persistence.prepared", false, error.toString());
        }
    }

    public static void verify(ServerPlayer player) {
        ContactQuestsPublicApiAssertions.rejoin(player);
        ContactQuestsGraphOptionsAssertions.rejoin(player);
        try {
            JsonObject saved = JsonParser.parseString(Files.readString(snapshotPath())).getAsJsonObject();
            require(Boolean.getBoolean("contactquests.test.rejoin") && saved.get("seedPid").getAsLong() != ProcessHandle.current().pid(),
                    "Rejoin is not a fresh process");
            var level = player.serverLevel(); var manager = FTBTeamsAPI.api().getManager();
            var collection = CollectionSavedData.Companion.get(level);
            boolean counts = collection.getTriggerCount(player.getUUID(), "CQCommandSelf") == saved.get("countSelf").getAsInt()
                    && collection.getTriggerCount(player.getUUID(), "CQCommandTarget") == saved.get("countTarget").getAsInt()
                    && collection.getTriggerCount(player.getUUID(), "CQTest_CountApi") == saved.get("countApi").getAsInt()
                    && collection.getTriggerCount(player.getUUID(), "CQRejoinWrongNow") == saved.get("countNow").getAsInt();
            ContactQuestsTestServerController.recordCase("server.persistence.npc_trigger_counts", counts,
                    "A new JVM read command self=7, target=9, Rhino count=9 and actual NOW returned-mail count=1 from disk without reseeding");
            var beforeQueries = collection.save(new CompoundTag());
            var dispatcher = player.server.getCommands().getDispatcher();
            var commandSource = player.createCommandSourceStack().withPermission(2);
            dispatcher.execute("contactquests admin getcount CQCommandSelf", commandSource);
            dispatcher.execute("contactquests admin getcount CQCommandTarget " + player.getGameProfile().getName(), commandSource);
            dispatcher.execute("contactquests admin getcount CQCommandMissing", commandSource);
            require(beforeQueries.equals(collection.save(new CompoundTag())),
                    "Querying restored counts changed persistent data");
            UUID teamId = UUID.fromString(saved.get("team").getAsString()); UUID personal = UUID.fromString(saved.get("personal").getAsString());
            var team = manager.getTeamForPlayer(player).orElseThrow();
            require(team.getId().equals(teamId) && team.isPartyTeam(), "Live FTB team was not restored");
            require(GlobalPos.of(level.dimension(), pos(saved, "teamPos")).equals(PlatformHelper.getMailboxPos(teamId))
                    && teamId.equals(PlatformHelper.getMailboxOwner(level.dimension(), pos(saved, "teamPos")))
                    && teamId.equals(((IMailboxTeamAccessor) level.getBlockEntity(pos(saved, "teamPos"))).contactQuests$getTeamId()),
                    "Party owner/position/marker did not survive restart");
            require(PlatformHelper.getMailboxPos(player.getUUID()) == null, "Party mailbox is also a player mailbox");
            require(GlobalPos.of(level.dimension(), pos(saved, "personalPos")).equals(PlatformHelper.getMailboxPos(personal))
                    && personal.equals(PlatformHelper.getMailboxOwner(level.dimension(), pos(saved, "personalPos")))
                    && personal.equals(((IMailboxTeamAccessor) level.getBlockEntity(pos(saved, "personalPos"))).contactQuests$getTeamId()),
                    "Personal owner/position/marker changed on restart");
            require(mailItems(teamId, Items.DIAMOND) == 7 && mailItems(personal, Items.EMERALD) == 5, "Mailbox contents changed across real shutdown");
            ContactQuestsTestServerController.recordCase("server.persistence.mailboxes", true,
                    "Fresh process restored live FTB party, exclusive ownership, both positions/markers and exact diamond=7/emerald=5");
            for (String key : java.util.List.of("teamRemoved", "personalRemoved")) {
                var upper = pos(saved, key);
                require(PlatformHelper.getMailboxOwner(level.dimension(), upper) == null
                        && ((IMailboxTeamAccessor) level.getBlockEntity(upper)).contactQuests$getTeamId() == null,
                        key + " replacement acquired its former owner after restart");
                player.setShiftKeyDown(false); player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                require(level.getBlockState(upper).use(level, player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(upper), Direction.UP, upper, false)) == InteractionResult.FAIL,
                        key + " replacement can receive before fresh binding");
            }
            ContactQuestsTestServerController.recordCase("server.persistence.break_replaced_unbound", true,
                    "Party and personal replacements remained unbound and rejected collection after restart");
            var file = ServerQuestFile.INSTANCE;
            var quest = file.getQuest(id(saved, "quest")); var task = (ParcelTask) file.getTask(id(saved, "task"));
            require(quest != null && file.getChapter(id(saved, "chapter")) == quest.getChapter() && file.getQuest(id(saved, "deleted")) == null,
                    "Dynamic graph creation/deletion was not persisted");
            CompoundTag nbt = new CompoundTag(); quest.writeData(nbt);
            var data = file.getOrCreateTeamData(team);
            boolean fields = task.getCount() == 10 && task.getItemStack().is(Items.DIAMOND)
                    && task.getTargetAddressee().equals("CQPersistentNpc");
            boolean progress = data.getProgress(task) == 3;
            boolean tags = ((ITeamDataExtension) data).contactQuests$hasTag("CQPersistTag");
            var mutex = file.getQuest(id(saved, "mutex"));
            boolean rules = mutex != null && mutex.getTasks().size() == 1 && !data.isCompleted(mutex)
                    && data.canStartTasks(quest) && quest.isVisible(data)
                    && nbt.getList("cq_mutex_ids", 8).getString(0).equals(saved.get("mutex").getAsString());
            ContactQuestsTestServerController.recordCase("server.persistence.task_fields", fields,
                    "Restored diamond count=" + task.getCount() + ", recipient=" + task.getTargetAddressee());
            ContactQuestsTestServerController.recordCase("server.persistence.progress", progress,
                    "Restored exact progress=" + data.getProgress(task) + ", expected=3");
            ContactQuestsTestServerController.recordCase("server.persistence.tags", tags, "Restored CQPersistTag=" + tags);
            ContactQuestsTestServerController.recordCase("server.persistence.mutex", rules,
                    "Mutex has unfinished task; canStart=" + data.canStartTasks(quest) + ", visible=" + quest.isVisible(data));
            var npc = KubeJSNpcSavedData.Companion.get(level).getNpcData("CQPersistentNpc");
            require(npc != null && npc.getDeliveryTime() == 8765 && npc.getAutoFill()
                    && TaskDeliverySavedData.Companion.get(level).getPendingAmount(teamId, task.id) == 2,
                    "NPC config or accepted pending amount was not persisted");
            require("重进后任务标题".equals(ContactQuestsScriptAssertions.overrideMap(quest, "contactQuests$titleOverrides").get("zh_cn"))
                    && "重进后任务副标题".equals(ContactQuestsScriptAssertions.overrideMap(quest, "contactQuests$subtitleOverrides").get("zh_cn"))
                    && java.util.List.of("重进后任务描述").equals(ContactQuestsScriptAssertions.overrideMap(quest, "contactQuests$descOverrides").get("zh_cn")),
                    "Saved script text overrides were not loaded");
            ContactQuestsTestServerController.recordCase("server.persistence.graph_and_scripts", fields && progress && tags && rules,
                    "Read-only fresh process restored graph/deletion, exact progress=3, pending=2, tags/mutex/NPC and three saved text overrides");
            var editor = file.getQuest(id(saved, "editorQuest"));
            boolean editorFields = ContactQuestsEditorAssertions.taskFields(editor)
                    && ContactQuestsEditorAssertions.rewardFields(editor, id(saved, "editorTable"))
                    && ContactQuestsEditorAssertions.questRules(editor, id(saved, "editorMutex"), player.level().registryAccess());
            ContactQuestsTestServerController.recordCase("server.persistence.native_editor_fields", editorFields,
                    "Fresh process loaded all three native task edits, five reward edits, tag/mutex fields and Long count=3000000005");
        } catch (Exception error) {
            ContactQuestsTestServerController.recordCase("server.persistence.failure", false, error.toString());
        }
    }

    private static int mailItems(UUID owner, net.minecraft.world.item.Item item) {
        var mail = PlatformHelper.getMailboxContents(owner); int count = 0;
        for (int i = 0; i < mail.getContainerSize(); i++) count += ContactMailNbt.INSTANCE.readContents(mail.getItem(i))
                .stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
        return count;
    }

    public static BlockPos pos(JsonObject snapshot, String key) { return BlockPos.of(snapshot.get(key).getAsLong()); }
    private static long id(JsonObject snapshot, String key) { return Long.parseUnsignedLong(snapshot.get(key).getAsString(), 16); }
    private static void putPos(JsonObject snapshot, String key, BlockPos pos) { snapshot.addProperty(key, pos.asLong()); }
    private static UUID owner(ServerLevel level, BlockPos lower) { return PlatformHelper.getMailboxOwner(level.dimension(), lower.above()); }
    private static void require(boolean valid, String message) { if (!valid) throw new IllegalStateException(message); }
    private static void place(ServerLevel level, BlockPos lower) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlock(lower.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 3);
                for (int dy = 0; dy <= 2; dy++) {
                    var clearPos = lower.offset(dx, dy, dz);
                    if (!(level.getBlockState(clearPos).getBlock() instanceof MailboxBlock)) {
                        level.setBlock(clearPos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
        level.setBlock(lower.below(), Blocks.STONE.defaultBlockState(), 3);
        var state = BlockRegistry.GREEN_MAILBOX.get().defaultBlockState();
        level.setBlock(lower, state.setValue(MailboxBlock.HALF, DoubleBlockHalf.LOWER), 3);
        level.setBlock(lower.above(), state.setValue(MailboxBlock.HALF, DoubleBlockHalf.UPPER), 3);
    }
    private static InteractionResult bind(ServerPlayer player, BlockPos lower) {
        var binder = (TeamBindingItem) ModItems.INSTANCE.getTEAM_BINDING_CARD().get();
        player.setShiftKeyDown(true); player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(binder));
        return binder.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(lower), Direction.UP, lower, false)));
    }
}
