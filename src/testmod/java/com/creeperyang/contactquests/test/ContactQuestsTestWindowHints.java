package com.creeperyang.contactquests.test;

import org.lwjgl.glfw.GLFW;

/** Applies unfocused GLFW window hints for automated client tests outside Mixin bytecode. */
public final class ContactQuestsTestWindowHints {
    private ContactQuestsTestWindowHints() {
    }

    public static void applyUnfocusedHints() {
        if (Boolean.getBoolean("contactquests.test.client")) {
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
        }
    }
}
