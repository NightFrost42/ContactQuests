package com.creeperyang.contactquests.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;

import java.lang.reflect.Method;

/** Moves Minecraft's internal virtual mouse pointer without touching the OS cursor. */
final class ContactQuestsTestVirtualPointer {
    private static Method onMoveMethod;

    private ContactQuestsTestVirtualPointer() {
    }

    static void moveRaw(Minecraft mc, double rawX, double rawY) throws Exception {
        if (onMoveMethod == null) {
            Method method = MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class);
            method.setAccessible(true);
            onMoveMethod = method;
        }
        onMoveMethod.invoke(mc.mouseHandler, mc.getWindow().getWindow(), rawX, rawY);
    }

    static void moveScaled(Minecraft mc, double guiX, double guiY) throws Exception {
        var window = mc.getWindow();
        double rawX = guiX * window.getScreenWidth() / window.getGuiScaledWidth();
        double rawY = guiY * window.getScreenHeight() / window.getGuiScaledHeight();
        moveRaw(mc, rawX, rawY);
        if (mc.screen != null) {
            mc.screen.mouseMoved(guiX, guiY);
        }
    }
}
