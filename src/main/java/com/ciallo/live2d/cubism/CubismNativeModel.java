package com.ciallo.live2d.cubism;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.io.Closeable;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class CubismNativeModel implements Closeable {
    private static final int MOC_ALIGNMENT = 64;
    private static final int MODEL_ALIGNMENT = 16;

    private final CubismCore core;
    private final Memory mocMemory;
    private final Memory modelMemory;
    private final Pointer model;
    private final Map<String, Integer> parameterIndices = new HashMap<>();
    private final Map<Integer, String> partIds = new HashMap<>();
    private final int drawableCount;
    private final int parameterCount;
    private final float canvasWidth;
    private final float canvasHeight;
    private final float originX;
    private final float originY;
    private final float pixelsPerUnit;
    private float boundsMinX, boundsMinY, boundsMaxX, boundsMaxY;

    private Pointer constantFlags;
    private Pointer dynamicFlags;
    private Pointer textureIndices;
    private Pointer renderOrders;
    private Pointer opacities;
    private Pointer maskCounts;
    private Pointer masks;
    private Pointer vertexCounts;
    private Pointer vertexPositions;
    private Pointer vertexUvs;
    private Pointer indexCounts;
    private Pointer indices;
    private Pointer multiplyColors;
    private Pointer drawableIds;

    private int[] renderOrderedDrawables;
    private final java.util.List<String> eyeBlinkParameterIds = new java.util.ArrayList<>();
    private final java.util.Map<String, CubismExpression> expressions = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, Live2dMotion> motions = new java.util.LinkedHashMap<>();

    private String playingMotion;
    private float motionTime;
    private float motionFadeIn;
    private float motionFadeOut;

    private String playingExpression;
    private float expressionHoldRemaining;
    private float expressionFade;
    private float expressionWeight;

    private static class ParamOverride {
        float value;
        float hold;
        float fade;
        float elapsed;
    }
    private final java.util.Map<String, ParamOverride> paramOverrides = new java.util.LinkedHashMap<>();

    public static CubismNativeModel load(MinecraftClient client, Identifier mocId, Path gameDir) throws Exception {
        try (InputStream input = client.getResourceManager().getResource(mocId).orElseThrow().getInputStream()) {
            return loadBytes(input.readAllBytes(), gameDir);
        }
    }

    public static CubismNativeModel loadBytes(byte[] mocBytes, Path gameDir) throws Exception {
        CubismCore core = loadCore(gameDir);
        return new CubismNativeModel(mocBytes, core);
    }

    private static CubismCore loadCore(Path gameDir) throws Exception {
        String resourcePath = "/assets/live2d/live2d/native/windows/x86_64/live2dcubismcore.dll";
        try (InputStream in = CubismNativeModel.class.getResourceAsStream(resourcePath)) {
            if (in != null) {
                Path dllPath = gameDir.resolve("live2d/native/live2dcubismcore.dll");
                Files.createDirectories(dllPath.getParent());
                if (!Files.exists(dllPath)) {
                    Files.copy(in, dllPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                return CubismCore.load(dllPath.toAbsolutePath().toString());
            }
        }
        return CubismCore.load("live2dcubismcore");
    }

    private CubismNativeModel(byte[] mocBytes, CubismCore core) {
        this.core = core;

        this.mocMemory = new Memory(mocBytes.length + MOC_ALIGNMENT);
        long mocAddr = Pointer.nativeValue(mocMemory);
        long mocOffset = (MOC_ALIGNMENT - (mocAddr & (MOC_ALIGNMENT - 1))) & (MOC_ALIGNMENT - 1);
        mocMemory.write(mocOffset, mocBytes, 0, mocBytes.length);
        Pointer alignedMoc = mocMemory.share(mocOffset);

        int consistent = core.csmHasMocConsistency(alignedMoc, mocBytes.length);
        if (consistent == 0) {
            throw new IllegalStateException("Invalid Cubism moc3 data");
        }

        Pointer moc = core.csmReviveMocInPlace(alignedMoc, mocBytes.length);
        if (moc == null || Pointer.nativeValue(moc) == 0) {
            throw new IllegalStateException("Cubism Core failed to revive moc");
        }

        int modelSize = core.csmGetSizeofModel(moc);
        this.modelMemory = new Memory(modelSize + MODEL_ALIGNMENT);
        long modelAddr = Pointer.nativeValue(modelMemory);
        long modelOffset = (MODEL_ALIGNMENT - (modelAddr & (MODEL_ALIGNMENT - 1))) & (MODEL_ALIGNMENT - 1);
        this.model = core.csmInitializeModelInPlace(moc, modelMemory.share(modelOffset), modelSize);
        if (this.model == null || Pointer.nativeValue(this.model) == 0) {
            throw new IllegalStateException("Cubism Core failed to initialize model");
        }

        this.parameterCount = core.csmGetParameterCount(this.model);
        this.drawableCount = core.csmGetDrawableCount(this.model);

        cacheParameterIndices();
        cachePartIds();

        this.constantFlags = core.csmGetDrawableConstantFlags(model);
        this.dynamicFlags = core.csmGetDrawableDynamicFlags(model);
        this.textureIndices = core.csmGetDrawableTextureIndices(model);
        this.renderOrders = core.csmGetRenderOrders(model);
        this.opacities = core.csmGetDrawableOpacities(model);
        this.maskCounts = core.csmGetDrawableMaskCounts(model);
        this.masks = core.csmGetDrawableMasks(model);
        this.vertexCounts = core.csmGetDrawableVertexCounts(model);
        this.vertexPositions = core.csmGetDrawableVertexPositions(model);
        this.vertexUvs = core.csmGetDrawableVertexUvs(model);
        this.indexCounts = core.csmGetDrawableIndexCounts(model);
        this.indices = core.csmGetDrawableIndices(model);
        this.multiplyColors = core.csmGetDrawableMultiplyColors(model);
        this.drawableIds = core.csmGetDrawableIds(model);

        computeRenderOrder();
        cacheEyeBlinkParameters();

        float[] size = new float[2];
        float[] origin = new float[2];
        float[] ppu = new float[1];
        core.csmReadCanvasInfo(this.model, size, origin, ppu);
        this.canvasWidth = size[0] / Math.max(1.0f, ppu[0]);
        this.canvasHeight = size[1] / Math.max(1.0f, ppu[0]);
        this.originX = origin[0] / Math.max(1.0f, ppu[0]);
        this.originY = origin[1] / Math.max(1.0f, ppu[0]);
        this.pixelsPerUnit = ppu[0];

        update();
        refreshBounds();
        System.out.println("[Live2D] CubismNativeModel loaded: params=" + parameterCount + " drawables=" + drawableCount + " canvas=" + canvasWidth + "x" + canvasHeight + " bounds=" + boundsMinX + "," + boundsMinY + "->" + boundsMaxX + "," + boundsMaxY);
    }

    public void update() {
        core.csmUpdateModel(model);
    }

    public void setParameter(String id, float value) {
        Integer index = parameterIndices.get(id);
        if (index == null) return;
        float min = core.csmGetParameterMinimumValues(model).getFloat((long) index * 4);
        float max = core.csmGetParameterMaximumValues(model).getFloat((long) index * 4);
        float clamped = Math.max(min, Math.min(max, value));
        core.csmGetParameterValues(model).setFloat((long) index * 4, clamped);
    }

    public int getDrawableCount() { return drawableCount; }
    public float getCanvasWidth() { return canvasWidth; }
    public float getCanvasHeight() { return canvasHeight; }
    public float getOriginX() { return originX; }
    public float getOriginY() { return originY; }
    public float getPixelsPerUnit() { return pixelsPerUnit; }
    public float getBoundsMinX() { return boundsMinX; }
    public float getBoundsMinY() { return boundsMinY; }
    public float getBoundsMaxX() { return boundsMaxX; }
    public float getBoundsMaxY() { return boundsMaxY; }
    public float getBboxMinX() { return boundsMinX; }
    public float getBboxMinY() { return boundsMinY; }
    public float getBboxMaxX() { return boundsMaxX; }
    public float getBboxMaxY() { return boundsMaxY; }
    public float getBoundsWidth() { return Math.max(0.001f, boundsMaxX - boundsMinX); }
    public float getBoundsHeight() { return Math.max(0.001f, boundsMaxY - boundsMinY); }

    public int getDrawableRenderOrder(int idx) {
        return core.csmGetRenderOrders(model).getInt((long) idx * 4);
    }

    public int getDrawableDrawOrder(int idx) {
        return core.csmGetDrawableDrawOrders(model).getInt((long) idx * 4);
    }

    public String getDrawableId(int idx) {
        Pointer ids = core.csmGetDrawableIds(model);
        Pointer idPtr = ids.getPointer((long) idx * Native.POINTER_SIZE);
        return idPtr == null ? "" : readCString(idPtr);
    }

    public int getDrawableParentPartIndex(int idx) {
        return core.csmGetDrawableParentPartIndices(model).getInt((long) idx * 4);
    }

    public String getDrawableParentPartId(int idx) {
        return partIds.getOrDefault(getDrawableParentPartIndex(idx), "");
    }

    public boolean isDrawableVisible(int idx) {
        byte flags = core.csmGetDrawableDynamicFlags(model).getByte((long) idx);
        return (flags & 1) != 0;
    }

    public boolean isDrawableAdditive(int idx) {
        byte flags = core.csmGetDrawableConstantFlags(model).getByte((long) idx);
        return (flags & 1) != 0;
    }

    public boolean isDrawableMultiplicative(int idx) {
        byte flags = core.csmGetDrawableConstantFlags(model).getByte((long) idx);
        return (flags & 2) != 0;
    }

    public int getDrawableTextureIndex(int idx) {
        return core.csmGetDrawableTextureIndices(model).getInt((long) idx * 4);
    }

    public float getDrawableOpacity(int idx) {
        return core.csmGetDrawableOpacities(model).getFloat((long) idx * 4);
    }

    public int getDrawableVertexCount(int idx) {
        return core.csmGetDrawableVertexCounts(model).getInt((long) idx * 4);
    }

    public int getDrawableIndexCount(int idx) {
        return core.csmGetDrawableIndexCounts(model).getInt((long) idx * 4);
    }

    public int getDrawableMaskCount(int idx) {
        return core.csmGetDrawableMaskCounts(model).getInt((long) idx * 4);
    }

    public float[] getDrawableVertexPositions(int idx) {
        int count = getDrawableVertexCount(idx) * 2;
        Pointer ptr = core.csmGetDrawableVertexPositions(model).getPointer((long) idx * Native.POINTER_SIZE);
        return ptr == null ? new float[0] : ptr.getFloatArray(0, count);
    }

    public float[] getDrawableVertexUvs(int idx) {
        int count = getDrawableVertexCount(idx) * 2;
        Pointer ptr = core.csmGetDrawableVertexUvs(model).getPointer((long) idx * Native.POINTER_SIZE);
        return ptr == null ? new float[0] : ptr.getFloatArray(0, count);
    }

    public short[] getDrawableIndices(int idx) {
        int count = getDrawableIndexCount(idx);
        Pointer ptr = core.csmGetDrawableIndices(model).getPointer((long) idx * Native.POINTER_SIZE);
        return ptr == null ? new short[0] : ptr.getShortArray(0, count);
    }

    private void cacheParameterIndices() {
        Pointer ids = core.csmGetParameterIds(model);
        for (int i = 0; i < parameterCount; i++) {
            Pointer idPtr = ids.getPointer((long) i * Native.POINTER_SIZE);
            if (idPtr == null) continue;
            String id = readCString(idPtr);
            if (!id.isEmpty()) parameterIndices.put(id, i);
        }
    }

    private void cachePartIds() {
        int partCount = core.csmGetPartCount(model);
        Pointer ids = core.csmGetPartIds(model);
        for (int i = 0; i < partCount; i++) {
            Pointer idPtr = ids.getPointer((long) i * Native.POINTER_SIZE);
            if (idPtr == null) continue;
            partIds.put(i, readCString(idPtr));
        }
    }

    private void refreshBounds() {
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;

        for (int i = 0; i < drawableCount; i++) {
            float[] verts = getDrawableVertexPositions(i);
            for (int j = 0; j + 1 < verts.length; j += 2) {
                minX = Math.min(minX, verts[j]);
                maxX = Math.max(maxX, verts[j]);
                minY = Math.min(minY, verts[j + 1]);
                maxY = Math.max(maxY, verts[j + 1]);
            }
        }

        if (minX == Float.POSITIVE_INFINITY) {
            minX = originX - canvasWidth * 0.5f;
            maxX = originX + canvasWidth * 0.5f;
            minY = originY - canvasHeight * 0.5f;
            maxY = originY + canvasHeight * 0.5f;
        }

        boundsMinX = minX;
        boundsMinY = minY;
        boundsMaxX = maxX;
        boundsMaxY = maxY;
    }

    private String readCString(Pointer ptr) {
        byte[] bytes = ptr.getByteArray(0, 128);
        int len = 0;
        while (len < bytes.length && bytes[len] != 0) len++;
        return new String(Arrays.copyOf(bytes, len), StandardCharsets.UTF_8);
    }

    public float getBboxAspect() {
        return canvasWidth / Math.max(1.0f, canvasHeight);
    }

    public void resetParametersToDefault() {
        Pointer defaults = core.csmGetParameterDefaultValues(model);
        for (int i = 0; i < parameterCount; i++) {
            core.csmGetParameterValues(model).setFloat((long) i * 4, defaults.getFloat((long) i * 4));
        }
    }

    public boolean hasExpression(String name) {
        return expressions.containsKey(name);
    }

    public void applyExpression(String name, float weight) {
        CubismExpression expr = expressions.get(name);
        if (expr == null) return;
        for (String param : expr.getParameters().keySet()) {
            Integer idx = parameterIndices.get(param);
            if (idx == null) continue;
            float base = core.csmGetParameterDefaultValues(model).getFloat((long) idx * 4);
            float delta = expr.getParameters().get(param);
            float min = core.csmGetParameterMinimumValues(model).getFloat((long) idx * 4);
            float max = core.csmGetParameterMaximumValues(model).getFloat((long) idx * 4);
            float value = Math.max(min, Math.min(max, base + delta * weight));
            core.csmGetParameterValues(model).setFloat((long) idx * 4, value);
        }
    }

    public boolean hasParameter(String id) {
        return parameterIndices.containsKey(id);
    }

    public void addMotion(String name, Live2dMotion motion) {
        motions.put(name, motion);
    }

    public boolean hasMotion(String name) {
        return motions.containsKey(name);
    }

    public java.util.Map<String, Live2dMotion> getMotions() {
        return java.util.Collections.unmodifiableMap(motions);
    }

    public String getPlayingMotion() {
        return playingMotion;
    }

    public void playMotion(String name) {
        Live2dMotion motion = motions.get(name);
        if (motion == null) {
            return;
        }
        playingMotion = name;
        motionTime = 0.0f;
        motionFadeIn = motion.getFadeIn();
        motionFadeOut = motion.getFadeOut();
    }

    public void stopMotion() {
        playingMotion = null;
    }

    public void playExpression(String name, float holdSeconds, float fadeSeconds) {
        if (!expressions.containsKey(name)) {
            return;
        }
        playingExpression = name;
        expressionHoldRemaining = Math.max(0.0f, holdSeconds);
        expressionFade = Math.max(0.05f, fadeSeconds);
        expressionWeight = 0.0f;
    }

    public void stopTransientExpression() {
        playingExpression = null;
        expressionWeight = 0.0f;
    }

    public void setParameterOverride(String id, float value, float hold, float fade) {
        if (!parameterIndices.containsKey(id)) {
            return;
        }
        ParamOverride o = new ParamOverride();
        o.value = value;
        o.hold = Math.max(0.0f, hold);
        o.fade = Math.max(0.05f, fade);
        o.elapsed = 0.0f;
        paramOverrides.put(id, o);
    }

    public void stopAllTransients() {
        playingMotion = null;
        playingExpression = null;
        expressionWeight = 0.0f;
        paramOverrides.clear();
    }

    public void updateTransientAnimations(float dt) {
        updateMotion(dt);
        updateExpressionOverlay(dt);
        updateParamOverrides(dt);
    }

    private void updateMotion(float dt) {
        if (playingMotion == null) {
            return;
        }
        Live2dMotion motion = motions.get(playingMotion);
        if (motion == null) {
            playingMotion = null;
            return;
        }
        motionTime += dt;
        if (motion.isLoop()) {
            motionTime = motionTime % motion.getDuration();
        } else if (motionTime >= motion.getDuration()) {
            playingMotion = null;
            return;
        }
        float weight = 1.0f;
        if (motionFadeIn > 0.0f && motionTime < motionFadeIn) {
            weight *= motionTime / motionFadeIn;
        }
        float remaining = motion.getDuration() - motionTime;
        if (motionFadeOut > 0.0f && remaining < motionFadeOut) {
            weight *= remaining / motionFadeOut;
        }
        if (weight <= 0.0001f) {
            return;
        }
        for (Live2dMotion.Curve curve : motion.getCurves()) {
            if (!"Parameter".equals(curve.target)) {
                continue;
            }
            Integer idx = parameterIndices.get(curve.id);
            if (idx == null) {
                continue;
            }
            float value = curve.valueAt(motionTime);
            if (Float.isNaN(value)) {
                continue;
            }
            float def = defaultValue(idx);
            setParameterRaw(idx, def + (value - def) * weight);
        }
    }

    private void updateExpressionOverlay(float dt) {
        if (playingExpression == null) {
            return;
        }
        if (expressionWeight < 1.0f) {
            expressionWeight = Math.min(1.0f, expressionWeight + dt / expressionFade);
        }
        if (expressionHoldRemaining > 0.0f) {
            expressionHoldRemaining -= dt;
            if (expressionHoldRemaining <= 0.0f) {
                expressionHoldRemaining = 0.0f;
            }
        } else if (expressionWeight > 0.0f) {
            expressionWeight = Math.max(0.0f, expressionWeight - dt / expressionFade);
        }
        applyExpression(playingExpression, expressionWeight);
        if (expressionWeight <= 0.0f) {
            playingExpression = null;
        }
    }

    private void updateParamOverrides(float dt) {
        java.util.Iterator<java.util.Map.Entry<String, ParamOverride>> it = paramOverrides.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<String, ParamOverride> entry = it.next();
            ParamOverride o = entry.getValue();
            o.elapsed += dt;
            Integer idx = parameterIndices.get(entry.getKey());
            if (idx == null) {
                it.remove();
                continue;
            }
            float weight;
            if (o.elapsed <= o.fade) {
                weight = o.elapsed / o.fade;
            } else if (o.elapsed <= o.fade + o.hold) {
                weight = 1.0f;
            } else if (o.elapsed <= o.fade + o.hold + o.fade) {
                weight = 1.0f - (o.elapsed - o.fade - o.hold) / o.fade;
            } else {
                it.remove();
                continue;
            }
            float def = defaultValue(idx);
            float clamped = Math.max(0.0f, Math.min(1.0f, weight));
            setParameterRaw(idx, def + (o.value - def) * clamped);
        }
    }

    private float defaultValue(int idx) {
        return core.csmGetParameterDefaultValues(model).getFloat((long) idx * 4);
    }

    private void setParameterRaw(int idx, float value) {
        float min = core.csmGetParameterMinimumValues(model).getFloat((long) idx * 4);
        float max = core.csmGetParameterMaximumValues(model).getFloat((long) idx * 4);
        core.csmGetParameterValues(model).setFloat((long) idx * 4, Math.max(min, Math.min(max, value)));
    }

    public java.util.List<String> getEyeBlinkParameterIds() {
        return java.util.Collections.unmodifiableList(eyeBlinkParameterIds);
    }

    public java.util.Map<String, CubismExpression> getExpressions() {
        return java.util.Collections.unmodifiableMap(expressions);
    }

    public void addExpression(String name, CubismExpression expr) {
        expressions.put(name, expr);
    }

    public Pointer getConstantFlags() { return constantFlags; }
    public Pointer getDynamicFlags() { return dynamicFlags; }
    public Pointer getTextureIndices() { return textureIndices; }
    public Pointer getRenderOrders() { return renderOrders; }
    public Pointer getOpacities() { return opacities; }
    public Pointer getMaskCounts() { return maskCounts; }
    public Pointer getMasks() { return masks; }
    public Pointer getVertexCounts() { return vertexCounts; }
    public Pointer getVertexPositions() { return vertexPositions; }
    public Pointer getVertexUvs() { return vertexUvs; }
    public Pointer getIndexCounts() { return indexCounts; }
    public Pointer getIndices() { return indices; }
    public Pointer getMultiplyColors() { return multiplyColors; }
    public Pointer getDrawableIds() { return drawableIds; }

    public int[] getRenderOrderedDrawables() {
        return renderOrderedDrawables;
    }

    private void computeRenderOrder() {
        Integer[] order = new Integer[drawableCount];
        for (int i = 0; i < drawableCount; i++) {
            order[i] = i;
        }
        java.util.List<Integer> sorted = new java.util.ArrayList<>(java.util.List.of(order));
        sorted.sort((a, b) -> {
            int cmp = java.lang.Integer.compare(
                    renderOrders.getInt((long) a * 4),
                    renderOrders.getInt((long) b * 4));
            if (cmp != 0) return cmp;
            return java.lang.Integer.compare(
                    core.csmGetDrawableDrawOrders(model).getInt((long) a * 4),
                    core.csmGetDrawableDrawOrders(model).getInt((long) b * 4));
        });
        renderOrderedDrawables = new int[drawableCount];
        for (int i = 0; i < drawableCount; i++) {
            renderOrderedDrawables[i] = sorted.get(i);
        }
    }

    private void cacheEyeBlinkParameters() {
        Pointer ids = core.csmGetParameterIds(model);
        for (int i = 0; i < parameterCount; i++) {
            Pointer idPtr = ids.getPointer((long) i * Native.POINTER_SIZE);
            if (idPtr == null) continue;
            String id = readCString(idPtr);
            if (id.contains("Eye") || id.contains("EyeLOpen") || id.contains("EyeROpen")) {
                eyeBlinkParameterIds.add(id);
            }
        }
        if (eyeBlinkParameterIds.isEmpty()) {
            eyeBlinkParameterIds.add("ParamEyeLOpen");
            eyeBlinkParameterIds.add("ParamEyeROpen");
        }
    }

    @Override
    public void close() {
        if (model != null && Pointer.nativeValue(model) != 0) {
            core.csmDisposeModel(model);
        }
        if (mocMemory != null) mocMemory.close();
        if (modelMemory != null) modelMemory.close();
    }
}