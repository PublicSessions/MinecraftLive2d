package com.ciallo.live2d.client;

import com.ciallo.live2d.config.Live2dConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.SpecialGuiElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;

public class Live2dClient implements ClientModInitializer {

    public static final String MOD_ID = "live2d";

    public static KeyBinding KEY_TOGGLE;
    public static KeyBinding KEY_MODEL;
    public static KeyBinding KEY_EXPRESSION;
    public static KeyBinding KEY_EDIT;

    private Live2dHudRenderer hud;

    @Override
    public void onInitializeClient() {
        MinecraftClient mc = MinecraftClient.getInstance();
        Path configPath = mc.runDirectory.toPath().resolve("config").resolve(MOD_ID + ".json");
        hud = new Live2dHudRenderer(mc, Live2dConfig.load(configPath), configPath);
        new Live2dCommand(hud).register();

        KEY_TOGGLE = registerKey("key.live2d.toggle", GLFW.GLFW_KEY_F9);
        KEY_MODEL = registerKey("key.live2d.model", GLFW.GLFW_KEY_F10);
        KEY_EXPRESSION = registerKey("key.live2d.expression", GLFW.GLFW_KEY_F11);
        KEY_EDIT = registerKey("key.live2d.edit", GLFW.GLFW_KEY_F12);

        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            if (hud == null || !hud.isEnabled()) return;
            hud.render(context, tickCounter.getTickProgress(false));
        });

        SpecialGuiElementRegistry.register(ctx -> new Live2dSpecialGuiElementRenderer(ctx.vertexConsumers(), hud));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (KEY_TOGGLE.wasPressed()) {
                hud.toggleEnabled();
            }
            if (KEY_MODEL.wasPressed()) {
                hud.cycleModel();
            }
            if (KEY_EXPRESSION.wasPressed()) {
                hud.cycleExpression();
            }
            if (KEY_EDIT.wasPressed()) {
                hud.toggleEditMode();
            }
            hud.handleTick();
        });
    }

    private KeyBinding registerKey(String id, int defaultKey) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(
                id, InputUtil.Type.KEYSYM, defaultKey, KeyBinding.Category.MISC));
    }
}