package com.ciallo.live2d.client;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Map;

public final class Live2dCommand {

	private final Live2dHudRenderer hud;

	public Live2dCommand(Live2dHudRenderer hud) {
		this.hud = hud;
	}

	public void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommandManager.literal("live2d")
						.then(ClientCommandManager.literal("toggle").executes(ctx -> {
							hud.toggleEnabled();
							send(ctx.getSource(), "Live2D " + (hud.isEnabled() ? "enabled" : "disabled"));
							return 1;
						}))
						.then(ClientCommandManager.literal("status").executes(ctx -> {
							send(ctx.getSource(), hud.getStatus());
							return 1;
						}))
						.then(ClientCommandManager.literal("gui").executes(ctx -> {
							MinecraftClient.getInstance().setScreen(new Live2dSettingsScreen(hud));
							return 1;
						}))
						.then(ClientCommandManager.literal("models").executes(ctx -> {
							send(ctx.getSource(), "Models: " + String.join(", ", hud.getAvailableModels()));
							return 1;
						}))
						.then(ClientCommandManager.literal("model")
								.then(ClientCommandManager.argument("name", StringArgumentType.word()).executes(ctx -> {
									String name = StringArgumentType.getString(ctx, "name");
									if (!hud.getAvailableModels().contains(name)) {
										send(ctx.getSource(), "Unknown model '" + name + "'. Use /live2d models");
										return 0;
									}
									hud.setModel(name);
									send(ctx.getSource(), "Live2D model set to " + hud.getModelName());
									return 1;
								})))
						.then(ClientCommandManager.literal("motions").executes(ctx -> {
							List<String> motions = hud.getAvailableMotions();
							send(ctx.getSource(), motions.isEmpty()
									? "No motions for model '" + hud.getModelName() + "'"
									: "Motions: " + String.join(", ", motions));
							return 1;
						}))
						.then(ClientCommandManager.literal("expressions").executes(ctx -> {
							List<String> expressions = hud.getAvailableExpressions();
							send(ctx.getSource(), expressions.isEmpty()
									? "No expressions for model '" + hud.getModelName() + "'"
									: "Expressions: " + String.join(", ", expressions));
							return 1;
						}))
						.then(ClientCommandManager.literal("size")
								.then(ClientCommandManager.argument("height", IntegerArgumentType.integer(40, 800)).executes(ctx -> {
									int size = IntegerArgumentType.getInteger(ctx, "height");
									hud.setSize(size);
									send(ctx.getSource(), "Live2D size set to " + hud.getConfig().size);
									return 1;
								})))
						.then(ClientCommandManager.literal("pos")
								.then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
										.then(ClientCommandManager.argument("y", IntegerArgumentType.integer()).executes(ctx -> {
											int x = IntegerArgumentType.getInteger(ctx, "x");
											int y = IntegerArgumentType.getInteger(ctx, "y");
											hud.setPosition(x, y);
											send(ctx.getSource(), "Live2D position set to " + x + ", " + y);
											return 1;
										}))))
						.then(ClientCommandManager.literal("move")
								.then(ClientCommandManager.argument("dx", IntegerArgumentType.integer())
										.then(ClientCommandManager.argument("dy", IntegerArgumentType.integer()).executes(ctx -> {
											int dx = IntegerArgumentType.getInteger(ctx, "dx");
											int dy = IntegerArgumentType.getInteger(ctx, "dy");
											hud.movePosition(dx, dy);
											send(ctx.getSource(), "Live2D position is now " + hud.getConfig().posX + ", " + hud.getConfig().posY);
											return 1;
										}))))
						.then(ClientCommandManager.literal("sway")
								.then(ClientCommandManager.argument("strength", FloatArgumentType.floatArg(0.0f, 3.0f)).executes(ctx -> {
									float strength = FloatArgumentType.getFloat(ctx, "strength");
									hud.setSwayStrength(strength);
									send(ctx.getSource(), "Live2D sway strength set to " + hud.getConfig().swayStrength);
									return 1;
								})))
						.then(ClientCommandManager.literal("smoothing")
								.then(ClientCommandManager.argument("value", FloatArgumentType.floatArg(0.02f, 0.6f)).executes(ctx -> {
									float value = FloatArgumentType.getFloat(ctx, "value");
									hud.setSmoothing(value);
									send(ctx.getSource(), "Live2D smoothing set to " + hud.getConfig().smoothing);
									return 1;
								})))
						.then(ClientCommandManager.literal("followpitch")
								.then(ClientCommandManager.argument("value", BoolArgumentType.bool()).executes(ctx -> {
									boolean value = BoolArgumentType.getBool(ctx, "value");
									hud.setFollowPitch(value);
									send(ctx.getSource(), "Live2D follow pitch set to " + hud.getConfig().followPitch);
									return 1;
								})))
						.then(ClientCommandManager.literal("blink")
								.then(ClientCommandManager.argument("value", BoolArgumentType.bool()).executes(ctx -> {
									boolean value = BoolArgumentType.getBool(ctx, "value");
									hud.setBlinkEnabled(value);
									send(ctx.getSource(), "Live2D blink set to " + hud.getConfig().blinkEnabled);
									return 1;
								})))
						.then(ClientCommandManager.literal("masks")
								.then(ClientCommandManager.argument("value", BoolArgumentType.bool()).executes(ctx -> {
									boolean value = BoolArgumentType.getBool(ctx, "value");
									hud.setMasksEnabled(value);
									send(ctx.getSource(), "Live2D masks set to " + hud.getConfig().masksEnabled);
									return 1;
								})))
						.then(ClientCommandManager.literal("expression")
								.then(ClientCommandManager.argument("name", StringArgumentType.word()).executes(ctx -> {
									String name = StringArgumentType.getString(ctx, "name");
									hud.setActiveExpression(name);
									send(ctx.getSource(), "Live2D expression set to " + hud.getActiveExpression());
									return 1;
								})))
						.then(ClientCommandManager.literal("chatdrag")
								.then(ClientCommandManager.argument("value", BoolArgumentType.bool()).executes(ctx -> {
									boolean value = BoolArgumentType.getBool(ctx, "value");
									hud.setChatDragEnabled(value);
									send(ctx.getSource(), "Chat drag/wheel set to " + hud.getConfig().chatDragEnabled);
									return 1;
								})))
						.then(ClientCommandManager.literal("soundblink")
								.then(ClientCommandManager.argument("value", BoolArgumentType.bool()).executes(ctx -> {
									boolean value = BoolArgumentType.getBool(ctx, "value");
									hud.setSoundBlinkEnabled(value);
									send(ctx.getSource(), "Sound blink set to " + hud.getConfig().soundBlinkEnabled + " (threshold " + hud.getConfig().soundBlinkThreshold + ")");
									return 1;
								}))
								.then(ClientCommandManager.argument("threshold", FloatArgumentType.floatArg(0.0f, 1.0f)).executes(ctx -> {
									float threshold = FloatArgumentType.getFloat(ctx, "threshold");
									hud.setSoundBlinkThreshold(threshold);
									send(ctx.getSource(), "Sound blink threshold set to " + hud.getConfig().soundBlinkThreshold);
									return 1;
								})))
						.then(ClientCommandManager.literal("events").executes(ctx -> {
							Map<String, com.ciallo.live2d.config.Live2dConfig.EventAction> events = hud.getEvents();
							if (events.isEmpty()) {
								send(ctx.getSource(), "No events configured. Use /live2d event <name> <type> <target>");
								return 1;
							}
							StringBuilder sb = new StringBuilder("Events:");
							for (Map.Entry<String, com.ciallo.live2d.config.Live2dConfig.EventAction> entry : events.entrySet()) {
								com.ciallo.live2d.config.Live2dConfig.EventAction a = entry.getValue();
								sb.append("\n ").append(entry.getKey()).append(" -> ").append(a.type).append(" ").append(a.target);
								if (a.type.equals("param")) {
									sb.append(" value=").append(a.value);
								}
								if (a.duration > 0) {
									sb.append(" dur=").append(a.duration).append(" fade=").append(a.fade);
								}
							}
							send(ctx.getSource(), sb.toString());
							return 1;
						}))
.then(eventCommand())
						.executes(ctx -> {
							send(ctx.getSource(), hud.getStatus());
							return 1;
						})));
	}

	private com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> eventCommand() {
		return ClientCommandManager.literal("event")
				.then(ClientCommandManager.argument("name", StringArgumentType.word())
						.executes(ctx -> {
							send(ctx.getSource(), "Usage: /live2d event <name> <motion|expression|param> <target> [value] [duration] [fade]");
							send(ctx.getSource(), "Events: chat_opened chat_closed screen_open screen_close join_world hurt death respawn attack jump land swim_start swim_end underwater surface sneak stand sprint_start sprint_end rain clear thunder night day low_health high_health volume_high volume_low idle");
							return 1;
						})
						.then(ClientCommandManager.literal("off").executes(ctx -> {
							String name = StringArgumentType.getString(ctx, "name");
							hud.removeEventAction(name);
							send(ctx.getSource(), "Event '" + name + "' cleared");
							return 1;
						}))
						.then(ClientCommandManager.argument("type", StringArgumentType.word())
								.then(ClientCommandManager.argument("target", StringArgumentType.word())
										.executes(ctx -> {
											registerEvent(ctx.getSource(), StringArgumentType.getString(ctx, "name"),
													StringArgumentType.getString(ctx, "type"),
													StringArgumentType.getString(ctx, "target"), 0.0f, 0.0f, 0.3f);
											return 1;
										})
										.then(ClientCommandManager.argument("value", FloatArgumentType.floatArg())
												.executes(ctx -> {
													registerEvent(ctx.getSource(), StringArgumentType.getString(ctx, "name"),
															StringArgumentType.getString(ctx, "type"),
															StringArgumentType.getString(ctx, "target"),
															FloatArgumentType.getFloat(ctx, "value"), 0.0f, 0.3f);
													return 1;
												})
												.then(ClientCommandManager.argument("duration", FloatArgumentType.floatArg(0.0f))
														.executes(ctx -> {
															registerEvent(ctx.getSource(), StringArgumentType.getString(ctx, "name"),
																	StringArgumentType.getString(ctx, "type"),
																	StringArgumentType.getString(ctx, "target"),
																	FloatArgumentType.getFloat(ctx, "value"),
																	FloatArgumentType.getFloat(ctx, "duration"), 0.3f);
															return 1;
														})
														.then(ClientCommandManager.argument("fade", FloatArgumentType.floatArg(0.0f)).executes(ctx -> {
															registerEvent(ctx.getSource(), StringArgumentType.getString(ctx, "name"),
																	StringArgumentType.getString(ctx, "type"),
																	StringArgumentType.getString(ctx, "target"),
																	FloatArgumentType.getFloat(ctx, "value"),
																	FloatArgumentType.getFloat(ctx, "duration"),
																	FloatArgumentType.getFloat(ctx, "fade"));
															return 1;
														})))))));
	}

	private void registerEvent(FabricClientCommandSource source, String name, String type, String target,
			float value, float duration, float fade) {
		if (!type.equals("motion") && !type.equals("expression") && !type.equals("param")) {
			send(source, "Invalid type '" + type + "'. Use motion, expression or param");
			return;
		}
		hud.setEventAction(name, type, target, value, duration, fade);
		send(source, "Event '" + name + "' -> " + type + " " + target
				+ (type.equals("param") ? " value=" + value : "")
				+ (duration > 0 ? " duration=" + duration + " fade=" + fade : ""));
	}

	private void send(FabricClientCommandSource source, String message) {
		source.sendFeedback(Text.literal("[Live2D] " + message));
	}
}