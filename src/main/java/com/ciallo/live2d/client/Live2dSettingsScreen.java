package com.ciallo.live2d.client;

import com.ciallo.live2d.config.Live2dConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Live2dSettingsScreen extends Screen {

	private final Live2dHudRenderer hud;
	private final Live2dConfig config;

	private final List<Label> labels = new ArrayList<>();
	private double scrollY;
	private int contentBottom;
	private TextFieldWidget eventField;

	private static final int ROW_H = 20;
	private static final int ROW_GAP = 4;
	private static final int LEFT = 10;
	private static final int TOP = 26;
	private static final int FOOTER = 36;

	private record Label(String text, int y) {
	}

	public Live2dSettingsScreen(Live2dHudRenderer hud) {
		super(Text.literal("Live2D Settings"));
		this.hud = hud;
		this.config = hud.getConfig();
	}

	@Override
	public void init() {
		rebuild();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		context.drawTextWithShadow(textRenderer, this.title, LEFT, 8, 0xFFFFFFFF);
		context.enableScissor(0, TOP, this.width, this.height);
		for (Label label : labels) {
			context.drawTextWithShadow(textRenderer, label.text(), LEFT, label.y() + 3, 0xFFE0E0E0);
		}
		super.render(context, mouseX, mouseY, delta);
		context.disableScissor();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		scrollY += verticalAmount * 12.0;
		clampScroll();
		rebuild();
		return true;
	}

	private void rebuild() {
		clearChildren();
		labels.clear();
		int x = LEFT;
		int contentW = this.width - LEFT - LEFT;
		int y = TOP;

		y = addToggle(x, y, contentW, "Enabled", config.enabled, hud::setEnabled);
		y = addModelButton(x, y, contentW, "Model");
		y = addSlider(x, y, contentW, "Size", (config.size - 40) / 760.0, v -> config.size = 40 + (int) Math.round(v * 760));
		y = addSlider(x, y, contentW, "PosX", (config.posX + 300) / 2300.0, v -> config.posX = (int) Math.round(v * 2300 - 300));
		y = addSlider(x, y, contentW, "PosY", (config.posY + 100) / 1100.0, v -> config.posY = (int) Math.round(v * 1100 - 100));
		y = addSlider(x, y, contentW, "Sway", config.swayStrength / 3.0, v -> config.swayStrength = (float) (v * 3.0));
		y = addSlider(x, y, contentW, "Smoothing", (config.smoothing - 0.02f) / 0.58, v -> config.smoothing = (float) (0.02 + v * 0.58));
		y = addSlider(x, y, contentW, "WheelStep", (config.wheelResizeStep - 1) / 19.0, v -> config.wheelResizeStep = 1 + (int) Math.round(v * 19));
		y = addSlider(x, y, contentW, "SoundThreshold", config.soundBlinkThreshold, v -> config.soundBlinkThreshold = v.floatValue());
		y = addToggle(x, y, contentW, "FollowPitch", config.followPitch, hud::setFollowPitch);
		y = addToggle(x, y, contentW, "Blink", config.blinkEnabled, hud::setBlinkEnabled);
		y = addToggle(x, y, contentW, "Masks", config.masksEnabled, hud::setMasksEnabled);
		y = addToggle(x, y, contentW, "ChatDrag", config.chatDragEnabled, hud::setChatDragEnabled);
		y = addToggle(x, y, contentW, "SoundBlink", config.soundBlinkEnabled, hud::setSoundBlinkEnabled);

		y = addEventRows(x, y, contentW);
		contentBottom = y;

		clampScroll();
		int footerY = this.height - 28;
		addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
				.dimensions(this.width - 90, footerY, 80, ROW_H).build());
	}

	private int addToggle(int x, int y, int w, String label, boolean value, Consumer<Boolean> onChanged) {
		labels.add(new Label(label, y));
		if (inView(y)) {
			addDrawableChild(CyclingButtonWidget.onOffBuilder(Text.literal("On"), Text.literal("Off"), value)
					.build(x, y, w, ROW_H, Text.literal(label), (btn, val) -> onChanged.accept(val)));
		}
		return y + ROW_H + ROW_GAP;
	}

	private int addModelButton(int x, int y, int w, String label) {
		labels.add(new Label(label, y));
		if (inView(y)) {
			List<String> models = hud.getAvailableModels();
			if (models.isEmpty() || !models.contains(config.model)) {
				addDrawableChild(ButtonWidget.builder(Text.literal("No models"), b -> {
				}).dimensions(x, y, w, ROW_H).build());
			} else {
				addDrawableChild(CyclingButtonWidget.builder(Text::literal, (Supplier<String>) () -> config.model)
						.values(models)
						.build(x, y, w, ROW_H, Text.literal(label), (btn, val) -> hud.setModel(val)));
			}
		}
		return y + ROW_H + ROW_GAP;
	}

	private int addSlider(int x, int y, int w, String label, double value, Consumer<Double> onChanged) {
		labels.add(new Label(label, y));
		if (inView(y)) {
			addDrawableChild(new SliderWidget(x, y, w, ROW_H, Text.literal(label), value) {
				@Override
				protected void updateMessage() {
					setMessage(Text.literal(label + ": " + fmt(this.value)));
				}

				@Override
				protected void applyValue() {
					onChanged.accept(this.value);
				}
			});
		}
		return y + ROW_H + ROW_GAP;
	}

	private int addEventRows(int x, int y, int w) {
		labels.add(new Label("Events (add as event:type:target[:value[:duration[:fade]]])", y));
		y += ROW_H + ROW_GAP;
		for (Map.Entry<String, Live2dConfig.EventAction> entry : config.events.entrySet()) {
			String ev = entry.getKey();
			Live2dConfig.EventAction a = entry.getValue();
			String desc = ev + " -> " + a.type + " " + a.target
					+ (a.type.equals("param") ? " value=" + fmt(a.value) : "")
					+ (a.duration > 0 ? " dur=" + fmt(a.duration) + "s" : "");
			labels.add(new Label(desc, y));
			if (inView(y)) {
				addDrawableChild(ButtonWidget.builder(Text.literal("Remove"), b -> {
					hud.removeEventAction(ev);
					rebuild();
				}).dimensions(x + w - 62, y, 62, ROW_H).build());
			}
			y += ROW_H + ROW_GAP;
		}
		labels.add(new Label("Add event:", y));
		if (inView(y)) {
			eventField = new TextFieldWidget(textRenderer, x, y, w - 62, ROW_H, Text.literal("event:type:target"));
			eventField.setMaxLength(128);
			addDrawableChild(eventField);
			addDrawableChild(ButtonWidget.builder(Text.literal("Add"), b -> addEvent())
					.dimensions(x + w - 56, y, 56, ROW_H).build());
		}
		return y + ROW_H + ROW_GAP;
	}

	private void addEvent() {
		if (eventField == null) {
			return;
		}
		String text = eventField.getText();
		if (text == null || text.isBlank()) {
			return;
		}
		String[] parts = text.split(":");
		if (parts.length < 3) {
			return;
		}
		float value = 0f;
		float duration = 0f;
		float fade = 0.3f;
		if (parts.length > 3) {
			try {
				value = Float.parseFloat(parts[3]);
			} catch (NumberFormatException ignored) {
			}
		}
		if (parts.length > 4) {
			try {
				duration = Float.parseFloat(parts[4]);
			} catch (NumberFormatException ignored) {
			}
		}
		if (parts.length > 5) {
			try {
				fade = Float.parseFloat(parts[5]);
			} catch (NumberFormatException ignored) {
			}
		}
		hud.setEventAction(parts[0], parts[1], parts[2], value, duration, fade);
		eventField.setText("");
		rebuild();
	}

	private void clampScroll() {
		int maxScroll = Math.max(0, contentBottom - (this.height - FOOTER));
		scrollY = Math.max(-maxScroll, Math.min(0, scrollY));
	}

	private boolean inView(double y) {
		return y + ROW_H > TOP && y < this.height - 8;
	}

	private static String fmt(double v) {
		if (v == Math.floor(v) && !Double.isInfinite(v)) {
			return Integer.toString((int) v);
		}
		return String.format(java.util.Locale.ROOT, "%.2f", v);
	}
}