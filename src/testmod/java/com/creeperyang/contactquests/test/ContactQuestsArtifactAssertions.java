package com.creeperyang.contactquests.test;

import com.creeperyang.contactquests.ContactQuests;
import com.google.gson.JsonObject;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

final class ContactQuestsArtifactAssertions {
    private static JsonObject cached;

    private ContactQuestsArtifactAssertions() {}

    static JsonObject inspect() {
        if (cached != null) return cached;
        cached = new JsonObject();
        String expected = System.getProperty("contactquests.test.expectedJar", "");
        cached.addProperty("expectedJar", expected);
        try {
            String source = ContactQuests.class.getProtectionDomain().getCodeSource().getLocation().toString();
            String resource = ContactQuests.class.getResource("ContactQuests.class").toString();
            cached.addProperty("codeSource", source);
            cached.addProperty("classResource", resource);
            if (!expected.isBlank()) {
                var path = Path.of(expected).toAbsolutePath().normalize();
                String normalized = path.toString().replace('\\', '/');
                String decoded = URLDecoder.decode(source + " " + resource, StandardCharsets.UTF_8).replace('\\', '/');
                if (System.getProperty("os.name").startsWith("Windows")) {
                    normalized = normalized.toLowerCase(Locale.ROOT);
                    decoded = decoded.toLowerCase(Locale.ROOT);
                }
                String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
                cached.addProperty("sha256", digest);
                cached.addProperty("matchesExpected", decoded.contains(normalized)
                        && digest.equals(System.getProperty("contactquests.test.expectedJarSha256")));
            }
        } catch (Exception error) {
            cached.addProperty("matchesExpected", false);
            cached.addProperty("error", error.toString());
        }
        return cached;
    }
}
