package com.ciallo.live2d.client;

import com.ciallo.live2d.config.Live2dConfig;
import com.ciallo.live2d.cubism.CubismNativeModel;
import com.ciallo.live2d.cubism.CubismNativeRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.WeightedSoundSet;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWScrollCallback;
import org.lwjgl.glfw.GLFWScrollCallbackI;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;

public class Live2dHudRenderer {

    private static final String PREVIEW_SUFFIX = "preview.png";

    private final MinecraftClient mc;
    private final Live2dConfig config;
    private final Path configPath;
    private final Live2dModelLoader modelLoader;
    private final Live2dEventSystem eventSystem;

    private CubismNativeModel model;
    private CubismNativeRenderer renderer;
    private String loadedModel;
    private String loadError;
    private boolean loadAttempted;

    private boolean firstFrame = true;
    private float lastYaw;
    private float lastPitch;
    private float angleX;
    private float angleY;
    private float angleZ;
    private float bodyAngleX;
    private float springX;
    private float springVX;
    private float springY;
    private float springVY;
    private float earSpring;
    private float earVelocity;

    private long nextBlinkAt = System.currentTimeMillis() + 1800L;
    private long blinkStartedAt = -1L;

    private String activeExpression;
    private float activeWeight;
    private float hurtWeight;
    private float deathWeight;
    private long hurtUntil;
    private long deathUntil;
    private long lastExpressionUpdateMs = System.currentTimeMillis();
    private boolean wasHurt;

    private boolean editMode;
    private long lastConfigSave;
    private long lastMoveTick;

    private boolean scrollHooked;
    private GLFWScrollCallbackI scrollHook;
    private GLFWScrollCallback scrollPrev;
    private double pendingScroll;
    private boolean chatDragActive;
    private double lastDragX;
    private double lastDragY;

    private float soundActivity;
    private boolean volumeHigh;

    public Live2dHudRenderer(MinecraftClient mc, Live2dConfig config, Path configPath) {
        this.mc = mc;
        this.config = config;
        this.configPath = configPath;
        this.modelLoader = new Live2dModelLoader(mc);
        this.eventSystem = new Live2dEventSystem(this, mc, config);
        try {
            mc.getSoundManager().registerListener(this::onSoundPlayed);
        } catch (Throwable t) {
            System.err.println("[Live2D] failed to register sound listener: " + t);
        }
    }

    public String getLoadError() {
        return loadError;
    }

    public boolean isEditMode() {
        return editMode;
    }

    public CubismNativeRenderer getCubismRenderer() {
        return renderer;
    }

    public List<String> getAvailableModels() {
        return modelLoader.getAvailableModels();
    }

    public List<String> getAvailableMotions() {
        return model == null ? List.of() : new ArrayList<>(model.getMotions().keySet());
    }

    public List<String> getAvailableExpressions() {
        return model == null ? List.of() : new ArrayList<>(model.getExpressions().keySet());
    }

    public Map<String, Live2dConfig.EventAction> getEvents() {
        return config.events;
    }

    public void toggleEnabled() {
        config.enabled = !config.enabled;
        saveConfig();
    }

    public void cycleModel() {
        List<String> models = getAvailableModels();
        if (models.isEmpty()) {
            return;
        }
        int idx = models.indexOf(config.model);
        setModel(models.get((idx + 1) % models.size()));
    }

    public void setModel(String name) {
        if (name == null || name.isBlank() || !getAvailableModels().contains(name)) {
            return;
        }
        config.model = name;
        loadAttempted = false;
        unloadModel();
        saveConfig();
    }

    public void setSize(int height) {
        config.size = MathHelper.clamp(height, 40, 800);
        saveConfig();
    }

    public void setPosition(int x, int y) {
        config.posX = x;
        config.posY = y;
        saveConfig();
    }

    public void movePosition(int dx, int dy) {
        config.posX += dx;
        config.posY += dy;
        saveConfig();
    }

    public void setSwayStrength(float value) {
        config.swayStrength = value;
        saveConfig();
    }

    public void setSmoothing(float value) {
        config.smoothing = value;
        saveConfig();
    }

    public void setFollowPitch(boolean value) {
        config.followPitch = value;
        saveConfig();
    }

    public void setBlinkEnabled(boolean value) {
        config.blinkEnabled = value;
        saveConfig();
    }

    public void setMasksEnabled(boolean value) {
        config.masksEnabled = value;
        saveConfig();
    }

    public void setActiveExpression(String name) {
        if (name == null || name.equalsIgnoreCase("normal") || name.equalsIgnoreCase("off") || name.equalsIgnoreCase("none")) {
            activeExpression = null;
        } else {
            activeExpression = name;
        }
        saveConfig();
    }

    public void setEnabled(boolean value) {
        config.enabled = value;
        saveConfig();
    }

    public boolean isEnabled() {
        return config.enabled;
    }

    public String getModelName() {
        return config.model;
    }

    public String getActiveExpression() {
        return activeExpression;
    }

    public Live2dConfig getConfig() {
        return config;
    }

    public String getStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("Enabled: ").append(config.enabled);
        sb.append(" | Model: ").append(config.model);
        sb.append(" | Size: ").append(config.size);
        sb.append(" | Pos: ").append(config.posX).append(",").append(config.posY);
        sb.append(" | Sway: ").append(String.format(java.util.Locale.ROOT, "%.2f", config.swayStrength));
        sb.append(" | Smoothing: ").append(String.format(java.util.Locale.ROOT, "%.2f", config.smoothing));
        sb.append(" | FollowPitch: ").append(config.followPitch);
        sb.append(" | Blink: ").append(config.blinkEnabled);
        sb.append(" | SoundBlink: ").append(config.soundBlinkEnabled);
        sb.append(" | ChatDrag: ").append(config.chatDragEnabled);
        sb.append(" | Expression: ").append(activeExpression == null ? "normal" : activeExpression);
        sb.append(" | SoundActivity: ").append(String.format(java.util.Locale.ROOT, "%.2f", soundActivity));
        if (model == null) {
            sb.append(" | [LOAD ERROR] ").append(loadError != null ? loadError : "");
        }
        return sb.toString();
    }

    public void cycleExpression() {

    }

    public void toggleEditMode() {
        editMode = !editMode;
        saveConfig();
    }

    public void setSoundBlinkEnabled(boolean value) {
        config.soundBlinkEnabled = value;
        saveConfig();
    }

    public void setSoundBlinkThreshold(float value) {
        config.soundBlinkThreshold = MathHelper.clamp(value, 0.0f, 1.0f);
        saveConfig();
    }

    public void setChatDragEnabled(boolean value) {
        config.chatDragEnabled = value;
        saveConfig();
    }

    public void setEventAction(String event, String type, String target, float value, float duration, float fade) {
        if (event == null || event.isBlank()) {
            return;
        }
        Live2dConfig.EventAction action = new Live2dConfig.EventAction();
        action.type = type == null || type.isBlank() ? "expression" : type;
        action.target = target == null ? "" : target;
        action.value = value;
        action.duration = duration;
        action.fade = fade;
        config.events.put(event, action);
        saveConfig();
    }

    public void removeEventAction(String event) {
        config.events.remove(event);
        saveConfig();
    }

    public void triggerAction(Live2dConfig.EventAction action) {
        if (model == null || action == null || action.target == null || action.target.isEmpty()) {
            return;
        }
        String type = action.type == null ? "expression" : action.type;
        switch (type) {
            case "motion" -> {
                if (model.hasMotion(action.target)) {
                    model.playMotion(action.target);
                }
            }
            case "expression" -> {
                if (!model.hasExpression(action.target)) {
                    break;
                }
                if (action.duration > 0f) {
                    model.playExpression(action.target, action.duration, action.fade);
                } else {
                    setActiveExpression(action.target);
                }
            }
            case "param" -> {
                if (!model.hasParameter(action.target)) {
                    break;
                }
                if (action.duration > 0f) {
                    model.setParameterOverride(action.target, action.value, action.duration, action.fade);
                } else {
                    model.setParameter(action.target, action.value);
                }
            }
        }
    }

    public void handleTick() {
        if (editMode) {
            net.minecraft.client.util.Window window = mc.getWindow();
            boolean moved = false;
            if (InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT)) {
                config.posX -= 2;
                moved = true;
            }
            if (InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_RIGHT)) {
                config.posX += 2;
                moved = true;
            }
            if (InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_UP)) {
                config.posY -= 2;
                moved = true;
            }
            if (InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_DOWN)) {
                config.posY += 2;
                moved = true;
            }
            if (InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_EQUAL) || InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_KP_ADD)) {
                config.size = Math.min(800, config.size + 4);
                moved = true;
            }
            if (InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_MINUS) || InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_KP_SUBTRACT)) {
                config.size = Math.max(40, config.size - 4);
                moved = true;
            }
            if (moved) {
                long now = System.currentTimeMillis();
                if (now - lastMoveTick > 80) {
                    saveConfig();
                    lastMoveTick = now;
                }
            }
        }

        soundActivity = Math.max(0.0f, soundActivity * 0.75f);
        boolean wasHigh = volumeHigh;
        volumeHigh = soundActivity >= config.soundBlinkThreshold;
        if (volumeHigh && !wasHigh) {
            eventSystem.fire("volume_high");
        }
        if (!volumeHigh && wasHigh) {
            eventSystem.fire("volume_low");
        }

        eventSystem.tick();
    }

public void render(DrawContext context, float tickDelta) {
	if (!config.enabled || mc.player == null || mc.world == null) {
		return;
	}
	ensureModelLoaded();

	handleChatMouseInteraction();

	updateTracking(tickDelta);
	if (model != null) {
		try {
			applyIdleAnimation();
			applyExpressions();
			model.updateTransientAnimations(tickDelta / 20.0f);
			applySoundBlink();
			model.update();
		} catch (Throwable t) {
			loadError = "update: " + t.getClass().getSimpleName() + ": " + t.getMessage();
			System.err.println("[Live2D] FAILED during model update:");
			t.printStackTrace(System.err);
		}
	}

	int h = Math.max(40, config.size);
	float aspect = model != null ? Math.max(0.05f, model.getBboxAspect()) : 0.99666667f;
	int w = Math.max(1, Math.round(h * aspect));

	int ex = Math.round(config.posX + springX * 0.55f);
	int ey = Math.round(config.posY + springY * 0.3f + idleBob());

	if (model != null && renderer != null) {
		try {
			context.state.addSpecialElement(new Live2dGuiElementRenderState(ex, ey, ex + w, ey + h));
		} catch (Throwable t) {
			loadError = t.getClass().getSimpleName() + ": " + t.getMessage();
			System.err.println("[Live2D] FAILED while rendering model:");
			t.printStackTrace(System.err);
			unloadModel();
			renderPreview(context, w, h);
		}
	} else {
		System.out.println("[Live2D] render: model=" + (model != null) + " renderer=" + (renderer != null) + " loadError=" + loadError);
		renderPreview(context, w, h);
	}

	if (model == null) {
		renderRuntimeHint(context, w, h);
	}

	if (editMode) {
		renderEditFrame(context, w, h);
	}
}

    private void handleChatMouseInteraction() {
        if (!config.chatDragEnabled) {
            return;
        }
        boolean chatOpen = mc.currentScreen instanceof ChatScreen;
        if (!chatOpen) {
            chatDragActive = false;
            return;
        }
        ensureScrollHooked();
        net.minecraft.client.util.Window window = mc.getWindow();
        boolean left = GLFW.glfwGetMouseButton(window.getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        double mx = mc.mouse.getScaledX(window);
        double my = mc.mouse.getScaledY(window);
        if (left) {
            if (chatDragActive) {
                int dx = (int) Math.round(mx - lastDragX);
                int dy = (int) Math.round(my - lastDragY);
                if (dx != 0 || dy != 0) {
                    config.posX += dx;
                    config.posY += dy;
                    saveConfig();
                }
            }
            chatDragActive = true;
            lastDragX = mx;
            lastDragY = my;
        } else {
            chatDragActive = false;
        }
        if (pendingScroll != 0.0) {
            int step = Math.max(1, config.wheelResizeStep);
            int newSize = MathHelper.clamp(config.size + (int) Math.round(pendingScroll * step), 40, 800);
            pendingScroll = 0.0;
            if (newSize != config.size) {
                config.size = newSize;
                saveConfig();
            }
        }
    }

    private void ensureScrollHooked() {
        if (scrollHooked) {
            return;
        }
        scrollHooked = true;
        scrollHook = this::handleScrollGlfw;
        scrollPrev = GLFW.glfwSetScrollCallback(mc.getWindow().getHandle(), scrollHook);
    }

    private void handleScrollGlfw(long window, double dx, double dy) {
        if (scrollPrev != null) {
            scrollPrev.invoke(window, dx, dy);
        }
        pendingScroll += dy;
    }

    private void onSoundPlayed(SoundInstance instance, WeightedSoundSet set, float volume) {
        float v = Math.min(1.0f, Math.max(0.0f, volume)) * Math.min(1.0f, Math.max(0.2f, instance.getPitch()));
        soundActivity = Math.min(1.2f, soundActivity + v);
    }

    private void applySoundBlink() {
        if (model == null || !config.soundBlinkEnabled || !volumeHigh) {
            return;
        }
        model.setParameter("ParamEyeLOpen", 0.0f);
        model.setParameter("ParamEyeROpen", 0.0f);
    }

    private void ensureModelLoaded() {
        if (loadAttempted || model != null) {
            return;
        }
        loadAttempted = true;
        System.out.println("[Live2D] ensureModelLoaded: attempting to load model '" + config.model + "'");
        try {
            loadModel(config.model);
            loadError = null;
            System.out.println("[Live2D] Model '" + config.model + "' loaded via hoprc");
        } catch (Throwable t) {
            loadError = t.getClass().getSimpleName() + ": " + t.getMessage();
            unloadModel();
            System.err.println("[Live2D] FAILED to load model '" + config.model + "':");
            t.printStackTrace(System.err);
        }
    }

    private void loadModel(String name) throws Exception {
        System.out.println("[Live2D] loadModel: name=" + name + " user=" + modelLoader.isUserModel(name));
        JsonObject model3 = modelLoader.readModel3(name);
        if (model3 == null) {
            throw new IllegalStateException("Missing model3.json: " + name);
        }
        Path gameDir = mc.runDirectory.toPath();
        CubismNativeModel newModel = modelLoader.createModel(name, gameDir);
        System.out.println("[Live2D] loadModel: native model loaded, drawables=" + newModel.getDrawableCount());

        List<net.minecraft.util.Identifier> textures = modelLoader.loadTextures(name, model3);
        if (textures.isEmpty()) {
            throw new IllegalStateException("Model " + name + " has no textures");
        }
        System.out.println("[Live2D] loadModel: textures=" + textures.size());

        CubismNativeRenderer newRenderer = new CubismNativeRenderer(mc, newModel, textures.toArray(new Identifier[0]));
        newRenderer.setFlipV(true);
        newRenderer.setMasksEnabled(config.masksEnabled);

        modelLoader.loadExpressions(newModel, name, model3);
        modelLoader.loadMotions(newModel, name, model3);
        System.out.println("[Live2D] loadModel: expressions=" + newModel.getExpressions().size() + " motions=" + newModel.getMotions().size());

        unloadModel();

        model = newModel;
        renderer = newRenderer;
        loadedModel = name;

        firstFrame = true;
        activeExpression = null;
        activeWeight = 0.0f;
        hurtWeight = 0.0f;
        deathWeight = 0.0f;
        nextBlinkAt = System.currentTimeMillis() + 1800L;
        blinkStartedAt = -1L;

        model.update();
    }

    private void unloadModel() {
        if (model != null) {
            try {
                model.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        model = null;
        renderer = null;
        loadedModel = null;
    }

    private void updateTracking(float tickDelta) {
        if (mc.player == null) return;

        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();
        if (firstFrame) {
            lastYaw = yaw;
            lastPitch = pitch;
            firstFrame = false;
            return;
        }
        float dt = Math.max(0.01f, tickDelta / 20.0f);
        float deltaYaw = MathHelper.wrapDegrees(yaw - lastYaw);
        float deltaPitch = pitch - lastPitch;
        lastYaw = yaw;
        lastPitch = pitch;

        float strength = config.swayStrength;
        float targetX = MathHelper.clamp(-deltaYaw / dt * 0.018f * strength, -30.0f, 30.0f);
        float targetY = config.followPitch ? MathHelper.clamp(-deltaPitch / dt * 0.014f * strength, -20.0f, 20.0f) : 0.0f;
        float targetZ = MathHelper.clamp(deltaYaw / dt * 0.01f * strength, -18.0f, 18.0f);
        float smooth = MathHelper.clamp(config.smoothing, 0.02f, 0.6f);
        angleX = lerp(angleX, targetX, smooth);
        angleY = lerp(angleY, targetY, smooth);
        angleZ = lerp(angleZ, targetZ, smooth * 0.75f);
        bodyAngleX = lerp(bodyAngleX, angleX * 0.35f, smooth * 0.55f);

        springX = updateSpring(springX, angleX + bodyAngleX, 0.12f, 0.78f, true);
        springY = updateSpring(springY, angleY, 0.08f, 0.82f, false);
    }

    private float updateSpring(float value, float target, float stiffness, float damping, boolean horizontal) {
        float v = (horizontal ? springVX : springVY) + (target - value) * stiffness;
        v *= damping;
        if (horizontal) {
            springVX = v;
        } else {
            springVY = v;
        }
        return value + v;
    }

    private void applyIdleAnimation() {
        if (model == null) return;

        long now = System.currentTimeMillis();
        double t = now / 1000.0;

        float eyeOpen = 1.0f;
        if (config.blinkEnabled) {
            if (blinkStartedAt < 0L && now >= nextBlinkAt) {
                blinkStartedAt = now;
            }
            if (blinkStartedAt >= 0L) {
                float p = MathHelper.clamp((float) (now - blinkStartedAt) / 180.0f, 0.0f, 1.0f);
                eyeOpen = p < 0.5f ? 1.0f - p * 2.0f : (p - 0.5f) * 2.0f;
                if (p >= 1.0f) {
                    blinkStartedAt = -1L;
                    nextBlinkAt = now + 2200L + (long) (Math.sin(t * 1.7) * 500.0 + 650.0);
                }
            }
        }
        model.setParameter("ParamEyeLOpen", eyeOpen);
        model.setParameter("ParamEyeROpen", eyeOpen);

        float breath = (float) ((Math.sin(t * 2.1) + 1.0) * 0.5);
        model.setParameter("ParamBreath", breath);

        model.setParameter("ParamAngleX", angleX);
        model.setParameter("ParamAngleY", angleY);
        model.setParameter("ParamAngleZ", angleZ);
        model.setParameter("ParamBodyAngleX", bodyAngleX);
        model.setParameter("ParamBodyAngleY", angleY * 0.2f);
        model.setParameter("ParamBodyAngleZ", angleZ * 0.3f);

        model.setParameter("ParamEyeBallX", MathHelper.clamp(angleX / 30.0f, -1.0f, 1.0f));
        model.setParameter("ParamEyeBallY", MathHelper.clamp(angleY / 20.0f, -1.0f, 1.0f));

        float hairX = angleX * 0.09f + (float) Math.sin(t * 2.8) * 1.2f;
        float hairY = angleY * 0.08f + (float) Math.sin(t * 2.1 + 1.0) * 0.8f;
        model.setParameter("ParamHairFront", hairX);
        model.setParameter("ParamHairSide", hairX);
        model.setParameter("ParamHairBack", hairX);

        earVelocity += (angleZ * 0.55f + angleX * 0.18f - earSpring) * 0.16f;
        earVelocity *= 0.74f;
        earSpring += earVelocity;
        float ear = MathHelper.clamp(earSpring, -12.0f, 12.0f);
        model.setParameter("Param10", ear);
        model.setParameter("Param14", -ear);
    }

    private void applyExpressions() {
        if (model == null || activeExpression == null) {
            return;
        }
        if (model.hasExpression(activeExpression)) {
            model.applyExpression(activeExpression, 1.0f);
        }
    }

    private float lerp(float from, float to, float factor) {
        return from + (to - from) * MathHelper.clamp(factor, 0.0f, 1.0f);
    }

	private void renderPreview(DrawContext context, int w, int h) {
		String name = config.model;
		Identifier preview = Identifier.of("live2d", "live2d/" + name + "/" + PREVIEW_SUFFIX);
		boolean hasPreview = mc.getResourceManager().getResource(preview).isPresent();
		if (hasPreview) {
			context.drawTexture(RenderPipelines.GUI_TEXTURED, preview, 0, 0, 0, 0, w, h, w, h);
		} else {
			context.fill(0, 0, w, h, 0xFF202028);
		}
	}

    private void renderEditFrame(DrawContext context, int w, int h) {
        TextRenderer text = mc.textRenderer;
        int color = 0xFFFFD25A;
        context.fill(-1, -1, w + 1, 0, color);
        context.fill(-1, h, w + 1, h + 1, color);
        context.fill(-1, -1, 0, h + 1, color);
        context.fill(w, -1, w + 1, h + 1, color);
        context.drawTextWithShadow(text, "Edit mode: arrows move | +/- resize | F12 exit", 4, 4, 0xFFFFFFFF);
    }

    private void saveConfig() {
        long now = System.currentTimeMillis();
        if (now - lastConfigSave < 300) {
            return;
        }
        lastConfigSave = now;
        config.save(configPath);
    }

    private float idleBob() {
        return (float) Math.sin(System.currentTimeMillis() / 1200.0) * 1.2f;
    }

    private void renderRuntimeHint(DrawContext context, int w, int h) {
        if (loadError == null) return;
        TextRenderer text = mc.textRenderer;
        String msg = "Live2D: " + loadError;
        int tw = text.getWidth(msg);
        context.fill(0, h - 10, Math.min(w, tw + 4), h, 0xAA000000);
        context.drawTextWithShadow(text, msg, 2, h - 9, 0xFFFF5555);
    }
}