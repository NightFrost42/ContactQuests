package com.creeperyang.contactquests.test;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Retains Minecraft's allocator reflection when test LWJGL uses JVM boot modules. */
public final class WindowsLwjglAccessAgent {
    private WindowsLwjglAccessAgent() {
    }

    public static void premain(String arguments, Instrumentation instrumentation) {
        if (!Boolean.getBoolean("contactquests.test.client")) {
            return;
        }
        Module lwjgl = ModuleLayer.boot().findModule("org.lwjgl").orElseThrow();
        AtomicBoolean opened = new AtomicBoolean();
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(Module module, ClassLoader loader, String className,
                                    Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
                                    byte[] classfileBuffer) {
                if (module != null && "minecraft".equals(module.getName()) && opened.compareAndSet(false, true)) {
                    instrumentation.redefineModule(lwjgl, Set.of(), Map.of(),
                            Map.of("org.lwjgl.system", Set.of(module)), Set.of(), Map.of());
                    System.out.println("[contactquests_test] Opened LWJGL allocator reflection for Minecraft");
                }
                return null;
            }
        });
    }
}
