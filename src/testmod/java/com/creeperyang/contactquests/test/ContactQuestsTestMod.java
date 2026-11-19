package com.creeperyang.contactquests.test;

import com.creeperyang.contactquests.utils.RedPacketUtils;
import com.flechazo.contact.client.gui.screen.PostboxScreen;
import com.flechazo.contact.common.item.ParcelItem;
import com.flechazo.contact.common.item.PostcardItem;
import com.flechazo.contact.common.screenhandler.EnvelopeScreenHandler;
import com.flechazo.contact.common.screenhandler.PostboxScreenHandler;
import com.flechazo.contact.common.screenhandler.RedPacketEnvelopeScreenHandler;
import com.flechazo.contact.helper.NetworkHelper;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.MenuConstructor;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@Mod(ContactQuestsTestMod.MOD_ID)
public final class ContactQuestsTestMod {
    public static final String MOD_ID = "contactquests_test";
    public static final String TEST_RECIPIENT = "CQTest_AliasCandidate";
    private static final java.util.Map<java.util.UUID, Integer> pendingAutofillPrefill = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, String> pendingWrappingPrefill = new java.util.HashMap<>();

    public ContactQuestsTestMod(IEventBus modBus) {
        dev.ftb.mods.ftbquests.integration.item_filtering.ItemMatchingSystem.INSTANCE.registerFilterAdapter(new ContactQuestsTestTagAdapter());
        modBus.addListener(this::registerGameTests);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::prefillAutofillContainer);
        NeoForge.EVENT_BUS.register(ContactQuestsTestServerController.class);
        NeoForge.EVENT_BUS.register(ContactQuestsLiveLlmFixtures.class);
        if (Boolean.getBoolean("contactquests.test.noKubejs")) {
            NeoForge.EVENT_BUS.register(ContactQuestsNoKubejsFixtures.class);
        }
        if (Boolean.getBoolean("contactquests.test.server")) {
            net.minecraft.gametest.framework.GlobalTestReporter.replaceWith(new ContactQuestsGameTestReporter());
        }
    }

    private void registerGameTests(net.neoforged.neoforge.event.RegisterGameTestsEvent event) {
        if (Boolean.getBoolean("contactquests.test.noKubejs")) return;
        event.register(ContactQuestsGameTests.class);
        event.register(ContactQuestsRegressionTests.class);
        event.register(ContactQuestsBoundaryTests.class);
        event.register(ContactQuestsNetworkTests.class);
        event.register(ContactQuestsFeatureGameTests.class);
        event.register(ContactQuestsLlmTransportGameTests.class);
        event.register(ContactQuestsMailTextGameTests.class);
        event.register(ContactQuestsNpcPostcardLettersGameTests.class);
        event.register(ContactQuestsExtendedGameTests.class);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("contactquests_test")
                        .then(Commands.literal("delivery_boundary")
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .then(Commands.argument("phase", StringArgumentType.word()).executes(context -> {
                                            ContactQuestsDeliveryBoundaryFixtures.run(context.getSource().getPlayerOrException(),
                                                    StringArgumentType.getString(context, "type"), StringArgumentType.getString(context, "phase"));
                                            return 1;
                                        }))))
                        .then(Commands.literal("prepare_reload").executes(context -> {
                            ContactQuestsNativeReloadAssertions.prepare(context.getSource().getPlayerOrException()); return 1;
                        }))
                        .then(Commands.literal("postcard_letters")
                                .then(Commands.argument("phase", StringArgumentType.word()).executes(context -> {
                                    ContactQuestsPostcardLetterFixtures.run(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "phase"));
                                    return 1;
                                })))
                        .then(Commands.literal("postcard_letter")
                                .then(Commands.argument("index", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 26)).executes(context -> {
                                    ContactQuestsPostcardLetterFixtures.collect(context.getSource().getPlayerOrException(),
                                            com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "index"));
                                    return 1;
                                })))
                        .then(Commands.literal("llm_live")
                                .then(Commands.argument("phase", StringArgumentType.word()).executes(context -> {
                                    String phase = StringArgumentType.getString(context, "phase");
                                    if (phase.equals("restored")) ContactQuestsLiveLlmFixtures.restoreAfterRestart(context.getSource().getPlayerOrException());
                                    else ContactQuestsLiveLlmFixtures.run(context.getSource().getPlayerOrException(), phase);
                                    return 1;
                                })))
                        .then(Commands.literal("llm_live_with_rewards_mail")
                                .executes(context -> {
                                    ContactQuestsLiveLlmFixtures.seedWithRewardsMail(context.getSource().getPlayerOrException());
                                    return 1;
                                }))
                        .then(Commands.literal("features")
                                .then(Commands.argument("phase", StringArgumentType.word())
                                        .executes(context -> {
                                            ContactQuestsNewFeatureFixtures.run(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "phase"));
                                            return 1;
                                        })))
                        .then(Commands.literal("features_seed_mail")
                                .then(Commands.argument("text", StringArgumentType.word()).executes(context -> {
                                    ContactQuestsNewFeatureFixtures.seedLlmMail(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "text")); return 1;
                                })))
                        .then(Commands.literal("no_kubejs")
                                .then(Commands.argument("phase", StringArgumentType.word())
                                        .executes(context -> {
                                            ContactQuestsNoKubejsFixtures.run(context.getSource().getPlayerOrException(),
                                                    StringArgumentType.getString(context, "phase"));
                                            return 1;
                                        })))
                        .then(Commands.literal("editor_rules")
                                .then(Commands.argument("phase", StringArgumentType.word())
                                        .executes(context -> {
                                            ContactQuestsEditorRuleFixtures.run(context.getSource().getPlayerOrException(),
                                                    StringArgumentType.getString(context, "phase"));
                                            return 1;
                                        })))
                        .then(Commands.literal("interaction_tags")
                                .then(Commands.argument("phase", StringArgumentType.word())
                                        .executes(context -> {
                                            ContactQuestsInteractionFixtures.tags(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "phase"));
                                            return 1;
                                        })))
                        .then(Commands.literal("interaction_mailbox")
                                .then(Commands.argument("phase", StringArgumentType.word())
                                        .executes(context -> {
                                            ContactQuestsInteractionFixtures.outsiderMailbox(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "phase"));
                                            return 1;
                                        })))
                        .then(Commands.literal("interaction_conversion")
                                .then(Commands.argument("phase", StringArgumentType.word())
                                        .executes(context -> {
                                            ContactQuestsInteractionFixtures.tagConversion(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "phase"));
                                            return 1;
                                        })))
                        .then(Commands.literal("text_phase")
                                .then(Commands.argument("phase", StringArgumentType.word())
                                        .executes(context -> {
                                            ContactQuestsTestServerController.requestTextPhase(StringArgumentType.getString(context, "phase"));
                                            return 1;
                                        })))
                        .then(Commands.literal("verify_editor_cancel")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(context -> {
                                            ContactQuestsIntegrationFixtures.verifyEditorCancel(context.getSource().getPlayerOrException(),
                                                    Long.parseUnsignedLong(StringArgumentType.getString(context, "id"), 16));
                                            return 1;
                                        })))
                        .then(Commands.literal("prepare_rejoin")
                                .executes(context -> {
                                    ContactQuestsRejoinFixtures.prepare(context.getSource().getPlayerOrException());
                                    return 1;
                                }))
                        .then(Commands.literal("verify_rejoin")
                                .executes(context -> {
                                    ContactQuestsRejoinFixtures.verify(context.getSource().getPlayerOrException());
                                    return 1;
                                }))
                        .then(Commands.literal("verify_editor")
                                .executes(context -> {
                                    ContactQuestsIntegrationFixtures.verifyEditorGraph(context.getSource().getPlayerOrException());
                                    return 1;
                                }))
                        .then(Commands.literal("verify_editor_fields")
                                .executes(context -> {
                                    ContactQuestsIntegrationFixtures.verifyEditorFields(context.getSource().getPlayerOrException());
                                    return 1;
                                }))
                        .then(Commands.literal("collect_editor_postcard")
                                .executes(context -> {
                                    ContactQuestsIntegrationFixtures.collectEditorPostcard(context.getSource().getPlayerOrException());
                                    return 1;
                                }))
                        .then(Commands.literal("prepare_editor_claims")
                                .executes(context -> {
                                    ContactQuestsIntegrationFixtures.prepareEditorRewardClaims(context.getSource().getPlayerOrException());
                                    return 1;
                                }))
                        .then(Commands.literal("verify_editor_claims")
                                .executes(context -> {
                                    ContactQuestsIntegrationFixtures.verifyEditorRewardClaims(context.getSource().getPlayerOrException(), false);
                                    return 1;
                                }))
                        .then(Commands.literal("verify_editor_repeat")
                                .executes(context -> {
                                    ContactQuestsIntegrationFixtures.verifyEditorRewardClaims(context.getSource().getPlayerOrException(), true);
                                    return 1;
                                }))
                        .then(Commands.literal("prepare_script_graph")
                                .executes(context -> {
                                    ContactQuestsGameTests.prepareScriptGraph(context.getSource().getPlayerOrException().serverLevel());
                                    return 1;
                                }))
                        .then(Commands.literal("verify_mail_persistence")
                                .executes(context -> {
                                    ContactQuestsIntegrationFixtures.verifyMailboxAndRejectedSend(context.getSource().getPlayerOrException());
                                    return 1;
                                }))
                        .then(Commands.literal("prepare_editor")
                                .executes(context -> {
                                    ContactQuestsIntegrationFixtures.prepareEditorGraph(context.getSource().getPlayerOrException());
                                    return 1;
                                }))
                        .then(Commands.literal("command_smoke")
                                .executes(context -> {
                                    ContactQuestsIntegrationFixtures.runCommandSmoke(
                                            context.getSource().getPlayerOrException()
                                    );
                                    return 1;
                                }))
                        .then(Commands.literal("task_runtime_flow")
                                .executes(context -> {
                                    ContactQuestsIntegrationFixtures.runTaskRuntimeFlow(
                                            context.getSource().getPlayerOrException()
                                    );
                                    return 1;
                                }))
                        .then(Commands.literal("team_binding_flow")
                                .executes(context -> {
                                    ContactQuestsIntegrationFixtures.runTeamBindingAndRewardRetry(
                                            context.getSource().getPlayerOrException()
                                    );
                                    return 1;
                                }))
                        .then(Commands.literal("open_postbox")
                                .executes(context -> openMenu(context.getSource().getPlayerOrException(),
                                        (id, inventory, player) -> new PostboxScreenHandler(id, inventory, false),
                                        "Test Postbox", true)))
                        .then(Commands.literal("open_envelope")
                                .executes(context -> openMenu(context.getSource().getPlayerOrException(),
                                        (id, inventory, player) -> new EnvelopeScreenHandler(id, inventory),
                                        "Test Envelope", false)))
                        .then(Commands.literal("open_red_packet")
                                .executes(context -> openMenu(context.getSource().getPlayerOrException(),
                                        (id, inventory, player) -> new RedPacketEnvelopeScreenHandler(id, inventory),
                                        "Test Red Packet", false)))
                        .then(Commands.literal("query_recipient")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            String input = StringArgumentType.getString(context, "name");
                                            NetworkHelper.handleNormalEnquiry(player, input.toLowerCase(), input, false);
                                            return 1;
                                        })))
                        .then(Commands.literal("force_send")
                                .then(Commands.argument("recipient", StringArgumentType.word())
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            String recipient = StringArgumentType.getString(context, "recipient");
                                            NetworkHelper.handleSendMail(player, recipient, 40);
                                            return 1;
                                        })))
                        .then(Commands.literal("seed_mail")
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            seedMail(player, StringArgumentType.getString(context, "type"));
                                            return 1;
                                        })))
                        .then(Commands.literal("seed_autofill")
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            seedAutofillInventory(player, StringArgumentType.getString(context, "type"));
                                            return 1;
                                        })))
        );
    }

    private static int openMenu(ServerPlayer player, MenuConstructor constructor, String title, boolean sendPostboxFlag) {
        SimpleMenuProvider provider = new SimpleMenuProvider(constructor, Component.literal(title));
        if (sendPostboxFlag) {
            player.openMenu(provider, buffer -> buffer.writeBoolean(false));
        } else {
            player.openMenu(provider);
        }
        return 1;
    }

    private static void seedMail(ServerPlayer player, String type) {
        ItemStack mail = switch (type) {
            case "parcel" -> {
                SimpleContainer contents = new SimpleContainer(4);
                contents.setItem(0, new ItemStack(Items.DIAMOND));
                yield ParcelItem.getParcel(contents, false, "ContactQuestsTest");
            }
            case "red_packet" -> {
                SimpleContainer contents = new SimpleContainer(1);
                contents.setItem(0, new ItemStack(Items.GOLD_INGOT));
                yield RedPacketUtils.INSTANCE.getRedPacket(contents, "fixed UI blessing", "ContactQuestsTest");
            }
            case "postcard" -> {
                ItemStack postcard = PostcardItem.getPostcard(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("contact", "default"), false);
                yield PostcardItem.setText(postcard, "fixed UI postcard");
            }
            default -> ItemStack.EMPTY;
        };

        player.getInventory().setItem(0, mail);
        broadcastInventory(player);
    }

    private static void seedAutofillInventory(ServerPlayer player, String type) {
        pendingAutofillPrefill.remove(player.getUUID());
        pendingWrappingPrefill.remove(player.getUUID());
        player.getInventory().clearContent();
        String base = type.startsWith("red_packet_") ? "red_packet" : type.startsWith("parcel_") ? "parcel"
                : type.startsWith("postcard_") ? "postcard" : type;
        ItemStack stationery = switch (base) {
            case "parcel" -> new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.stream()
                    .filter(i -> type.startsWith("parcel_wrapping_")
                            ? i instanceof com.flechazo.contact.common.item.WrappingPaperItem
                                && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(i).getPath().equals("wrapping_paper")
                            : i instanceof com.flechazo.contact.common.item.EnvelopeItem).findFirst().orElseThrow());
            case "red_packet" -> new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.stream()
                    .filter(i -> i instanceof com.flechazo.contact.common.item.RedPacketEnvelopeItem).findFirst().orElseThrow());
            case "postcard" -> PostcardItem.getPostcard(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("contact",
                    type.equals("postcard_narrow") ? "icarus_wings" : "default"), false);
            default -> ItemStack.EMPTY;
        };
        boolean outsideHotbar = type.equals("parcel_exact") || type.equals("red_packet_exact") || type.equals("postcard_inventory")
                || type.startsWith("parcel_wrapping_");
        player.getInventory().setItem(outsideHotbar ? 18 : 0, stationery);
        if (outsideHotbar) {
            var previousHeld = new ItemStack(Items.STICK);
            if (type.equals("postcard_inventory")) {
                previousHeld = PostcardItem.setText(PostcardItem.getPostcard(
                        net.minecraft.resources.ResourceLocation.parse("contact:default"), false), "Keep the original received card");
                previousHeld.set(com.flechazo.contact.common.component.ContactDataComponents.POSTCARD_SENDER.get(), "Earlier mail");
            }
            player.getInventory().setItem(0, previousHeld);
        }
        int count = switch (type) {
            case "parcel_exact", "red_packet_exact" -> 2;
            case "red_packet_prefilled" -> 4;
            case "parcel_wrapping_precise" -> 5;
            case "parcel_wrapping_prefilled" -> 4;
            case "red_packet_missing", "parcel_wrapping_missing" -> 0;
            default -> 3;
        };
        ItemStack valid = switch (base) {
            case "parcel" -> new ItemStack(Items.DIAMOND, count);
            case "red_packet" -> new ItemStack(Items.GOLD_INGOT, count);
            default -> ItemStack.EMPTY;
        };
        player.getInventory().setItem(9, valid);
        if (!base.equals("postcard")) player.getInventory().setItem(10, new ItemStack(Items.DIRT,
                type.equals("red_packet_wrong") ? 4 : type.equals("parcel_wrapping_full") ? 7 : 3));
        if (type.equals("red_packet_prefilled")) pendingAutofillPrefill.put(player.getUUID(), 9);
        if (type.equals("red_packet_wrong")) pendingAutofillPrefill.put(player.getUUID(), 10);
        if (type.equals("parcel_wrapping_prefilled") || type.equals("parcel_wrapping_full"))
            pendingWrappingPrefill.put(player.getUUID(), type);
        broadcastInventory(player);
    }

    private void prefillAutofillContainer(net.neoforged.neoforge.event.entity.player.PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getContainer() instanceof com.flechazo.contact.common.screenhandler.WrappingPaperScreenHandler wrapping) {
            String type = pendingWrappingPrefill.remove(player.getUUID());
            if (type == null) return;
            boolean full = type.equals("parcel_wrapping_full");
            int source = full ? 10 : 9;
            for (int i = 0; i < (full ? 4 : 1); i++) {
                if (!wrapping.getSlot(i).getItem().isEmpty() || player.getInventory().getItem(source).isEmpty())
                    throw new IllegalStateException("Wrapping prefill fixture lost its real inventory source");
                wrapping.getSlot(i).set(player.getInventory().getItem(source).split(1));
            }
            player.getInventory().setChanged();
            wrapping.broadcastFullState(); player.inventoryMenu.broadcastFullState();
            return;
        }
        if (!(event.getContainer() instanceof RedPacketEnvelopeScreenHandler menu)) return;
        Integer slot = pendingAutofillPrefill.remove(player.getUUID());
        if (slot == null) return;
        if (!menu.getSlot(0).getItem().isEmpty() || player.getInventory().getItem(slot).isEmpty())
            throw new IllegalStateException("Autofill prefill fixture has no source or an occupied input");
        menu.getSlot(0).set(player.getInventory().getItem(slot).split(1));
        player.getInventory().setChanged();
        menu.broadcastFullState(); player.inventoryMenu.broadcastFullState();
    }

    private static void broadcastInventory(ServerPlayer player) {
        player.inventoryMenu.broadcastFullState();
        if (player.containerMenu != player.inventoryMenu) {
            player.containerMenu.broadcastFullState();
        }
    }
}
