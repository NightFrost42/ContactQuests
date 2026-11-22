package com.creeperyang.contactquests.test;

import com.creeperyang.contactquests.compat.jade.MailboxComponentProvider;
import com.creeperyang.contactquests.bridge.IMailboxTeamAccessor;
import com.creeperyang.contactquests.mailbox.TeamMailboxBinding;
import com.flechazo.contact.platform.PlatformHelper;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.property.TeamProperties;
import net.minecraft.ChatFormatting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Nameable;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.Identifiers;
import snownee.jade.api.config.IWailaConfig;
import snownee.jade.api.ui.IElement;
import snownee.jade.impl.ObjectDataCenter;
import snownee.jade.impl.Tooltip;
import snownee.jade.impl.WailaClientRegistration;
import snownee.jade.impl.ui.TextElement;
import snownee.jade.overlay.TooltipRenderer;
import snownee.jade.overlay.WailaTickHandler;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * One integrated Windows-client action after interaction_mailbox short_name: run("all").
 * Retry false every five ticks; true means finished (including a recorded failure).
 * No sleeps, blocking future waits, synthetic ray hits or manual overlay rendering.
 * Server tasks only rename the existing outsider team and change/restore its marker.
 * A passive callback observes Jade's own BoxElement after its actual native render.
 */
public final class ContactQuestsJadeAssertions {
    private static final String PREFIX = "client.jade.mailbox.";
    private static final String TEAM_KEY = "ContactQuestsTeamID";
    private static final ResourceLocation UID = new ResourceLocation("contactquests", "mailbox_team_info");
    private static final UUID UNKNOWN = UUID.fromString("c3bfe9a2-273c-4de9-9abe-71d9bda06fe7");
    private static final String SHORT = "CQ Mail Team";
    private static final String LONG = "CQ foreign UI team with a deliberately long name for checking that the entire mailbox warning wraps instead of being clipped";
    private static final List<Variant> VARIANTS = List.of(
            new Variant("long_spaced", LONG),
            new Variant("long_unbroken", "WIDE0123456789".repeat(8) + "END"),
            new Variant("cjk", "星海探险队伍邮件归属完整显示".repeat(5) + "终点"),
            new Variant("short", SHORT),
            new Variant("unknown", SHORT),
            new Variant("unbound", SHORT),
            new Variant("narrow_large_scale", LONG));
    private static Session session;
    private static boolean registered, finished;

    private record Variant(String key, String name) {
        boolean narrow() { return key.equals("narrow_large_scale"); }
        UUID marker(UUID team) { return key.equals("unbound") ? null : key.equals("unknown") ? UNKNOWN : team; }
        boolean wraps() { return key.startsWith("long_") || key.equals("cjk") || narrow(); }
    }
    private enum Stage { PREPARING, MUTATING, OBSERVING, RESTORING }
    private record Glyph(int codepoint, Style style) {}
    private record ServerState(ServerLevel level, String name, UUID marker, UUID owner,
                               GlobalPos location, List<String> mail, float yaw, float pitch) {}

    private static final class Session {
        final net.minecraft.client.server.IntegratedServer server;
        final UUID player, team;
        final BlockPos pos;
        final ClientState client;
        final long started = System.nanoTime();
        final int firstTick;
        final Path evidence;
        volatile ServerState saved;
        CompletableFuture<ServerState> pending;
        Stage stage = Stage.PREPARING;
        long stageTime = System.nanoTime();
        int stageTick, index, validFrames, lastFrameTick = -1;
        String note = "Awaiting server snapshot", failureId;
        Throwable failure, renderFailure;
        JsonObject observation;

        Session(Minecraft mc, JsonObject fixture) {
            server = mc.getSingleplayerServer();
            player = mc.player.getUUID();
            team = UUID.fromString(fixture.get("interactionForeignTeam").getAsString());
            pos = new BlockPos(fixture.get("interactionForeignMailboxX").getAsInt(),
                    fixture.get("interactionForeignMailboxY").getAsInt(), fixture.get("interactionForeignMailboxZ").getAsInt());
            client = new ClientState(mc);
            firstTick = stageTick = mc.player.tickCount;
            evidence = Path.of(System.getProperty("contactquests.test.clientResult")).toAbsolutePath()
                    .getParent().resolve("screenshots");
        }
        Variant variant() { return VARIANTS.get(index); }
        void stage(Stage next, Minecraft mc) {
            stage = next; stageTime = System.nanoTime(); stageTick = mc.player.tickCount;
        }
    }

    /** Bounded, idempotent controller entry; all nine IDs below are business assertions. */
    public static boolean run(String phase) {
        if (finished) return true;
        Minecraft mc = Minecraft.getInstance();
        try {
            require("all".equals(phase), "Only Jade phase=all is supported");
            if (session == null) {
                require(mc.player != null && mc.level != null && mc.getSingleplayerServer() != null,
                        "Jade mailbox assertions require the existing integrated-server player");
                require(mc.screen == null && mc.player.containerMenu == mc.player.inventoryMenu,
                        "Close real screens before the automatic Jade overlay action");
                checkTranslations(mc);
                var method = ContactQuestsClientTestController.class.getDeclaredMethod("editorServerResult");
                method.setAccessible(true);
                session = new Session(mc, (JsonObject) method.invoke(null));
                registerObserver();
                Session s = session;
                s.pending = s.server.submit(() -> {
                    s.saved = serverState(s);
                    require(SHORT.equals(s.saved.name), "Call Jade after native FTB short_name restoration");
                    require(s.team.equals(s.saved.marker) && s.team.equals(s.saved.owner)
                                    && s.saved.location != null && s.pos.equals(s.saved.location.pos())
                                    && s.saved.level.dimension().equals(s.saved.location.dimension()),
                            "Existing outsider mailbox marker/owner/location disagree");
                    require(FTBTeamsAPI.api().getManager().getTeamByID(UNKNOWN).isEmpty(), "Unknown fixture UUID already exists");
                    return s.saved;
                });
                return false;
            }
            Session s = session;
            if (s.stage == Stage.RESTORING) return finishRestore(s, mc);
            require(mc.player != null && mc.player.getUUID().equals(s.player) && mc.getSingleplayerServer() == s.server,
                    "Player/server changed during Jade assertions");
            require(!expired(s.started, s.firstTick, mc, 120, 2000), "Jade all exceeded its overall time/tick bound");
            require(!expired(s.stageTime, s.stageTick, mc, 20, 200), "Jade " + s.stage + " timed out: " + s.note);
            if (s.renderFailure != null) throw new IllegalStateException("Actual native overlay: " + s.note, s.renderFailure);
            switch (s.stage) {
                case PREPARING -> {
                    if (!s.pending.isDone()) return false;
                    s.pending.join();
                    startVariant(s, mc);
                }
                case MUTATING -> {
                    if (!s.pending.isDone()) return false;
                    ServerState changed = s.pending.join();
                    require(changed.name.equals(s.variant().name) && Objects.equals(changed.marker, s.variant().marker(s.team)),
                            "Native server mutation did not take effect");
                    aim(s, mc);
                    s.stage(Stage.OBSERVING, mc);
                }
                case OBSERVING -> {
                    if (s.observation == null) return false;
                    ContactQuestsClientTestController.assertCase(PREFIX + s.variant().key, true,
                            "Actual appendTooltip and native rendered overlay: full styled text, bounded geometry; "
                                    + s.observation);
                    if (++s.index == VARIANTS.size()) beginRestore(s, mc);
                    else startVariant(s, mc);
                }
                default -> throw new IllegalStateException("Unexpected Jade stage " + s.stage);
            }
            return false;
        } catch (Exception | LinkageError error) {
            Session s = session;
            if (s == null) return failNow(PREFIX + "native_overlay", error.toString());
            if (s.failure == null) {
                s.failure = error;
                s.failureId = PREFIX + (s.index < VARIANTS.size() ? s.variant().key : "restored");
            } else s.failure.addSuppressed(error);
            if (s.stage == Stage.RESTORING) return failNow(s.failureId, s.failure + "; cleanup: " + error);
            try { beginRestore(s, mc); return false; }
            catch (Exception | LinkageError cleanup) {
                return failNow(s.failureId, s.failure + "; cleanup could not be queued: " + cleanup);
            }
        }
    }

    private static void startVariant(Session s, Minecraft mc) {
        Variant v = s.variant();
        s.validFrames = 0; s.lastFrameTick = -1; s.observation = null; s.renderFailure = null;
        s.note = "Waiting for native team property, server NBT and automatic raycast: " + v.key;
        s.stage(Stage.MUTATING, mc);
        var overlay = IWailaConfig.get().getOverlay();
        var general = IWailaConfig.get().getGeneral();
        general.setDisplayTooltip(true); general.setDisplayBlocks(true);
        general.setDisplayMode(IWailaConfig.DisplayMode.TOGGLE);
        general.setHideFromTabList(false);
        overlay.setAnimation(false); overlay.setFlipMainHand(false); overlay.setAlpha(1);
        overlay.setIconMode(IWailaConfig.IconMode.TOP); overlay.setDisappearingDelay(0);
        overlay.setOverlayPosX(0.5f); overlay.setAnchorX(0.5f);
        overlay.setOverlayPosY(0.98f); overlay.setAnchorY(0);
        // Jade 8591319 clamps its public scale setter to [0.2, 2.0].
        overlay.setOverlayScale(v.narrow() ? 2 : 1);
        mc.options.hideGui = false; mc.options.pauseOnLostFocus = false;
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.options.guiScale().set(2);
        mc.getWindow().setWindowed(v.narrow() ? 640 : 960, v.narrow() ? 480 : 720);
        mc.resizeDisplay();
        s.pending = s.server.submit(() -> {
            var team = FTBTeamsAPI.api().getManager().getTeamByID(s.team).orElseThrow();
            team.setProperty(TeamProperties.DISPLAY_NAME, v.name);
            team.markDirty();
            dev.ftb.mods.ftbteams.net.UpdatePropertiesResponseMessage.oneProperty(team.getId(), TeamProperties.DISPLAY_NAME, v.name).sendToAll(s.server);
            TeamMailboxBinding.INSTANCE.updateMarker(s.saved.level, s.pos, v.marker(s.team));
            return serverState(s);
        });
    }

    private static void aim(Session s, Minecraft mc) {
        Vec3 eye = mc.player.getEyePosition();
        var shape = mc.level.getBlockState(s.pos).getShape(mc.level, s.pos);
        for (var box : shape.toAabbs()) {
            Vec3 target = box.getCenter().add(s.pos.getX(), s.pos.getY(), s.pos.getZ());
            var hit = mc.level.clip(new ClipContext(eye, target, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
            if (!s.pos.equals(hit.getBlockPos())) continue;
            Vec3 delta = target.subtract(eye);
            float yaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90;
            float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)));
            mc.player.setYRot(yaw); mc.player.yRotO = yaw;
            mc.player.setXRot(pitch); mc.player.xRotO = pitch;
            return;
        }
        throw new IllegalStateException("Real player cannot see the outsider mailbox upper-half outline at " + s.pos);
    }

    private static void registerObserver() {
        if (registered) return;
        var registration = WailaClientRegistration.instance();
        registration.addAfterRenderCallback(Integer.MAX_VALUE, ContactQuestsJadeAssertions::afterRender);
        registration.afterRenderCallback.sort();
        registered = true;
    }

    private static void afterRender(ITooltip rendered, Rect2i rect, GuiGraphics graphics, Accessor<?> accessor) {
        Session s = session;
        Minecraft mc = Minecraft.getInstance();
        if (s == null || finished || s.stage != Stage.OBSERVING || s.observation != null || s.renderFailure != null
                || mc.player == null || mc.player.tickCount - s.stageTick < 30 || s.lastFrameTick == mc.player.tickCount) return;
        if (!(accessor instanceof BlockAccessor block) || !s.pos.equals(block.getPosition())) {
            s.note = "Automatic Jade accessor is not the actual outsider mailbox: " + accessor;
            return;
        }
        try {
            var team = FTBTeamsAPI.api().getClientManager().getTeamByID(s.team).orElse(null);
            var data = block.getServerData();
            UUID marker = data.hasUUID(TEAM_KEY) ? data.getUUID(TEAM_KEY) : null;
            if (team == null || !s.variant().name.equals(team.getProperty(TeamProperties.DISPLAY_NAME))
                    || !Objects.equals(marker, s.variant().marker(s.team)) || !block.isServerConnected() || !block.verifyData(data)) {
                s.note = "Waiting for real FTB/Jade server sync; team=" + (team == null ? "missing" : team.getName().getString())
                        + ", marker=" + marker + ", connected=" + block.isServerConnected();
                return;
            }
            TooltipRenderer renderer = WailaTickHandler.instance().tooltipRenderer;
            require(rendered instanceof Tooltip && renderer != null && rendered == renderer.getTooltip()
                            && accessor == ObjectDataCenter.get(), "Callback did not observe Jade's automatic root/accessor");
            require(mc.hitResult instanceof BlockHitResult hit && s.pos.equals(hit.getBlockPos()),
                    "Real Minecraft crosshair does not target the tested mailbox");
            require(!(block.getBlockEntity() instanceof Nameable), "Native Nameable title requires separate investigation");
            Tooltip root = (Tooltip) rendered;
            var providers = WailaClientRegistration.instance().getBlockProviders(block.getBlock(), provider -> true);
            require(providers.contains(MailboxComponentProvider.INSTANCE), "Production mailbox provider is not registered");
            Tooltip direct = new Tooltip();
            MailboxComponentProvider.INSTANCE.appendTooltip(direct, block, IWailaConfig.get().getPlugin());
            List<IElement> own = root.get(UID);
            List<IElement> directLines = direct.get(UID);
            Component expected = expected(s);
            int budget = widthBudget(mc);
            verifyText(own, expected, budget, s.variant());
            verifyText(directLines, expected, budget, s.variant());
            require(textLines(own).equals(textLines(directLines)), "Automatic provider output differs from actual appendTooltip output");
            String blockName = Component.translatable(block.getBlock().getDescriptionId()).getString();
            require(root.get(Identifiers.CORE_OBJECT_NAME).stream().anyMatch(e -> Objects.toString(e.getCachedMessage(), "").contains(blockName)),
                    "Native Jade block title is absent or replaced: " + blockName);
            for (var line : root.lines) {
                for (var align : IElement.Align.values()) {
                    for (var element : line.getAlignedElements(align)) {
                        require(UID.equals(element.getTag()) || !Objects.toString(element.getCachedMessage(), "").contains(s.variant().name),
                                "Another provider repeats the team name: " + describe(element));
                    }
                }
            }
            JsonObject evidence = geometry(renderer, rect, mc);
            evidence.addProperty("variant", s.variant().key);
            evidence.addProperty("expectedText", expected == null ? "" : expected.getString());
            evidence.addProperty("ownLines", own.size()); evidence.addProperty("lineBudget", budget);
            evidence.addProperty("providers", providers.stream().map(p -> p.getUid() + "=" + p.getClass().getName()).toList().toString());
            evidence.addProperty("nativeTitle", blockName);
            if (s.variant().narrow()) {
                require(mc.getWindow().getScreenWidth() == 640 && mc.getWindow().getScreenHeight() == 480
                                && mc.options.guiScale().get() == 2 && mc.getWindow().getGuiScaledWidth() == 320
                                && IWailaConfig.get().getOverlay().getOverlayScale() == 2
                                && budget > 0 && budget < 120 && renderer.getRealScale() >= 1,
                        "Narrow-window/large-Jade-scale boundary was not actually exercised");
            }
            s.lastFrameTick = mc.player.tickCount;
            if (++s.validFrames < 2) return;
            evidence.addProperty("distinctRenderedTicks", s.validFrames);
            saveEvidence(s, graphics, evidence, "");
            s.observation = evidence;
        } catch (Exception | LinkageError error) {
            s.note = s.variant().key + ": " + error;
            s.renderFailure = error;
            try {
                JsonObject evidence = new JsonObject(); evidence.addProperty("failure", s.note);
                evidence.addProperty("nativeTooltip", rendered.getMessage());
                saveEvidence(s, graphics, evidence, "-failed");
            } catch (Exception screenshotError) { error.addSuppressed(screenshotError); }
        }
    }

    private static Component expected(Session s) {
        if (s.variant().key.equals("unbound")) return null;
        if (s.variant().key.equals("unknown")) {
            require(FTBTeamsAPI.api().getClientManager().getTeamByID(UNKNOWN).isEmpty(), "Unknown UUID exists on client");
            Component text = Component.translatable("jade.contactquests.unknown_team").withStyle(ChatFormatting.RED);
            String fixed = activeChinese() ? "队伍：未知" : "Team: Unknown";
            require(text.getString().equals(fixed), "Unknown must be exactly " + fixed + " without UUID");
            return text;
        }
        var team = FTBTeamsAPI.api().getClientManager().getTeamByID(s.team).orElseThrow();
        Component text = Component.translatable("jade.contactquests.team_owner", team.getName()).withStyle(ChatFormatting.GOLD);
        if (s.variant().key.equals("short")) {
            String fixed = activeChinese() ? "队伍：CQ Mail Team" : "Team: CQ Mail Team";
            require(text.getString().equals(fixed), "Short name must be exactly " + fixed);
        }
        return text;
    }

    private static boolean activeChinese() {
        String template = Language.getInstance().getOrDefault("jade.contactquests.team_owner", "");
        require(template.equals("队伍：%s") || template.equals("Team: %s"), "Expected the fixed Chinese or English active Jade label: " + template);
        return template.equals("队伍：%s");
    }

    private static void checkTranslations(Minecraft mc) throws Exception {
        for (String locale : List.of("en_us", "zh_cn")) {
            var location = new ResourceLocation("contactquests", "lang/" + locale + ".json");
            try (var reader = new InputStreamReader(mc.getResourceManager().getResource(location).orElseThrow().open(), StandardCharsets.UTF_8)) {
                var json = JsonParser.parseReader(reader).getAsJsonObject();
                boolean zh = locale.equals("zh_cn");
                require(json.get("jade.contactquests.team_owner").getAsString().equals(zh ? "队伍：%s" : "Team: %s")
                                && json.get("jade.contactquests.unknown_team").getAsString().equals(zh ? "队伍：未知" : "Team: Unknown"),
                        "Packaged " + locale + " Jade labels changed or contain an unknown UUID placeholder");
            }
        }
    }

    private static int widthBudget(Minecraft mc) {
        var overlay = IWailaConfig.get().getOverlay();
        var theme = overlay.getTheme();
        return Math.max(1, Math.min(220, (int) Math.floor((mc.getWindow().getGuiScaledWidth() - 8)
                / Math.max(0.1f, overlay.getOverlayScale())) - theme.padding[3]
                - theme.padding[1] - (overlay.shouldShowIcon() ? 32 : 0) - 2));
    }

    private static List<Glyph> glyphs(FormattedText text) {
        List<Glyph> result = new ArrayList<>();
        text.visit((style, part) -> {
            part.codePoints().forEach(cp -> result.add(new Glyph(cp, style)));
            return Optional.<Void>empty();
        }, Style.EMPTY);
        return result;
    }

    private static List<List<Glyph>> textLines(List<IElement> elements) {
        List<List<Glyph>> result = new ArrayList<>();
        for (var element : elements) {
            require(element instanceof TextElement, "Mailbox line is not native TextElement: " + describe(element));
            result.add(glyphs(((TextElement) element).text));
        }
        return result;
    }

    private static void verifyText(List<IElement> elements, Component expected, int budget, Variant v) {
        if (expected == null) { require(elements.isEmpty(), "Unbound mailbox still displays team information"); return; }
        require(!elements.isEmpty(), "Mailbox team lines are missing");
        require(v.wraps() ? elements.size() > 1 : elements.size() == 1, "Unexpected line count for " + v.key + ": " + elements.size());
        List<Glyph> original = glyphs(expected);
        int offset = 0;
        var lines = textLines(elements);
        for (int i = 0; i < lines.size(); i++) {
            List<Glyph> line = lines.get(i);
            require(!line.isEmpty(), "Empty mailbox line");
            // Vanilla may consume whitespace at a wrap, never inside a displayed line.
            if (i > 0) while (offset < original.size() && Character.isWhitespace(original.get(offset).codepoint)
                    && !original.get(offset).equals(line.get(0))) offset++;
            for (Glyph actual : line) {
                require(offset < original.size() && original.get(offset).equals(actual),
                        "Lost/changed character or resolved style (including team color, click and hover) at " + offset + " of " + v.key);
                offset++;
            }
            var element = (TextElement) elements.get(i);
            require(UID.equals(element.getTag()) && Minecraft.getInstance().font.width(element.text) <= budget,
                    "Mailbox line exceeds its font-width budget or lost provider UID: " + describe(element));
        }
        require(offset == original.size(), "Mailbox name suffix was lost: consumed " + offset + "/" + original.size());
    }

    private static JsonObject geometry(TooltipRenderer renderer, Rect2i actual, Minecraft mc) {
        Rect2i expected = Objects.requireNonNull(renderer.getRealRect());
        float scale = renderer.getRealScale();
        int width = mc.getWindow().getGuiScaledWidth(), height = mc.getWindow().getGuiScaledHeight();
        String whole = renderer.getTooltip().getMessage();
        require(Float.isFinite(scale) && scale > 0, "Invalid native overlay scale");
        require(actual.getX() == expected.getX() && actual.getY() == expected.getY()
                        && actual.getWidth() == expected.getWidth() && actual.getHeight() == expected.getHeight(),
                "Native overlay did not reach its nonanimated rectangle");
        require(actual.getX() >= 0 && actual.getY() >= 0 && actual.getWidth() > 0 && actual.getHeight() > 0
                        && actual.getX() + actual.getWidth() <= width && actual.getY() + actual.getHeight() <= height,
                "Whole native Jade overlay exceeds " + width + "x" + height + ": " + rectangle(actual) + "; providers/text=" + whole);
        require(renderer.getIcon() != null, "Native block icon is missing");
        float localWidth = actual.getWidth() / scale;
        float localHeight = actual.getHeight() / scale;
        require(Math.abs(actual.getWidth() - renderer.getSize().x * scale) <= 2
                        && Math.abs(actual.getHeight() - renderer.getSize().y * scale) <= 2,
                "Rendered native rectangle disagrees with cached layout size");
        JsonArray elements = new JsonArray();
        inspectRenderer(renderer, 0, 0, localWidth, localHeight, Math.max(1, 1 / scale), elements, "root");
        JsonObject result = new JsonObject();
        result.addProperty("guiWidth", width); result.addProperty("guiHeight", height);
        result.addProperty("windowWidth", mc.getWindow().getScreenWidth()); result.addProperty("windowHeight", mc.getWindow().getScreenHeight());
        result.addProperty("requestedJadeScale", IWailaConfig.get().getOverlay().getOverlayScale());
        result.addProperty("renderedJadeScale", scale); result.addProperty("nativeRect", rectangle(actual));
        result.addProperty("nativeTooltip", whole); result.add("elements", elements);
        return result;
    }

    /** Follows Jade 8479276 TooltipRenderer/Line placement, including native icon and other providers. */
    private static void inspectRenderer(TooltipRenderer renderer, float x, float y, float width, float height, float epsilon,
                                        JsonArray evidence, String path) {
        float left = renderer.getPadding(3), right = width - renderer.getPadding(1);
        float top = renderer.getPadding(0);
        IElement icon = renderer.getIcon();
        if (icon != null) {
            var size = icon.getCachedSize(); var shift = icon.getTranslation();
            float iconLeft = IWailaConfig.get().getOverlay().getTheme().padding[3];
            inspectElement(icon, x + iconLeft + shift.x, y + top + shift.y, size.x, size.y, x, y, width, height, epsilon, evidence, path + "/icon");
        }
        var lines = renderer.getTooltip().lines;
        for (int row = 0; row < lines.size(); row++) {
            var line = lines.get(row);
            require(line.getSize().x <= right - left + epsilon, "Native row exceeds content width: " + path + "/" + row);
            float lw = groupWidth(line, IElement.Align.LEFT), rw = groupWidth(line, IElement.Align.RIGHT);
            int totalElements = line.getAlignedElements(IElement.Align.LEFT).size() + line.getAlignedElements(IElement.Align.RIGHT).size();
            for (var align : IElement.Align.values()) {
                var group = line.getAlignedElements(align);
                float cursor = align == IElement.Align.LEFT ? left : right - rw;
                for (int i = 0; i < group.size(); i++) {
                    var element = group.get(i); var size = element.getCachedSize(); var shift = element.getTranslation();
                    float renderedWidth = align == IElement.Align.LEFT && group.size() == totalElements && i == group.size() - 1
                            ? right - cursor : size.x;
                    inspectElement(element, x + cursor + shift.x, y + top + shift.y, renderedWidth, line.getSize().y,
                            x, y, width, height, epsilon, evidence, path + "/" + row + "/" + align + "/" + i);
                    cursor += size.x;
                }
            }
            top += line.getSize().y;
        }
    }

    private static float groupWidth(Tooltip.Line line, IElement.Align align) {
        float width = 0;
        for (var element : line.getAlignedElements(align)) width += element.getCachedSize().x;
        return width;
    }

    private static void inspectElement(IElement element, float x, float y, float allocatedWidth, float allocatedHeight,
                                       float parentX, float parentY, float parentWidth, float parentHeight, float epsilon,
                                       JsonArray evidence, String path) {
        var size = element.getCachedSize();
        float actualWidth = element instanceof TextElement text ? Minecraft.getInstance().font.width(text.text) : size.x;
        require(Float.isFinite(actualWidth) && actualWidth >= 0 && size.y >= 0
                        && actualWidth <= size.x + epsilon && actualWidth <= allocatedWidth + epsilon
                        && x >= parentX - epsilon && y >= parentY - epsilon
                        && x + actualWidth <= parentX + parentWidth + epsilon && y + size.y <= parentY + parentHeight + epsilon,
                "Native element overflows/underreports its rendered extent at " + path + ": " + describe(element));
        JsonObject entry = new JsonObject();
        entry.addProperty("path", path); entry.addProperty("element", describe(element));
        entry.addProperty("x", x); entry.addProperty("y", y); entry.addProperty("width", actualWidth); entry.addProperty("height", size.y);
        evidence.add(entry);
    }

    private static String describe(IElement element) {
        return element.getTag() + " " + element.getClass().getSimpleName() + " " + element.getCachedSize()
                + " text=" + element.getCachedMessage();
    }

    private static String rectangle(Rect2i rect) {
        return rect.getX() + "," + rect.getY() + " " + rect.getWidth() + "x" + rect.getHeight();
    }

    private static void saveEvidence(Session s, GuiGraphics graphics, JsonObject evidence, String suffix) throws Exception {
        Files.createDirectories(s.evidence);
        String name = "jade-mailbox-" + s.variant().key + suffix;
        Path screenshot = s.evidence.resolve(name + ".png");
        graphics.flush();
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) { image.writeToFile(screenshot); }
        evidence.addProperty("screenshot", screenshot.toString());
        Files.writeString(s.evidence.resolve(name + ".json"), new GsonBuilder().setPrettyPrinting().create().toJson(evidence));
    }

    private static ServerState serverState(Session s) {
        var player = Objects.requireNonNull(s.server.getPlayerList().getPlayer(s.player));
        var level = s.saved == null ? player.serverLevel() : s.saved.level;
        var be = level.getBlockEntity(s.pos);
        require(be instanceof IMailboxTeamAccessor, "Outsider mailbox block entity disappeared");
        var team = FTBTeamsAPI.api().getManager().getTeamByID(s.team).orElseThrow();
        var contents = PlatformHelper.getMailboxContents(s.team);
        List<String> mail = new ArrayList<>();
        for (int i = 0; i < contents.getContainerSize(); i++) {
            var item = contents.getItem(i);
            mail.add(item.isEmpty() ? "EMPTY" : item.save(new CompoundTag()).toString());
        }
        return new ServerState(level, team.getProperty(TeamProperties.DISPLAY_NAME),
                ((IMailboxTeamAccessor) be).contactQuests$getTeamId(), PlatformHelper.getMailboxOwner(level.dimension(), s.pos),
                PlatformHelper.getMailboxPos(s.team), List.copyOf(mail), player.getYRot(), player.getXRot());
    }

    private static void beginRestore(Session s, Minecraft mc) {
        s.stage = Stage.RESTORING;
        s.stageTime = System.nanoTime(); s.stageTick = mc.player == null ? 0 : mc.player.tickCount;
        // Queue on the same server after any outstanding mutation, even after a failed setup.
        s.pending = s.server.submit(() -> {
            if (s.saved == null) return null;
            var team = FTBTeamsAPI.api().getManager().getTeamByID(s.team).orElseThrow();
            team.setProperty(TeamProperties.DISPLAY_NAME, s.saved.name); team.markDirty();
            dev.ftb.mods.ftbteams.net.UpdatePropertiesResponseMessage.oneProperty(team.getId(), TeamProperties.DISPLAY_NAME, s.saved.name).sendToAll(s.server);
            TeamMailboxBinding.INSTANCE.updateMarker(s.saved.level, s.pos, s.saved.marker);
            var player = s.server.getPlayerList().getPlayer(s.player);
            if (player != null) { player.setYRot(s.saved.yaw); player.setXRot(s.saved.pitch); }
            return serverState(s);
        });
        s.client.restore(mc);
    }

    private static boolean finishRestore(Session s, Minecraft mc) {
        require(!expired(s.stageTime, s.stageTick, mc, 15, 200), "Jade cleanup timed out; " + s.failure);
        if (!s.pending.isDone()) return false;
        ServerState restored = s.pending.join();
        if (mc.player == null || mc.player.tickCount - s.stageTick < 20) return false;
        if (s.saved != null) {
            var team = FTBTeamsAPI.api().getClientManager().getTeamByID(s.team).orElse(null);
            if (team == null || !s.saved.name.equals(team.getProperty(TeamProperties.DISPLAY_NAME))) return false;
            require(restored != null && s.saved.name.equals(restored.name) && Objects.equals(s.saved.marker, restored.marker)
                            && Objects.equals(s.saved.owner, restored.owner) && Objects.equals(s.saved.location, restored.location)
                            && s.saved.mail.equals(restored.mail), "Restoration changed original owner/location/exact mailbox slots");
        }
        require(s.client.matches(mc), "Client window/view/Jade settings were not restored");
        ContactQuestsClientTestController.assertCase(PREFIX + "restored", true,
                "Original native FTB name, mailbox marker/owner/location/exact slots and client window/view/Jade settings restored");
        if (s.failure != null) return failNow(s.failureId, s.failure + "; cleanup completed; last observation: " + s.note);
        require(s.index == VARIANTS.size(), "Not all Jade variants completed");
        ContactQuestsClientTestController.assertCase(PREFIX + "native_overlay", true,
                "Seven real automatic Jade overlays rendered on two distinct ticks each; native title/icon/all provider elements fit; per-case PNG and geometry JSON saved");
        finished = true; session = null;
        return true;
    }

    /** Only settings changed by this fixture are saved; no Jade/config files are written. */
    private static final class ClientState {
        final int width, height, x, y, guiScale;
        final boolean fullscreen, hideGui, pauseOnLostFocus, display, blocks, hideTab, animation, flip;
        final CameraType camera;
        final IWailaConfig.DisplayMode displayMode;
        final IWailaConfig.IconMode iconMode;
        final float yaw, pitch, scale, posX, posY, anchorX, anchorY, alpha, delay;

        ClientState(Minecraft mc) {
            var window = mc.getWindow(); var overlay = IWailaConfig.get().getOverlay(); var general = IWailaConfig.get().getGeneral();
            width = window.getScreenWidth(); height = window.getScreenHeight(); x = window.getX(); y = window.getY();
            fullscreen = window.isFullscreen(); guiScale = mc.options.guiScale().get();
            hideGui = mc.options.hideGui; pauseOnLostFocus = mc.options.pauseOnLostFocus; camera = mc.options.getCameraType();
            yaw = mc.player.getYRot(); pitch = mc.player.getXRot();
            display = general.shouldDisplayTooltip(); blocks = general.getDisplayBlocks(); displayMode = general.getDisplayMode();
            hideTab = general.shouldHideFromTabList();
            animation = overlay.getAnimation(); flip = overlay.getFlipMainHand(); iconMode = overlay.getIconMode();
            scale = overlay.getOverlayScale(); posX = overlay.getOverlayPosX(); posY = overlay.getOverlayPosY();
            anchorX = overlay.getAnchorX(); anchorY = overlay.getAnchorY(); alpha = overlay.getAlpha(); delay = overlay.getDisappearingDelay();
        }

        void restore(Minecraft mc) {
            var overlay = IWailaConfig.get().getOverlay(); var general = IWailaConfig.get().getGeneral();
            general.setDisplayTooltip(display); general.setDisplayBlocks(blocks); general.setDisplayMode(displayMode);
            general.setHideFromTabList(hideTab);
            overlay.setAnimation(animation); overlay.setFlipMainHand(flip); overlay.setIconMode(iconMode); overlay.setOverlayScale(scale);
            overlay.setOverlayPosX(posX); overlay.setOverlayPosY(posY); overlay.setAnchorX(anchorX); overlay.setAnchorY(anchorY);
            overlay.setAlpha(alpha); overlay.setDisappearingDelay(delay);
            mc.options.hideGui = hideGui; mc.options.pauseOnLostFocus = pauseOnLostFocus; mc.options.setCameraType(camera);
            mc.options.guiScale().set(guiScale);
            mc.getWindow().setWindowed(width, height);
            if (fullscreen) mc.getWindow().toggleFullScreen();
            else GLFW.glfwSetWindowPos(mc.getWindow().getWindow(), x, y);
            mc.resizeDisplay();
            if (mc.player != null) {
                mc.player.setYRot(yaw); mc.player.yRotO = yaw; mc.player.setXRot(pitch); mc.player.xRotO = pitch;
            }
        }

        boolean matches(Minecraft mc) {
            var overlay = IWailaConfig.get().getOverlay(); var general = IWailaConfig.get().getGeneral();
            return mc.getWindow().isFullscreen() == fullscreen && mc.getWindow().getScreenWidth() == width
                    && mc.getWindow().getScreenHeight() == height && mc.options.guiScale().get() == guiScale
                    && mc.options.hideGui == hideGui && mc.options.pauseOnLostFocus == pauseOnLostFocus && mc.options.getCameraType() == camera
                    && Math.abs(mc.player.getYRot() - yaw) < 0.01 && Math.abs(mc.player.getXRot() - pitch) < 0.01
                    && general.shouldDisplayTooltip() == display && general.getDisplayBlocks() == blocks && general.getDisplayMode() == displayMode
                    && general.shouldHideFromTabList() == hideTab
                    && overlay.getAnimation() == animation && overlay.getFlipMainHand() == flip && overlay.getIconMode() == iconMode
                    && overlay.getOverlayScale() == scale && overlay.getOverlayPosX() == posX && overlay.getOverlayPosY() == posY
                    && overlay.getAnchorX() == anchorX && overlay.getAnchorY() == anchorY && overlay.getAlpha() == alpha
                    && overlay.getDisappearingDelay() == delay;
        }
    }

    private static boolean expired(long start, int tick, Minecraft mc, int seconds, int ticks) {
        return System.nanoTime() - start > seconds * 1_000_000_000L || mc.player != null && mc.player.tickCount - tick > ticks;
    }

    private static boolean failNow(String id, String message) {
        finished = true; session = null;
        ContactQuestsClientTestController.assertCase(id, false, "Jade mailbox: " + message);
        return true;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private ContactQuestsJadeAssertions() {}
}
