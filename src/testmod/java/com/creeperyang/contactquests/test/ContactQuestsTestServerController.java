package com.creeperyang.contactquests.test;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ContactQuestsTestServerController {
    private static boolean loaded;
    private static int parcelEvents;
    private static int redPacketEvents;
    private static int postcardEvents;
    private static final Set<String> recipients = new LinkedHashSet<>();
    private static final Set<String> interceptedTypes = new LinkedHashSet<>();
    private static final List<String> failures = new ArrayList<>();
    private static final Map<String, Boolean> caseResults = new LinkedHashMap<>();
    private static final Map<String, String> caseMessages = new LinkedHashMap<>();
    private static int tick;
    private static long editorQuestId = -1L;
    private static long editorRewardTableId = -1L;
    private static long editorEditedTableId = -1L;
    private static long editorMutexId = -1L;
    private static String editorPostcardText = "";
    private static boolean dedicated;
    private static long runtimeQuestId = -1L;
    private static long runtimeParcelTaskId = -1L;
    private static long runtimeRedPacketTaskId = -1L;
    private static long runtimePostcardTaskId = -1L;
    private static long runtimeParcelRewardId = -1L;
    private static long runtimeRedPacketRewardId = -1L;
    private static long runtimePostcardRewardId = -1L;
    private static long runtimeStateQuestId = -1L;
    private static String requestedTextPhase = "";

    private ContactQuestsTestServerController() {
    }

    public static void reset() {
        loaded = false;
        parcelEvents = 0;
        redPacketEvents = 0;
        postcardEvents = 0;
        recipients.clear();
        interceptedTypes.clear();
        failures.clear();
        caseResults.clear();
        caseMessages.clear();
        tick = 0;
        editorQuestId = -1L;
        editorRewardTableId = -1L;
        editorEditedTableId = -1L;
        editorMutexId = -1L;
        editorPostcardText = "";
        runtimeQuestId = -1L;
        runtimeParcelTaskId = -1L;
        runtimeRedPacketTaskId = -1L;
        runtimePostcardTaskId = -1L;
        runtimeParcelRewardId = -1L;
        runtimeRedPacketRewardId = -1L;
        runtimePostcardRewardId = -1L;
        runtimeStateQuestId = -1L;
        requestedTextPhase = "";
        writeResult();
    }

    public static void setEditorQuestId(long id) { editorQuestId = id; writeResult(); }
    public static long getEditorQuestId() { return editorQuestId; }
    public static void setEditorRewardTableId(long id) { editorRewardTableId = id; writeResult(); }
    public static void setEditorEditedTableId(long id) { editorEditedTableId = id; writeResult(); }
    public static long getEditorEditedTableId() { return editorEditedTableId; }
    public static void setEditorMutexId(long id) { editorMutexId = id; writeResult(); }
    public static long getEditorMutexId() { return editorMutexId; }
    public static void setEditorPostcardText(String value) { editorPostcardText = value; writeResult(); }
    public static String getEditorPostcardText() { return editorPostcardText; }

    public static void markLoaded() {
        loaded = true;
        writeResult();
    }

    public static void recordFailure(String message) {
        failures.add(message);
        writeResult();
    }

    public static void recordMail(String type, String recipient, boolean intercepted) {
        switch (type) {
            case "parcel" -> parcelEvents++;
            case "red_packet" -> redPacketEvents++;
            case "postcard" -> postcardEvents++;
            default -> failures.add("unexpected mail type: " + type);
        }
        recipients.add(recipient);
        if (intercepted) {
            interceptedTypes.add(type);
        }
        writeResult();
    }

    public static void setRuntimeGraphIds(
            long questId,
            long parcelTaskId,
            long redPacketTaskId,
            long postcardTaskId,
            long parcelRewardId,
            long redPacketRewardId,
            long postcardRewardId
    ) {
        runtimeQuestId = questId;
        runtimeParcelTaskId = parcelTaskId;
        runtimeRedPacketTaskId = redPacketTaskId;
        runtimePostcardTaskId = postcardTaskId;
        runtimeParcelRewardId = parcelRewardId;
        runtimeRedPacketRewardId = redPacketRewardId;
        runtimePostcardRewardId = postcardRewardId;
    }

    public static void setRuntimeStateQuestId(long questId) {
        runtimeStateQuestId = questId;
    }

    public static boolean hasRuntimeStateGraph() {
        return runtimeStateQuestId >= 0L;
    }

    public static String getRuntimeStateQuestHexId() {
        return Long.toUnsignedString(runtimeStateQuestId, 16);
    }

    public static long getRuntimeStateQuestId() {
        return runtimeStateQuestId;
    }

    public static boolean hasRuntimeGraph() {
        return runtimeQuestId >= 0L
                && runtimeParcelTaskId >= 0L
                && runtimeRedPacketTaskId >= 0L
                && runtimePostcardTaskId >= 0L
                && runtimeParcelRewardId >= 0L
                && runtimeRedPacketRewardId >= 0L
                && runtimePostcardRewardId >= 0L;
    }

    public static long getRuntimeQuestId() { return runtimeQuestId; }
    public static long getRuntimeParcelTaskId() { return runtimeParcelTaskId; }
    public static long getRuntimeRedPacketTaskId() { return runtimeRedPacketTaskId; }
    public static long getRuntimePostcardTaskId() { return runtimePostcardTaskId; }
    public static long getRuntimeParcelRewardId() { return runtimeParcelRewardId; }
    public static long getRuntimeRedPacketRewardId() { return runtimeRedPacketRewardId; }
    public static long getRuntimePostcardRewardId() { return runtimePostcardRewardId; }

    public static void recordCase(String id, boolean passed, String message) {
        caseResults.put(id, passed);
        caseMessages.put(id, message);
        if (!passed) {
            failures.add(id + ": " + message);
        }
        writeResult();
    }

    public static boolean isCasePassed(String id) {
        return Boolean.TRUE.equals(caseResults.get(id));
    }

    public static boolean hasCase(String id) {
        return caseResults.containsKey(id);
    }

    public static void requestTextPhase(String phase) { requestedTextPhase = phase; }
    public static String consumeTextPhase() {
        String phase = requestedTextPhase;
        requestedTextPhase = "";
        return phase;
    }

    @SubscribeEvent
    public static void onPlayerLogin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (Boolean.getBoolean("contactquests.test.dedicated") && event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player
                && player.server.isDedicatedServer()) {
            player.server.getPlayerList().op(player.getGameProfile());
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("contactquests.test.server")) {
            return;
        }
        tick++;
        dedicated = event.getServer().isDedicatedServer();
        if (tick % 10 == 0) {
            writeResult();
        }
    }

    private static synchronized void writeResult() {
        String pathValue = System.getProperty("contactquests.test.serverResult");
        if (pathValue == null || pathValue.isBlank()) {
            return;
        }

        JsonObject result = new JsonObject();
        result.addProperty("loaded", loaded);
        JsonObject runtime = new JsonObject();
        runtime.addProperty("pid", ProcessHandle.current().pid());
        runtime.addProperty("dedicated", dedicated);
        runtime.addProperty("os", System.getProperty("os.name"));
        runtime.addProperty("noKubejs", Boolean.getBoolean("contactquests.test.noKubejs"));
        runtime.add("artifact", ContactQuestsArtifactAssertions.inspect());
        result.add("runtime", runtime);
        if (editorQuestId >= 0) result.addProperty("editorQuestId", Long.toUnsignedString(editorQuestId, 16));
        if (editorRewardTableId >= 0) result.addProperty("editorRewardTableId", Long.toUnsignedString(editorRewardTableId, 16));
        if (editorEditedTableId >= 0) result.addProperty("editorEditedTableId", Long.toUnsignedString(editorEditedTableId, 16));
        if (editorMutexId >= 0) result.addProperty("editorMutexId", Long.toUnsignedString(editorMutexId, 16));
        result.addProperty("editorPostcardText", editorPostcardText);
        ContactQuestsInteractionFixtures.addResultFields(result);
        ContactQuestsNewFeatureFixtures.addResultFields(result);
        ContactQuestsLiveLlmFixtures.addResultFields(result);
        ContactQuestsPostcardLetterFixtures.addResultFields(result);
        if (Boolean.getBoolean("contactquests.test.noKubejs")) ContactQuestsNoKubejsFixtures.addResultFields(result);
        if (runtimeQuestId >= 0) result.addProperty("runtimeQuestId", Long.toUnsignedString(runtimeQuestId, 16));
        result.addProperty("success", loaded
                && (Boolean.getBoolean("contactquests.test.noKubejs") || (parcelEvents > 0
                && redPacketEvents > 0
                && postcardEvents > 0))
                && failures.isEmpty());
        result.addProperty("parcelEvents", parcelEvents);
        result.addProperty("redPacketEvents", redPacketEvents);
        result.addProperty("postcardEvents", postcardEvents);

        JsonArray recipientArray = new JsonArray();
        recipients.forEach(recipientArray::add);
        result.add("recipients", recipientArray);

        JsonArray interceptedArray = new JsonArray();
        interceptedTypes.forEach(interceptedArray::add);
        result.add("interceptedTypes", interceptedArray);

        JsonArray failuresArray = new JsonArray();
        failures.forEach(failuresArray::add);
        result.add("failures", failuresArray);

        JsonObject cases = new JsonObject();
        for (Map.Entry<String, Boolean> entry : caseResults.entrySet()) {
            JsonObject caseResult = new JsonObject();
            caseResult.addProperty("success", entry.getValue());
            caseResult.addProperty("message", caseMessages.getOrDefault(entry.getKey(), ""));
            cases.add(entry.getKey(), caseResult);
        }
        result.add("cases", cases);

        try {
            Path output = Paths.get(pathValue);
            Path parent = output.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            ContactQuestsTestEvidence.write(output, new GsonBuilder().setPrettyPrinting().create().toJson(result));
        } catch (IOException exception) {
            System.err.println("[contactquests_test] Failed to write server result: " + exception);
        }
    }
}
