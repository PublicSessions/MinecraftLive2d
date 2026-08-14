package com.ciallo.live2d.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.render.SpecialGuiElementRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;

public class Live2dSpecialGuiElementRenderer extends SpecialGuiElementRenderer<Live2dGuiElementRenderState> {

	private final Live2dHudRenderer hudRenderer;
	private boolean logged;

	public Live2dSpecialGuiElementRenderer(VertexConsumerProvider.Immediate vertexConsumers, Live2dHudRenderer hudRenderer) {
		super(vertexConsumers);
		this.hudRenderer = hudRenderer;
	}

	@Override
	public Class<Live2dGuiElementRenderState> getElementClass() {
		return Live2dGuiElementRenderState.class;
	}

	@Override
	protected String getName() {
		return "live2d";
	}

	@Override
	protected void render(Live2dGuiElementRenderState state, MatrixStack matrixStack) {
		com.ciallo.live2d.cubism.CubismNativeRenderer renderer = hudRenderer.getCubismRenderer();
		if (renderer == null) {
			return;
		}
		int scaleFactor = MinecraftClient.getInstance().getWindow().getScaleFactor();
		int w = (state.x2() - state.x1()) * scaleFactor;
		int h = (state.y2() - state.y1()) * scaleFactor;
		if (w <= 0 || h <= 0) {
			return;
		}
		if (!logged) {
			logged = true;
			System.out.println("[Live2D] special element renderer active: elementPx=" + w + "x" + h + " scaleFactor=" + scaleFactor);
		}
		renderer.renderPixels(w, h, 1.0f);
	}
}