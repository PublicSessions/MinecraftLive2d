package com.ciallo.mixin;

import com.ciallo.live2d.client.Live2dClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

	@Inject(method = "onEntityStatus", at = @At("RETURN"))
	private void live2d$onEntityStatus(EntityStatusS2CPacket packet, CallbackInfo ci) {
		if (packet.getStatus() != EntityStatuses.USE_TOTEM_OF_UNDYING) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null) {
			return;
		}
		Entity entity = packet.getEntity(mc.world);
		if (!(entity instanceof PlayerEntity)) {
			return;
		}
		boolean self = entity == mc.player;
		System.out.println("[Live2D] totem status received: self=" + self);
		Live2dClient.onTotemUsed(self);
	}
}