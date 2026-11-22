package com.creeperyang.contactquests.test;

import com.creeperyang.contactquests.compat.kubejs.QuestGraphApi;
import com.creeperyang.contactquests.quest.task.ParcelTask;
import com.creeperyang.contactquests.bridge.IMailboxTeamAccessor;
import com.creeperyang.contactquests.bridge.ITeamDataExtension;
import com.creeperyang.contactquests.mailbox.TeamMailboxBinding;
import com.flechazo.contact.common.block.MailboxBlock;
import com.creeperyang.contactquests.utils.ContactMailNbt;
import com.flechazo.contact.common.item.PostcardItem;
import com.flechazo.contact.common.registry.BlockRegistry;
import com.flechazo.contact.platform.PlatformHelper;
import com.mojang.authlib.GameProfile;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.CheckmarkTask;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

final class ContactQuestsInteractionFixtures {
    static final String TAG_A = "CQUiUnlockA", TAG_B = "CQUiUnlockB";
    static final String UNLOCK_POSTCARD_TEXT = "ContactQuests unlock postcard";
    static final String UNLOCK_POSTCARD_SENDER = "CQ unlock postcard NPC";
    static final ResourceLocation UNLOCK_POSTCARD_STYLE = new ResourceLocation("contact:creeper");
    static final long LONG_COUNT = 3_000_000_005L;
    static final String FOREIGN_TEAM_NAME = "CQ Mail Team";
    static final String LONG_FOREIGN_TEAM_NAME = "CQ foreign UI team with a deliberately long name for checking that the entire mailbox warning wraps instead of being clipped";
    private static Quest unlocked, dependencyLocked, mutexLocked;
    private static ParcelTask singleTagTask, multipleTagTask;
    private static Item singleTagItem, multipleTagItem;
    private static UUID foreignTeamId;
    private static BlockPos foreignMailbox;

    private ContactQuestsInteractionFixtures() {}

    static void addResultFields(com.google.gson.JsonObject result) {
        if (unlocked != null) result.addProperty("interactionUnlockQuest", unlocked.getCodeString());
        if (dependencyLocked != null) result.addProperty("interactionDependencyQuest", dependencyLocked.getCodeString());
        if (mutexLocked != null) result.addProperty("interactionMutexQuest", mutexLocked.getCodeString());
        if (singleTagTask != null) result.addProperty("interactionSingleTagTask", singleTagTask.getCodeString());
        if (multipleTagTask != null) result.addProperty("interactionMultipleTagTask", multipleTagTask.getCodeString());
        if (foreignTeamId != null) result.addProperty("interactionForeignTeam", foreignTeamId.toString());
        if (foreignMailbox != null) {
            result.addProperty("interactionForeignMailboxX", foreignMailbox.getX());
            result.addProperty("interactionForeignMailboxY", foreignMailbox.getY());
            result.addProperty("interactionForeignMailboxZ", foreignMailbox.getZ());
        }
    }

    private static Quest quest(net.minecraft.server.level.ServerLevel level, Chapter chapter, String suffix, String title) {
        return QuestGraphApi.INSTANCE.createQuest(level,
                chapter.getCodeString(), "cqtest:ui-interactions/" + suffix, title);
    }

    private static CheckmarkTask checkmark(Quest quest) {
        var task = new CheckmarkTask(ServerQuestFile.INSTANCE.newID(), quest);
        quest.addTask(task);
        ContactQuestsTestUtil.refreshIndex(ServerQuestFile.INSTANCE);
        return task;
    }

    static void tags(ServerPlayer player, String phase) {
        try {
            var level = player.serverLevel();
            var file = ServerQuestFile.INSTANCE;
            TeamData data = file.getOrCreateTeamData(player);
            if (phase.equals("prepare")) {
                var chapter = QuestGraphApi.INSTANCE.createChapter(level, "cqtest:ui-interactions/chapter", "Interaction regression");
                unlocked = quest(level, chapter, "available", "CQ UI available quest");
                dependencyLocked = quest(level, chapter, "dependency", "CQ UI dependency locked quest");
                mutexLocked = quest(level, chapter, "mutex", "CQ UI mutex locked quest");
                Quest dependency = quest(level, chapter, "dependency-blocker", "CQ UI unfinished dependency");
                Quest mutex = quest(level, chapter, "mutex-blocker", "CQ UI completed mutex");
                checkmark(unlocked); checkmark(dependencyLocked); checkmark(mutexLocked); checkmark(dependency);
                var mutexTask = checkmark(mutex);
                data.setProgress(mutexTask, 1);
                data.setCanEdit(player, false);
                for (Quest q : List.of(unlocked, dependencyLocked, mutexLocked)) {
                    QuestGraphApi.INSTANCE.updateQuest(level, q.getCodeString(), Map.of("requiredTags", List.of(TAG_A, TAG_B),
                            "tagMode", "ALL", "hideWithoutTags", true, "hideIfLocked", true));
                }
                QuestGraphApi.INSTANCE.updateQuest(level, dependencyLocked.getCodeString(), Map.of("dependencies", List.of(dependency.getCodeString())));
                QuestGraphApi.INSTANCE.updateQuest(level, mutexLocked.getCodeString(), Map.of("mutex", List.of(mutex.getCodeString()), "mutexMode", "ANY"));
                require(!data.canStartTasks(unlocked) && !unlocked.isVisible(data), "Missing ALL tags did not lock and hide the quest");
                seedTagItem(player, TAG_A);
                ContactQuestsTestServerController.recordCase("server.tags.ui_prepared", true, "Locked ALL-tag quest plus native dependency and completed mutex fixtures");
                return;
            }
            boolean first = phase.equals("first");
            require(isUnlockPostcard(player.getMainHandItem(), first ? new String[]{TAG_A} : new String[]{TAG_A, TAG_B}),
                    "Contact postcard identity, style, text, sender or unlock tags changed during item use");
            require(((ITeamDataExtension) data).contactQuests$hasTag(TAG_A), "Actual right-click did not unlock tag A");
            require(((ITeamDataExtension) data).contactQuests$hasTag(TAG_B) == !first, "Unexpected tag B state");
            require(data.canStartTasks(unlocked) == !first && unlocked.isVisible(data) == !first,
                    "ALL tags did not enforce the real locked-to-available transition");
            require(!data.canStartTasks(dependencyLocked) && !data.canStartTasks(mutexLocked),
                    "Tag right-click bypassed native dependencies or mutex completion");
            ContactQuestsTestServerController.recordCase("server.tags.right_click." + phase, true,
                    "Real client Contact postcard use: ALL availability=" + !first
                            + ", dependency/mutex remain locked; postcard style/text/sender/unlock tags preserved");
            ContactQuestsNewFeatureFixtures.verifyPhysicalEvents(player, phase);
            if (first) seedTagItem(player, TAG_A, TAG_B);
        } catch (Exception error) {
            ContactQuestsTestServerController.recordCase("server.tags.right_click." + phase, false, error.toString());
        }
    }

    private static void seedTagItem(ServerPlayer player, String... tags) {
        var item = PostcardItem.setText(PostcardItem.getPostcard(UNLOCK_POSTCARD_STYLE, false), UNLOCK_POSTCARD_TEXT);
        ContactMailNbt.INSTANCE.setSender(item, UNLOCK_POSTCARD_SENDER);
        var list = new ListTag();
        for (String tag : tags) list.add(StringTag.valueOf(tag));
        item.getOrCreateTag().put("ContactQuestsUnlockTags", list);
        player.getInventory().selected = 0;
        player.setItemInHand(InteractionHand.MAIN_HAND, item);
        player.inventoryMenu.broadcastChanges();
    }

    static boolean isUnlockPostcard(ItemStack item, String... tags) {
        if (!(item.getItem() instanceof PostcardItem) || item.getCount() != 1
                || !BuiltInRegistries.ITEM.getKey(item.getItem()).equals(new ResourceLocation("contact:postcard"))
                || !UNLOCK_POSTCARD_STYLE.equals(ContactMailNbt.INSTANCE.getPostcardStyleId(item))
                || !UNLOCK_POSTCARD_TEXT.equals(PostcardItem.getText(item))
                || !UNLOCK_POSTCARD_SENDER.equals(ContactMailNbt.INSTANCE.getSender(item))) return false;
        var custom = item.getTag();
        if (custom == null) return false;
        var actual = custom.getList("ContactQuestsUnlockTags", 8);
        if (actual.size() != tags.length) return false;
        for (int i = 0; i < tags.length; i++) if (!actual.getString(i).equals(tags[i])) return false;
        return true;
    }

    static void outsiderMailbox(ServerPlayer player, String phase) {
        try {
            var level = player.serverLevel();
            if (phase.equals("long_name") || phase.equals("short_name")) {
                var manager = (TeamManagerImpl) FTBTeamsAPI.api().getManager();
                var team = manager.getTeamByID(foreignTeamId).orElseThrow();
                String name = phase.equals("long_name") ? LONG_FOREIGN_TEAM_NAME : FOREIGN_TEAM_NAME;
                team.setProperty(dev.ftb.mods.ftbteams.api.property.TeamProperties.DISPLAY_NAME, name);
                team.markDirty();
                manager.syncToAll(team);
                ContactQuestsTestServerController.recordCase("server.mailbox." + phase, true,
                        "Native FTB team display name changed and synchronized for the named UI boundary: " + name);
                return;
            }
            if (phase.equals("prepare")) {
                player.closeContainer();
                var id = UUID.randomUUID();
                var profile = new GameProfile(id, "CQUIForeign");
                var fake = FakePlayerFactory.get(level, profile);
                ((TeamManagerImpl) FTBTeamsAPI.api().getManager()).playerLoggedIn(null, id, profile.getName());
                foreignTeamId = FTBTeamsAPI.api().getManager().createPartyTeam(fake,
                        FOREIGN_TEAM_NAME, "Outsider interaction fixture", Color4I.WHITE).getId();
                var origin = player.blockPosition();
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -1; dz <= 5; dz++) {
                        level.setBlock(origin.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                        for (int dy = 0; dy <= 3; dy++) {
                            var clearPos = origin.offset(dx, dy, dz);
                            if (!(level.getBlockState(clearPos).getBlock() instanceof MailboxBlock)) {
                                level.setBlock(clearPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                            }
                        }
                    }
                }
                var lower = player.blockPosition().offset(0, 0, 3);
                level.setBlock(lower.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                var block = BlockRegistry.GREEN_MAILBOX.get().defaultBlockState();
                var lowerState = block.setValue(MailboxBlock.HALF, DoubleBlockHalf.LOWER);
                require(level.setBlock(lower, lowerState, 3), "Foreign mailbox lower half was not placed");
                foreignMailbox = lower.above();
                require(level.setBlock(foreignMailbox, block.setValue(MailboxBlock.HALF, DoubleBlockHalf.UPPER), 3),
                        "Foreign mailbox upper half was not placed");
                require(lowerState.canSurvive(level, lower) && level.getBlockState(foreignMailbox).canSurvive(level, foreignMailbox),
                        "Foreign mailbox fixture cannot survive at its test position");
                PlatformHelper.setMailboxData(foreignTeamId, level.dimension(), foreignMailbox);
                var contents = new SimpleContainer(4); contents.setItem(0, new ItemStack(Items.DIRT, 11));
                PlatformHelper.setMailboxContents(foreignTeamId, contents);
                TeamMailboxBinding.INSTANCE.updateMarker(level, foreignMailbox, foreignTeamId);
                player.setShiftKeyDown(false);
                player.getInventory().selected = 0;
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.inventoryMenu.broadcastChanges();
                require(level.getBlockEntity(foreignMailbox) instanceof IMailboxTeamAccessor accessor
                                && foreignTeamId.equals(accessor.contactQuests$getTeamId()),
                        "Foreign mailbox fixture has no actual block entity and team marker");
                ContactQuestsTestServerController.recordCase("server.mailbox.outsider_prepared", true,
                        "Supported two-block foreign team mailbox exists with exact marker and 11 dirt; real client is outside that team");
            } else {
                require(foreignTeamId.equals(PlatformHelper.getMailboxOwner(level.dimension(), foreignMailbox)), "Client outsider use changed the mailbox owner");
                require(foreignTeamId.equals(((IMailboxTeamAccessor) level.getBlockEntity(foreignMailbox)).contactQuests$getTeamId()), "Team marker changed");
                var contents = PlatformHelper.getMailboxContents(foreignTeamId);
                int amount = 0;
                for (int i = 0; i < contents.getContainerSize(); i++) if (contents.getItem(i).is(Items.DIRT)) amount += contents.getItem(i).getCount();
                require(amount == 11, "Outsider collected or duplicated foreign team mail");
                ContactQuestsTestServerController.recordCase("server.mailbox.outsider_denied", true, "Real network block use preserved foreign owner, marker and dirt=11");
            }
        } catch (Exception error) {
            ContactQuestsTestServerController.recordCase("server.mailbox.outsider_" + phase, false, error.toString());
        }
    }

    static void tagConversion(ServerPlayer player, String phase) {
        try {
            var file = ServerQuestFile.INSTANCE;
            if (phase.equals("prepare")) {
                singleTagItem = BuiltInRegistries.ITEM.stream().filter(i -> new ItemStack(i).getTags().count() == 1).findFirst().orElseThrow();
                multipleTagItem = BuiltInRegistries.ITEM.stream().filter(i -> new ItemStack(i).getTags().count() > 1).findFirst().orElseThrow();
                var chapter = QuestGraphApi.INSTANCE.createChapter(player.serverLevel(), "cqtest:ui-conversions/chapter", "Tag conversion regression");
                var quest = QuestGraphApi.INSTANCE.createQuest(player.serverLevel(), chapter.getCodeString(), "cqtest:ui-conversions/quest", "CQ tag conversion quest");
                singleTagTask = new ParcelTask(file.newID(), quest);
                multipleTagTask = new ParcelTask(file.newID(), quest);
                singleTagTask.setItemStack(new ItemStack(singleTagItem));
                multipleTagTask.setItemStack(new ItemStack(multipleTagItem));
                multipleTagTask.setRawTitle("CQ retained conversion title");
                for (var task : List.of(singleTagTask, multipleTagTask)) {
                    task.setCount(LONG_COUNT); task.setTargetAddressee("CQTagConversionNpc"); quest.addTask(task);
                }
                file.getOrCreateTeamData(player).setCanEdit(player, true);
                QuestGraphApi.INSTANCE.updateQuest(player.serverLevel(), quest.getCodeString(), Map.of("x", 0, "y", 0));
                ContactQuestsTestServerController.recordCase("server.parcel.tag_conversion_prepared", true,
                        "Real item tags: single=" + BuiltInRegistries.ITEM.getKey(singleTagItem) + ", multiple=" + BuiltInRegistries.ITEM.getKey(multipleTagItem));
            } else {
                ParcelTask task = phase.equals("single") ? singleTagTask : multipleTagTask;
                Item original = phase.equals("single") ? singleTagItem : multipleTagItem;
                require(task.getCount() == LONG_COUNT && task.getTargetAddressee().equals("CQTagConversionNpc"), "Tag conversion truncated Long count or changed NPC");
                require(dev.ftb.mods.ftbquests.integration.item_filtering.ItemMatchingSystem.INSTANCE.isItemFilter(task.getItemStack())
                        && task.test(new ItemStack(original)), "Edited tag filter does not match the original tagged item");
                require(phase.equals("single") ? task.getRawTitle().startsWith("Any #") : task.getRawTitle().equals("CQ retained conversion title"), "Tag conversion changed title incorrectly");
                ContactQuestsTestServerController.recordCase("server.parcel.tag_conversion." + phase, true,
                        "Actual editor packet preserves count=3000000005, NPC and tag-filter matching; title rule holds");
            }
        } catch (Exception error) {
            ContactQuestsTestServerController.recordCase("server.parcel.tag_conversion." + phase, false, error.toString());
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
