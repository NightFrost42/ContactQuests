package com.creeperyang.contactquests.test;

import com.creeperyang.contactquests.llm.*;
import com.google.gson.*;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Exercises the production HTTP client and parser against a fixed loopback server, without a provider dependency. */
@GameTestHolder(ContactQuestsTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ContactQuestsLlmTransportGameTests {
    private record Request(String path, Map<String, String> headers, JsonObject body) {}
    private record Answer(int status, String body) {}

    private static final class Endpoint implements AutoCloseable {
        private final ServerSocket socket = new ServerSocket();
        private final BlockingQueue<Answer> answers = new LinkedBlockingQueue<>();
        private final BlockingQueue<Request> requests = new LinkedBlockingQueue<>();
        private final Thread thread;
        private volatile Throwable failure;
        Endpoint() throws IOException {
            socket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
            thread = new Thread(this::serve, "CQ-Test-HTTP");
            thread.setDaemon(true);
            thread.start();
        }
        String url() { return "http://127.0.0.1:" + socket.getLocalPort() + "/v1"; }
        void answer(int status, String body) { answers.add(new Answer(status, body)); }
        Request request() throws Exception {
            var value = requests.poll(3, TimeUnit.SECONDS);
            if (failure != null) throw new AssertionError("Fixed HTTP fixture failed", failure);
            if (value == null) throw new AssertionError("Production client did not issue the expected HTTP request");
            return value;
        }
        private void serve() {
            try {
                while (!socket.isClosed()) try (var connection = socket.accept()) {
                    connection.setSoTimeout(5000);
                    var in = connection.getInputStream();
                    var first = line(in);
                    var headers = new LinkedHashMap<String, String>();
                    String header;
                    while (!(header = line(in)).isEmpty()) {
                        int colon = header.indexOf(':');
                        headers.put(header.substring(0, colon).toLowerCase(Locale.ROOT), header.substring(colon + 1).trim());
                    }
                    int length = Integer.parseInt(headers.getOrDefault("content-length", "0"));
                    var body = JsonParser.parseString(new String(in.readNBytes(length), StandardCharsets.UTF_8)).getAsJsonObject();
                    requests.add(new Request(first.split(" ")[1], headers, body));
                    var answer = answers.poll(3, TimeUnit.SECONDS);
                    if (answer == null) throw new AssertionError("No fixed HTTP response was prepared");
                    var bytes = answer.body.getBytes(StandardCharsets.UTF_8);
                    var out = connection.getOutputStream();
                    out.write(("HTTP/1.1 " + answer.status + " Test\r\nContent-Type: application/json\r\nContent-Length: " + bytes.length
                            + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    out.write(bytes); out.flush();
                }
            } catch (Throwable error) { if (!socket.isClosed()) failure = error; }
        }
        private static String line(InputStream in) throws IOException {
            var bytes = new ByteArrayOutputStream();
            int value;
            while ((value = in.read()) != -1 && value != '\n') if (value != '\r') bytes.write(value);
            if (value == -1) throw new EOFException("Incomplete HTTP request");
            return bytes.toString(StandardCharsets.US_ASCII);
        }
        @Override public void close() throws Exception { socket.close(); thread.join(1000); }
    }

    private static NpcLlmConfig.Provider provider(String url, String credential) {
        return new NpcLlmConfig.Provider("openai_compatible", url, credential, "provider-default", "default", 2, 512,
                List.of("publish_quest", "get_quest"));
    }
    private static NpcLlmTransport.Input input(String reasoning, Set<String> tools) {
        return new NpcLlmTransport.Input("HTTPTestNPC", "Persona: precise courier 人设", "npc-model-override", reasoning,
                List.of(new NpcLlmSavedData.Turn("user", "previous player postcard"),
                        new NpcLlmSavedData.Turn("assistant", "previous NPC reply")), "current postcard 正文", tools);
    }
    private static String reaction(String text, String policy) {
        var value = new JsonObject(); value.addProperty("reply", text);
        if (policy != null) value.addProperty("mail_policy", policy);
        return completion(new JsonPrimitive(value.toString()), null);
    }
    private static String completion(JsonElement content, JsonArray tools) {
        var message = new JsonObject(); message.add("content", content);
        if (tools != null) message.add("tool_calls", tools);
        var choice = new JsonObject(); choice.add("message", message);
        var choices = new JsonArray(); choices.add(choice);
        var response = new JsonObject(); response.add("choices", choices);
        return response.toString();
    }
    private static JsonObject tool(String id, String name, String arguments) {
        var function = new JsonObject(); function.addProperty("name", name); function.addProperty("arguments", arguments);
        var tool = new JsonObject(); tool.addProperty("id", id); tool.addProperty("type", "function"); tool.add("function", function);
        return tool;
    }
    private static void rejected(GameTestHelper helper, Runnable action, String reason) {
        boolean rejected = false;
        try { action.run(); } catch (RuntimeException expected) { rejected = true; }
        helper.assertTrue(rejected, "Production HTTP parser accepted " + reason);
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void httpNpcReplyPreservesRequestSettingsAndHistory(GameTestHelper helper) throws Exception {
        try (var endpoint = new Endpoint()) {
            for (String policy : List.of("keep", "return", "withReward", "discard")) {
                endpoint.answer(200, reaction("固定 HTTP 回信", policy));
                var response = NpcLlmTransport.INSTANCE.call(provider(endpoint.url() + "/", ""), input("low", Set.of()));
                helper.assertTrue(response.getText().equals("固定 HTTP 回信") && response.getMailPolicy().equals(policy)
                        && response.getTools().isEmpty(), "HTTP JSON reaction/policy was changed");
                var request = endpoint.request(); var body = request.body;
                helper.assertTrue(request.path.equals("/v1/chat/completions") && !request.headers.containsKey("authorization")
                        && request.headers.get("user-agent").equals("Magpie/0.1.3933")
                        && !request.headers.containsKey("upgrade") && !request.headers.containsKey("http2-settings")
                        && body.get("model").getAsString().equals("npc-model-override") && body.get("max_tokens").getAsInt() == 512
                        && body.get("reasoning_effort").getAsString().equals("low") && !body.has("tools")
                        && body.getAsJsonObject("response_format").get("type").getAsString().equals("json_object"),
                        "Actual HTTP request lost private settings, endpoint suffix or output contract");
                var messages = body.getAsJsonArray("messages");
                helper.assertTrue(messages.size() == 4 && messages.get(0).getAsJsonObject().get("content").getAsString().contains("Persona: precise courier 人设")
                        && messages.get(1).getAsJsonObject().get("content").getAsString().equals("previous player postcard")
                        && messages.get(2).getAsJsonObject().get("role").getAsString().equals("assistant")
                        && messages.get(3).getAsJsonObject().get("content").getAsString().equals("current postcard 正文"),
                        "Actual HTTP messages lost persona, role order, persisted history or Unicode");
            }
            endpoint.answer(200, reaction("Explicit keep policy", "keep"));
            var response = NpcLlmTransport.INSTANCE.call(provider(endpoint.url() + "/chat/completions", ""), input("default", Set.of()));
            var request = endpoint.request();
            helper.assertTrue(response.getText().equals("Explicit keep policy") && response.getMailPolicy().equals("keep") && request.path.equals("/v1/chat/completions")
                    && !request.body.has("reasoning_effort"), "Default reasoning/policy or complete endpoint was changed");
            for (String legacy : List.of("with_rewards", "with_reward")) {
                endpoint.answer(200, reaction("Legacy reward policy", legacy));
                response = NpcLlmTransport.INSTANCE.call(provider(endpoint.url(), ""), input("default", Set.of()));
                endpoint.request();
                helper.assertTrue(response.getMailPolicy().equals("withReward"),
                        "Legacy " + legacy + " policy was not normalized to withReward");
            }
            endpoint.answer(200, completion(new JsonPrimitive("' {\"reply\":\"Quoted exact reply\",\"mail_policy\":\"discard\"} '"), null));
            response = NpcLlmTransport.INSTANCE.call(provider(endpoint.url(), ""), input("default", Set.of()));
            endpoint.request();
            helper.assertTrue(response.getText().equals("Quoted exact reply") && response.getMailPolicy().equals("discard"),
                    "Single-quoted JSON reaction wrapper changed the exact reply or policy");
            endpoint.answer(200, completion(new JsonPrimitive("Plain model reply"), null));
            rejected(helper, () -> NpcLlmTransport.INSTANCE.call(provider(endpoint.url(), ""), input("default", Set.of())),
                    "plain-text response without a provider-authored item disposition");
            endpoint.request();
            endpoint.answer(200, completion(new JsonPrimitive("{\"reply\":\"No disposition\"}"), null));
            rejected(helper, () -> NpcLlmTransport.INSTANCE.call(provider(endpoint.url(), ""), input("default", Set.of())),
                    "JSON reaction with no provider-authored item disposition");
            endpoint.request();
        }
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void failedNpcRequestAndWithRewardsEscrowPersistExactly(GameTestHelper helper) throws Exception {
        var level = helper.getLevel().getServer().overworld();
        var store = NpcLlmSavedData.Companion.get(level);
        var team = UUID.randomUUID(); var player = UUID.randomUUID();
        var conversation = store.conversation("CQFailurePersistence", team, player, "team");
        var incoming = com.flechazo.contact.common.item.PostcardItem.setText(
                com.flechazo.contact.common.item.PostcardItem.getPostcard(net.minecraft.resources.ResourceLocation.parse("contact:creeper"), false),
                "unanswered original postcard");
        var mail = new NpcLlmSavedData.Mail(UUID.randomUUID(), player, "provider timeout", List.of(incoming));
        conversation.getInbox().add(mail); conversation.setPendingId(mail.getId()); conversation.setPendingPlayer(player);
        conversation.setPendingText(mail.getText()); conversation.setRetryAfterTick(level.getGameTime() + 80);
        conversation.getRewardItems().add(new NpcLlmSavedData.RewardEscrow(player, new ArrayList<>(List.of(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD, 3)))));
        var restored = ContactQuestsTestUtil.roundTrip(store, level.registryAccess());
        var loaded = restored.all().stream().filter(c -> c.getKey().equals(conversation.getKey())).findFirst().orElseThrow();
        helper.assertTrue(loaded.getInbox().size() == 1 && loaded.getInbox().getFirst().getId().equals(mail.getId())
                && loaded.getPendingId().equals(mail.getId()) && loaded.getPendingText().equals("provider timeout")
                && loaded.getRetryAfterTick() == conversation.getRetryAfterTick() && !loaded.getResolved()
                && loaded.getRewardItems().size() == 1 && loaded.getRewardItems().getFirst().getPlayerId().equals(player)
                && loaded.getRewardItems().getFirst().getItems().getFirst().getCount() == 3,
                "Restart lost failed request identity, retry time, original postcard or player-scoped reward escrow");
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void httpNpcToolsCarryExactResultsIntoFinalResponse(GameTestHelper helper) throws Exception {
        try (var endpoint = new Endpoint()) {
            var calls = new JsonArray();
            calls.add(tool("publication", "publish_quest", "{\"key\":\"intro\",\"tasks\":[],\"rewards\":[]}"));
            calls.add(tool("lookup", "get_quest", "{\"key\":\"intro\"}"));
            endpoint.answer(200, completion(JsonNull.INSTANCE, calls));
            var input = input("high", Set.of("publish_quest", "get_quest")); var provider = provider(endpoint.url(), "");
            var response = NpcLlmTransport.INSTANCE.call(provider, input);
            var request = endpoint.request(); var schemas = request.body.getAsJsonArray("tools");
            helper.assertTrue(response.getText().isEmpty() && response.getTools().size() == 2
                    && response.getTools().getFirst().getArguments().get("key").getAsString().equals("intro")
                    && schemas.size() == 2 && schemas.get(0).getAsJsonObject().getAsJsonObject("function").get("name").getAsString().equals("get_quest")
                    && schemas.get(1).getAsJsonObject().getAsJsonObject("function").getAsJsonObject("parameters").has("required"),
                    "HTTP tool plan or authorized schemas were not represented by the production client");
            var publicationSchema = schemas.get(1).getAsJsonObject().getAsJsonObject("function")
                    .getAsJsonObject("parameters").getAsJsonObject("properties");
            var taskTypes = publicationSchema.getAsJsonObject("tasks").getAsJsonObject("items")
                    .getAsJsonObject("properties").getAsJsonObject("type").getAsJsonArray("enum");
            var rewardTypes = publicationSchema.getAsJsonObject("rewards").getAsJsonObject("items")
                    .getAsJsonObject("properties").getAsJsonObject("type").getAsJsonArray("enum");
            helper.assertTrue(taskTypes.size() == 4 && taskTypes.contains(new JsonPrimitive("contactquests:parcel_task"))
                    && rewardTypes.size() == 6 && rewardTypes.contains(new JsonPrimitive("contactquests:parcel_choice")),
                    "Provider schema omitted the server-authorized native task/reward type enumeration");
            endpoint.answer(200, reaction("Publication was confirmed", "return"));
            var results = List.of("6011223344556677", "{\"id\":\"6011223344556677\",\"title\":\"Confirmed\"}");
            var finalResponse = NpcLlmTransport.INSTANCE.followUp(provider, input, response, results);
            var followUp = endpoint.request().body; var messages = followUp.getAsJsonArray("messages");
            helper.assertTrue(!followUp.has("tools") && messages.size() == 7
                    && messages.get(4).getAsJsonObject().getAsJsonArray("tool_calls").equals(calls)
                    && messages.get(5).getAsJsonObject().get("tool_call_id").getAsString().equals("publication")
                    && messages.get(5).getAsJsonObject().get("content").getAsString().equals(results.get(0))
                    && messages.get(6).getAsJsonObject().get("content").getAsString().equals(results.get(1))
                    && finalResponse.getText().equals("Publication was confirmed") && finalResponse.getMailPolicy().equals("return"),
                    "Actual final request lost tool IDs/results or advertised another tool execution");
            rejected(helper, () -> NpcLlmTransport.INSTANCE.followUp(provider, input, response, List.of()), "mismatched tool result count");
            endpoint.answer(200, completion(JsonNull.INSTANCE, calls));
            rejected(helper, () -> NpcLlmTransport.INSTANCE.followUp(provider, input, response, results), "new tools in the final reaction");
            endpoint.request();
        }
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void httpNpcMalformedOutputAndLimitsFailClosed(GameTestHelper helper) throws Exception {
        try (var endpoint = new Endpoint()) {
            var provider = provider(endpoint.url(), ""); var input = input("none", Set.of());
            for (var bad : List.of(
                    "not JSON", "{}", "{\"choices\":[]}", completion(JsonNull.INSTANCE, null),
                    completion(new JsonPrimitive("{}"), null), completion(new JsonPrimitive("{\"reply\":\"x\"}"), null),
                    completion(new JsonPrimitive("{\"reply\":\"x\",\"unknown\":true}"), null),
                    completion(new JsonPrimitive("{\"reply\":\"x\",\"mail_policy\":\"delete_all\"}"), null),
                    reaction("x".repeat(2001), "keep"), completion(new JsonPrimitive("x".repeat(8193)), null))) {
                endpoint.answer(200, bad);
                rejected(helper, () -> NpcLlmTransport.INSTANCE.call(provider, input), "malformed or excessive response: " + bad.substring(0, Math.min(80, bad.length())));
                endpoint.request();
            }
            endpoint.answer(200, " ".repeat(262145));
            rejected(helper, () -> NpcLlmTransport.INSTANCE.call(provider, input), "HTTP body above 256 KiB"); endpoint.request();
            var excessive = new JsonArray();
            for (int i = 0; i < 5; i++) excessive.add(tool("tool" + i, "get_quest", "{\"key\":\"intro\"}"));
            endpoint.answer(200, completion(JsonNull.INSTANCE, excessive));
            rejected(helper, () -> NpcLlmTransport.INSTANCE.call(provider, input), "more than four tools"); endpoint.request();
            var malformed = new JsonArray(); malformed.add(tool("bad", "get_quest", "[]"));
            endpoint.answer(200, completion(JsonNull.INSTANCE, malformed));
            rejected(helper, () -> NpcLlmTransport.INSTANCE.call(provider, input), "non-object tool arguments"); endpoint.request();
            malformed = new JsonArray(); malformed.add(tool("quoted", "get_quest", "' {\"key\":\"intro\"} '") );
            endpoint.answer(200, completion(JsonNull.INSTANCE, malformed));
            var quoted = NpcLlmTransport.INSTANCE.call(provider, input);
            helper.assertTrue(quoted.getTools().size() == 1
                    && quoted.getTools().getFirst().getArguments().get("key").getAsString().equals("intro"),
                    "Single-quoted JSON tool arguments were not normalized as one compatible wrapper");
            endpoint.request();
            malformed = new JsonArray(); malformed.add(tool("bad_json", "get_quest", "{\"key\":"));
            endpoint.answer(200, completion(JsonNull.INSTANCE, malformed));
            try {
                NpcLlmTransport.INSTANCE.call(provider, input);
                throw new AssertionError("Malformed structured-tool JSON was accepted");
            } catch (IllegalArgumentException expected) {
                helper.assertTrue(expected.getMessage().contains("Malformed provider tool arguments"),
                        "Malformed tool arguments were not identified without exposing provider content");
            }
            endpoint.request();
            endpoint.answer(200, completion(new JsonPrimitive("{\"reply\":"), null));
            try {
                NpcLlmTransport.INSTANCE.call(provider, input);
                throw new AssertionError("Malformed reaction JSON was accepted");
            } catch (IllegalArgumentException expected) {
                helper.assertTrue(expected.getMessage().contains("Malformed provider NPC reaction"),
                        "Malformed NPC reaction was not identified without exposing provider content");
            }
            endpoint.request();
        }
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void httpNpcFailuresDoNotBecomeNpcReactions(GameTestHelper helper) throws Exception {
        try (var endpoint = new Endpoint()) {
            var input = input("low", Set.of());
            for (int status : List.of(301, 401, 429, 503)) {
                endpoint.answer(status, reaction("This must never be delivered", "discard"));
                rejected(helper, () -> NpcLlmTransport.INSTANCE.call(provider(endpoint.url(), ""), input), "HTTP " + status);
                endpoint.request();
            }
            rejected(helper, () -> NpcLlmTransport.INSTANCE.call(provider(endpoint.url(), "CQ_TEST_ABSENT_CREDENTIAL_" + UUID.randomUUID().toString().replace("-", "")), input),
                    "missing configured API credential");
        }
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void httpNpcReactionRequiresStringFields(GameTestHelper helper) throws Exception {
        try (var endpoint = new Endpoint()) {
            for (String reaction : List.of("{\"reply\":123}", "{\"reply\":true}", "{\"reply\":\"x\",\"mail_policy\":null}")) {
                endpoint.answer(200, completion(new JsonPrimitive(reaction), null));
                rejected(helper, () -> NpcLlmTransport.INSTANCE.call(provider(endpoint.url(), ""), input("default", Set.of())), "non-string reaction fields");
                endpoint.request();
            }
        }
        helper.succeed();
    }
}
