package com.creeperyang.contactquests.test;

import com.creeperyang.contactquests.bridge.ITeamDataExtension;
import dev.ftb.mods.ftbquests.net.SyncTeamDataMessage;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.util.ProgressChange;
import net.minecraft.server.level.ServerPlayer;

/** Exercise the exact rules saved by the real client editor, without rewriting them. */
public final class ContactQuestsEditorRuleFixtures {
    private ContactQuestsEditorRuleFixtures() {}

    public static void run(ServerPlayer player, String phase) {
        String id = "server.editor.rules_effect." + phase;
        try {
            var file = ServerQuestFile.INSTANCE;
            var quest = file.getQuest(ContactQuestsTestServerController.getEditorQuestId());
            var mutex = file.getQuest(ContactQuestsTestServerController.getEditorMutexId());
            if (quest == null || mutex == null || mutex.getTasks().size() != 1
                    || !ContactQuestsEditorAssertions.questRules(quest, mutex.getId(), player.level().registryAccess())) {
                throw new IllegalStateException("Actual native editor rules were not saved exactly");
            }
            var data = file.getOrCreateTeamData(player);
            var tags = (ITeamDataExtension) data;
            data.setLocked(false);
            switch (phase) {
                case "missing" -> {
                    tags.contactQuests$removeTag("CQEditorRequiredTag");
                    mutex.forceProgressRaw(data, new ProgressChange(file, mutex, player.getUUID()).setReset(true));
                }
                case "unlocked" -> tags.contactQuests$unlockTag("CQEditorRequiredTag");
                case "mutex" -> data.setProgress(mutex.getTasks().iterator().next(), 1);
                case "reset" -> mutex.forceProgressRaw(data, new ProgressChange(file, mutex, player.getUUID()).setReset(true));
                default -> throw new IllegalArgumentException("Unknown editor rules phase: " + phase);
            }
            data.clearCachedProgress();
            boolean expected = phase.equals("unlocked") || phase.equals("reset");
            boolean startable = data.canStartTasks(quest);
            boolean visible = quest.isVisible(data);
            boolean correctSourceState = tags.contactQuests$hasTag("CQEditorRequiredTag") == !phase.equals("missing")
                    && data.isCompleted(mutex) == phase.equals("mutex");
            ContactQuestsTestServerController.recordCase(id, correctSourceState && startable == expected && visible == expected,
                    "Rules saved by native editor: phase=" + phase + ", canStart=" + startable + ", visible=" + visible
                            + "; native mutex completed=" + data.isCompleted(mutex));
            new SyncTeamDataMessage(data, true).sendTo(player);
            file.saveNow();
        } catch (Exception error) {
            ContactQuestsTestServerController.recordCase(id, false, error.toString());
        }
    }
}
