package com.ciallo.live2d.cubism;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.OutputTarget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CubismNativeRenderer {

	private final MinecraftClient mc;
	private final CubismNativeModel model;
	private final Identifier[] textures;
	private final Set<Integer> hiddenDrawables = new HashSet<>();
	private final OutputTarget mainTarget;

	private RenderPipeline pipeNormal;
	private RenderPipeline pipeAdditive;
	private RenderPipeline pipeMultiplicative;

	private final Map<String, RenderLayer> layerCache = new HashMap<>();

	private boolean flipV;
	private boolean masksEnabled;
	private boolean debugLogged;
	private int frameCount;
	private boolean screenDumped;

	private float scale;
	private float offsetX;
	private float offsetY;

	public CubismNativeRenderer(MinecraftClient mc, CubismNativeModel model, Identifier[] textures) {
		this.mc = mc;
		this.model = model;
		this.textures = textures;
		this.mainTarget = new OutputTarget("live2d_main", () -> mc.getFramebuffer());
		for (Identifier texture : textures) {
			mc.getTextureManager().getTexture(texture);
		}
	}

	public void setHiddenDrawables(Collection<String> ids) {
		hiddenDrawables.clear();
		for (int i = 0; i < model.getDrawableCount(); i++) {
			String id = model.getDrawableIds().getPointer((long) i * Native.POINTER_SIZE).getString(0);
			if (ids.contains(id)) {
				hiddenDrawables.add(i);
			}
		}
	}

	public void setMasksEnabled(boolean enabled) {
		this.masksEnabled = enabled;
	}

	public void setFlipV(boolean flipV) {
		this.flipV = flipV;
	}

	public void renderPixels(int w, int h, float globalOpacity) {
		if (w <= 0 || h <= 0) {
			return;
		}
		ensurePipelines();
		computeTransform(w, h);
		int rendered = 0;
		for (int rank = 0; rank < model.getRenderOrderedDrawables().length; rank++) {
			int idx = model.getRenderOrderedDrawables()[rank];
			if (hiddenDrawables.contains(idx)) {
				continue;
			}
			byte dynFlags = model.getDynamicFlags().getByte(idx);
			if ((dynFlags & CubismCore.Flags.VISIBLE) == 0) {
				continue;
			}
			float opacity = model.getOpacities().getFloat((long) idx * 4);
			if (opacity <= 0.0f) {
				continue;
			}
			int texIdx = model.getTextureIndices().getInt((long) idx * 4);
			if (texIdx < 0 || texIdx >= textures.length) {
				continue;
			}
			rendered++;
			renderDrawable(idx, texIdx, globalOpacity);
		}
		if (!debugLogged) {
			debugLogged = true;
			System.out.println(String.format("[Live2D] render done: rendered=%d/%d scale=%.2f offset=(%.2f,%.2f) flipV=%b masks=%b",
					rendered, model.getDrawableCount(), scale, offsetX, offsetY, flipV, masksEnabled));
		}
		frameCount++;
		if (frameCount == 5 && !screenDumped) {
			screenDumped = true;
			try {
				net.minecraft.client.util.ScreenshotRecorder.takeScreenshot(mc.getFramebuffer(), img -> {
					try {
						java.nio.file.Path p = java.nio.file.Path.of(System.getProperty("user.dir"), "live2d_screen.png");
						img.writeTo(p);
						System.out.println("[Live2D] screen dumped to " + p);
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				});
			} catch (Throwable t) {
				t.printStackTrace();
			}
		}
	}

	private void renderDrawable(int idx, int texIdx, float globalOpacity) {
		int flags = model.getConstantFlags().getByte(idx) & 0xFF;
		float opacity = model.getOpacities().getFloat((long) idx * 4) * globalOpacity;
		if (opacity <= 0.001f) {
			return;
		}
		RenderLayer layer = layerFor(blendKey(flags) + "_" + texIdx, pickBlendPipeline(flags), textures[texIdx]);
		BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_TEXTURE_COLOR);
		int emitted = emitMesh(buffer, idx, opacity);
		if (emitted == 0) {
			return;
		}
		layer.draw(buffer.end());
	}

	private int emitMesh(VertexConsumer consumer, int idx, float opacity) {
		int vCount = model.getVertexCounts().getInt((long) idx * 4);
		int iCount = model.getIndexCounts().getInt((long) idx * 4);
		if (vCount <= 0 || iCount <= 0) {
			return 0;
		}
		Pointer positions = model.getVertexPositions().getPointer((long) idx * Native.POINTER_SIZE);
		Pointer uvs = model.getVertexUvs().getPointer((long) idx * Native.POINTER_SIZE);
		Pointer indices = model.getIndices().getPointer((long) idx * Native.POINTER_SIZE);
		int a = colorByte(opacity);
		int emitted = 0;
		for (int j = 0; j < iCount; j++) {
			int vi = indices.getShort((long) j * 2) & 0xFFFF;
			if (vi >= vCount) {
				continue;
			}
			float vx = positions.getFloat((long) vi * 8);
			float vy = positions.getFloat((long) vi * 8 + 4);
			if (Float.isNaN(vx) || Float.isNaN(vy) || Float.isInfinite(vx) || Float.isInfinite(vy)) {
				continue;
			}
			float u = uvs.getFloat((long) vi * 8);
			float v = uvs.getFloat((long) vi * 8 + 4);
			if (flipV) {
				v = 1.0f - v;
			}
			float px = offsetX + (vx - model.getBboxMinX()) * scale;
			float py = offsetY + (model.getBboxMaxY() - vy) * scale;
			consumer.vertex(px, py, 0).color(255, 255, 255, a).texture(u, v);
			emitted++;
		}
		return emitted;
	}

	private void computeTransform(int w, int h) {
		float bw = model.getBboxMaxX() - model.getBboxMinX();
		float bh = model.getBboxMaxY() - model.getBboxMinY();
		if (bw <= 0.0f || bh <= 0.0f) {
			scale = 1.0f;
			offsetX = 0.0f;
			offsetY = 0.0f;
			return;
		}
		scale = Math.min(w / bw, h / bh);
		float drawW = bw * scale;
		float drawH = bh * scale;
		offsetX = (w - drawW) * 0.5f;
		offsetY = (h - drawH) * 0.5f;
	}

	private void ensurePipelines() {
		if (pipeNormal != null) {
			return;
		}
		try {
			pipeNormal = RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
					.withLocation(Identifier.of("live2d", "live2d_gui_normal"))
					.withBlend(BlendFunction.TRANSLUCENT)
					.withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.TRIANGLES)
					.build();
			pipeAdditive = RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
					.withLocation(Identifier.of("live2d", "live2d_gui_additive"))
					.withBlend(BlendFunction.ADDITIVE)
					.withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.TRIANGLES)
					.build();
			pipeMultiplicative = RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
					.withLocation(Identifier.of("live2d", "live2d_gui_multiplicative"))
					.withBlend(new BlendFunction(SourceFactor.DST_COLOR, DestFactor.ONE_MINUS_SRC_ALPHA))
					.withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.TRIANGLES)
					.build();
		} catch (Throwable t) {
			t.printStackTrace(System.err);
		}
	}

	private RenderPipeline pickBlendPipeline(int flags) {
		if ((flags & CubismCore.Flags.ADDITIVE) != 0) {
			return pipeAdditive;
		}
		if ((flags & CubismCore.Flags.MULTIPLICATIVE) != 0) {
			return pipeMultiplicative;
		}
		return pipeNormal;
	}

	private String blendKey(int flags) {
		if ((flags & CubismCore.Flags.ADDITIVE) != 0) {
			return "add";
		}
		if ((flags & CubismCore.Flags.MULTIPLICATIVE) != 0) {
			return "mul";
		}
		return "norm";
	}

	private RenderLayer layerFor(String key, RenderPipeline pipeline, Identifier texture) {
		return layerCache.computeIfAbsent(key, k -> RenderLayer.of("live2d_" + k, RenderSetup.builder(pipeline)
				.texture("Sampler0", texture)
				.outputTarget(mainTarget)
				.expectedBufferSize(65536)
				.build()));
	}

	private static int colorByte(float value) {
		int v = (int) (value * 255.0f);
		return v < 0 ? 0 : (Math.min(v, 255));
	}

	public void close() {
		layerCache.clear();
	}
}
