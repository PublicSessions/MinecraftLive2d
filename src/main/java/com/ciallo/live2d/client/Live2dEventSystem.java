package com.ciallo.live2d.client;

import com.ciallo.live2d.config.Live2dConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;

public class Live2dEventSystem {

	private final Live2dHudRenderer hud;
	private final MinecraftClient mc;
	private final Live2dConfig config;

	private boolean joined;
	private boolean wasChatOpen;
	private boolean wasScreenOpen;
	private boolean wasHurt;
	private boolean wasDead;
	private boolean wasSwinging;
	private boolean wasOnGround;
	private boolean wasSwimming;
	private boolean wasUnderwater;
	private boolean wasSneaking;
	private boolean wasSprinting;
	private boolean wasRaining;
	private boolean wasThundering;
	private boolean wasNight;
	private boolean wasLowHealth;
	private long lastIdleFire = System.currentTimeMillis();

	public Live2dEventSystem(Live2dHudRenderer hud, MinecraftClient mc, Live2dConfig config) {
		this.hud = hud;
		this.mc = mc;
		this.config = config;
	}

	public void tick() {
		ClientPlayerEntity player = mc.player;
		ClientWorld world = mc.world;
		if (player == null || world == null) {
			joined = false;
			return;
		}
		if (!joined) {
			joined = true;
			snapshot(player, world);
			fire("join_world");
			return;
		}

		boolean chatOpen = mc.currentScreen instanceof ChatScreen;
		boolean screenOpen = mc.currentScreen != null;
		boolean hurt = player.hurtTime > 0;
		boolean dead = !player.isAlive();
		boolean swinging = player.handSwinging;
		boolean onGround = player.isOnGround();
		boolean swimming = player.isSwimming();
		boolean underwater = player.isSubmergedInWater();
		boolean sneaking = player.isSneaking();
		boolean sprinting = player.isSprinting();
		boolean raining = world.isRaining();
		boolean thundering = world.isThundering();
		boolean night = world.isNight();
		boolean lowHealth = player.getHealth() / Math.max(0.001f, player.getMaxHealth()) < 0.3f;

		if (chatOpen && !wasChatOpen) fire("chat_opened");
		if (!chatOpen && wasChatOpen) fire("chat_closed");
		if (screenOpen && !wasScreenOpen) fire("screen_open");
		if (!screenOpen && wasScreenOpen) fire("screen_close");
		if (hurt && !wasHurt) fire("hurt");
		if (dead && !wasDead) fire("death");
		if (!dead && wasDead) fire("respawn");
		if (swinging && !wasSwinging) fire("attack");
		if (!onGround && wasOnGround && player.getVelocity().y > 0.05f) fire("jump");
		if (onGround && !wasOnGround) fire("land");
		if (swimming && !wasSwimming) fire("swim_start");
		if (!swimming && wasSwimming) fire("swim_end");
		if (underwater && !wasUnderwater) fire("underwater");
		if (!underwater && wasUnderwater) fire("surface");
		if (sneaking && !wasSneaking) fire("sneak");
		if (!sneaking && wasSneaking) fire("stand");
		if (sprinting && !wasSprinting) fire("sprint_start");
		if (!sprinting && wasSprinting) fire("sprint_end");
		if (raining && !wasRaining) fire("rain");
		if (!raining && wasRaining) fire("clear");
		if (thundering && !wasThundering) fire("thunder");
		if (night && !wasNight) fire("night");
		if (!night && wasNight) fire("day");
		if (lowHealth && !wasLowHealth) fire("low_health");
		if (!lowHealth && wasLowHealth) fire("high_health");

		long now = System.currentTimeMillis();
		if (now - lastIdleFire > 30000L) {
			lastIdleFire = now;
			fire("idle");
		}

		wasChatOpen = chatOpen;
		wasScreenOpen = screenOpen;
		wasHurt = hurt;
		wasDead = dead;
		wasSwinging = swinging;
		wasOnGround = onGround;
		wasSwimming = swimming;
		wasUnderwater = underwater;
		wasSneaking = sneaking;
		wasSprinting = sprinting;
		wasRaining = raining;
		wasThundering = thundering;
		wasNight = night;
		wasLowHealth = lowHealth;
	}

	private void snapshot(ClientPlayerEntity player, ClientWorld world) {
		wasChatOpen = mc.currentScreen instanceof ChatScreen;
		wasScreenOpen = mc.currentScreen != null;
		wasHurt = player.hurtTime > 0;
		wasDead = !player.isAlive();
		wasSwinging = player.handSwinging;
		wasOnGround = player.isOnGround();
		wasSwimming = player.isSwimming();
		wasUnderwater = player.isSubmergedInWater();
		wasSneaking = player.isSneaking();
		wasSprinting = player.isSprinting();
		wasRaining = world.isRaining();
		wasThundering = world.isThundering();
		wasNight = world.isNight();
		wasLowHealth = player.getHealth() / Math.max(0.001f, player.getMaxHealth()) < 0.3f;
		lastIdleFire = System.currentTimeMillis();
	}

	public void fire(String event) {
		Live2dConfig.EventAction action = config.events.get(event);
		if (action == null) {
			return;
		}
		hud.triggerAction(action);
		System.out.println("[Live2D] event fired: " + event + " -> " + action.type + " " + action.target);
	}
}