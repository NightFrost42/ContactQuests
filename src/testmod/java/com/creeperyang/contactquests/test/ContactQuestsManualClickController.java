package com.creeperyang.contactquests.test;

import com.creeperyang.contactquests.client.gui.ContactConfigScreen;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manual-only click-test launcher. It is enabled by the Gradle
 * contactquestsClickTest run and is never enabled by the automatic runner.
 */
@EventBusSubscriber(modid = ContactQuestsTestMod.MOD_ID, value = Dist.CLIENT)
public final class ContactQuestsManualClickController {
    private static boolean opened;
    private static boolean completed;
    private static Screen parent;

    private ContactQuestsManualClickController() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Boolean.getBoolean("contactquests.test.manualClick") || completed) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (!opened) {
            if (minecraft.level == null || minecraft.player == null) {
                return;
            }
            opened = true;
            parent = new Screen(Component.literal("ContactQuests manual click test")) {
            };
            minecraft.setScreen(new ContactConfigScreen(parent));
            minecraft.player.displayClientMessage(
                    Component.literal("[ContactQuests] Manual click test: edit values, press Done/Cancel, then close the game."),
                    false
            );
            writeResult("STARTED", "ContactConfigScreen opened for manual interaction");
            return;
        }

        if (minecraft.screen == parent || minecraft.screen == null) {
            completed = true;
            writeResult("COMPLETED", "Manual click test returned from ContactConfigScreen");
        }
    }

    private static void writeResult(String status, String message) {
        String configured = System.getProperty(
                "contactquests.test.manualClickResult",
                "test-results/1.20.1-forge/manual-click-result.json"
        );
        Map<String, String> result = new LinkedHashMap<>();
        result.put("scope", "manual");
        result.put("status", status);
        result.put("message", message);
        try {
            Path output = Paths.get(configured);
            Path parentPath = output.getParent();
            if (parentPath != null) {
                Files.createDirectories(parentPath);
            }
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(result));
        } catch (IOException exception) {
            System.err.println("[contactquests_test] Failed to write manual click result: " + exception);
        }
    }
}
