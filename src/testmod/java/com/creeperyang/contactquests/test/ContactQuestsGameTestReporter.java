package com.creeperyang.contactquests.test;

import com.google.gson.GsonBuilder;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.TestReporter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class ContactQuestsGameTestReporter implements TestReporter {
    private final Map<String, Object> cases = new LinkedHashMap<>();

    @Override
    public void onTestFailed(GameTestInfo info) {
        record(info, false);
    }

    @Override
    public void onTestSuccess(GameTestInfo info) {
        record(info, true);
    }

    private void record(GameTestInfo info, boolean passed) {
        cases.put(info.getTestName(), Map.of("success", passed,
                "message", info.getError() == null ? "" : info.getError().toString(),
                "durationMs", info.getRunTime()));
        write(false);
    }

    @Override
    public void finish() {
        write(true);
    }

    private void write(boolean finished) {
        String target = System.getProperty("contactquests.test.gameTestResult");
        if (target == null) return;
        try {
            Path path = Path.of(target);
            Files.createDirectories(path.toAbsolutePath().getParent());
            ContactQuestsTestEvidence.write(path, new GsonBuilder().setPrettyPrinting().create().toJson(
                    Map.of("finished", finished, "cases", cases)));
        } catch (Exception error) {
            throw new IllegalStateException("Could not write GameTest evidence", error);
        }
    }
}
