package com.creeperyang.contactquests.test;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Publish complete evidence snapshots for the runner and the separate native client. */
final class ContactQuestsTestEvidence {
    private ContactQuestsTestEvidence() {}

    static void write(Path output, String text) throws IOException {
        Path absolute = output.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        Path pending = Files.createTempFile(absolute.getParent(), absolute.getFileName() + ".", ".pending");
        try {
            Files.writeString(pending, text);
            try {
                Files.move(pending, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(pending, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(pending);
        }
    }
}
