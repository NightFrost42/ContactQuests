package com.creeperyang.contactquests.test;

import com.creeperyang.contactquests.client.util.ParcelAutoFiller;
import com.creeperyang.contactquests.client.util.PostcardAutoFiller;
import com.creeperyang.contactquests.client.util.RedPacketAutoFiller;
import com.creeperyang.contactquests.mixin.PostcardEditScreenAccessor;
import com.creeperyang.contactquests.mixin.RedPacketEnvelopeScreenAccessor;
import com.creeperyang.contactquests.quest.task.ParcelTask;
import com.creeperyang.contactquests.quest.task.PostcardTask;
import com.creeperyang.contactquests.quest.task.RedPacketTask;
import com.creeperyang.contactquests.quest.task.TaskRegistry;
import com.creeperyang.contactquests.quest.reward.RewardRegistry;
import com.flechazo.contact.client.gui.screen.EnvelopeScreen;
import com.flechazo.contact.client.gui.screen.PostboxScreen;
import com.flechazo.contact.client.gui.screen.PostcardEditScreen;
import com.flechazo.contact.client.gui.screen.RedPacketEnvelopeScreen;
import com.flechazo.contact.common.item.ParcelItem;
import com.flechazo.contact.common.item.PostcardItem;
import com.flechazo.contact.common.item.RedPacketItem;
import com.flechazo.contact.common.screenhandler.EnvelopeScreenHandler;
import com.flechazo.contact.common.screenhandler.PostboxScreenHandler;
import com.flechazo.contact.common.screenhandler.RedPacketEnvelopeScreenHandler;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.client.gui.quests.AddRewardButton;
import dev.ftb.mods.ftbquests.client.gui.quests.AddTaskButton;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.ftb.mods.ftblibrary.config.ui.resource.SelectableResource;
import dev.ftb.mods.ftblibrary.ui.Button;
import dev.ftb.mods.ftblibrary.ui.ContextMenu;
import dev.ftb.mods.ftblibrary.ui.ModalPanel;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.TextField;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

@EventBusSubscriber(modid = ContactQuestsTestMod.MOD_ID, value = Dist.CLIENT)
public final class ContactQuestsClientTestController {
    private static final Map<String, Object> result = new LinkedHashMap<>();
    private static final java.util.List<Map<String, Object>> assertions = new java.util.ArrayList<>();
    private static JsonArray steps;
    private static int stepIndex;
    private static int waitTicks;
    private static int levelWaitTicks;
    private static int serverResultWaitTicks;
    private static boolean started;
    private static boolean finished;
    private static boolean syntheticTasksInstalled;
    private static ParcelTask parcelTask;
    private static RedPacketTask redPacketTask;
    private static PostcardTask postcardTask;
    private static Quest editorQuest;
    private static QuestScreen editorScreen;
    private static int editorUiTaskActions;
    private static int editorUiRewardActions;
    private static boolean editorRequested;
    private static int editorWaitTicks;
    private static String movedMailType;
    private static Quest visibleTextQuest;
    private static int tagSelectionStage;
    private static com.creeperyang.contactquests.client.gui.TagSelectionScreen tagSelectionScreen;
    private static dev.ftb.mods.ftblibrary.config.StringConfig tagSelectionConfig;
    private static final java.util.List<String> tagSelectionValues = new java.util.ArrayList<>();
    private static final java.util.List<Boolean> tagSelectionCallbacks = new java.util.ArrayList<>();
    private static boolean tagSelectionExcluded;
    private static StyleSelectorDrawProbe styleDrawProbe;
    private static final java.util.List<Map<String, Object>> toasts = new java.util.ArrayList<>();
    private static final java.util.Set<String> sentMailTypes = new java.util.LinkedHashSet<>();
    private static final java.util.List<Component> receivedSystemMessages = new java.util.ArrayList<>();
    private static final java.util.List<Boolean> receivedMessageOverlays = new java.util.ArrayList<>();
    private static int interactionMessageStart;
    private static net.minecraft.network.chat.Style unlockLinkStyle;
    private static ItemStack usedUnlockPostcard = ItemStack.EMPTY;
    private static int foreignMailboxWaitTicks;
    private static ParcelTask conversionTask;
    private static dev.ftb.mods.ftbquests.api.ItemFilterAdapter conversionAdapter;
    private static net.minecraft.tags.TagKey<net.minecraft.world.item.Item> conversionTag;
    private static ItemStack conversionOriginal;
    private static java.util.List<net.minecraft.tags.TagKey<net.minecraft.world.item.Item>> conversionTags;
    private static QuestScreen conversionScreen;
    private static com.creeperyang.contactquests.quest.reward.ParcelChoiceReward featureChoice;
    private static QuestScreen featureScreen;
    private static String featureText;
    private static String featureRecipient;
    private static String featureMailText;
    private static Screen noJeiSubmission;
    private static ParcelTask noJeiTask;
    private static java.util.List<ItemStack> noJeiInventory;
    private static long noJeiProgress;
    private static long seededMailDeadline;
    private static final Map<Long, java.util.Set<String>> featureAnimationDraws = new LinkedHashMap<>();
    private static final Map<String, Long> caseDeadlines = new LinkedHashMap<>();

    private ContactQuestsClientTestController() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (finished || !Boolean.getBoolean("contactquests.test.client")) {
            return;
        }

        if (!started) {
            started = true;
            Minecraft.getInstance().options.pauseOnLostFocus = false;
            loadScenario();
        }
        if (finished || steps == null) {
            return;
        }
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }

        while (stepIndex < steps.size()) {
            JsonObject step = steps.get(stepIndex++).getAsJsonObject();
            if (step.has("requires_real_llm") && step.get("requires_real_llm").getAsBoolean()
                    && !Boolean.getBoolean("contactquests.test.realLlm")) continue;
            String action = step.get("action").getAsString();
            switch (action) {
                case "llm_live" -> {
                    String phase = step.get("phase").getAsString();
                    if (java.util.Set.of("publish", "return", "restart").contains(phase)) {
                        featureMailText = phase.equals("return") ? "CQ_LIVE_ESCROW_RETURN"
                                : phase.equals("restart") ? "CQ_LIVE_RESTART" : null;
                    }
                    sendServerCommand("contactquests_test llm_live " + phase);
                }
                case "llm_live_with_rewards_mail" -> {
                    featureMailText = "CQ_LIVE_ESCROW_RETURN";
                    sendServerCommand("contactquests_test llm_live_with_rewards_mail");
                }
                case "llm_live_ui" -> liveLlmUi(step.get("phase").getAsString());
                case "wait_for_server_case" -> waitForServerCase(step);
                case "features" -> sendServerCommand("contactquests_test features " + step.get("phase").getAsString());
                case "features_seed_mail" -> {
                    featureMailText = step.get("text").getAsString();
                    sendServerCommand("contactquests_test features_seed_mail " + featureMailText);
                }
                case "features_ui" -> featureUi(step.get("phase").getAsString());
                case "graph_options_ui" -> {
                    if (!ContactQuestsGraphOptionsClientAssertions.run(step.get("phase").getAsString())) {
                        stepIndex--;
                        waitTicks = 5;
                    }
                }
                case "wait_ticks" -> waitTicks = step.get("ticks").getAsInt();
                case "assert_level_loaded" -> assertCondition(
                        Minecraft.getInstance().level != null,
                        "client level is loaded"
                );
                case "wait_for_level" -> waitForLevel();
                case "wait_for_server_result" -> waitForServerResult(step);
                case "run_team_binding_flow" -> sendServerCommand("contactquests_test team_binding_flow");
                case "run_command_smoke" -> sendServerCommand("contactquests_test command_smoke");
                case "no_kubejs" -> sendServerCommand("contactquests_test no_kubejs " + step.get("phase").getAsString());
                case "assert_no_kubejs" -> assertCase("client.no_kubejs.dependencies_absent",
                        ContactQuestsNoKubejsFixtures.dependenciesAbsent(), "KubeJS/Rhino mods and classes are absent in the real client");
                case "assert_no_jei" -> assertCase("client.no_jei.dependencies_absent",
                        ContactQuestsNoKubejsFixtures.recipeViewerAbsent(), "JEI mod and API are absent in the real Windows client");
                case "no_jei_recipe" -> noJeiRecipe(step.get("phase").getAsString());
                case "open_no_kubejs_editor" -> openNoKubejsEditor();
                case "assert_no_kubejs_progress" -> assertNoKubejsProgress(step.get("phase").getAsString());
                case "editor_rules" -> sendServerCommand("contactquests_test editor_rules " + step.get("phase").getAsString());
                case "open_editor_rule_effect" -> openEditorRuleEffect();
                case "assert_editor_rule_effect" -> assertEditorRuleEffect(step.get("phase").getAsString());
                case "assert_command_count_messages" -> assertCommandCountMessages();
                case "interaction_tags" -> sendServerCommand("contactquests_test interaction_tags " + step.get("phase").getAsString());
                case "use_unlock_item" -> useUnlockItem(step.get("phase").getAsString());
                case "assert_unlock_postcard" -> assertUnlockPostcard(step.get("phase").getAsString());
                case "assert_unlock_messages" -> assertUnlockMessages(step.get("phase").getAsString());
                case "click_unlock_link" -> clickUnlockLink();
                case "assert_unlock_link_opened" -> assertUnlockLinkOpened();
                case "interaction_mailbox" -> sendServerCommand("contactquests_test interaction_mailbox " + step.get("phase").getAsString());
                case "use_foreign_mailbox" -> useForeignMailbox();
                case "assert_foreign_mailbox_message" -> assertForeignMailboxMessage();
                case "jade_mailbox" -> {
                    if (!ContactQuestsJadeAssertions.run(step.get("phase").getAsString())) {
                        stepIndex--;
                        waitTicks = 5;
                    }
                }
                case "test_localization" -> {
                    if (!ContactQuestsLocalizationTests.run(step.get("language").getAsString())) {
                        stepIndex--;
                        waitTicks = 5;
                    }
                }
                case "set_client_language" -> ContactQuestsLocalizationTests.setLanguage(step.get("language").getAsString());
                case "interaction_conversion" -> sendServerCommand("contactquests_test interaction_conversion " + step.get("phase").getAsString());
                case "convert_parcel_tag" -> convertParcelTag(step.get("phase").getAsString());
                case "run_task_runtime_flow" -> sendServerCommand("contactquests_test task_runtime_flow");
                case "prepare_script_graph" -> sendServerCommand("contactquests_test prepare_script_graph");
                case "verify_mail_persistence" -> sendServerCommand("contactquests_test verify_mail_persistence");
                case "assert_server_case" -> assertServerCase(step.get("id").getAsString());
                case "assert_screen" -> assertScreen(step.get("screen").getAsString());
                case "assert_gui_providers" -> assertGuiProviders();
                case "install_synthetic_tasks" -> installSyntheticTasks();
                case "assert_client_text_resolution" -> assertClientTextResolution();
                case "open_ftb_editor" -> openFtbEditor();
                case "create_ui_task" -> createUiTask(
                        step.get("type").getAsString(),
                        step.get("item").getAsString(),
                        step.get("count").getAsInt()
                );
                case "create_ui_reward" -> createUiReward(
                        step.get("type").getAsString(),
                        step.get("item").getAsString(),
                        step.get("count").getAsInt()
                );
                case "assert_editor_graph" -> assertEditorGraph();
                case "edit_ui_object" -> editUiObject(step);
                case "test_style_selector" -> testStyleSelector(step.get("kind").getAsString());
                case "click_binder_button" -> clickBinderButton();
                case "assert_binder_button" -> assertBinderButton();
                case "assert_editor_fields" -> assertEditorFields();
                case "verify_editor_fields" -> sendServerCommand("contactquests_test verify_editor_fields");
                case "collect_editor_postcard" -> sendServerCommand("contactquests_test collect_editor_postcard");
                case "open_received_postcard" -> openReceivedPostcard();
                case "assert_received_postcard" -> assertReceivedPostcard();
                case "verify_editor_server" -> sendServerCommand("contactquests_test verify_editor");
                case "prepare_editor_claims" -> sendServerCommand("contactquests_test prepare_editor_claims");
                case "click_editor_reward_buttons" -> clickEditorRewardButtons(step.get("repeated").getAsBoolean());
                case "verify_editor_claims" -> sendServerCommand("contactquests_test verify_editor_claims");
                case "verify_editor_repeat" -> sendServerCommand("contactquests_test verify_editor_repeat");
                case "assert_script_sync" -> assertScriptSync();
                case "open_script_quest" -> openScriptQuest();
                case "assert_visible_quest_text" -> assertVisibleQuestText();
                case "request_live_text" -> sendServerCommand("contactquests_test text_phase " + step.get("phase").getAsString());
                case "assert_live_text" -> assertLiveText(step);
                case "screenshot" -> screenshot(step.get("name").getAsString());
                case "assert_postcard_layout" -> ContactQuestsPostcardLayoutAssertions.verify(step.get("id").getAsString());
                case "assert_choice_templates_layout" -> ContactQuestsPostcardLayoutAssertions.verifyBundledTemplates();
                case "postcard_letters" -> sendServerCommand("contactquests_test postcard_letters " + step.get("phase").getAsString());
                case "postcard_letters_ui" -> readPostcardLetters(step.has("rejoin") && step.get("rejoin").getAsBoolean());
                case "test_config_gui" -> testConfigGui();
                case "test_tag_selection" -> testTagSelection();
                case "tag_selection_ui" -> ContactQuestsTagSelectionAssertions.run(step);
                case "prepare_rejoin" -> sendServerCommand("contactquests_test prepare_rejoin");
                case "verify_rejoin" -> sendServerCommand("contactquests_test verify_rejoin");
                case "prepare_native_reload" -> sendServerCommand("contactquests_test prepare_reload");
                case "native_reload" -> sendServerCommand("reload");
                case "assert_rejoin_client" -> assertRejoinClient();
                case "open_task_display" -> openTaskDisplay(step.get("type").getAsString());
                case "click_task_submit" -> clickTaskSubmit(step.get("type").getAsString());
                case "open_world" -> openWorld(step.get("world").getAsString());
                case "open_postbox_screen" -> openPostboxScreen();
                case "open_real_postbox" -> sendServerCommand("contactquests_test open_postbox");
                case "open_real_envelope" -> sendServerCommand("contactquests_test open_envelope");
                case "open_real_red_packet" -> sendServerCommand("contactquests_test open_red_packet");
                case "seed_mail" -> sendServerCommand("contactquests_test seed_mail " + step.get("type").getAsString());
                case "seed_autofill" -> sendServerCommand("contactquests_test seed_autofill " + step.get("type").getAsString());
                case "move_mail_to_postbox" -> moveMailToPostbox(step.get("type").getAsString());
                case "set_recipient" -> setRecipient(step.get("value").getAsString());
                case "query_recipient_server" -> sendServerCommand("contactquests_test query_recipient " + step.get("value").getAsString());
                case "assert_recipient" -> assertRecipient(step.get("value").getAsString());
                case "select_first_recipient" -> selectFirstRecipient();
                case "click_send" -> invokeScreenMethod("send");
                case "force_send" -> sendServerCommand("contactquests_test force_send " + step.get("value").getAsString());
                case "assert_postbox_empty" -> assertPostboxEmpty();
                case "schedule_autofill" -> scheduleAutofill(step.get("type").getAsString());
                case "assert_autofill_container" -> assertAutofillContainer(step);
                case "autofill_boundary" -> ContactQuestsAutofillAssertions.run(step);
                case "autofill_lifecycle" -> {
                    if (!ContactQuestsAutofillLifecycleAssertions.run(step.get("phase").getAsString())) {
                        stepIndex--;
                        waitTicks = 1;
                    }
                }
                case "wrapping_autofill" -> ContactQuestsWrappingPaperAssertions.run(step);
                case "mailbox_binding_ui" -> ContactQuestsMailboxBindingUiAssertions.run(step);
                case "ui_navigation" -> ContactQuestsUiNavigationAssertions.run(step);
                case "additional_ui" -> additionalUi(step);
                case "delivery_boundary" -> deliveryBoundary(step);
                case "assert_red_packet_blessing" -> assertRedPacketBlessing(step.get("value").getAsString());
                case "open_postcard_screen" -> openPostcardScreen();
                case "assert_postcard_text" -> assertPostcardText(step.get("value").getAsString());
                case "close_screen" -> Minecraft.getInstance().setScreen(null);
                case "finish" -> {
                    if (!Boolean.getBoolean("contactquests.test.noKubejs")) {
                        assertCase("kubejs.client.ticks", Boolean.TRUE.equals(result.get("kubejsClientTicks")),
                                "Client KubeJS tick script executed");
                    }
                    if (!finished) finish(true, "scenario completed");
                }
                default -> finish(false, "unknown action: " + action);
            }
            if (finished || waitTicks > 0) {
                return;
            }
        }
        finish(true, "scenario completed");
    }

    private static void featureUi(String phase) {
        try {
            var mc = Minecraft.getInstance(); var file = ClientQuestFile.INSTANCE;
            if (phase.startsWith("visible_")) {
                var server = editorServerResult();
                var reward = (com.creeperyang.contactquests.quest.reward.ParcelChoiceReward) file.getReward(
                        Long.parseUnsignedLong(server.get("featureVisibleChoice").getAsString(), 16));
                if (phase.equals("visible_choose")) {
                    var screen = ClientQuestFile.openGui(reward.getQuest(), false);
                    var button = flatten(screen.viewQuestPanel).stream()
                            .filter(w -> w instanceof dev.ftb.mods.ftbquests.client.gui.quests.RewardButton)
                            .filter(w -> { try { return readField(w, "reward") == reward; } catch (Exception e) { return false; } })
                            .map(w -> (Button) w).findFirst().orElseThrow();
                    var outerTip = new dev.ftb.mods.ftblibrary.util.TooltipList();
                    button.addMouseOverText(outerTip);
                    String outerText = tooltipText(outerTip).replaceAll("\\s+", " ");
                    boolean outerContents = !file.canEdit() && !reward.getHideContents();
                    for (var entry : reward.getTable().getWeightedRewards()) {
                        outerContents &= outerText.contains(entry.getReward().getTitle().getString().replaceAll("\\s+", " "));
                    }
                    assertCase("client.features.visible_choice_outer_tooltip", outerContents
                                    && outerText.contains(Component.translatable("contactquests.choice.postcard_required")
                                        .getString().replaceAll("\\s+", " ")),
                            "Actual non-editor outer RewardButton tooltip lists native choices and postcard instruction: " + outerText);
                    button.onClicked(MouseButton.LEFT);
                    var selection = (com.creeperyang.contactquests.client.gui.ChoiceRewardScreen) currentGui();
                    boolean good = !file.canEdit() && !reward.getHideContents() && reward.getOptionLabels().isEmpty()
                            && selection.getOptionButtons().size() == 2;
                    var details = new java.util.ArrayList<String>();
                    for (int i = 0; i < 2; i++) {
                        var option = selection.getOptionButtons().get(i);
                        var entry = (dev.ftb.mods.ftbquests.quest.reward.ItemReward) reward.getTable().getWeightedRewards().get(i).getReward();
                        var icon = (dev.ftb.mods.ftblibrary.icon.Icon) readField(option, "icon");
                        var tip = new dev.ftb.mods.ftblibrary.util.TooltipList(); option.addMouseOverText(tip);
                        String title = option.getTitle().getString(), text = tooltipText(tip);
                        String itemName = entry.getItem().getHoverName().getString();
                        good &= title.equals(entry.getTitle().getString()) && title.contains(itemName)
                                && title.contains(Integer.toString(entry.getCount())) && text.contains(title)
                                && text.replaceAll("\\s+", " ").contains(Component.translatable("contactquests.choice.postcard_required")
                                    .getString().replaceAll("\\s+", " "))
                                && icon.getIngredient() instanceof ItemStack stack && stack.is(entry.getItem().getItem());
                        details.add(title + ": " + text);
                    }
                    assertCase("client.features.visible_choice_display", good,
                            "Actual non-editor choice uses native item names/counts/icons and complete option tooltip with no author labels: " + details);
                    selection.getOptionButtons().getFirst().onClicked(MouseButton.LEFT);
                } else if (phase.equals("visible_request")) {
                    var requestScreen = (com.creeperyang.contactquests.client.gui.ChoicePostcardScreen) currentGui();
                    var request = requestScreen.getRequest();
                    featureText = request.getText(); featureMailText = featureText; featureRecipient = request.getRecipient();
                    assertCase("client.features.visible_choice_request", featureText.contains(reward.optionLabel(0, false).getString())
                                    && featureRecipient.equals(reward.getTargetAddressee()) && !featureText.contains("CQ-Choice-")
                                    && !featureText.contains("{request}"),
                            "Natural unhidden letter identifies the chosen native item and actual NPC without visible technical routing data");
                    var before = mc.player.getInventory().items.stream().map(ItemStack::copy).toList();
                    boolean claimed = file.selfTeamData.isRewardClaimed(mc.player.getUUID(), reward);
                    var copy = flatten(requestScreen).stream().filter(w -> w instanceof Button b
                                    && b.getTitle().getString().equals(Component.translatable("contactquests.choice.copy_text").getString()))
                            .map(w -> (Button) w).findFirst().orElseThrow();
                    clickActualFtbButton(requestScreen, copy);
                    String clipboard = readClipboardWithRetry(mc);
                    assertCase("client.features.choice_copy_text", clipboard.equals(request.getText())
                                    && currentGui() == requestScreen && requestScreen.getRequest() == request
                                    && claimed == file.selfTeamData.isRewardClaimed(mc.player.getUUID(), reward)
                                    && java.util.stream.IntStream.range(0, before.size()).allMatch(i ->
                                        ItemStack.matches(before.get(i), mc.player.getInventory().getItem(i))),
                            "Actual copy-text click copies the complete letter without changing request, claim or any stationery components"
                                    + " (clipboardLength=" + clipboard.length() + ", expectedLength=" + request.getText().length() + ")");
                    var writeTip = new dev.ftb.mods.ftblibrary.util.TooltipList();
                    requestScreen.getWriteButton().addMouseOverText(writeTip);
                    String writeText = tooltipText(writeTip).replaceAll("\\s+", " ");
                    assertCase("client.features.choice_write_tooltip", writeText.contains(Component.translatable(
                                    "contactquests.choice.send_hint", request.getRecipient()).getString().replaceAll("\\s+", " "))
                                    && writeText.contains(request.getRecipient()) && !writeText.contains("ContactQuestsChoice")
                                    && !writeText.contains("CQ-Choice-"),
                            "Actual write-button tooltip names the real NPC and contains no technical request identity: " + writeText);
                    requestScreen.getWriteButton().onClicked(MouseButton.LEFT);
                    waitTicks = 30 + featureText.length() * Math.max(1, com.creeperyang.contactquests.config.ContactConfig.INSTANCE.getAutoFillSpeed().get());
                } else if (phase.equals("visible_write")) {
                    if (!(mc.screen instanceof PostcardEditScreen)) throw new IllegalStateException("Real visible-choice Contact editor missing");
                    ContactQuestsPostcardLayoutAssertions.verify("client.postcard.layout.visible_choice");
                    var page = readField(((PostcardEditScreenAccessor) mc.screen).contactQuests$getTextBox(), "page");
                    assertCase("client.features.visible_choice_written", featureText.equals(page)
                                    && com.creeperyang.contactquests.data.ChoiceRewardSavedData.cardToken(mc.player.getMainHandItem()) != null,
                            "Real native editor contains the exact natural selected-item letter and invisible server marker");
                    ((net.minecraft.client.gui.components.Button) readField(mc.screen, "buttonDone")).onPress();
                } else if (phase.equals("visible_sync")) {
                    assertCase("client.features.visible_choice_claim_sync", file.selfTeamData.isRewardClaimed(mc.player.getUUID(), reward),
                            "Actual non-hidden selection claim synchronized after the real postcard was posted");
                } else throw new IllegalArgumentException("Unknown visible choice phase " + phase);
                return;
            }
            if (phase.equals("creation") || phase.equals("creation_rejoin")) {
                var server = editorServerResult();
                var quest = file.getQuest(Long.parseUnsignedLong(server.get("creationQuest").getAsString(), 16));
                var gui = ClientQuestFile.openGui(quest, true);
                var texts = flatten(gui.viewQuestPanel).stream().filter(w -> w instanceof TextField).map(w -> renderedText((TextField)w)).toList();
                var q = new dev.ftb.mods.ftblibrary.config.ConfigGroup("quest"); quest.fillConfigGroup(q);
                var c = new dev.ftb.mods.ftblibrary.config.ConfigGroup("chapter"); quest.getChapter().fillConfigGroup(c);
                boolean nativeFields = ContactQuestsCreationAssertions.verify(quest.getChapter(), quest, mc.level.registryAccess());
                boolean good = nativeFields
                        && texts.contains("Creation bundle") && texts.contains("Creation quest subtitle")
                        && texts.contains("One") && texts.contains("Two")
                        && flatten(gui.viewQuestPanel).stream().filter(w -> w instanceof dev.ftb.mods.ftbquests.client.gui.quests.RewardButton).count() == 4;
                var missing = new java.util.ArrayList<String>();
                for (String key : java.util.List.of("x","y","size","icon_scale","min_width","shape","guide_page","hide_details_until_startable","dependency_requirement","require_sequential_tasks")) if (findConfig(q,key) == null) missing.add("quest."+key);
                for (String key : java.util.List.of("subtitle","default_quest_size","default_min_width","default_repeatable","consume_items","require_sequential_tasks")) if (findConfig(c,key) == null) missing.add("chapter."+key);
                good &= missing.isEmpty();
                assertCase("client.features." + phase, good, "Actual native quest panel and editor controls reflect the full Rhino creation bundle and all configured children: " + texts
                        + "; fields=" + nativeFields + "; missing=" + missing + (nativeFields ? "" : "; " + ContactQuestsCreationAssertions.diagnostic(quest.getChapter(), quest, mc.level.registryAccess())));
                return;
            }
            // SyncQuestsMessage replaces the complete client file. Resolve the stable ID
            // again after each graph edit instead of retaining an invalid reward/quest.
            var featureResult = editorServerResult();
            featureChoice = (com.creeperyang.contactquests.quest.reward.ParcelChoiceReward) file.getReward(
                    Long.parseUnsignedLong(featureResult.get("featureChoice").getAsString(), 16));
            if (phase.equals("open") || phase.equals("rejoin")) {
                var server = editorServerResult();
                var quest = file.getQuest(Long.parseUnsignedLong(server.get("featureQuest").getAsString(), 16));
                featureChoice = (com.creeperyang.contactquests.quest.reward.ParcelChoiceReward) file.getReward(
                        Long.parseUnsignedLong(server.get("featureChoice").getAsString(),16));
                featureScreen = ClientQuestFile.openGui(quest, false);
                return;
            }
            if (phase.equals("hidden") || phase.equals("hidden_rejoin")) {
                var quest = file.getQuest(featureChoice.getQuest().id);
                featureScreen = ClientQuestFile.openGui(quest, false);
                var buttons = flatten(featureScreen.viewQuestPanel).stream().filter(w -> w instanceof dev.ftb.mods.ftbquests.client.gui.quests.RewardButton).toList();
                boolean hidden = buttons.size() == 6 && !file.canEdit();
                for (var widget : buttons) {
                    var reward = (com.creeperyang.contactquests.quest.reward.ParcelRewardBase) readField(widget,"reward");
                    var tip = new dev.ftb.mods.ftblibrary.util.TooltipList(); widget.addMouseOverText(tip);
                    String text = tooltipText(tip).toLowerCase();
                    hidden &= reward.getTitle().getString().equals(Component.translatable("contactquests.reward.sealed").getString())
                            && reward.getIcon().getIngredient() instanceof ItemStack stack && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals("contact:parcel")
                            && widget.getIngredientUnderMouse().isEmpty() && !text.contains("secret") && !text.contains("777")
                            && !text.contains("diamond") && !text.contains("emerald") && !text.contains("%") && !text.contains("91");
                }
                assertCase(phase.equals("hidden") ? "client.features.hidden" : "client.features.hidden_rejoin", hidden,
                        "Actual six FTB reward buttons: sealed title/Contact parcel icon; no contents, quantities, probabilities, custom title/icon or ingredient leakage");
            } else if (phase.equals("editor")) {
                featureAnimationDraws.clear();
                featureScreen = ClientQuestFile.openGui(featureChoice.getQuest(), true);
                var tableRewards = featureChoice.getQuest().getRewards().stream().filter(r -> r instanceof com.creeperyang.contactquests.quest.reward.ParcelRandomReward
                        && !(r instanceof com.creeperyang.contactquests.quest.reward.ParcelChoiceReward)).toList();
                boolean good = file.canEdit() && tableRewards.size() == 2;
                for (var reward : tableRewards) {
                    // The actual table alt icon preserves native animations even with an author's custom override.
                    var icon = reward.getAltIcon();
                    good &= icon instanceof dev.ftb.mods.ftblibrary.icon.IconAnimation animation && animation.list.size() == 2;
                    var tip = new dev.ftb.mods.ftblibrary.util.TooltipList(); reward.addMouseOverText(tip);
                    String text = tooltipText(tip);
                    if (reward instanceof com.creeperyang.contactquests.quest.reward.ParcelAllTableReward) good &= !text.contains("%");
                    else good &= text.contains("25") && text.contains("75") && text.contains("%");
                }
                for (var reward : tableRewards) {
                    good &= reward.getIcon() instanceof dev.ftb.mods.ftblibrary.icon.IconAnimation animation && animation.list.size() == 2;
                    if (reward.getIcon() instanceof dev.ftb.mods.ftblibrary.icon.IconAnimation animation) {
                        var observed = featureAnimationDraws.computeIfAbsent(reward.id, ignored -> new java.util.LinkedHashSet<>());
                        for (int i = 0; i < animation.list.size(); i++) {
                            var original = animation.list.get(i);
                            animation.list.set(i, new dev.ftb.mods.ftblibrary.icon.Icon() {
                                @Override public void draw(net.minecraft.client.gui.GuiGraphics graphics, int x, int y, int w, int h) {
                                    Object ingredient = original.getIngredient();
                                    if (ingredient instanceof ItemStack stack) observed.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                                    original.draw(graphics, x, y, w, h);
                                }
                                @Override public Object getIngredient() { return original.getIngredient(); }
                            });
                        }
                    }
                }
                var group = new dev.ftb.mods.ftblibrary.config.ConfigGroup("choice"); featureChoice.fillConfigGroup(group);
                good &= findConfig(group,"hide_contents") != null && findConfig(group,"option_labels") != null && findConfig(group,"template_group") != null;
                assertCase("client.features.table_editor", good, "Native editor sees two rotating contents, original FTB 25%/75% tooltip and new hide/template/labels controls; canEdit="
                        + file.canEdit() + "; icons=" + tableRewards.stream().map(r -> r.getIcon().getClass().getSimpleName()).toList()
                        + "; tooltips=" + tableRewards.stream().map(r -> { var tip = new dev.ftb.mods.ftblibrary.util.TooltipList(); r.addMouseOverText(tip); return tooltipText(tip); }).toList());
            } else if (phase.equals("animation")) {
                assertCase("client.features.table_animation", featureAnimationDraws.size() == 2 && featureAnimationDraws.values().stream()
                        .allMatch(items -> items.equals(java.util.Set.of("minecraft:apple", "minecraft:diamond"))),
                        "Actual FTB Random and All reward buttons drew both native content icons across real frames: " + featureAnimationDraws);
            } else if (phase.equals("choose")) {
                featureScreen = ClientQuestFile.openGui(featureChoice.getQuest(), false);
                var button = flatten(featureScreen.viewQuestPanel).stream().filter(w -> w instanceof dev.ftb.mods.ftbquests.client.gui.quests.RewardButton)
                        .filter(w -> { try { return readField(w,"reward") == featureChoice; } catch(Exception e) { return false; } }).map(w -> (Button) w).findFirst().orElseThrow();
                button.onClicked(MouseButton.LEFT);
                var selection = (com.creeperyang.contactquests.client.gui.ChoiceRewardScreen) currentGui();
                boolean good = selection.getOptionButtons().size() == 2 && selection.getOptionButtons().get(1).getTitle().getString().equals("B");
                assertCase("client.features.choice_selection", good, "Actual FTB reward click opens two hidden labelled choices; selecting B requests a Contact postcard");
                selection.getOptionButtons().get(1).onClicked(MouseButton.LEFT);
            } else if (phase.equals("request")) {
                var request = ((com.creeperyang.contactquests.client.gui.ChoicePostcardScreen) currentGui()).getRequest();
                featureText = request.getText(); featureRecipient = request.getRecipient();
                featureMailText = featureText;
                assertCase("client.features.choice_request", !featureText.contains("CQ:") && !featureText.contains("{request}")
                                && featureText.contains("B") && featureRecipient.equals(featureChoice.getTargetAddressee()),
                        "Natural server letter uses the actual NPC name and selected public label; no technical identifier or hidden reward content");
                assertCase("client.features.choice_stationery_ready",
                        mc.player.getInventory().getItem(0).has(com.flechazo.contact.common.component.ContactDataComponents.POSTCARD_SENDER.get())
                                && com.creeperyang.contactquests.data.ChoiceRewardSavedData.isEditablePostcard(mc.player.getInventory().getItem(9)),
                        "Received read-only card precedes a genuine editable card outside the hotbar");
                ((com.creeperyang.contactquests.client.gui.ChoicePostcardScreen) currentGui()).getWriteButton().onClicked(MouseButton.LEFT);
                // Exercise the configured animated typing, then assert the complete
                // native page rather than checking a fixed early partial prefix.
                waitTicks = 30 + featureText.length() * Math.max(1, com.creeperyang.contactquests.config.ContactConfig.INSTANCE.getAutoFillSpeed().get());
            } else if (phase.equals("write")) {
                if (!(mc.screen instanceof PostcardEditScreen)) throw new IllegalStateException("Native Contact postcard editor is not open");
                ContactQuestsPostcardLayoutAssertions.verify("client.postcard.layout.hidden_choice");
                assertCase("client.features.choice_stationery", mc.player.getMainHandItem().getItem() instanceof PostcardItem
                                && com.creeperyang.contactquests.data.ChoiceRewardSavedData.cardToken(mc.player.getMainHandItem()) != null
                                && !mc.player.getMainHandItem().has(com.flechazo.contact.common.component.ContactDataComponents.POSTCARD_SENDER.get())
                                && mc.player.getInventory().items.stream().filter(s ->
                                        "An earlier correspondent".equals(s.get(com.flechazo.contact.common.component.ContactDataComponents.POSTCARD_SENDER.get()))
                                                && !com.creeperyang.contactquests.data.ChoiceRewardSavedData.hasChoiceMarker(s)).count() == 1,
                        "The normal Write button moved blank stationery from inventory to hand and opened its real editor, leaving the earlier letter unmarked");
                var text = readField(((PostcardEditScreenAccessor) mc.screen).contactQuests$getTextBox(), "page");
                assertCase("client.features.choice_written", featureText.equals(text), "Genuine Contact postcard editor contains the natural server template: expected="
                        + featureText + "; actual=" + text);
                var textbox = ((PostcardEditScreenAccessor) mc.screen).contactQuests$getTextBox();
                // Replace every word, including the chosen reward name. The hidden selection alone authorizes it.
                var helper = readField(textbox, "textInputUtil");
                helper.getClass().getMethod("selectAll").invoke(helper);
                helper.getClass().getMethod("insertText", String.class).invoke(helper, "你好，前些日子的事办妥啦。多谢关照，回头见！");
                var page = textbox.getClass().getDeclaredField("page"); page.setAccessible(true);
                page.set(textbox, "你好，前些日子的事办妥啦。多谢关照，回头见！");
                try { textbox.getClass().getMethod("shouldRefresh").invoke(textbox); }
                catch (NoSuchMethodException missing) {
                    var modified = textbox.getClass().getDeclaredField("isModified"); modified.setAccessible(true);
                    modified.setBoolean(textbox, true);
                }
                featureMailText = "你好，前些日子的事办妥啦。多谢关照，回头见！";
                ((net.minecraft.client.gui.components.Button) readField(mc.screen,"buttonDone")).onPress();
            } else if (phase.equals("written_card")) {
                var card = mc.player.getInventory().items.stream().filter(s -> s.getItem() instanceof PostcardItem)
                        .filter(s -> featureMailText.equals(s.get(com.flechazo.contact.common.component.ContactDataComponents.POSTCARD_TEXT.get())))
                        .findFirst().orElseThrow();
                var token = com.creeperyang.contactquests.data.ChoiceRewardSavedData.cardToken(card);
                var visible = new java.util.ArrayList<String>(); visible.add(card.getHoverName().getString());
                for (var flag : java.util.List.of(net.minecraft.world.item.TooltipFlag.NORMAL, net.minecraft.world.item.TooltipFlag.ADVANCED))
                    card.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc.level), mc.player, flag)
                            .stream().map(Component::getString).forEach(visible::add);
                assertCase("client.features.choice_card_private", token != null && visible.stream().noneMatch(t ->
                                t.contains(token.toString()) || t.contains("ContactQuestsChoice") || t.contains("CQ-Choice-")),
                        "Actual written card's normal and advanced title/tooltips contain no request identity: " + visible);
            } else if (phase.equals("recipient")) {
                setRecipient(featureRecipient);
            } else if (phase.equals("sync")) {
                assertCase("client.features.choice_claim_sync", file.selfTeamData.isRewardClaimed(mc.player.getUUID(), featureChoice), "Actual FTB claimed state arrived after real postcard submission");
            }
        } catch (Exception error) { assertCase("client.features." + phase, false, error.toString()); }
    }

    private static String tooltipText(dev.ftb.mods.ftblibrary.util.TooltipList list) {
        return list.getLines().stream().map(Component::getString).collect(java.util.stream.Collectors.joining("\n"));
    }

    private static void clickActualFtbButton(dev.ftb.mods.ftblibrary.ui.BaseScreen gui, Button button) {
        var screen = Minecraft.getInstance().screen;
        if (currentGui() != gui) throw new IllegalStateException("FTB button belongs to an inactive screen");
        int x = button.getX() + button.width / 2, y = button.getY() + button.height / 2;
        gui.updateGui(x, y, 0);
        gui.updateMouseOver(x, y);
        if (!screen.mouseClicked(x, y, 0)) throw new IllegalStateException("FTB button did not consume actual mouse click");
        screen.mouseReleased(x, y, 0);
    }

    private static void noJeiRecipe(String phase) {
        try {
            var mc = Minecraft.getInstance(); var file = ClientQuestFile.INSTANCE;
            if (phase.equals("open")) {
                if (!ContactQuestsNoKubejsFixtures.recipeViewerAbsent()) throw new IllegalStateException("JEI is still present");
                var server = editorServerResult();
                var quest = file.getQuest(Long.parseUnsignedLong(server.get("noKubejsQuestId").getAsString(), 16));
                noJeiTask = (ParcelTask) quest.getTasks().stream().filter(t -> t instanceof ParcelTask).findFirst().orElseThrow();
                file.setEditorPermission(false);
                var gui = ClientQuestFile.openGui(quest, false);
                noJeiInventory = mc.player.getInventory().items.stream().map(ItemStack::copy).toList();
                noJeiProgress = file.selfTeamData.getProgress(noJeiTask);
                var button = flatten(gui.viewQuestPanel).stream().filter(w -> w instanceof dev.ftb.mods.ftbquests.client.gui.quests.TaskButton)
                        .filter(w -> { try { return readField(w, "task") == noJeiTask; } catch (Exception e) { return false; } })
                        .map(w -> (Button) w).findFirst().orElseThrow();
                button.onClicked(MouseButton.LEFT);
                noJeiSubmission = mc.screen;
                if (!(currentGui() instanceof com.creeperyang.contactquests.client.gui.BaseValidTaskScreen<?>))
                    throw new IllegalStateException("Actual non-editor no-JEI TaskButton did not open submission page");
            } else if (phase.equals("click")) {
                if (mc.screen != noJeiSubmission) throw new IllegalStateException("No-JEI submission page missing");
                var valid = (com.creeperyang.contactquests.client.gui.BaseValidTaskScreen<?>) currentGui();
                var cell = flatten(valid.getMainPanel()).stream().filter(w -> w.getClass().getName().equals(
                        "com.creeperyang.contactquests.client.gui.BaseGridTaskScreen$ValidItemButton")).findFirst().orElseThrow();
                var ingredient = cell.getIngredientUnderMouse().orElseThrow().ingredient();
                if (!(ingredient instanceof ItemStack stack) || !stack.is(Items.DIAMOND))
                    throw new IllegalStateException("Wrong no-JEI recipe cell");
                double x = cell.getX() + cell.getWidth() / 2.0, y = cell.getY() + cell.getHeight() / 2.0;
                if (!mc.screen.mouseClicked(x, y, 0)) throw new IllegalStateException("Native item-cell click was not consumed");
                noJeiSubmission.mouseReleased(x, y, 0);
            } else if (phase.equals("verify")) {
                boolean good = mc.screen == noJeiSubmission && !file.canEdit()
                        && file.selfTeamData.getProgress(noJeiTask) == noJeiProgress
                        && dev.ftb.mods.ftbquests.FTBQuests.getRecipeModHelper() instanceof dev.ftb.mods.ftbquests.integration.RecipeModHelper.NoOp
                        && mc.player.containerMenu.getCarried().isEmpty();
                for (int i = 0; i < noJeiInventory.size(); i++) good &= ItemStack.matches(noJeiInventory.get(i), mc.player.getInventory().getItem(i));
                for (Object filler : java.util.List.of(ParcelAutoFiller.INSTANCE, RedPacketAutoFiller.INSTANCE, PostcardAutoFiller.INSTANCE))
                    good &= Boolean.FALSE.equals(readField(filler, "isTaskScheduled")) && readField(filler, "taskData") == null;
                assertCase("client.no_jei.recipe_noop", good,
                        "Actual item-cell click without JEI keeps the same ordinary submission page, full inventory/progress and idle fillers; no optional class loading error");
                mc.setScreen(null);
            } else throw new IllegalArgumentException(phase);
        } catch (Exception error) { assertCase("client.no_jei.recipe_noop", false, phase + ": " + error); }
    }

    @SubscribeEvent
    public static void onSystemMessage(net.neoforged.neoforge.client.event.ClientChatReceivedEvent.System event) {
        if (Boolean.getBoolean("contactquests.test.client")) {
            receivedSystemMessages.add(event.getMessage().copy());
            receivedMessageOverlays.add(event.isOverlay());
        }
    }

    private static void assertCommandCountMessages() {
        String self = Component.translatable("contactquests.command.get_count.success", "CQCommandSelf", 7).getString();
        String target = Component.translatable("contactquests.command.get_count.success_target",
                Minecraft.getInstance().player.getDisplayName(), "CQCommandTarget", 9).getString();
        String absent = Component.translatable("contactquests.command.get_count.success", "CQCommandMissing", 0).getString();
        java.util.List<String> actual = receivedSystemMessages.stream().map(Component::getString).toList();
        assertCase("client.commands.getcount_visible", actual.contains(self) && actual.contains(target) && actual.contains(absent),
                "Real translated query messages contain exact counts: " + self + " / " + target + " / " + absent + "; observed=" + actual);
        if (!Boolean.getBoolean("contactquests.test.rejoin")) {
            String setTarget = Component.translatable("contactquests.command.set_count.success_target",
                    Minecraft.getInstance().player.getDisplayName(), "CQCommandTarget", 9).getString();
            assertCase("client.commands.setcount_visible", actual.contains(setTarget) && !setTarget.contains("%s")
                    && !setTarget.contains("%d") && setTarget.contains("9"),
                    "Real target setcount message formats all three arguments: " + setTarget);
        }
    }

    private static void useUnlockItem(String phase) {
        try {
            interactionMessageStart = receivedSystemMessages.size();
            Minecraft.getInstance().setScreen(null);
            var player = Minecraft.getInstance().player;
            player.getInventory().selected = 0;
            usedUnlockPostcard = player.getMainHandItem().copy();
            String[] tags = phase.equals("first")
                    ? new String[]{ContactQuestsInteractionFixtures.TAG_A}
                    : new String[]{ContactQuestsInteractionFixtures.TAG_A, ContactQuestsInteractionFixtures.TAG_B};
            boolean valid = ContactQuestsInteractionFixtures.isUnlockPostcard(usedUnlockPostcard, tags);
            assertCase("client.tags.contact_postcard_used." + phase, valid,
                    "Right-click input is the real contact:postcard with creeper style, text, sender and exact unlock tags");
            if (!valid) return;
            Minecraft.getInstance().gameMode.useItem(player, InteractionHand.MAIN_HAND);
            var screen = Minecraft.getInstance().screen;
            assertCase("client.tags.contact_postcard_reader." + phase,
                    screen instanceof com.flechazo.contact.client.gui.screen.PostcardReadScreen
                            && ItemStack.isSameItemSameComponents((ItemStack) readField(screen, "postcard"), usedUnlockPostcard),
                    "Contact's actual item use opens its received postcard reader while the server processes unlock tags");
        } catch (Exception error) {
            assertCase("client.tags.contact_postcard_used." + phase, false, error.toString());
        }
    }

    private static java.util.List<Component> newInteractionMessages() {
        return java.util.List.copyOf(receivedSystemMessages.subList(interactionMessageStart, receivedSystemMessages.size()));
    }

    private static void assertUnlockPostcard(String phase) {
        var held = Minecraft.getInstance().player.getMainHandItem();
        assertCase("client.tags.contact_postcard_preserved." + phase,
                held.getCount() == usedUnlockPostcard.getCount()
                        && ItemStack.isSameItemSameComponents(held, usedUnlockPostcard),
                "After server item use the synchronized Contact postcard and all components remain unchanged");
    }

    private static String translationKey(Component message) {
        return message.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents contents ? contents.getKey() : "";
    }

    private static void assertUnlockMessages(String phase) {
        try {
            var messages = newInteractionMessages();
            var notifications = messages.stream().filter(m -> translationKey(m).equals("contactquests.message.new_quest_found")).toList();
            var tags = messages.stream().filter(m -> translationKey(m).equals("contactquests.message.tags_unlocked")).toList();
            var server = editorServerResult();
            var quest = ClientQuestFile.INSTANCE.getQuest(Long.parseUnsignedLong(server.get("interactionUnlockQuest").getAsString(), 16));
            var dependency = ClientQuestFile.INSTANCE.getQuest(Long.parseUnsignedLong(server.get("interactionDependencyQuest").getAsString(), 16));
            var mutex = ClientQuestFile.INSTANCE.getQuest(Long.parseUnsignedLong(server.get("interactionMutexQuest").getAsString(), 16));
            boolean second = phase.equals("second"), first = phase.equals("first");
            boolean valid = tags.size() == (phase.equals("repeated") ? 0 : 1) && notifications.size() == (second ? 1 : 0)
                    && ClientQuestFile.INSTANCE.selfTeamData.canStartTasks(quest) == !first
                    && quest.isVisible(ClientQuestFile.INSTANCE.selfTeamData) == !first
                    && !ClientQuestFile.INSTANCE.selfTeamData.canStartTasks(dependency)
                    && !ClientQuestFile.INSTANCE.selfTeamData.canStartTasks(mutex);
            if (second) {
                unlockLinkStyle = null;
                String expected = "/contactquests open " + quest.getCodeString();
                notifications.getFirst().visit((style, text) -> {
                    var click = style.getClickEvent();
                    if (click != null && click.getAction() == net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND
                            && click.getValue().equals(expected)) unlockLinkStyle = style;
                    return java.util.Optional.empty();
                }, net.minecraft.network.chat.Style.EMPTY);
                valid &= unlockLinkStyle != null && notifications.getFirst().getString().contains(quest.getTitle().getString());
            }
            assertCase("client.tags.unlock_notification." + phase, valid,
                    "Real item use: notifications=" + notifications.size() + ", tag messages=" + tags.size()
                            + "; visible/startable=" + !first + "; native dependency and mutex stay locked; messages="
                            + messages.stream().map(Component::getString).toList());
        } catch (Exception error) {
            assertCase("client.tags.unlock_notification." + phase, false, error.toString());
        }
    }

    private static void clickUnlockLink() {
        try {
            if (unlockLinkStyle == null) throw new IllegalStateException("Actual notification contains no quest link");
            var chat = new net.minecraft.client.gui.screens.ChatScreen("");
            Minecraft.getInstance().setScreen(chat);
            assertCase("client.tags.unlock_link_clicked", chat.handleComponentClicked(unlockLinkStyle),
                    "Minecraft handled the RUN_COMMAND style captured from the actual translated notification");
        } catch (Exception error) {
            assertCase("client.tags.unlock_link_clicked", false, error.toString());
        }
    }

    private static void assertUnlockLinkOpened() {
        try {
            var gui = currentGui();
            var id = Long.parseUnsignedLong(editorServerResult().get("interactionUnlockQuest").getAsString(), 16);
            assertCase("client.tags.unlock_link_opened", gui instanceof QuestScreen screen
                    && readField(screen.viewQuestPanel, "quest") instanceof Quest quest && quest.getId() == id,
                    "Real server command and OpenQuest payload displayed the linked quest detail panel");
        } catch (Exception error) {
            assertCase("client.tags.unlock_link_opened", false, error.toString());
        }
    }

    private static void useForeignMailbox() {
        try {
            Minecraft.getInstance().setScreen(null);
            var minecraft = Minecraft.getInstance();
            var data = editorServerResult();
            var upper = new net.minecraft.core.BlockPos(data.get("interactionForeignMailboxX").getAsInt(),
                    data.get("interactionForeignMailboxY").getAsInt(), data.get("interactionForeignMailboxZ").getAsInt());
            var team = java.util.UUID.fromString(data.get("interactionForeignTeam").getAsString());
            var blockEntity = minecraft.level.getBlockEntity(upper);
            if (!(blockEntity instanceof com.creeperyang.contactquests.bridge.IMailboxTeamAccessor accessor)
                    || !team.equals(accessor.contactQuests$getTeamId())) {
                if (++foreignMailboxWaitTicks > 40) {
                    assertCase("client.mailbox.outsider_visible", false,
                            "Foreign mailbox block entity and team marker did not synchronize within 200 ticks; state="
                                    + minecraft.level.getBlockState(upper) + ", entity=" + blockEntity
                                    + ", paused=" + minecraft.isPaused() + ", screen=" + minecraft.screen);
                    return;
                }
                stepIndex--;
                waitTicks = 5;
                return;
            }
            result.put("foreignMailboxWaitTicks", foreignMailboxWaitTicks * 5);
            interactionMessageStart = receivedSystemMessages.size();
            minecraft.player.getInventory().selected = 0;
            minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(upper),
                            net.minecraft.core.Direction.UP, upper, false));
        } catch (Exception error) {
            assertCase("client.mailbox.outsider_visible", false, error.toString());
        }
    }

    private static void assertForeignMailboxMessage() {
        try {
            var data = editorServerResult();
            var upper = new net.minecraft.core.BlockPos(data.get("interactionForeignMailboxX").getAsInt(),
                    data.get("interactionForeignMailboxY").getAsInt(), data.get("interactionForeignMailboxZ").getAsInt());
            var team = java.util.UUID.fromString(data.get("interactionForeignTeam").getAsString());
            var blockEntity = Minecraft.getInstance().level.getBlockEntity(upper);
            boolean marker = blockEntity instanceof com.creeperyang.contactquests.bridge.IMailboxTeamAccessor accessor
                    && team.equals(accessor.contactQuests$getTeamId());
            String expected = Component.translatable("message.contactquests.binding.team_owner", ContactQuestsInteractionFixtures.LONG_FOREIGN_TEAM_NAME).getString();
            var messages = newInteractionMessages().stream().map(Component::getString).toList();
            boolean menuClosed = Minecraft.getInstance().player.containerMenu == Minecraft.getInstance().player.inventoryMenu;
            String actualMarker = blockEntity instanceof com.creeperyang.contactquests.bridge.IMailboxTeamAccessor accessor
                    ? String.valueOf(accessor.contactQuests$getTeamId()) : String.valueOf(blockEntity);
            assertCase("client.mailbox.outsider_visible", marker && messages.contains(expected)
                    && menuClosed,
                    "Client foreign team marker=" + marker + " (" + actualMarker + "), menuClosed=" + menuClosed
                            + ", menu=" + Minecraft.getInstance().player.containerMenu.getClass().getName()
                            + "; expected=" + expected + "; messages=" + messages);
            int index = java.util.stream.IntStream.range(interactionMessageStart, receivedSystemMessages.size())
                    .filter(i -> receivedSystemMessages.get(i).getString().equals(expected)).findFirst().orElseThrow();
            var chat = Minecraft.getInstance().gui.getChat();
            int width = (int) Math.floor(chat.getWidth() / chat.getScale());
            var rendered = Minecraft.getInstance().font.getSplitter().splitLines(receivedSystemMessages.get(index), width,
                    net.minecraft.network.chat.Style.EMPTY);
            assertCase("client.mailbox.outsider_chat_wrap", !receivedMessageOverlays.get(index) && rendered.size() > 1
                            && rendered.stream().allMatch(line -> Minecraft.getInstance().font.width(line) <= width),
                    "Actual server denial uses chat, not overlay; complete long team name wraps into " + rendered.size()
                            + " lines at chat width=" + width);
            Minecraft.getInstance().setScreen(new net.minecraft.client.gui.screens.ChatScreen(""));
        } catch (Exception error) {
            assertCase("client.mailbox.outsider_visible", false, error.toString());
        }
    }

    private static void convertParcelTag(String phase) {
        String id = "client.parcel.tag_conversion." + phase;
        try {
            if (phase.equals("single") || phase.equals("multiple_cancel")) {
                boolean single = phase.equals("single");
                var data = editorServerResult();
                conversionTask = (ParcelTask) ClientQuestFile.INSTANCE.getTask(Long.parseUnsignedLong(
                        data.get(single ? "interactionSingleTagTask" : "interactionMultipleTagTask").getAsString(), 16));
                conversionOriginal = conversionTask.getItemStack().copy();
                conversionTags = conversionOriginal.getTags().sorted(java.util.Comparator.comparing(t -> t.location().toString())).toList();
                conversionTag = conversionTags.getFirst();
                conversionAdapter = dev.ftb.mods.ftbquests.integration.item_filtering.ItemMatchingSystem.INSTANCE.adapters().stream()
                        .filter(a -> a.getName().equals(ContactQuestsTestTagAdapter.NAME)).findFirst().orElseThrow();
                // This profile is explicitly the fixture's editor. The server has
                // already enabled edit mode; also grant the client editor capability.
                ClientQuestFile.INSTANCE.setEditorPermission(true);
                if (!ClientQuestFile.INSTANCE.canEdit())
                    throw new IllegalStateException("Server fixture edit mode has not reached the client");
                conversionScreen = ClientQuestFile.openGui(conversionTask.getQuest(), true);
                editorScreen = conversionScreen;
                conversionScreen.viewQuest(conversionTask.getQuest());
                var button = new dev.ftb.mods.ftbquests.client.gui.quests.TaskButton(conversionScreen.viewQuestPanel, conversionTask);
                button.onClicked(MouseButton.RIGHT);
                clickContextMenuEntry(Component.translatable("ftbquests.task.ftbquests.item.convert_tag", conversionAdapter.getName()).getString());
                if (single) {
                    assertCase(id, conversionTask.getCount() == ContactQuestsInteractionFixtures.LONG_COUNT
                            && conversionTask.test(conversionOriginal) && conversionTask.getRawTitle().equals("Any #" + conversionTag.location()),
                            "Real right-click single-tag conversion preserves Long=3000000005 and matches original tagged item");
                } else {
                    if (!(currentGui() instanceof com.creeperyang.contactquests.client.gui.ParcelTagSelectionScreen))
                        throw new IllegalStateException("Multiple tags did not open ContactQuests tag selector: " + currentGui());
                    Method cancel = findMethod(currentGui().getClass(), "doCancel");
                    cancel.setAccessible(true); cancel.invoke(currentGui());
                    assertCase(id, currentGui() == conversionScreen && conversionTask.getCount() == ContactQuestsInteractionFixtures.LONG_COUNT
                            && ItemStack.isSameItemSameComponents(conversionTask.getItemStack(), conversionOriginal)
                            && conversionTask.getRawTitle().equals("CQ retained conversion title"),
                            "Multiple-tag Cancel returns to quest screen and preserves item, Long count, NPC and custom title");
                    button.onClicked(MouseButton.RIGHT);
                    clickContextMenuEntry(Component.translatable("ftbquests.task.ftbquests.item.convert_tag", conversionAdapter.getName()).getString());
                    waitTicks = 10;
                }
                return;
            }
            var selector = currentGui();
            if (!(selector instanceof com.creeperyang.contactquests.client.gui.ParcelTagSelectionScreen screen))
                throw new IllegalStateException("ContactQuests tag selector missing: " + selector);
            var rows = flatten(screen).stream().filter(w -> w instanceof Button b
                    && conversionTags.stream().anyMatch(t -> b.getTitle().getString().equals(t.location().toString())))
                    .map(w -> (Button) w).toList();
            rows.stream().filter(b -> b.getTitle().getString().equals(conversionTag.location().toString())).findFirst().orElseThrow().onClicked(MouseButton.LEFT);
            assertCase(id, rows.size() == conversionTags.size() && conversionTask.getCount() == ContactQuestsInteractionFixtures.LONG_COUNT
                    && conversionTask.test(conversionOriginal) && conversionTask.getRawTitle().equals("CQ retained conversion title")
                    && currentGui() == conversionScreen, "Actual tag row selected with all rows present; count=3000000005, NPC and custom title preserved");
        } catch (Exception error) {
            assertCase(id, false, error.toString());
        }
    }

    @SubscribeEvent
    public static void onToast(net.neoforged.neoforge.client.event.ToastAddEvent event) {
        if (!Boolean.getBoolean("contactquests.test.client")) return;
        var toast = event.getToast();
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("type", toast.getClass().getName());
        entry.put("step", stepIndex);
        if (toast instanceof dev.ftb.mods.ftbquests.client.gui.CustomToast custom) {
            entry.put("title", custom.getTitle().getString());
            entry.put("subtitle", custom.getSubtitle() == null ? "" : custom.getSubtitle().getString());
            entry.put("barrier", custom.getIcon() == dev.ftb.mods.ftblibrary.icon.Icons.BARRIER);
        }
        entry.put("callers", java.util.Arrays.stream(Thread.currentThread().getStackTrace()).skip(2).limit(14)
                .map(StackTraceElement::toString).toList());
        toasts.add(entry);
        System.out.println("[contactquests_test] toast=" + new GsonBuilder().create().toJson(entry));
    }

    public static String getScreenName() {
        Screen screen = Minecraft.getInstance().screen;
        return screen == null ? "none" : screen.getClass().getSimpleName();
    }

    public static void recordScriptTick() {
        result.put("kubejsClientTicks", true);
    }

    private static void loadScenario() {
        String pathValue = System.getProperty(
                "contactquests.test.clientScenario",
                "tests/scenarios/common/client-smoke.json"
        );
        Path path = Paths.get(pathValue);
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject scenario = JsonParser.parseReader(reader).getAsJsonObject();
            steps = scenario.getAsJsonArray("steps");
            result.put("scenario", scenario.has("id") ? scenario.get("id").getAsString() : pathValue);
            if (steps == null) {
                finish(false, "scenario has no steps");
            }
        } catch (Exception exception) {
            finish(false, "failed to load scenario: " + exception.getMessage());
        }
    }

    private static void openPostboxScreen() {
        if (Minecraft.getInstance().player == null) {
            finish(false, "cannot open postbox without a client player");
            return;
        }
        var inventory = Minecraft.getInstance().player.getInventory();
        var handler = new PostboxScreenHandler(0, inventory, false);
        Minecraft.getInstance().setScreen(new PostboxScreen(handler, inventory, Component.literal("Test Postbox")));
    }

    private static void openPostcardScreen() {
        if (Minecraft.getInstance().player == null) {
            finish(false, "cannot open postcard without a client player");
            return;
        }
        ItemStack postcard = PostcardItem.getPostcard(
                ResourceLocation.fromNamespaceAndPath("contact", "default"), false);
        Minecraft.getInstance().setScreen(new PostcardEditScreen(
                postcard, Minecraft.getInstance().player, InteractionHand.MAIN_HAND));
    }

    private static void openWorld(String worldId) {
        String address = System.getProperty("contactquests.test.serverAddress");
        if (address != null && !address.isBlank()) {
            Minecraft minecraft = Minecraft.getInstance();
            net.minecraft.client.gui.screens.ConnectScreen.startConnecting(minecraft.screen, minecraft,
                    net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(address),
                    new net.minecraft.client.multiplayer.ServerData("ContactQuests separate-server test", address,
                            net.minecraft.client.multiplayer.ServerData.Type.OTHER), false, null);
            return;
        }
        boolean exists = Minecraft.getInstance().getLevelSource().levelExists(worldId);
        result.put("worldExists", exists);
        result.put("worldBaseDir", Minecraft.getInstance().getLevelSource().getBaseDir().toString());
        if (!exists) {
            finish(false, "client world does not exist: " + worldId);
            return;
        }
        Minecraft.getInstance().createWorldOpenFlows().openWorld(worldId, () -> {
        });
    }

    private static void waitForLevel() {
        if (Minecraft.getInstance().level != null) {
            if (System.getProperty("contactquests.test.serverAddress") != null) {
                try (Reader reader = Files.newBufferedReader(Path.of(System.getProperty("contactquests.test.serverResult")))) {
                    var server = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("runtime");
                    assertCase("client.multiplayer.separate_process", Minecraft.getInstance().getSingleplayerServer() == null
                                    && server.get("dedicated").getAsBoolean() && server.get("pid").getAsLong() != ProcessHandle.current().pid(),
                            "Windows client is connected over TCP to a dedicated server in a separate JVM");
                } catch (Exception error) {
                    assertCase("client.multiplayer.separate_process", false, error.toString());
                }
            }
            levelWaitTicks = 0;
            return;
        }
        if (levelWaitTicks++ > 600) {
            finish(false, "client level did not load within 600 ticks");
            return;
        }
        stepIndex--;
        waitTicks = 5;
    }

    private static void waitForServerResult(JsonObject step) {
        Path resultPath = Paths.get(step.has("path")
                ? step.get("path").getAsString()
                : System.getProperty("contactquests.test.serverResult", "test-results/server-results.json"));
        if (!Files.exists(resultPath)) {
            retryServerResult(step, "server result file does not exist yet");
            return;
        }

        try (Reader reader = Files.newBufferedReader(resultPath)) {
            JsonObject serverResult = JsonParser.parseReader(reader).getAsJsonObject();
            result.put("serverResult", serverResult);
            JsonArray failures = serverResult.has("failures")
                    ? serverResult.getAsJsonArray("failures") : new JsonArray();
            if (!failures.isEmpty()) {
                finish(false, "server fixture failed: " + failures);
                return;
            }
            if (serverResult.has("success") && serverResult.get("success").getAsBoolean()) {
                return;
            }
            retryServerResult(step, "server fixture has not observed all mail yet");
        } catch (Exception exception) {
            finish(false, "failed to read server result: " + exception.getMessage());
        }
    }

    private static void assertServerCase(String id) {
        Path resultPath = Paths.get(System.getProperty(
                "contactquests.test.serverResult",
                "test-results/server-results.json"
        ));
        if (!Files.exists(resultPath)) {
            finish(false, "server result file does not exist for case " + id);
            return;
        }
        try (Reader reader = Files.newBufferedReader(resultPath)) {
            JsonObject serverResult = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject cases = serverResult.has("cases")
                    ? serverResult.getAsJsonObject("cases") : new JsonObject();
            if (!cases.has(id)) {
                finish(false, "server case was not recorded: " + id);
                return;
            }
            JsonObject caseResult = cases.getAsJsonObject(id);
            boolean success = caseResult.has("success") && caseResult.get("success").getAsBoolean();
            assertCondition(success, "server case " + id + ": "
                    + (caseResult.has("message") ? caseResult.get("message").getAsString() : "failed"));
        } catch (Exception exception) {
            finish(false, "failed to read server case " + id + ": " + exception.getMessage());
        }
    }

    private static void waitForServerCase(JsonObject step) {
        String id = step.get("id").getAsString();
        int configuredTimeout = step.has("timeout_seconds") ? step.get("timeout_seconds").getAsInt() : 160;
        int timeoutSeconds = id.startsWith("server.llm_live.") ? Math.max(400, configuredTimeout) : configuredTimeout;
        long deadline = caseDeadlines.computeIfAbsent(id, key -> System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(
                timeoutSeconds));
        try {
            var server = editorServerResult();
            if (server.has("failures") && !server.getAsJsonArray("failures").isEmpty()) {
                finish(false, "Server assertion failed while waiting for " + id + ": " + server.get("failures")); return;
            }
            var cases = server.getAsJsonObject("cases");
            if (cases != null && cases.has(id)) { caseDeadlines.remove(id); assertServerCase(id); return; }
            if (System.nanoTime() > deadline) { finish(false, "Required server case timed out: " + id); return; }
            stepIndex--; waitTicks = 5;
        } catch (Exception error) { finish(false, "Cannot read live provider evidence: " + error); }
    }

    private static int letterReadIndex, letterReadStage;
    private static final StringBuilder letterReadText = new StringBuilder();

    private static String additionalUiType;
    private static int additionalUiPhase;
    private static String deliveryBoundaryType;
    private static int deliveryBoundaryPhase;
    private static long deliveryBoundaryDeadline;

    private static void deliveryBoundary(JsonObject step) {
        String type = step.get("type").getAsString();
        try {
            if (deliveryBoundaryType == null) { deliveryBoundaryType = type; deliveryBoundaryPhase = 0; }
            if (!deliveryBoundaryType.equals(type)) throw new IllegalStateException("Delivery boundary phases interleaved");
            var mc = Minecraft.getInstance();
            String prefix = "contactquests_test delivery_boundary " + type + " ";
            String npc = ContactQuestsDeliveryBoundaryFixtures.recipient(type);
            boolean advance = true;
            waitTicks = 10;
            switch (deliveryBoundaryPhase) {
                case 0 -> { if (mc.player.containerMenu != mc.player.inventoryMenu) mc.player.closeContainer(); mc.setScreen(null); }
                case 1 -> sendServerCommand(prefix + "prepare");
                case 2 -> advance = deliveryBoundaryReady(type, "prepare");
                case 3 -> deliveryClientStep(step, "arm");
                case 4 -> sendServerCommand("contactquests_test open_postbox");
                case 5 -> deliveryClientStep(step, "move");
                case 6 -> advance = deliveryBoundaryReady(type, "staged");
                case 7 -> deliveryClientStep(step, "staged");
                case 8 -> { setRecipient(npc); sendServerCommand("contactquests_test query_recipient " + npc); }
                case 9 -> { assertRecipient(npc); selectFirstRecipient(); }
                case 10 -> invokeScreenMethod("send");
                case 11 -> advance = deliveryBoundaryReady(type, "accepted");
                case 12 -> deliveryClientStep(step, "early");
                case 13 -> {
                    mc.player.closeContainer(); mc.setScreen(null);
                    if (type.equals("deleted_task")) sendServerCommand(prefix + "delete");
                }
                case 14 -> advance = deliveryBoundaryReady(type, "due");
                case 15 -> deliveryClientStep(step, "due");
                case 16 -> advance = deliveryBoundaryReady(type, "no_repeat");
                case 17 -> deliveryClientStep(step, "no_repeat");
                case 18 -> sendServerCommand(prefix + "verify");
                case 19 -> advance = deliveryBoundaryReady(type, "verify");
                case 20 -> sendServerCommand(prefix + "finish");
                case 21 -> advance = deliveryBoundaryReady(type, "finish");
                case 22 -> deliveryClientStep(step, "finish");
                default -> throw new IllegalStateException("Unknown delivery phase " + deliveryBoundaryPhase);
            }
            if (advance) { deliveryBoundaryPhase++; deliveryBoundaryDeadline = 0; }
            if (deliveryBoundaryPhase <= 22) stepIndex--;
            else { deliveryBoundaryType = null; deliveryBoundaryPhase = 0; }
        } catch (Exception error) {
            assertCase("client.delivery_boundary." + type + ".flow", false, error.toString());
        }
    }

    private static void deliveryClientStep(JsonObject step, String phase) {
        var current = step.deepCopy(); current.addProperty("phase", phase);
        ContactQuestsDeliveryBoundaryAssertions.run(current);
    }

    private static boolean deliveryBoundaryReady(String type, String phase) throws Exception {
        if (deliveryBoundaryDeadline == 0) deliveryBoundaryDeadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(40);
        var server = editorServerResult();
        if (server.has("failures") && !server.getAsJsonArray("failures").isEmpty())
            throw new IllegalStateException("Delivery server assertion failed: " + server.getAsJsonArray("failures"));
        var cases = server.getAsJsonObject("cases");
        String id = ContactQuestsDeliveryBoundaryFixtures.caseId(type, phase);
        if (cases != null && cases.has(id)) {
            var evidence = cases.getAsJsonObject(id);
            if (!evidence.get("success").getAsBoolean()) throw new IllegalStateException(id + ": " + evidence);
            return true;
        }
        if (System.nanoTime() >= deliveryBoundaryDeadline) throw new IllegalStateException("No server evidence for " + id);
        return false;
    }

    private static void additionalUi(JsonObject step) {
        String type = step.get("type").getAsString();
        if (additionalUiType == null) { additionalUiType = type; additionalUiPhase = 0; }
        if (!type.equals(additionalUiType)) throw new IllegalStateException("Additional UI phases were interleaved");
        var phases = ContactQuestsAdditionalUiAssertions.phases(type);
        String phase = phases.get(additionalUiPhase);
        var current = step.deepCopy(); current.addProperty("phase", phase);
        ContactQuestsAdditionalUiAssertions.run(current);
        waitTicks = ContactQuestsAdditionalUiAssertions.waitTicksAfter(type, phase);
        if (++additionalUiPhase < phases.size()) stepIndex--;
        else { additionalUiType = null; additionalUiPhase = 0; }
    }

    private static void readPostcardLetters(boolean rejoin) {
        String id = "client.postcard.letters." + (rejoin ? "reader_rejoin" : "reader");
        try {
            var mc = Minecraft.getInstance(); var server = editorServerResult();
            int count = server.get("postcardLetterCount").getAsInt();
            if (count < 2 || count > 27) throw new IllegalStateException("No actual long letter cards available: " + count);
            if (letterReadStage == 0) {
                mc.setScreen(null);
                sendServerCommand("contactquests_test postcard_letter " + letterReadIndex);
                letterReadStage = 1; stepIndex--; waitTicks = 20; return;
            }
            if (letterReadStage == 1) {
                mc.player.getInventory().selected = 0;
                var card = mc.player.getMainHandItem();
                if (!(card.getItem() instanceof PostcardItem) || !ContactQuestsPostcardLetterFixtures.NPC.equals(card.get(
                        com.flechazo.contact.common.component.ContactDataComponents.POSTCARD_SENDER.get())))
                    throw new IllegalStateException("Actual delivered letter was not synchronized to the player");
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                letterReadStage = 2; stepIndex--; waitTicks = 5; return;
            }
            if (!(mc.screen instanceof com.flechazo.contact.client.gui.screen.PostcardReadScreen)) throw new IllegalStateException("Real Contact letter reader did not open");
            ContactQuestsPostcardLayoutAssertions.verify(id + ".card_" + letterReadIndex);
            if (Boolean.getBoolean("contactquests.test.noKubejs") && !rejoin && letterReadIndex == 0)
                ContactQuestsPostcardLayoutAssertions.verify("client.postcard.layout.received");
            var card = (ItemStack) readField(mc.screen, "postcard");
            String page = (String) readField(readField(mc.screen, "textBox"), "page");
            if (!page.equals(PostcardItem.getText(card))) throw new IllegalStateException("Reader changed actual delivered text");
            letterReadText.append(page); mc.screen.onClose(); letterReadIndex++; letterReadStage = 0;
            if (letterReadIndex < count) { stepIndex--; waitTicks = 5; return; }
            assertCase(id, letterReadText.toString().equals(server.get("postcardLetterText").getAsString()),
                    "All " + count + " real reply cards opened through item use; native font/lines fit every card and reconstruct the exact entire NPC reply");
            letterReadIndex = 0; letterReadText.setLength(0);
        } catch (Exception error) {
            letterReadIndex = 0; letterReadStage = 0; letterReadText.setLength(0);
            assertCase(id, false, error.toString());
        }
    }

    private static void liveLlmUi(String phase) {
        try {
            var mc = Minecraft.getInstance(); var server = editorServerResult();
            String id = server.get("realLlmQuest").getAsString();
            if (phase.equals("graph")) {
                var quest = ClientQuestFile.INSTANCE.getQuest(Long.parseUnsignedLong(id, 16));
                assertCase("client.llm_live.graph", quest != null && quest.getRawTitle().equals("CQ Magpie quest")
                        && quest.getX() == 6 && quest.getY() == 7 && quest.getTasks().size() == 1 && quest.getRewards().size() == 1,
                        "Actual native client graph received the quest created by the real model tool");
                return;
            }
            if (phase.equals("open")) {
                mc.player.getInventory().selected = 0; mc.setScreen(null);
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                return;
            }
            if (!(mc.screen instanceof com.flechazo.contact.client.gui.screen.PostcardReadScreen)) throw new IllegalStateException("Native Contact reader is absent");
            ContactQuestsPostcardLayoutAssertions.verify("client.postcard.layout.live_llm");
            var card = (ItemStack) readField(mc.screen, "postcard");
            String page = String.valueOf(readField(readField(mc.screen, "textBox"), "page"));
            String text = PostcardItem.getText(card);
            assertCase("client.llm_live.reader", text.startsWith("CQ_LIVE_RECALLED:" + id + ":") && text.equals(page)
                    && ContactQuestsLiveLlmFixtures.NPC.equals(card.get(com.flechazo.contact.common.component.ContactDataComponents.POSTCARD_SENDER.get()))
                    && ResourceLocation.parse("contact:default").equals(card.get(com.flechazo.contact.common.component.ContactDataComponents.POSTCARD_STYLE_ID.get())),
                    "Real server-validated model response displays unchanged in the Windows native Contact reader");
        } catch (Exception error) { assertCase("client.llm_live." + phase, false, error.toString()); }
    }

    private static void retryServerResult(JsonObject step, String waitingMessage) {
        int timeout = step.has("timeout_ticks") ? step.get("timeout_ticks").getAsInt() : 600;
        if (serverResultWaitTicks++ > timeout) {
            finish(false, waitingMessage + " within " + timeout + " ticks");
            return;
        }
        stepIndex--;
        waitTicks = 5;
    }

    private static void sendServerCommand(String command) {
        if (Minecraft.getInstance().player == null) {
            finish(false, "cannot send server command without a client player");
            return;
        }
        Minecraft.getInstance().player.connection.sendCommand(command);
    }

    private static void moveMailToPostbox(String type) {
        Player player = Minecraft.getInstance().player;
        if (player == null || Minecraft.getInstance().gameMode == null) {
            finish(false, "cannot move mail without a client player/game mode");
            return;
        }
        AbstractContainerMenu menu = player.containerMenu;
        for (int slot = 1; slot < menu.slots.size(); slot++) {
            ItemStack stack = menu.getSlot(slot).getItem();
            if (matchesMailType(stack, type) && (featureMailText == null || !type.equals("postcard") || featureMailText.equals(
                    stack.get(com.flechazo.contact.common.component.ContactDataComponents.POSTCARD_TEXT.get())))) {
                movedMailType = type;
                seededMailDeadline = 0;
                Minecraft.getInstance().gameMode.handleInventoryMouseClick(
                        menu.containerId, slot, 0, ClickType.QUICK_MOVE, player);
                return;
            }
        }
        if (seededMailDeadline == 0) seededMailDeadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        if (System.nanoTime() < seededMailDeadline) {
            stepIndex--; waitTicks = 2;
            return;
        }
        var seen = new java.util.ArrayList<String>();
        for (int slot = 1; slot < menu.slots.size(); slot++) {
            ItemStack stack = menu.getSlot(slot).getItem();
            if (!stack.isEmpty()) seen.add(slot + ":" + stack.getItem() + ":" + stack.getCount() + ":"
                    + stack.get(com.flechazo.contact.common.component.ContactDataComponents.POSTCARD_TEXT.get()));
        }
        finish(false, "no seeded " + type + " item found after inventory synchronization; expected text="
                + featureMailText + ", menu=" + menu.getClass().getSimpleName() + ", slots=" + seen);
    }

    private static boolean matchesMailType(ItemStack stack, String type) {
        return switch (type) {
            case "parcel" -> stack.getItem() instanceof ParcelItem;
            case "red_packet" -> stack.getItem() instanceof RedPacketItem;
            case "postcard" -> stack.getItem() instanceof PostcardItem;
            default -> false;
        };
    }

    private static void setRecipient(String value) {
        if (!(Minecraft.getInstance().screen instanceof PostboxScreen screen)) {
            finish(false, "recipient can only be set on PostboxScreen");
            return;
        }
        try {
            if (!(screen.getMenu() instanceof PostboxScreenHandler handler)) {
                finish(false, "recipient can only be set on a postbox handler");
                return;
            }
            // The Contact slot listener normally sets this when the mail stack
            // arrives. Set it explicitly as a deterministic client-side guard
            // before invoking the screen's real autocomplete callback.
            if (handler.status == 0) {
                handler.status = 1;
            }
            EditBox nameField = (EditBox) readField(screen, "nameField");
            nameField.setValue(value);
            Method whileTyping = findMethod(screen.getClass(), "whileTyping", String.class);
            whileTyping.setAccessible(true);
            whileTyping.invoke(screen, value);
        } catch (Exception exception) {
            finish(false, "failed to set recipient: " + exception.getMessage());
        }
    }

    private static void assertRecipient(String expected) {
        if (!(Minecraft.getInstance().screen instanceof PostboxScreen screen)
                || !(screen.getMenu() instanceof PostboxScreenHandler handler)) {
            finish(false, "recipient assertion requires PostboxScreen");
            return;
        }
        boolean matches = !handler.names.isEmpty() && expected.equals(handler.names.get(0));
        result.put("recipientSuggestion", handler.names.isEmpty() ? "" : handler.names.get(0));
        assertCase("client.postbox.recipient_alias", matches, "recipient suggestion is " + expected);
    }

    private static void selectFirstRecipient() {
        if (!(Minecraft.getInstance().screen instanceof PostboxScreen screen)
                || !(screen.getMenu() instanceof PostboxScreenHandler handler)) {
            finish(false, "recipient selection requires PostboxScreen");
            return;
        }
        if (handler.names.isEmpty()) {
            finish(false, "recipient suggestion list is empty");
            return;
        }
        try {
            EditBox nameField = (EditBox) readField(screen, "nameField");
            nameField.setValue(handler.names.get(0));
        } catch (Exception exception) {
            finish(false, "failed to select recipient: " + exception.getMessage());
        }
    }

    private static void assertPostboxEmpty() {
        Screen screen = Minecraft.getInstance().screen;
        if (!(screen instanceof PostboxScreen postbox)
                || !(postbox.getMenu() instanceof PostboxScreenHandler handler)) {
            finish(false, "postbox assertion requires PostboxScreen");
            return;
        }
        boolean empty = handler.parcel.getItem(0).isEmpty();
        assertCondition(empty, "postbox mail slot is empty after send");
        if (empty && movedMailType != null) sentMailTypes.add(movedMailType);
        if (sentMailTypes.containsAll(java.util.List.of("parcel", "red_packet", "postcard"))) {
            assertCase("client.postbox.mail_shapes", true, "Each real parcel/red packet/postcard was moved through the postbox and consumed");
        }
    }

    private static void installSyntheticTasks() {
        if (syntheticTasksInstalled) {
            return;
        }
        try {
            ClientQuestFile file = ClientQuestFile.INSTANCE;
            Player player = Minecraft.getInstance().player;
            if (file == null || player == null) {
                finish(false, "client quest file/player is unavailable for synthetic tasks");
                return;
            }
            if (file.selfTeamData == null) {
                file.selfTeamData = new TeamData(player.getUUID(), file, "ContactQuestsTest");
            }

            Chapter chapter = new Chapter(
                    0x7f00000000000001L,
                    file,
                    file.getDefaultChapterGroup(),
                    "contactquests_test"
            );
            file.getDefaultChapterGroup().addChapter(chapter);

            Quest quest = new Quest(0x7f00000000000002L, chapter);
            quest.setRawTitle("ContactQuests deterministic autofill");
            chapter.addQuest(quest);

            parcelTask = new ParcelTask(0x7f00000000000011L, quest)
                    .setStackAndCount(new ItemStack(Items.DIAMOND), 2);
            parcelTask.setTargetAddressee(ContactQuestsTestMod.TEST_RECIPIENT);
            quest.addTask(parcelTask);

            redPacketTask = new RedPacketTask(0x7f00000000000012L, quest)
                    .setStackAndCount(new ItemStack(Items.GOLD_INGOT), 2);
            redPacketTask.setBlessing("fixed autofill blessing");
            redPacketTask.setTargetAddressee(ContactQuestsTestMod.TEST_RECIPIENT);
            quest.addTask(redPacketTask);

            postcardTask = new PostcardTask(0x7f00000000000013L, quest);
            postcardTask.setPostcardStyle("contact:default");
            postcardTask.setPostcardText("fixed autofill postcard");
            postcardTask.setTargetAddressee(ContactQuestsTestMod.TEST_RECIPIENT);
            quest.addTask(postcardTask);

            editorQuest = quest;
            ContactQuestsTestUtil.refreshIndex(file);
            file.clearCachedData();
            syntheticTasksInstalled = true;
            result.put("syntheticTasks", "installed");
        } catch (Exception exception) {
            finish(false, "failed to install synthetic tasks: " + exception.getMessage());
        }
    }

    private static void openFtbEditor() {
        if (!editorRequested) {
            editorRequested = true;
            sendServerCommand("contactquests_test prepare_editor");
            stepIndex--;
            waitTicks = 20;
            return;
        }
        try (Reader reader = Files.newBufferedReader(Path.of(System.getProperty("contactquests.test.serverResult")))) {
            JsonObject server = JsonParser.parseReader(reader).getAsJsonObject();
            if (!server.has("editorQuestId")) {
                if (server.has("cases") && server.getAsJsonObject("cases").has("server.editor.prepared")
                        && !server.getAsJsonObject("cases").getAsJsonObject("server.editor.prepared").get("success").getAsBoolean()) {
                    finish(false, "Server editor preparation failed: " + server.getAsJsonObject("cases")
                            .getAsJsonObject("server.editor.prepared").get("message").getAsString());
                    return;
                }
                if (++editorWaitTicks > 60) {
                    finish(false, "Server editor quest was not registered within the test timeout");
                    return;
                }
                stepIndex--;
                waitTicks = 10;
                return;
            }
            long id = Long.parseUnsignedLong(server.get("editorQuestId").getAsString(), 16);
            editorQuest = ClientQuestFile.INSTANCE.getQuest(id);
            if (editorQuest == null || ClientQuestFile.INSTANCE.selfTeamData == null) {
                stepIndex--;
                waitTicks = 10;
                return;
            }
            ClientQuestFile.INSTANCE.setEditorPermission(true);
            editorScreen = ClientQuestFile.openGui(editorQuest, true);
            result.put("ftbEditor", "opened server graph");
        } catch (Exception exception) {
            finish(false, "failed to open FTB editor: " + exception.getMessage());
        }
    }

    private static void assertClientTextResolution() {
        var player = Minecraft.getInstance().player;
        var data = ClientQuestFile.INSTANCE.selfTeamData;
        var ext = (com.creeperyang.contactquests.bridge.ITeamDataExtension) data;
        var cards = new java.util.HashMap<>(ext.contactQuests$getAllPostcardTexts());
        var reds = new java.util.HashMap<>(ext.contactQuests$getAllRedPacketBlessings());
        String cardText = postcardTask.getPostcardText(), redText = redPacketTask.getBlessing();
        try {
            var team = dev.ftb.mods.ftbteams.api.FTBTeamsAPI.api().getClientManager().getTeamByID(data.getTeamId()).orElseThrow();
            ext.contactQuests$setAllPostcardTexts(new java.util.HashMap<>());
            ext.contactQuests$setAllRedPacketBlessings(new java.util.HashMap<>());
            postcardTask.setPostcardText("<player_name>|<team_name>|<team_size>");
            redPacketTask.setBlessing("<player_name>|<team_name>|<team_size>");
            String expected = player.getName().getString() + "|" + data.getName() + "|" + team.getMembers().size();
            assertCase("client.text.team_size", postcardTask.getResolvedText(data, player).equals(expected)
                            && redPacketTask.getResolvedText(data, player).equals(expected),
                    "Actual client FTB team supplies player/team names and member count: " + expected);
            ext.contactQuests$setPostcardText(postcardTask.id, "客户端缓存明信片\n第二行");
            ext.contactQuests$setRedPacketBlessing(redPacketTask.id, "客户端缓存红包\n第二行");
            assertCase("client.text.cache_priority", postcardTask.getResolvedText(data, player).equals("客户端缓存明信片\n第二行")
                            && redPacketTask.getResolvedText(data, player).equals("客户端缓存红包\n第二行"),
                    "Client task text uses the synchronized per-task cache before the raw placeholder template");
        } catch (Exception error) { assertCase("client.text.resolution", false, error.toString()); }
        finally {
            postcardTask.setPostcardText(cardText); redPacketTask.setBlessing(redText);
            ext.contactQuests$setAllPostcardTexts(cards); ext.contactQuests$setAllRedPacketBlessings(reds);
        }
    }

    private static void createUiTask(String type, String itemName, int count) {
        if (editorScreen == null || editorQuest == null) {
            finish(false, "FTB editor is not open");
            return;
        }
        try {
            editorQuest = ClientQuestFile.INSTANCE.getQuest(editorQuest.getId());
            editorScreen = ClientQuestFile.openGui(editorQuest, true);
            new AddTaskButton(editorScreen.questPanel, editorQuest).onClicked(MouseButton.LEFT);
            String title = switch (type) {
                case "parcel" -> TaskRegistry.PARCEL.getDisplayName().getString();
                case "red_packet" -> TaskRegistry.RED_PACKET.getDisplayName().getString();
                case "postcard" -> TaskRegistry.POSTCARD.getDisplayName().getString();
                default -> throw new IllegalArgumentException("unknown UI task type: " + type);
            };
            clickContextMenuEntry(title);
            acceptItemSelector(itemForName(itemName, count));
            result.put("editorTaskSelector." + type, result.get("editorSelectorScreen"));
            editorUiTaskActions++;
        } catch (Exception exception) {
            finish(false, "failed to create UI task " + type + ": " + exception.getMessage());
        }
    }

    private static void createUiReward(String type, String itemName, int count) {
        if (editorScreen == null || editorQuest == null) {
            finish(false, "FTB editor is not open");
            return;
        }
        try {
            editorQuest = ClientQuestFile.INSTANCE.getQuest(editorQuest.getId());
            editorScreen = ClientQuestFile.openGui(editorQuest, true);
            new AddRewardButton(editorScreen.questPanel, editorQuest).onClicked(MouseButton.LEFT);
            String title = switch (type) {
                case "parcel" -> RewardRegistry.INSTANCE.getPARCEL().getDisplayName().getString();
                case "postcard" -> RewardRegistry.INSTANCE.getPOSTCARD().getDisplayName().getString();
                case "red_packet" -> RewardRegistry.INSTANCE.getRED_PACKET().getDisplayName().getString();
                case "parcel_random" -> RewardRegistry.INSTANCE.getPARCEL_RANDOM().getDisplayName().getString();
                case "parcel_all_table" -> RewardRegistry.INSTANCE.getPARCEL_ALL_TABLE().getDisplayName().getString();
                default -> throw new IllegalArgumentException("unknown UI reward type: " + type);
            };
            clickContextMenuEntry(title);
            if (type.equals("parcel_random") || type.equals("parcel_all_table")) acceptRewardTableSelector();
            else acceptItemSelector(itemForName(itemName, count));
            result.put("editorRewardSelector." + type, result.get("editorSelectorScreen"));
            editorUiRewardActions++;
        } catch (Exception exception) {
            finish(false, "failed to create UI reward " + type + ": " + exception.getMessage());
        }
    }

    private static void acceptRewardTableSelector() throws Exception {
        Object gui = ((dev.ftb.mods.ftblibrary.ui.IScreenWrapper) Minecraft.getInstance().screen).getGui();
        if (!(gui instanceof dev.ftb.mods.ftbquests.client.gui.SelectQuestObjectScreen<?> selector)) {
            throw new IllegalStateException("Real reward-table selector did not open: " + gui.getClass().getName());
        }
        result.put("editorSelectorScreen", gui.getClass().getName());
        var server = JsonParser.parseString(Files.readString(Path.of(System.getProperty("contactquests.test.serverResult")))).getAsJsonObject();
        long tableId = Long.parseUnsignedLong(server.get("editorRewardTableId").getAsString(), 16);
        Button choice = null;
        var titles = new java.util.ArrayList<String>();
        for (Widget widget : flatten(selector)) {
            if (!(widget instanceof Button button)) continue;
            titles.add(button.getTitle().getString());
            if (widget.getClass().getName().endsWith("$QuestObjectButton")) {
                Object object = readField(widget, "object");
                if (object instanceof dev.ftb.mods.ftbquests.quest.loot.RewardTable table && table.getId() == tableId) choice = button;
            }
        }
        result.put("rewardTableSelectorTitles", titles);
        if (choice == null) throw new IllegalStateException("Registered table " + Long.toUnsignedString(tableId, 16)
                + " absent from actual selector buttons: " + titles);
        // Select the actual FTB button; its handler sets the config and accepts.
        choice.onClicked(MouseButton.LEFT);
    }

    private static void clickEditorRewardButtons(boolean repeated) {
        try {
            editorQuest = ClientQuestFile.INSTANCE.getQuest(editorQuest.getId());
            editorScreen = ClientQuestFile.openGui(editorQuest, false);
            editorScreen.viewQuest(editorQuest);
            var buttons = flatten(editorScreen.viewQuestPanel).stream()
                    .filter(w -> w instanceof dev.ftb.mods.ftbquests.client.gui.quests.RewardButton)
                    .map(w -> (dev.ftb.mods.ftbquests.client.gui.quests.RewardButton) w).toList();
            if (buttons.size() != 5) throw new IllegalStateException("Actual quest panel has " + buttons.size() + " reward buttons");
            var states = new java.util.ArrayList<String>();
            for (var button : buttons) {
                var reward = (dev.ftb.mods.ftbquests.quest.reward.Reward) readField(button, "reward");
                var type = ClientQuestFile.INSTANCE.selfTeamData.getClaimType(Minecraft.getInstance().player.getUUID(), reward);
                states.add(reward.getType().getTypeForNBT() + ":" + type);
                if (type.canClaim() == repeated) throw new IllegalStateException("Unexpected real claim state: " + states);
                button.onClicked(MouseButton.LEFT);
            }
            result.put(repeated ? "repeatedRewardButtonStates" : "rewardButtonStates", states);
            assertCase(repeated ? "client.rewards.repeat_buttons" : "client.rewards.five_buttons", true,
                    "Actual five displayed reward buttons clicked; observed claim states=" + states);
        } catch (Exception error) {
            assertCase(repeated ? "client.rewards.repeat_buttons" : "client.rewards.five_buttons", false, error.toString());
        }
    }

    private static java.util.List<Widget> flatten(Panel panel) {
        var widgets = new java.util.ArrayList<Widget>();
        for (Widget widget : panel.getWidgets()) {
            widgets.add(widget);
            if (widget instanceof Panel child) widgets.addAll(flatten(child));
        }
        return widgets;
    }

    private static void clickContextMenuEntry(String title) throws Exception {
        if (editorScreen == null) {
            throw new IllegalStateException("FTB editor screen is unavailable");
        }
        var menuOptional = editorScreen.getContextMenu();
        if (menuOptional.isEmpty() || !(menuOptional.get() instanceof ContextMenu menu)) {
            throw new IllegalStateException("FTB context menu did not open");
        }
        for (Widget widget : menu.getWidgets()) {
            if (widget instanceof Button button && button.getTitle() != null
                    && button.getTitle().getString().equals(title)) {
                button.onClicked(MouseButton.LEFT);
                return;
            }
        }
        throw new IllegalStateException("FTB context menu item not found: " + title);
    }

    private static void acceptItemSelector(ItemStack stack) throws Exception {
        Screen screen = Minecraft.getInstance().screen;
        if (screen == null) {
            throw new IllegalStateException("item selector screen did not open");
        }
        Object selector = screen instanceof dev.ftb.mods.ftblibrary.ui.IScreenWrapper wrapper
                ? wrapper.getGui() : screen;
        result.put("editorSelectorScreen", selector.getClass().getName());
        Object resource = SelectableResource.item(stack);
        try {
            Method setSelected = findMethod(selector.getClass(), "setSelected", SelectableResource.class);
            setSelected.setAccessible(true);
            setSelected.invoke(selector, resource);
            Object config = readField(selector, "config");
            Object selectedValue = ((SelectableResource<?>) readField(selector, "selectedStack")).resource();
            result.put("editorSelectionBeforeAccept", Map.of("config", String.valueOf(
                    config.getClass().getMethod("getValue").invoke(config)), "selected", String.valueOf(selectedValue)));
            Method accept = findMethod(selector.getClass(), "doAccept");
            accept.setAccessible(true);
            accept.invoke(selector);
        } catch (NoSuchMethodException noSelectorMethod) {
            acceptConfigEditor(selector, stack);
        }
    }

    private static void acceptConfigEditor(Object editor, ItemStack stack) throws Exception {
        Object group = readField(editor, "group");
        applyConfigGroupValues(group, stack);
        Method accept = findMethod(editor.getClass(), "doAccept");
        accept.setAccessible(true);
        accept.invoke(editor);
    }

    private static void applyConfigGroupValues(Object group, ItemStack stack) throws Exception {
        Method getValues = findMethod(group.getClass(), "getValues");
        Object values = getValues.invoke(group);
        if (values instanceof Iterable<?> iterable) {
            for (Object value : iterable) {
                String id = String.valueOf(readField(value, "id")).toLowerCase();
                // Leave the optional author-chosen icon empty when creating an
                // object, so the test exercises its production default icon.
                if (id.equals("icon")) continue;
                Object current = value.getClass().getMethod("getValue").invoke(value);
                Object replacement = null;
                if (current instanceof ItemStack) {
                    replacement = stack.copy();
                } else if (current instanceof Integer && id.contains("count")) {
                    replacement = stack.getCount();
                } else if (current instanceof Long && id.equals("count")) {
                    replacement = (long) stack.getCount();
                } else if (current instanceof String) {
                    if (id.contains("target") || id.contains("addressee") || id.contains("sender")) {
                        replacement = ContactQuestsTestMod.TEST_RECIPIENT;
                    } else if (id.contains("blessing")) {
                        replacement = "fixed editor blessing";
                    } else if (id.contains("postcard_text") || id.endsWith("text")) {
                        replacement = "fixed editor postcard";
                    } else if (id.contains("style")) {
                        replacement = ContactQuestsEditorAssertions.STYLE;
                    }
                }
                if (replacement != null) {
                    Method setValue = findMethod(value.getClass(), "setValue", Object.class);
                    setValue.invoke(value, replacement);
                }
            }
        }
        Method getSubgroups = findMethod(group.getClass(), "getSubgroups");
        Object subgroups = getSubgroups.invoke(group);
        if (subgroups instanceof Iterable<?> iterable) {
            for (Object subgroup : iterable) {
                applyConfigGroupValues(subgroup, stack);
            }
        }
    }

    private static JsonObject editorServerResult() throws Exception {
        return JsonParser.parseString(Files.readString(Path.of(System.getProperty("contactquests.test.serverResult")))).getAsJsonObject();
    }

    private static dev.ftb.mods.ftblibrary.config.ui.EditConfigScreen openObjectEditor(String kind, String type) throws Exception {
        editorQuest = ClientQuestFile.INSTANCE.getQuest(editorQuest.getId());
        editorScreen = ClientQuestFile.openGui(editorQuest, true);
        editorScreen.viewQuest(editorQuest);
        Button selected = null;
        var widgets = kind.equals("quest") ? flatten(editorScreen.questPanel) : flatten(editorScreen.viewQuestPanel);
        for (Widget widget : widgets) {
            if (kind.equals("task") && widget instanceof dev.ftb.mods.ftbquests.client.gui.quests.TaskButton button) {
                var task = (dev.ftb.mods.ftbquests.quest.task.Task) readField(button, "task");
                if (task.getQuest() == editorQuest && task.getType().getTypeForNBT().equals("contactquests:" + type + "_task")) {
                    selected = button; break;
                }
            } else if (kind.equals("reward") && widget instanceof dev.ftb.mods.ftbquests.client.gui.quests.RewardButton button) {
                var reward = (dev.ftb.mods.ftbquests.quest.reward.Reward) readField(button, "reward");
                if (reward.getQuest() == editorQuest && reward.getType().getTypeForNBT().equals("contactquests:" + type)) {
                    selected = button; break;
                }
            } else if (kind.equals("quest") && widget instanceof dev.ftb.mods.ftbquests.client.gui.quests.QuestButton button
                    && readField(button, "quest") == editorQuest) {
                selected = button; break;
            }
        }
        if (selected == null) throw new IllegalStateException("Actual displayed " + kind + "/" + type + " button missing: "
                + widgets.stream().map(w -> w.getClass().getName()).toList());
        selected.onClicked(MouseButton.RIGHT);
        clickContextMenuEntry(Component.translatable("selectServer.edit").getString());
        var gui = Minecraft.getInstance().screen instanceof dev.ftb.mods.ftblibrary.ui.IScreenWrapper wrapper ? wrapper.getGui() : null;
        if (!(gui instanceof dev.ftb.mods.ftblibrary.config.ui.EditConfigScreen edit)) {
            throw new IllegalStateException("Native EditConfigScreen missing after right-click Edit: " + gui);
        }
        return edit;
    }

    private static void editUiObject(JsonObject step) {
        String kind = step.get("kind").getAsString();
        String type = step.has("type") ? step.get("type").getAsString() : "quest";
        boolean cancel = step.has("cancel") && step.get("cancel").getAsBoolean();
        try {
            var edit = openObjectEditor(kind, type);
            var group = (dev.ftb.mods.ftblibrary.config.ConfigGroup) readField(edit, "group");
            var observed = new java.util.LinkedHashSet<String>();
            applyExactConfigValues(group, step.getAsJsonObject("values"), observed);
            if (!observed.equals(step.getAsJsonObject("values").keySet())) {
                throw new IllegalStateException("Requested config fields absent: expected=" + step.getAsJsonObject("values").keySet() + ", observed=" + observed);
            }
            // Use the real lower panel button; Done saves via FTB's edit packet.
            var bottom = readField(edit, "bottomPanel");
            ((Button) readField(bottom, cancel ? "buttonCancel" : "buttonAccept")).onClicked(MouseButton.LEFT);
            assertCase("client.editor." + kind + "." + type + (cancel ? ".cancel" : ".save"), true,
                    "Actual right-click Edit and " + (cancel ? "Cancel" : "Done") + "; fields=" + observed);
            if (cancel && kind.equals("task") && type.equals("parcel")) {
                var task = editorQuest.getTasks().stream().filter(t -> t instanceof ParcelTask).map(t -> (ParcelTask) t).findFirst().orElseThrow();
                assertCase("client.editor.cancel_preserves_fields", task.getCount() == 2 && task.getItemStack().is(Items.DIAMOND)
                        && task.getTargetAddressee().equals("CQEditorNpc"), "Native Cancel preserved the actual client task fields");
                sendServerCommand("contactquests_test verify_editor_cancel " + task.getCodeString());
            }
        } catch (Exception error) {
            assertCase("client.editor." + kind + "." + type + (cancel ? ".cancel" : ".save"), false, error.toString());
        }
    }

    private static void clickBinderButton() {
        try {
            var gui = ClientQuestFile.openGui(null, false);
            int before = binderCount();
            result.put("binderBefore", before);
            var button = flatten(gui).stream().filter(w -> w instanceof com.creeperyang.contactquests.client.gui.GetBinderButton)
                    .map(w -> (Button) w).findFirst().orElseThrow();
            assertCase("client.binder.button_and_tooltip", button.getTitle().getString().equals(
                    Component.translatable("contactquests.sidebar.get_binder").getString()), "Real quest sidebar contains the named binding-card button");
            button.onClicked(MouseButton.LEFT);
        } catch (Exception error) {
            assertCase("client.binder.button_and_tooltip", false, error.toString());
        }
    }

    private static int binderCount() {
        return Minecraft.getInstance().player.getInventory().items.stream()
                .filter(stack -> stack.is(com.creeperyang.contactquests.registry.ModItems.INSTANCE.getTEAM_BINDING_CARD().get()))
                .mapToInt(ItemStack::getCount).sum();
    }

    private static void assertBinderButton() {
        assertCase("client.binder.received", binderCount() == ((Number) result.get("binderBefore")).intValue() + 1,
                "Actual sidebar request packet delivered exactly one binding card into the synchronized client inventory");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void testStyleSelector(String kind) {
        try {
            if (styleDrawProbe != null) {
                var probe = styleDrawProbe;
                if (probe.phase == 3) {
                    if (!(Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.ConfirmScreen)) {
                        throw new IllegalStateException("Changed native style editor must request discard confirmation");
                    }
                    var confirmation = Minecraft.getInstance().screen;
                    var yes = confirmation.children().stream().filter(w -> w instanceof net.minecraft.client.gui.components.Button b
                                    && b.getMessage().getString().equals(Component.translatable("gui.yes").getString()))
                            .map(w -> (net.minecraft.client.gui.components.Button) w).findFirst().orElseThrow();
                    double yesX = yes.getX() + yes.getWidth() / 2.0, yesY = yes.getY() + yes.getHeight() / 2.0;
                    if (!yes.active || !confirmation.mouseClicked(yesX, yesY, 0)) throw new IllegalStateException("Native discard confirmation did not consume Yes click");
                    confirmation.mouseReleased(yesX, yesY, 0);
                    String actualStyle = kind.equals("task")
                            ? editorQuest.getTasks().stream().filter(t -> t instanceof PostcardTask).map(t -> ((PostcardTask) t).getPostcardStyle()).findFirst().orElseThrow()
                            : editorQuest.getRewards().stream().filter(r -> r instanceof com.creeperyang.contactquests.quest.reward.PostcardReward)
                                .map(r -> ((com.creeperyang.contactquests.quest.reward.PostcardReward) r).getPostcardStyle()).findFirst().orElseThrow();
                    assertCase("client.postcard.style_selector." + kind + ".cancel_restored", currentGui() == editorScreen
                                    && actualStyle.equals(probe.original),
                            "Real unsaved-changes Yes discards selected style, preserves the underlying object and returns to the same quest editor");
                    styleDrawProbe = null;
                    return;
                }
                if (!probe.kind.equals(kind) || currentGui() != probe.selector) throw new IllegalStateException("Style drawing probe changed screen");
                if (probe.phase == 0) {
                    String title = Component.translatable("tooltip.postcard.contact.stripes").getString();
                    probe.button = flatten(probe.selector).stream().filter(w -> w instanceof Button b && b.getTitle().getString().equals(title))
                            .map(w -> (Button) w).findFirst().orElseThrow();
                    probe.icon = (com.creeperyang.contactquests.client.gui.PostcardStyleIcon) readField(probe.button, "icon");
                    observeStyleRow(probe);
                    probe.phase = 1; stepIndex--; waitTicks = 5;
                    return;
                }
                if (probe.phase == 1) {
                    var card = com.flechazo.contact.data.PostcardDataManager.getPostcard(ResourceLocation.parse(probe.choice));
                    int[] box = probe.box, actual = probe.actual;
                    if (box == null || actual == null) throw new IllegalStateException("The visible style row did not draw during real render frames");
                    double scale = Math.min((double) box[2] / Math.max(1, card.cardWidth()), (double) box[3] / Math.max(1, card.cardHeight()));
                    int width = Math.max(1, (int) (card.cardWidth() * scale)), height = Math.max(1, (int) (card.cardHeight() * scale));
                    assertCase("client.postcard.style_selector." + kind + ".draw", actual[0] == box[0] + (box[2] - width) / 2
                                    && actual[1] == box[1] + (box[3] - height) / 2 && actual[2] == width && actual[3] == height
                                    && Math.abs(probe.icon.aspectRatio() - (double) card.cardWidth() / Math.max(1, card.cardHeight())) < 0.000001
                                    && ((dev.ftb.mods.ftblibrary.icon.ImageIcon) readField(probe.icon, "image")).texture.equals(card.getCardTexture()),
                            "Real style-row render uses the Contact texture, centered native aspect ratio and fitted size; box="
                                    + java.util.Arrays.toString(box) + ", image=" + java.util.Arrays.toString(actual));
                    Field image = probe.icon.getClass().getDeclaredField("image"); image.setAccessible(true);
                    image.set(probe.icon, new dev.ftb.mods.ftblibrary.icon.ImageIcon(dev.ftb.mods.ftblibrary.icon.ImageIcon.MISSING_IMAGE));
                    probe.phase = 2; stepIndex--; waitTicks = 5;
                    return;
                }
                var card = com.flechazo.contact.data.PostcardDataManager.getPostcard(ResourceLocation.parse(probe.choice));
                assertCase("client.postcard.style_selector." + kind + ".texture_refresh",
                        ((dev.ftb.mods.ftblibrary.icon.ImageIcon) readField(probe.icon, "image")).texture.equals(card.getCardTexture()),
                        "A subsequent real frame replaces a stale cached image with the current Contact card texture");
                probe.button.setIcon(probe.icon);
                probe.button.onClicked(MouseButton.LEFT);
                assertCase("client.postcard.style_selector." + kind + ".selection", probe.value.getValue().equals(probe.choice)
                                && currentGui() == probe.edit, "Clicking the actual drawn card row selected stripes and returned to the editor");
                ((Button) readField(readField(probe.edit, "bottomPanel"), "buttonCancel")).onClicked(MouseButton.LEFT);
                probe.phase = 3; stepIndex--; waitTicks = 5;
                return;
            }
            var edit = openObjectEditor(kind, "postcard");
            var group = (dev.ftb.mods.ftblibrary.config.ConfigGroup) readField(edit, "group");
            var value = findConfig(group, "postcard_style");
            String original = value.getValue().toString();
            Button entry = flatten(edit).stream().filter(w -> w instanceof Button && w.getClass().getSimpleName().equals("ConfigEntryButton"))
                    .filter(w -> {
                        try { return readField(w, "configValue") == value; }
                        catch (Exception error) { throw new IllegalStateException(error); }
                    }).map(w -> (Button) w).findFirst().orElseThrow();
            clickStyleConfigEntry(edit, entry);
            var selector = currentStyleSelector();
            Method cancel = findMethod(selector.getClass(), "doCancel");
            cancel.setAccessible(true);
            cancel.invoke(selector);
            assertCase("client.postcard.style_selector." + kind + ".cancel", value.getValue().equals(original),
                    "Actual style list Cancel preserved " + original);
            if (currentGui() != edit) throw new IllegalStateException("Style Cancel did not return to the native editor");
            clickStyleConfigEntry(edit, entry);
            selector = currentStyleSelector();
            String choice = "contact:stripes";
            String title = Component.translatable("tooltip.postcard.contact.stripes").getString();
            var button = flatten(selector).stream().filter(w -> w instanceof Button b && b.getTitle().getString().equals(title))
                    .map(w -> (Button) w).findFirst().orElseThrow();
            assertCase("client.postcard.style_selector." + kind + ".card_face", readField(button, "icon") instanceof com.creeperyang.contactquests.client.gui.PostcardStyleIcon icon
                    && icon.getResourceLocation().equals(com.flechazo.contact.data.PostcardDataManager.getPostcard(ResourceLocation.parse(choice)).getCardTexture()),
                    "Actual selectable row uses Contact's stripes card face");
            var icon = (com.creeperyang.contactquests.client.gui.PostcardStyleIcon) readField(button, "icon");
            var probe = new StyleSelectorDrawProbe(kind, choice, original, edit, selector, value, button, icon);
            // FTB performs an initial scheduled refresh on the next frame; attach
            // the observer to the surviving row only after those frames settle.
            styleDrawProbe = probe; stepIndex--; waitTicks = 5;
        } catch (Exception error) {
            styleDrawProbe = null;
            assertCase("client.postcard.style_selector." + kind + ".failure", false, error.toString());
        }
    }

    private static final class StyleSelectorDrawProbe {
        final String kind, choice, original;
        final dev.ftb.mods.ftblibrary.config.ui.EditConfigScreen edit;
        final dev.ftb.mods.ftblibrary.ui.misc.AbstractButtonListScreen selector;
        final dev.ftb.mods.ftblibrary.config.ConfigValue<?> value;
        Button button;
        com.creeperyang.contactquests.client.gui.PostcardStyleIcon icon;
        int[] box, actual;
        int phase;
        StyleSelectorDrawProbe(String kind, String choice, String original, dev.ftb.mods.ftblibrary.config.ui.EditConfigScreen edit,
                               dev.ftb.mods.ftblibrary.ui.misc.AbstractButtonListScreen selector,
                               dev.ftb.mods.ftblibrary.config.ConfigValue<?> value, Button button,
                               com.creeperyang.contactquests.client.gui.PostcardStyleIcon icon) {
            this.kind = kind; this.choice = choice; this.original = original; this.edit = edit; this.selector = selector;
            this.value = value; this.button = button; this.icon = icon;
        }
    }

    private static void observeStyleRow(StyleSelectorDrawProbe probe) throws ReflectiveOperationException {
        var icon = probe.icon;
        Field image = icon.getClass().getDeclaredField("image"); image.setAccessible(true);
        image.set(icon, new dev.ftb.mods.ftblibrary.icon.ImageIcon(icon.getResourceLocation()) {
            @Override public void draw(net.minecraft.client.gui.GuiGraphics graphics, int x, int y, int w, int h) {
                probe.actual = new int[]{x, y, w, h}; super.draw(graphics, x, y, w, h);
            }
        });
        probe.button.setIcon(new dev.ftb.mods.ftblibrary.icon.Icon() {
            @Override public void draw(net.minecraft.client.gui.GuiGraphics graphics, int x, int y, int w, int h) {
                probe.box = new int[]{x, y, w, h}; icon.draw(graphics, x, y, w, h);
            }
            @Override public double aspectRatio() { return icon.aspectRatio(); }
        });
        probe.button.getParent().setScrollY(probe.button.getPosY());
    }

    private static void clickStyleConfigEntry(dev.ftb.mods.ftblibrary.config.ui.EditConfigScreen edit, Button entry) {
        // updateMouseOver updates widget hover flags. ConfigEntryButton also reads
        // the screen's stored mouse coordinates, normally set during a render.
        edit.updateGui(entry.getX() + 25, Math.max(25, entry.getY() + 3), 0);
        if (entry.getMouseY() < 20) {
            throw new IllegalStateException("Style config click has no valid screen mouse position: " + entry.getMouseY());
        }
        entry.onClicked(MouseButton.LEFT);
    }

    private static Object currentGui() {
        var screen = Minecraft.getInstance().screen;
        return screen instanceof dev.ftb.mods.ftblibrary.ui.IScreenWrapper wrapper ? wrapper.getGui() : screen;
    }

    private static dev.ftb.mods.ftblibrary.ui.misc.AbstractButtonListScreen currentStyleSelector() {
        Object gui = currentGui();
        if (gui instanceof dev.ftb.mods.ftblibrary.ui.misc.AbstractButtonListScreen selector) return selector;
        throw new IllegalStateException("Native Contact card style selector did not open: " + gui);
    }

    private static dev.ftb.mods.ftblibrary.config.ConfigValue<?> findConfig(dev.ftb.mods.ftblibrary.config.ConfigGroup group, String id) {
        for (var value : group.getValues()) if (value.id.equals(id)) return value;
        for (var subgroup : group.getSubgroups()) {
            var value = findConfig(subgroup, id);
            if (value != null) return value;
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void applyExactConfigValues(dev.ftb.mods.ftblibrary.config.ConfigGroup group, JsonObject values,
                                               java.util.Set<String> observed) throws Exception {
        var server = editorServerResult();
        for (var raw : group.getValues()) {
            if (!values.has(raw.id)) continue;
            Object current = raw.getValue();
            var requested = values.get(raw.id);
            Object replacement;
            if (current instanceof ItemStack) replacement = itemForName(requested.getAsString(), 1);
            else if (current instanceof Long) replacement = requested.getAsLong();
            else if (current instanceof Integer) replacement = requested.getAsInt();
            else if (current instanceof Double) replacement = requested.getAsDouble();
            else if (current instanceof Boolean) replacement = requested.getAsBoolean();
            else if (current instanceof Enum e) replacement = Enum.valueOf(e.getDeclaringClass(), requested.getAsString());
            else if (raw.id.equals("table")) replacement = ClientQuestFile.INSTANCE.getRewardTable(
                    Long.parseUnsignedLong(server.get("editorEditedTableId").getAsString(), 16));
            else if (current instanceof java.util.List) {
                var entries = new java.util.ArrayList<>();
                for (var value : requested.getAsJsonArray()) {
                    entries.add(raw.id.equals("mutex_refs") ? ClientQuestFile.INSTANCE.getQuest(
                            Long.parseUnsignedLong(server.get("editorMutexId").getAsString(), 16)) : value.getAsString());
                }
                replacement = entries;
            } else if (current instanceof String) replacement = requested.getAsString();
            else throw new IllegalStateException("Unhandled actual config type for " + raw.id + ": " + current);
            ((dev.ftb.mods.ftblibrary.config.ConfigValue) raw).setValue(replacement);
            observed.add(raw.id);
        }
        for (var subgroup : group.getSubgroups()) applyExactConfigValues(subgroup, values, observed);
    }

    private static void assertEditorFields() {
        try {
            var server = editorServerResult();
            editorQuest = ClientQuestFile.INSTANCE.getQuest(editorQuest.getId());
            assertCase("client.editor.task_fields_sync", ContactQuestsEditorAssertions.taskFields(editorQuest),
                    "Actual synchronized three task fields including Long count=3000000005");
            assertCase("client.editor.reward_fields_sync", ContactQuestsEditorAssertions.rewardFields(editorQuest,
                    Long.parseUnsignedLong(server.get("editorEditedTableId").getAsString(), 16)), "Actual synchronized five reward field sets");
            assertCase("client.editor.quest_rules_sync", ContactQuestsEditorAssertions.questRules(editorQuest,
                    Long.parseUnsignedLong(server.get("editorMutexId").getAsString(), 16), Minecraft.getInstance().level.registryAccess()),
                    "Actual native tag and mutex fields synchronized");
            editorScreen = ClientQuestFile.openGui(editorQuest, true); editorScreen.viewQuest(editorQuest);
            assertEditorPostcardIcons(true);
        } catch (Exception error) {
            assertCase("client.editor.fields_failure", false, error.toString());
        }
    }

    private static void openNoKubejsEditor() {
        try {
            var server = editorServerResult();
            if (!server.has("editorQuestId")) throw new IllegalStateException("No script-independent editor fixture exists");
            long id = Long.parseUnsignedLong(server.get("editorQuestId").getAsString(), 16);
            editorQuest = ClientQuestFile.INSTANCE.getQuest(id);
            if (editorQuest == null) throw new IllegalStateException("Native editor graph has not synchronized");
            ClientQuestFile.INSTANCE.setEditorPermission(true);
            editorScreen = ClientQuestFile.openGui(editorQuest, true);
            editorScreen.viewQuest(editorQuest);
        } catch (Exception error) {
            assertCase("client.no_kubejs.editor_open", false, error.toString());
        }
    }

    private static void openEditorRuleEffect() {
        ClientQuestFile.INSTANCE.setEditorPermission(false);
        editorQuest = ClientQuestFile.INSTANCE.getQuest(editorQuest.getId());
        editorScreen = ClientQuestFile.openGui(editorQuest, false);
        editorScreen.selectChapter(editorQuest.getChapter());
    }

    private static void assertEditorRuleEffect(String phase) {
        try {
            var file = ClientQuestFile.INSTANCE;
            var quest = file.getQuest(editorQuest.getId());
            var data = file.selfTeamData;
            var server = editorServerResult();
            var mutex = file.getQuest(Long.parseUnsignedLong(server.get("editorMutexId").getAsString(), 16));
            boolean expected = phase.equals("unlocked") || phase.equals("reset");
            boolean node = false;
            for (var widget : flatten(editorScreen)) {
                if (widget instanceof dev.ftb.mods.ftbquests.client.gui.quests.QuestButton button
                        && readField(widget, "quest") instanceof Quest displayed && displayed.getId() == quest.getId()) node |= button.shouldDraw();
            }
            boolean rules = ContactQuestsEditorAssertions.questRules(quest, mutex.getId(), Minecraft.getInstance().level.registryAccess());
            assertCase("client.editor.rules_effect." + phase, rules && data.canStartTasks(quest) == expected
                            && quest.isVisible(data) == expected && node == expected && data.isCompleted(mutex) == phase.equals("mutex"),
                    "Native saved rules in player GUI: phase=" + phase + ", canStart=" + data.canStartTasks(quest)
                            + ", visible=" + quest.isVisible(data) + ", actual quest node=" + node
                            + ", mutex completed=" + data.isCompleted(mutex));
            file.setEditorPermission(true);
        } catch (Exception error) {
            assertCase("client.editor.rules_effect." + phase, false, error.toString());
        }
    }

    private static void assertNoKubejsProgress(String phase) {
        try {
            var server = editorServerResult();
            var quest = ClientQuestFile.INSTANCE.getQuest(Long.parseUnsignedLong(server.get("noKubejsQuestId").getAsString(), 16));
            var data = ClientQuestFile.INSTANCE.selfTeamData;
            boolean claimed = !phase.equals("mail");
            boolean quantities = quest != null && quest.getTasks().size() == 3 && quest.getRewards().size() == 5
                    && java.util.List.of(2L, 2L, 1L).equals(quest.getTasks().stream().map(data::getProgress).toList());
            assertCase("client.no_kubejs.progress." + phase, quantities && data.isCompleted(quest)
                            && quest.getRewards().stream().allMatch(r -> data.isRewardClaimed(Minecraft.getInstance().player.getUUID(), r) == claimed),
                    "Real client synchronized progress [2,2,1], exactly five rewards, claimed=" + claimed);
            ClientQuestFile.INSTANCE.setEditorPermission(false);
            var gui = ClientQuestFile.openGui(quest, false); gui.viewQuest(quest);
        } catch (Exception error) {
            assertCase("client.no_kubejs.progress." + phase, false, error.toString());
        }
    }

    private static void openReceivedPostcard() {
        try {
            var player = Minecraft.getInstance().player;
            player.getInventory().selected = 0;
            if (!(player.getMainHandItem().getItem() instanceof PostcardItem)) {
                throw new IllegalStateException("Server-collected Contact postcard is not in the selected inventory slot");
            }
            Minecraft.getInstance().setScreen(null);
            Minecraft.getInstance().gameMode.useItem(player, InteractionHand.MAIN_HAND);
            assertCase("client.postcard.native_reader_open", Minecraft.getInstance().screen instanceof com.flechazo.contact.client.gui.screen.PostcardReadScreen,
                    "Contact item use opened the actual received postcard reader");
        } catch (Exception error) {
            assertCase("client.postcard.native_reader_open", false, error.toString());
        }
    }

    private static void assertReceivedPostcard() {
        try {
            var screen = Minecraft.getInstance().screen;
            if (!(screen instanceof com.flechazo.contact.client.gui.screen.PostcardReadScreen)) throw new IllegalStateException("Contact reader missing");
            ContactQuestsPostcardLayoutAssertions.verify("client.postcard.layout.received");
            var style = (com.flechazo.contact.data.PostcardStyle) readField(screen, "style");
            var card = (ItemStack) readField(screen, "postcard");
            var page = String.valueOf(readField(readField(screen, "textBox"), "page"));
            String expected = editorServerResult().get("editorPostcardText").getAsString();
            assertCase("client.postcard.received_style_and_text", style.getCardTexture().equals(
                    com.flechazo.contact.data.PostcardDataManager.getPostcard(ResourceLocation.parse(ContactQuestsEditorAssertions.STYLE)).getCardTexture())
                    && PostcardItem.getText(card).equals(expected) && page.equals(expected), "Real reader card texture=" + style.getCardTexture()
                    + "; displayed text=" + page);
        } catch (Exception error) {
            assertCase("client.postcard.received_style_and_text", false, error.toString());
        }
    }

    private static void assertEditorGraph() {
        if (editorQuest == null) {
            finish(false, "FTB editor quest is unavailable");
            return;
        }
        editorQuest = ClientQuestFile.INSTANCE.getQuest(editorQuest.getId());
        int parcelTasks = 0;
        int redPacketTasks = 0;
        int postcardTasks = 0;
        for (var task : editorQuest.getTasks()) {
            if (task instanceof ParcelTask) parcelTasks++;
            if (task instanceof RedPacketTask) redPacketTasks++;
            if (task instanceof PostcardTask) postcardTasks++;
        }
        int rewards = editorQuest.getRewards().size();
        long parcelRewards = editorQuest.getRewards().stream()
                .filter(reward -> reward instanceof com.creeperyang.contactquests.quest.reward.ParcelReward).count();
        long redPacketRewards = editorQuest.getRewards().stream()
                .filter(reward -> reward instanceof com.creeperyang.contactquests.quest.reward.RedPacketReward).count();
        long postcardRewards = editorQuest.getRewards().stream()
                .filter(reward -> reward instanceof com.creeperyang.contactquests.quest.reward.PostcardReward).count();
        long randomRewards = editorQuest.getRewards().stream()
                .filter(r -> r.getType() == RewardRegistry.INSTANCE.getPARCEL_RANDOM()).count();
        long allRewards = editorQuest.getRewards().stream()
                .filter(r -> r.getType() == RewardRegistry.INSTANCE.getPARCEL_ALL_TABLE()).count();
        boolean tableValues = editorQuest.getRewards().stream()
                .filter(r -> r instanceof com.creeperyang.contactquests.quest.reward.ParcelRandomReward)
                .allMatch(r -> ((com.creeperyang.contactquests.quest.reward.ParcelRandomReward) r).getTable() != null
                        && ((com.creeperyang.contactquests.quest.reward.ParcelRandomReward) r).getTable().getWeightedRewards().size() == 1
                        && ((com.creeperyang.contactquests.quest.reward.ParcelRandomReward) r).getTable().getWeightedRewards().getFirst()
                        .getReward() instanceof dev.ftb.mods.ftbquests.quest.reward.ItemReward item
                        && item.getItem().is(Items.DIAMOND) && item.getCount() == 4);
        boolean passed = parcelTasks == 2 && redPacketTasks == 2 && postcardTasks == 2
                && rewards == 5 && parcelRewards == 1 && redPacketRewards == 1 && postcardRewards == 1
                && randomRewards == 1 && allRewards == 1 && tableValues
                && editorUiTaskActions == 3 && editorUiRewardActions == 5;
        result.put("ftbEditorTaskCounts", parcelTasks + "/" + redPacketTasks + "/" + postcardTasks);
        result.put("ftbEditorRewardCount", rewards);
        result.put("ftbEditorRewardTypes", editorQuest.getRewards().stream()
                .map(reward -> reward.getType().getTypeForNBT().toString()).toList());
        result.put("ftbEditorTables", editorQuest.getRewards().stream()
                .filter(r -> r instanceof com.creeperyang.contactquests.quest.reward.ParcelRandomReward)
                .map(r -> {
                    var table = ((com.creeperyang.contactquests.quest.reward.ParcelRandomReward) r).getTable();
                    return table == null ? "null" : table.getCodeString() + ":" + table.getRawTitle()
                            + ":weights=" + table.getWeightedRewards().size();
                }).toList());
        result.put("ftbEditorGraphPassed", passed);
        result.put("ftbEditorUiActions", editorUiTaskActions + "/" + editorUiRewardActions);
        assertCase("client.ftb.quest_editor.full_graph", passed,
                "FTB editor task objects=" + parcelTasks + "/" + redPacketTasks + "/" + postcardTasks + "; rewards=" + rewards);
        assertEditorPostcardIcons(false);
    }

    private static void assertEditorPostcardIcons(boolean edited) {
        String id = edited ? "client.postcard.editor_card_icons" : "client.postcard.editor_created_icons";
        try {
            editorScreen = ClientQuestFile.openGui(editorQuest, true);
            editorScreen.viewQuest(editorQuest);
            var observed = new java.util.ArrayList<String>();
            int tasks = 0, rewards = 0;
            boolean valid = resolveLazyIcon(TaskRegistry.POSTCARD.getIconSupplier()) instanceof dev.ftb.mods.ftblibrary.icon.ItemIcon taskType
                    && taskType.getStack().getItem() instanceof PostcardItem
                    && resolveLazyIcon(RewardRegistry.INSTANCE.getPOSTCARD().getIconSupplier()) instanceof dev.ftb.mods.ftblibrary.icon.ItemIcon rewardType
                    && rewardType.getStack().getItem() instanceof PostcardItem;
            for (Widget widget : flatten(editorScreen.viewQuestPanel)) {
                dev.ftb.mods.ftbquests.quest.QuestObjectBase object;
                String style;
                boolean ender;
                if (widget instanceof dev.ftb.mods.ftbquests.client.gui.quests.TaskButton button
                        && readField(button, "task") instanceof PostcardTask task) {
                    object = task; style = task.getPostcardStyle(); ender = false; tasks++;
                } else if (widget instanceof dev.ftb.mods.ftbquests.client.gui.quests.RewardButton button
                        && readField(button, "reward") instanceof com.creeperyang.contactquests.quest.reward.PostcardReward reward) {
                    object = reward; style = reward.getPostcardStyle(); ender = reward.isEnder(); rewards++;
                } else continue;
                valid &= ((ItemStack) readField(object, "rawIcon")).isEmpty();
                if (!(object.getIcon() instanceof dev.ftb.mods.ftblibrary.icon.ItemIcon icon)) {
                    throw new IllegalStateException("Actual FTB button resolved a non-item postcard icon: " + object.getIcon());
                }
                var expected = PostcardItem.getPostcard(com.creeperyang.contactquests.utils.PostcardUtils.INSTANCE.resolveStyleId(style), ender);
                var stack = icon.getStack();
                var model = Minecraft.getInstance().getItemRenderer().getModel(stack,
                        Minecraft.getInstance().level, Minecraft.getInstance().player, 0);
                var texture = model.getParticleIcon().contents().name();
                valid &= ItemStack.isSameItemSameComponents(stack, expected)
                        && model != Minecraft.getInstance().getModelManager().getMissingModel()
                        && texture.equals(ResourceLocation.parse(ender ? "contact:item/ender_postcard" : "contact:item/postcard"));
                observed.add(object.getCodeString() + ":" + texture + ":style=" + style);
            }
            assertCase(id, valid && tasks == 2 && rewards == 1,
                    "Displayed FTB buttons use two consistent Contact postcard task item models and the matching normal/ender reward model;"
                            + " no test-written custom icon; observed=" + observed);
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(Minecraft.getInstance().getWindow().getWindow(), 20, 20);
        } catch (Exception error) {
            assertCase(id, false, error.toString());
        }
    }

    private static dev.ftb.mods.ftblibrary.icon.Icon resolveLazyIcon(dev.ftb.mods.ftblibrary.icon.Icon icon) {
        while (icon instanceof dev.ftb.mods.ftblibrary.icon.LazyIcon lazy) icon = lazy.getIcon();
        return icon;
    }

    private static void openScriptQuest() {
        try (Reader reader = Files.newBufferedReader(Path.of(System.getProperty("contactquests.test.serverResult")))) {
            JsonObject server = JsonParser.parseReader(reader).getAsJsonObject();
            visibleTextQuest = ClientQuestFile.INSTANCE.getQuest(Long.parseUnsignedLong(server.get("runtimeQuestId").getAsString(), 16));
            var gui = ClientQuestFile.openGui(visibleTextQuest, false);
            gui.viewQuest(visibleTextQuest);
        } catch (Exception error) {
            assertCase("client.quest_text.rendered", false, "Opening real quest panel: " + error);
        }
    }

    private static void assertVisibleQuestText() {
        var screen = Minecraft.getInstance().screen;
        Object gui = screen instanceof dev.ftb.mods.ftblibrary.ui.IScreenWrapper wrapper ? wrapper.getGui() : screen;
        if (!(gui instanceof QuestScreen questScreen)) {
            assertCase("client.quest_text.rendered", false, "Actual quest screen not open");
            return;
        }
        var texts = flatten(questScreen.viewQuestPanel).stream().filter(w -> w instanceof TextField)
                .map(w -> renderedText((TextField) w)).toList();
        result.put("visibleQuestText", texts);
        result.put("visibleQuestLocale", ClientQuestFile.INSTANCE.getLocale());
        assertCase("client.quest_text.rendered", texts.contains("valid graph title") && texts.contains("valid graph subtitle")
                && texts.stream().anyMatch(t -> t.contains("valid graph description")),
                "Actual player-visible TextField values in " + ClientQuestFile.INSTANCE.getLocale() + ": " + texts);
    }

    private static void assertLiveText(JsonObject step) {
        String phase = step.get("phase").getAsString();
        var screen = Minecraft.getInstance().screen;
        Object gui = screen instanceof dev.ftb.mods.ftblibrary.ui.IScreenWrapper wrapper ? wrapper.getGui() : screen;
        if (!(gui instanceof QuestScreen questScreen)) {
            assertCase("client.quest_text.live." + phase, false, "Open quest panel was lost during text synchronization");
            return;
        }
        var texts = flatten(questScreen.viewQuestPanel).stream().filter(w -> w instanceof TextField)
                .map(w -> renderedText((TextField) w)).toList();
        String prefix = switch (phase) {
            case "saved" -> "脚本存档覆盖";
            case "global_after_saved", "reset" -> "脚本再次全局";
            default -> "脚本实时全局";
        };
        String second = switch (phase) {
            case "saved" -> "覆盖第二行";
            case "global_after_saved", "reset" -> "再次第二行";
            default -> "全局第二行";
        };
        result.put("liveText." + phase, texts);
        assertCase("client.quest_text.live." + phase, texts.contains(prefix + "标题") && texts.contains(prefix + "副标题")
                        && texts.stream().anyMatch(t -> t.contains(prefix + "描述"))
                        && texts.stream().anyMatch(t -> t.contains(second)),
                "Panel stayed open; rendered title/subtitle/two description lines after " + phase + ": " + texts);
    }

    private static String renderedText(TextField field) {
        try {
            // FTB's component field is unused here. draw() renders formattedText
            // built from rawText by setText(), so observe those exact glyph lines.
            var lines = (net.minecraft.network.chat.FormattedText[]) readField(field, "formattedText");
            return java.util.Arrays.stream(lines).map(net.minecraft.network.chat.FormattedText::getString)
                    .collect(java.util.stream.Collectors.joining("\n"));
        } catch (Exception error) {
            throw new IllegalStateException("Cannot observe FTB rendered text", error);
        }
    }

    private static void screenshot(String name) {
        try {
            if (!name.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("Invalid screenshot name");
            Path path = Path.of(System.getProperty("contactquests.test.clientResult")).getParent().resolve("screenshots/" + name + ".png");
            Files.createDirectories(path.getParent());
            try (var image = net.minecraft.client.Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
                image.writeToFile(path);
            }
            result.put("screenshot." + name, path.toString());
        } catch (Exception error) {
            finish(false, "Screenshot capture failed: " + error);
        }
    }

    private static void assertRejoinClient() {
        try {
            var saved = JsonParser.parseString(Files.readString(ContactQuestsRejoinFixtures.snapshotPath())).getAsJsonObject();
            var level = Minecraft.getInstance().level;
            var teamId = java.util.UUID.fromString(saved.get("team").getAsString());
            var personalId = java.util.UUID.fromString(saved.get("personal").getAsString());
            boolean markers = teamId.equals(((com.creeperyang.contactquests.bridge.IMailboxTeamAccessor) level.getBlockEntity(
                    ContactQuestsRejoinFixtures.pos(saved, "teamPos"))).contactQuests$getTeamId())
                    && personalId.equals(((com.creeperyang.contactquests.bridge.IMailboxTeamAccessor) level.getBlockEntity(
                    ContactQuestsRejoinFixtures.pos(saved, "personalPos"))).contactQuests$getTeamId());
            for (String key : java.util.List.of("teamRemoved", "personalRemoved")) {
                markers &= ((com.creeperyang.contactquests.bridge.IMailboxTeamAccessor) level.getBlockEntity(
                        ContactQuestsRejoinFixtures.pos(saved, key))).contactQuests$getTeamId() == null;
            }
            assertCase("client.persistence.markers", markers, "Client received party and solo mailbox markers and no broken replacement marker after new process login");
            var file = ClientQuestFile.INSTANCE;
            var quest = file.getQuest(Long.parseUnsignedLong(saved.get("quest").getAsString(), 16));
            var task = (ParcelTask) file.getTask(Long.parseUnsignedLong(saved.get("task").getAsString(), 16));
            assertCase("client.persistence.graph_sync", quest != null && task.getCount() == 10 && file.selfTeamData.getProgress(task) == 3
                    && ((com.creeperyang.contactquests.bridge.ITeamDataExtension) file.selfTeamData).contactQuests$hasTag("CQPersistTag")
                    && file.selfTeamData.canStartTasks(quest), "Actual client task count=10, restored progress=3, team tag and unlocked rules synchronized");
            var editor = file.getQuest(Long.parseUnsignedLong(saved.get("editorQuest").getAsString(), 16));
            assertCase("client.persistence.native_editor_fields", ContactQuestsEditorAssertions.taskFields(editor)
                    && ContactQuestsEditorAssertions.rewardFields(editor, Long.parseUnsignedLong(saved.get("editorTable").getAsString(), 16))
                    && ContactQuestsEditorAssertions.questRules(editor, Long.parseUnsignedLong(saved.get("editorMutex").getAsString(), 16), level.registryAccess()),
                    "Player received all native editor fields unchanged after fresh-process login");
            var gui = ClientQuestFile.openGui(quest, false); gui.viewQuest(quest);
            // Query the actual text widgets, not only the SavedData or override map.
            var texts = flatten(gui.viewQuestPanel).stream().filter(w -> w instanceof TextField)
                    .map(w -> renderedText((TextField) w)).toList();
            result.put("rejoinVisibleText", texts);
            assertCase("client.persistence.visible_text", texts.contains("重进后任务标题") && texts.contains("重进后任务副标题")
                    && texts.stream().anyMatch(t -> t.contains("重进后任务描述")), "Player-visible reopened quest text: " + texts);
        } catch (Exception error) {
            assertCase("client.persistence.failure", false, error.toString());
        }
    }

    private static void openTaskDisplay(String type) {
        try {
            var task = switch (type) {
                case "parcel" -> parcelTask;
                case "red_packet" -> redPacketTask;
                case "postcard" -> postcardTask;
                default -> throw new IllegalArgumentException("Unknown valid-item screen: " + type);
            };
            var file = ClientQuestFile.INSTANCE;
            file.setEditorPermission(false);
            var gui = ClientQuestFile.openGui(task.getQuest(), false);
            gui.viewQuest(task.getQuest());
            var button = flatten(gui.viewQuestPanel).stream()
                    .filter(w -> w instanceof dev.ftb.mods.ftbquests.client.gui.quests.TaskButton)
                    .filter(w -> {
                        try { return ((dev.ftb.mods.ftbquests.quest.task.Task) readField(w, "task")).id == task.id; }
                        catch (Exception error) { throw new IllegalStateException(error); }
                    }).map(w -> (Button) w).findFirst().orElseThrow();
            assertCase("client.task." + type + ".ordinary_entry", !file.canEdit()
                            && file.selfTeamData.canStartTasks(task.getQuest()) && !file.selfTeamData.isCompleted(task),
                    "Ordinary non-editor quest detail contains the actual right-panel TaskButton; task is startable and unfinished");
            if (!finished) button.onClicked(MouseButton.LEFT);
        } catch (Exception error) {
            assertCase("client.task." + type + ".ordinary_entry", false, error.toString());
        }
    }

    private static void clickTaskSubmit(String type) {
        Screen screen = Minecraft.getInstance().screen;
        Object gui = screen instanceof dev.ftb.mods.ftblibrary.ui.IScreenWrapper wrapper ? wrapper.getGui() : screen;
        if (!(gui instanceof com.creeperyang.contactquests.client.gui.BaseValidTaskScreen<?> valid)) {
            assertCase("client.task." + type + ".display_and_submit", false, "Real valid-task screen did not open");
            return;
        }
        boolean ready = valid.findValidSlot(Minecraft.getInstance().player) >= 0
                && valid.getSubmitButton().getWidgetType() != dev.ftb.mods.ftblibrary.ui.WidgetType.DISABLED;
        assertCase("client.task." + type + ".display_and_submit", ready && !ClientQuestFile.INSTANCE.canEdit()
                        && valid.getSubmitButton().getPosX() > valid.getBackButton().getPosX(),
                "Real " + gui.getClass().getSimpleName() + " found matching stationery; Submit button enabled");
        if (!finished) valid.getSubmitButton().onClicked(MouseButton.LEFT);
    }

    private static void testConfigGui() {
        var mc = Minecraft.getInstance();
        var config = com.creeperyang.contactquests.config.ContactConfig.INSTANCE;
        int oldSpeed = config.getAutoFillSpeed().get(), oldRetry = config.getRetryInterval().get();
        boolean oldDelivery = config.getEnableDeliveryTime().get();
        Screen parent = mc.screen;
        try {
            var screen = new com.creeperyang.contactquests.client.gui.ContactConfigScreen(parent == null
                    ? new net.minecraft.client.gui.screens.TitleScreen() : parent);
            mc.setScreen(screen);
            ((EditBox) readField(screen, "autoFillSpeedBox")).setValue("99");
            ((EditBox) readField(screen, "retryIntervalBox")).setValue("99");
            clickVanillaButton(screen, net.minecraft.network.chat.CommonComponents.GUI_CANCEL.getString());
            boolean cancelled = config.getAutoFillSpeed().get() == oldSpeed && config.getRetryInterval().get() == oldRetry;
            screen = new com.creeperyang.contactquests.client.gui.ContactConfigScreen(new net.minecraft.client.gui.screens.TitleScreen());
            mc.setScreen(screen);
            ((EditBox) readField(screen, "autoFillSpeedBox")).setValue("999");
            ((EditBox) readField(screen, "retryIntervalBox")).setValue("0");
            clickVanillaButton(screen, net.minecraft.network.chat.CommonComponents.GUI_DONE.getString());
            boolean clamped = config.getAutoFillSpeed().get() == 100 && config.getRetryInterval().get() == 1;
            config.getAutoFillSpeed().set(oldSpeed); config.getRetryInterval().set(oldRetry);
            config.getEnableDeliveryTime().set(oldDelivery); config.getSPEC().save();
            mc.setScreen(parent);
            assertCase("client.config.save_cancel_bounds", cancelled && clamped,
                    "Real Cancel preserved settings; Done clamped speed=100/retry=1; restored original settings");
        } catch (Exception error) {
            config.getAutoFillSpeed().set(oldSpeed); config.getRetryInterval().set(oldRetry);
            config.getEnableDeliveryTime().set(oldDelivery);
            assertCase("client.config.save_cancel_bounds", false, error.toString());
        }
    }

    private static void clickVanillaButton(Screen screen, String title) {
        var button = screen.children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.Button b
                && b.getMessage().getString().equals(title)).map(c -> (net.minecraft.client.gui.components.Button) c)
                .findFirst().orElseThrow(() -> new IllegalStateException("Visible button not found: " + title));
        button.onPress();
    }

    private static void testTagSelection() {
        try {
            var tags = com.creeperyang.contactquests.config.TagConfig.Companion.getSessionTags();
            if (tagSelectionStage == 0) {
                tagSelectionConfig = new dev.ftb.mods.ftblibrary.config.StringConfig();
                tags.add("CQExistingUiTag");
                tags.add("CQExcludedUiTag");
                tagSelectionScreen = newTagSelectionScreen(java.util.List.of("CQExcludedUiTag"));
                tagSelectionScreen.openGui();
                var widgets = flatten(tagSelectionScreen);
                tagSelectionExcluded = widgets.stream().noneMatch(w -> w instanceof Button b
                        && b.getTitle().getString().equals("CQExcludedUiTag"));
                widgets.stream().filter(w -> w instanceof Button b && b.getTitle().getString().equals("CQExistingUiTag"))
                        .map(w -> (Button) w).findFirst().orElseThrow().onClicked(MouseButton.LEFT);
                tagSelectionScreen = newTagSelectionScreen(java.util.List.of());
                tagSelectionScreen.openGui();
                flatten(tagSelectionScreen).stream().filter(w -> w instanceof Button b && b.getTitle().getString()
                        .equals(Component.translatable("contactquests.gui.create_tag").getString()))
                        .map(w -> (Button) w).findFirst().orElseThrow().onClicked(MouseButton.LEFT);
                // FTB refreshWidgets schedules rebuilding for the next frame.
                // Observe the real creation row only after that frame has run.
                tagSelectionStage = 1;
                stepIndex--;
                waitTicks = 5;
                return;
            }
            result.put("tagSelectorWidgets", flatten(tagSelectionScreen).stream()
                    .map(w -> w.getClass().getName() + ":" + w.getTitle().getString()).toList());
            var creation = flatten(tagSelectionScreen).stream().filter(w -> w instanceof Panel p
                    && p.getTitle().getString().equals("creation_row")).map(w -> (Panel) w).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Creation row was not rendered after refresh"));
            var text = flatten(creation).stream().filter(w -> w instanceof dev.ftb.mods.ftblibrary.ui.TextBox)
                    .map(w -> (dev.ftb.mods.ftblibrary.ui.TextBox) w).findFirst().orElseThrow();
            text.setText("  CQCreatedUiTag  "); text.onTextChanged();
            flatten(creation).stream().filter(w -> w instanceof Button b && b.getTitle().getString()
                    .equals(Component.translatable("gui.accept").getString()))
                    .map(w -> (Button) w).findFirst().orElseThrow().onClicked(MouseButton.LEFT);
            tagSelectionScreen = newTagSelectionScreen(java.util.List.of());
            tagSelectionScreen.openGui();
            Method cancel = findMethod(tagSelectionScreen.getClass(), "doCancel");
            cancel.setAccessible(true); cancel.invoke(tagSelectionScreen);
            Minecraft.getInstance().setScreen(null);
            result.put("tagSelectorValues", tagSelectionValues);
            result.put("tagSelectorCallbacks", tagSelectionCallbacks);
            result.put("tagSelectorSession", java.util.List.copyOf(tags));
            assertCase("client.tags.create_select_cancel", tagSelectionExcluded
                    && tagSelectionValues.equals(java.util.List.of("CQExistingUiTag", "CQCreatedUiTag"))
                    && tagSelectionCallbacks.equals(java.util.List.of(true, true, false)) && tags.contains("CQCreatedUiTag"),
                    "Real selector values=" + tagSelectionValues + ", callbacks=" + tagSelectionCallbacks
                            + ", excluded=" + tagSelectionExcluded);
        } catch (Exception error) {
            assertCase("client.tags.create_select_cancel", false, error.toString());
        }
    }

    private static com.creeperyang.contactquests.client.gui.TagSelectionScreen newTagSelectionScreen(java.util.List<String> excluded) {
        return new com.creeperyang.contactquests.client.gui.TagSelectionScreen(tagSelectionConfig,
                value -> { tagSelectionCallbacks.add(value); if (value) tagSelectionValues.add(tagSelectionConfig.getValue()); }, excluded);
    }

    private static void assertScriptSync() {
        try (Reader reader = Files.newBufferedReader(Path.of(System.getProperty("contactquests.test.serverResult")))) {
            JsonObject server = JsonParser.parseReader(reader).getAsJsonObject();
            long id = Long.parseUnsignedLong(server.get("runtimeQuestId").getAsString(), 16);
            Quest quest = ClientQuestFile.INSTANCE.getQuest(id);
            boolean text = quest != null
                    && "valid graph title".equals(ContactQuestsScriptAssertions.overrideMap(quest, "contactQuests$titleOverrides").get("zh_cn"))
                    && "valid graph subtitle".equals(ContactQuestsScriptAssertions.overrideMap(quest, "contactQuests$subtitleOverrides").get("zh_cn"))
                    && java.util.List.of("valid graph description").equals(
                    ContactQuestsScriptAssertions.overrideMap(quest, "contactQuests$descOverrides").get("zh_cn"));
            boolean tasks = quest != null && quest.getTasks().stream().anyMatch(t -> t instanceof ParcelTask p
                    && p.getItemStack().is(Items.EMERALD) && p.getCount() == 3)
                    && quest.getTasks().stream().anyMatch(t -> t instanceof RedPacketTask r
                    && r.getBlessing().equals("saved graph blessing"));
            assertCase("kubejs.override_sync.client", text && tasks,
                    "Client received script task fields and all three saved text overrides");
        } catch (Exception error) {
            assertCase("kubejs.override_sync.client", false, "Script synchronization: " + error);
        }
    }

    private static ItemStack itemForName(String name, int count) {
        ItemStack stack = itemForName(name);
        stack.setCount(count);
        return stack;
    }

    private static void scheduleAutofill(String type) {
        installSyntheticTasks();
        if (finished) {
            return;
        }
        var player = Minecraft.getInstance().player;
        var file = ClientQuestFile.INSTANCE;
        result.put("autofillDiagnostic", Map.of(
                "teamLocked", file.selfTeamData.isLocked(),
                "canStart", file.selfTeamData.canStartTasks(parcelTask.getQuest()),
                "parcelProgress", file.selfTeamData.getProgress(parcelTask),
                "parcelCompleted", file.selfTeamData.isCompleted(parcelTask),
                "diamondMatches", parcelTask.test(new ItemStack(Items.DIAMOND)),
                "containerId", player.containerMenu.containerId
        ));
        switch (type) {
            case "parcel" -> ParcelAutoFiller.INSTANCE.schedule(ContactQuestsTestMod.TEST_RECIPIENT);
            case "red_packet" -> RedPacketAutoFiller.INSTANCE.schedule(redPacketTask);
            case "postcard" -> PostcardAutoFiller.INSTANCE.schedule(postcardTask);
            default -> finish(false, "unknown autofill type: " + type);
        }
    }

    private static void assertAutofillContainer(JsonObject step) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            finish(false, "autofill assertion requires a client player");
            return;
        }
        AbstractContainerMenu menu = player.containerMenu;
        String expectedItem = step.get("item").getAsString();
        int expectedCount = step.get("count").getAsInt();
        JsonArray slotSnapshot = new JsonArray();
        for (int slot = 0; slot < menu.slots.size(); slot++) {
            ItemStack stack = menu.getSlot(slot).getItem();
            if (!stack.isEmpty()) {
                JsonObject slotData = new JsonObject();
                slotData.addProperty("slot", slot);
                slotData.addProperty("item", stack.getItem().toString());
                slotData.addProperty("count", stack.getCount());
                slotSnapshot.add(slotData);
            }
        }
        result.put("autofillSlots", slotSnapshot);
        result.put("autofillMenuClass", menu.getClass().getName());
        result.put("autofillScreenClass", Minecraft.getInstance().screen == null
                ? "none" : Minecraft.getInstance().screen.getClass().getName());
        result.put("autofillCarried", player.containerMenu.getCarried().isEmpty()
                ? "empty"
                : player.containerMenu.getCarried().getItem().toString() + " x" + player.containerMenu.getCarried().getCount());
        ItemStack input = menu.getSlot(0).getItem();
        String actualInput = input.isEmpty()
                ? "empty"
                : input.getItem().toString() + " x" + input.getCount();
        result.put("autofillInput", actualInput);
        boolean inputMatches = input.is(itemForName(expectedItem).getItem()) && input.getCount() == expectedCount;
        assertCondition(inputMatches, "autofill input is " + expectedItem + " x" + expectedCount + ", actual " + actualInput);

        if (step.has("wrong_item")) {
            ItemStack wrongItem = itemForName(step.get("wrong_item").getAsString());
            int wrongCount = 0;
            for (int slot = 1; slot < menu.slots.size(); slot++) {
                ItemStack stack = menu.getSlot(slot).getItem();
                if (stack.is(wrongItem.getItem())) {
                    wrongCount += stack.getCount();
                }
            }
            int minimum = step.has("wrong_min_count") ? step.get("wrong_min_count").getAsInt() : 1;
            assertCondition(wrongCount >= minimum, "wrong inventory item remains available");
        }
    }

    private static ItemStack itemForName(String name) {
        var id = ResourceLocation.tryParse(name.contains(":") ? name : "minecraft:" + name);
        var item = id == null ? Items.AIR : net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) throw new IllegalArgumentException("Unknown test item: " + name);
        return new ItemStack(item);
    }

    private static void assertRedPacketBlessing(String expected) {
        if (!(Minecraft.getInstance().screen instanceof RedPacketEnvelopeScreen screen)
                || !(screen.getMenu() instanceof RedPacketEnvelopeScreenHandler handler)) {
            finish(false, "red packet assertion requires RedPacketEnvelopeScreen");
            return;
        }
        assertCondition(expected.equals(handler.blessings), "red packet blessing is filled");
        if (screen instanceof RedPacketEnvelopeScreenAccessor accessor) {
            EditBox box = accessor.contactQuests$getBlessingsBox();
            assertCondition(box != null && expected.equals(box.getValue()), "red packet text box is filled");
        } else {
            finish(false, "red packet screen accessor mixin is unavailable");
        }
    }

    private static void assertPostcardText(String expected) {
        if (!(Minecraft.getInstance().screen instanceof PostcardEditScreen screen)) {
            finish(false, "postcard assertion requires PostcardEditScreen");
            return;
        }
        try {
            if (!(screen instanceof PostcardEditScreenAccessor accessor)) {
                finish(false, "postcard screen accessor mixin is unavailable");
                return;
            }
            Object textBox = accessor.contactQuests$getTextBox();
            Object page = readField(textBox, "page");
            assertCondition(expected.equals(page), "postcard text is filled");
        } catch (Exception exception) {
            finish(false, "failed to inspect postcard text: " + exception.getMessage());
        }
    }

    private static void invokeScreenMethod(String name) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen == null) {
            finish(false, "cannot invoke " + name + " without a screen");
            return;
        }
        try {
            Method method = findMethod(screen.getClass(), name);
            method.setAccessible(true);
            method.invoke(screen);
        } catch (Exception exception) {
            finish(false, "failed to invoke screen method " + name + ": " + exception.getMessage());
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static String readClipboardWithRetry(Minecraft mc) {
        var clipboardManager = new com.mojang.blaze3d.platform.ClipboardManager();
        long window = mc.getWindow().getWindow();
        for (int attempt = 0; attempt < 10; attempt++) {
            boolean[] platformBusy = new boolean[]{false};
            int currentAttempt = attempt;
            String value = clipboardManager.getClipboard(window, (error, description) -> {
                if (error == org.lwjgl.glfw.GLFW.GLFW_PLATFORM_ERROR && currentAttempt < 9) {
                    platformBusy[0] = true;
                } else if (error != com.mojang.blaze3d.platform.ClipboardManager.FORMAT_UNAVAILABLE) {
                    mc.getWindow().defaultErrorCallback(error, description);
                }
            });
            if (!platformBusy[0]) return value;
            try {
                Thread.sleep(10L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return value;
            }
        }
        return mc.keyboardHandler.getClipboard();
    }

    private static Object readField(Object object, String name) throws IllegalAccessException, NoSuchFieldException {
        Class<?> current = object.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(object);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void assertGuiProviders() {
        assertCondition(TaskRegistry.PARCEL.getGuiProvider() != null, "parcel task GUI provider");
        assertCondition(TaskRegistry.RED_PACKET.getGuiProvider() != null, "red packet task GUI provider");
        assertCondition(TaskRegistry.POSTCARD.getGuiProvider() != null, "postcard task GUI provider");
        assertCondition(RewardRegistry.INSTANCE.getPARCEL().getGuiProvider() != null, "parcel reward GUI provider");
        assertCondition(RewardRegistry.INSTANCE.getPARCEL_RANDOM().getGuiProvider() != null, "random reward GUI provider");
        assertCondition(RewardRegistry.INSTANCE.getPARCEL_ALL_TABLE().getGuiProvider() != null, "all-table reward GUI provider");
        assertCondition(RewardRegistry.INSTANCE.getPOSTCARD().getGuiProvider() != null, "postcard reward GUI provider");
        assertCondition(RewardRegistry.INSTANCE.getRED_PACKET().getGuiProvider() != null, "red packet reward GUI provider");
        if (!finished) assertCase("client.bootstrap", true, "World loaded and all three task/five reward GUI providers exist");
    }

    private static void assertScreen(String expected) {
        Screen screen = Minecraft.getInstance().screen;
        boolean matches = "none".equals(expected)
                ? screen == null
                : screen != null && screen.getClass().getSimpleName().equals(expected);
        assertCondition(matches, "screen is " + expected);
    }

    private static void assertCondition(boolean condition, String description) {
        assertions.add(Map.of("success", condition, "message", description));
        if (!condition) {
            finish(false, "assertion failed: " + description);
        }
    }

    static void assertCase(String id, boolean success, String description) {
        assertions.add(Map.of("id", id, "success", success, "message", description));
        if (!success) finish(false, "assertion failed: " + description);
    }

    private static void finish(boolean success, String message) {
        if (finished) {
            return;
        }
        finished = true;
        result.put("success", success);
        result.put("message", message);
        result.put("steps", stepIndex);
        result.put("assertions", assertions);
        result.put("toasts", toasts);
        result.put("runtime", Map.of(
                "os", System.getProperty("os.name"),
                "javaVersion", System.getProperty("java.version"),
                "javaVendor", System.getProperty("java.vendor"),
                "noKubejs", Boolean.getBoolean("contactquests.test.noKubejs"),
                "fontModule", inspectFontModule(),
                "artifact", ContactQuestsArtifactAssertions.inspect()
        ));

        String output = new GsonBuilder().setPrettyPrinting().create().toJson(result);
        try {
            Path outputPath = Paths.get(
                    System.getProperty("contactquests.test.clientResult", "test-results/client-results.json")
            );
            Path parent = outputPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            ContactQuestsTestEvidence.write(outputPath, output);
        } catch (Exception exception) {
            System.err.println("[contactquests_test] Failed to write client result: " + exception);
        }

        Minecraft.getInstance().stop();
    }

    private static Map<String, Object> inspectFontModule() {
        try {
            Class<?> fontClass = Class.forName("org.lwjgl.stb.STBTTFontinfo");
            Module module = fontClass.getModule();
            return Map.of(
                    "name", String.valueOf(module.getName()),
                    "bootLayer", module.getLayer() == ModuleLayer.boot(),
                    "stbNativeBootLayer", ModuleLayer.boot().findModule("org.lwjgl.stb.natives").isPresent(),
                    "freetypeNativeBootLayer", ModuleLayer.boot().findModule("org.lwjgl.freetype.natives").isPresent(),
                    "allocatorOpenToMinecraft", ModuleLayer.boot().findModule("org.lwjgl").orElseThrow()
                            .isOpen("org.lwjgl.system", Minecraft.class.getModule()),
                    "classResource", String.valueOf(fontClass.getResource("STBTTFontinfo.class"))
            );
        } catch (Throwable error) {
            return Map.of("bootLayer", false, "error", error.toString());
        }
    }
}
