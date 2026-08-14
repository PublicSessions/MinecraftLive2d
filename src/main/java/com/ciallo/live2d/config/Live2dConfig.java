package com.ciallo.live2d.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Live2dConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public boolean enabled = true;
	public String model = "kaguya";
	public int size = 260;
	public int posX = 16;
	public int posY = 110;
	public float swayStrength = 3.0f;
	public float smoothing = 0.1f;
	public boolean followPitch = true;
	public boolean blinkEnabled = true;
	public boolean expressionsEnabled = true;
	public boolean masksEnabled = true;
	public boolean showPreviewOnError = true;
	public List<String> hiddenDrawables = new ArrayList<>();

	public boolean chatDragEnabled = true;
	public int wheelResizeStep = 8;

	public boolean soundBlinkEnabled = false;
	public float soundBlinkThreshold = 0.5f;

	public Map<String, EventAction> events = new LinkedHashMap<>();

	public static class EventAction {
		public String type = "expression";
		public String target = "";
		public float value = 0f;
		public float duration = 0f;
		public float fade = 0.3f;
	}

	public void save(Path path) {
		try {
			if (path.getParent() != null) {
				Files.createDirectories(path.getParent());
			}
			JsonObject root = new JsonObject();
			root.addProperty("enabled", enabled);
			root.addProperty("model", model);
			root.addProperty("size", size);
			root.addProperty("posX", posX);
			root.addProperty("posY", posY);
			root.addProperty("swayStrength", swayStrength);
			root.addProperty("smoothing", smoothing);
			root.addProperty("followPitch", followPitch);
			root.addProperty("blinkEnabled", blinkEnabled);
			root.addProperty("expressionsEnabled", expressionsEnabled);
			root.addProperty("masksEnabled", masksEnabled);
			root.addProperty("showPreviewOnError", showPreviewOnError);
			root.add("hiddenDrawables", GSON.toJsonTree(hiddenDrawables));
			root.addProperty("chatDragEnabled", chatDragEnabled);
			root.addProperty("wheelResizeStep", wheelResizeStep);
			root.addProperty("soundBlinkEnabled", soundBlinkEnabled);
			root.addProperty("soundBlinkThreshold", soundBlinkThreshold);
			JsonObject eventsObj = new JsonObject();
			for (Map.Entry<String, EventAction> entry : events.entrySet()) {
				EventAction a = entry.getValue();
				JsonObject ao = new JsonObject();
				ao.addProperty("type", a.type);
				ao.addProperty("target", a.target);
				ao.addProperty("value", a.value);
				ao.addProperty("duration", a.duration);
				ao.addProperty("fade", a.fade);
				eventsObj.add(entry.getKey(), ao);
			}
			root.add("events", eventsObj);
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(root, writer);
			}
		} catch (IOException e) {

		}
	}

	public static Live2dConfig load(Path path) {
		Live2dConfig config = new Live2dConfig();
		if (!Files.exists(path)) {
			return config;
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			if (root.has("enabled")) config.enabled = root.get("enabled").getAsBoolean();
			if (root.has("model")) config.model = root.get("model").getAsString();
			if (root.has("size")) config.size = root.get("size").getAsInt();
			if (root.has("posX")) config.posX = root.get("posX").getAsInt();
			if (root.has("posY")) config.posY = root.get("posY").getAsInt();
			if (root.has("swayStrength")) config.swayStrength = root.get("swayStrength").getAsFloat();
			if (root.has("smoothing")) config.smoothing = root.get("smoothing").getAsFloat();
			if (root.has("followPitch")) config.followPitch = root.get("followPitch").getAsBoolean();
			if (root.has("blinkEnabled")) config.blinkEnabled = root.get("blinkEnabled").getAsBoolean();
			if (root.has("expressionsEnabled")) config.expressionsEnabled = root.get("expressionsEnabled").getAsBoolean();
			if (root.has("masksEnabled")) config.masksEnabled = root.get("masksEnabled").getAsBoolean();
			if (root.has("showPreviewOnError")) config.showPreviewOnError = root.get("showPreviewOnError").getAsBoolean();
			if (root.has("hiddenDrawables")) {
				for (var el : root.getAsJsonArray("hiddenDrawables")) {
					config.hiddenDrawables.add(el.getAsString());
				}
			}
			if (root.has("chatDragEnabled")) config.chatDragEnabled = root.get("chatDragEnabled").getAsBoolean();
			if (root.has("wheelResizeStep")) config.wheelResizeStep = root.get("wheelResizeStep").getAsInt();
			if (root.has("soundBlinkEnabled")) config.soundBlinkEnabled = root.get("soundBlinkEnabled").getAsBoolean();
			if (root.has("soundBlinkThreshold")) config.soundBlinkThreshold = root.get("soundBlinkThreshold").getAsFloat();
			if (root.has("events") && root.get("events").isJsonObject()) {
				for (var entry : root.getAsJsonObject("events").entrySet()) {
					JsonObject ao = entry.getValue().getAsJsonObject();
					EventAction action = new EventAction();
					if (ao.has("type")) action.type = ao.get("type").getAsString();
					if (ao.has("target")) action.target = ao.get("target").getAsString();
					if (ao.has("value")) action.value = ao.get("value").getAsFloat();
					if (ao.has("duration")) action.duration = ao.get("duration").getAsFloat();
					if (ao.has("fade")) action.fade = ao.get("fade").getAsFloat();
					config.events.put(entry.getKey(), action);
				}
			}
		} catch (Exception e) {

		}
		return config;
	}
}