package com.ciallo.live2d.client;

import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.special.SpecialGuiElementRenderState;

public class Live2dGuiElementRenderState implements SpecialGuiElementRenderState {

	private final int x1;
	private final int y1;
	private final int x2;
	private final int y2;

	public Live2dGuiElementRenderState(int x1, int y1, int x2, int y2) {
		this.x1 = x1;
		this.y1 = y1;
		this.x2 = x2;
		this.y2 = y2;
	}

	@Override
	public ScreenRect bounds() {
		return new ScreenRect(x1, y1, x2 - x1, y2 - y1);
	}

	@Override
	public int x1() {
		return x1;
	}

	@Override
	public int y1() {
		return y1;
	}

	@Override
	public int x2() {
		return x2;
	}

	@Override
	public int y2() {
		return y2;
	}

	@Override
	public float scale() {
		return 1.0f;
	}

	@Override
	public ScreenRect scissorArea() {
		return bounds();
	}
}