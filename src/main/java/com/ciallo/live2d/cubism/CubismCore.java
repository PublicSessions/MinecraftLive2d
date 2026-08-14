package com.ciallo.live2d.cubism;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

public interface CubismCore extends Library {
    int csmGetVersion();
    int csmGetLatestMocVersion();
    int csmGetMocVersion(Pointer address, int size);
    int csmHasMocConsistency(Pointer address, int size);
    Pointer csmReviveMocInPlace(Pointer address, int size);
    int csmGetSizeofModel(Pointer moc);
    Pointer csmInitializeModelInPlace(Pointer moc, Pointer address, int size);
    void csmUpdateModel(Pointer model);
    void csmReadCanvasInfo(Pointer model, float[] outSize, float[] outOrigin, float[] outPixelsPerUnit);
    void csmDisposeModel(Pointer model);
    void csmReleaseMoc(Pointer moc);

    int csmGetParameterCount(Pointer model);
    Pointer csmGetParameterIds(Pointer model);
    Pointer csmGetParameterMinimumValues(Pointer model);
    Pointer csmGetParameterMaximumValues(Pointer model);
    Pointer csmGetParameterDefaultValues(Pointer model);
    Pointer csmGetParameterValues(Pointer model);

    int csmGetPartCount(Pointer model);
    Pointer csmGetPartIds(Pointer model);
    Pointer csmGetPartOpacities(Pointer model);
    Pointer csmGetDrawableParentPartIndices(Pointer model);

    int csmGetDrawableCount(Pointer model);
    Pointer csmGetDrawableIds(Pointer model);
    Pointer csmGetDrawableConstantFlags(Pointer model);
    Pointer csmGetDrawableDynamicFlags(Pointer model);
    Pointer csmGetDrawableTextureIndices(Pointer model);
    Pointer csmGetDrawableDrawOrders(Pointer model);
    Pointer csmGetRenderOrders(Pointer model);
    Pointer csmGetDrawableOpacities(Pointer model);
    Pointer csmGetDrawableMaskCounts(Pointer model);
    Pointer csmGetDrawableMasks(Pointer model);
    Pointer csmGetDrawableVertexCounts(Pointer model);
    Pointer csmGetDrawableVertexPositions(Pointer model);
    Pointer csmGetDrawableVertexUvs(Pointer model);
    Pointer csmGetDrawableIndexCounts(Pointer model);
    Pointer csmGetDrawableIndices(Pointer model);
    Pointer csmGetDrawableMultiplyColors(Pointer model);
    Pointer csmGetDrawableScreenColors(Pointer model);
    void csmResetDrawableDynamicFlags(Pointer model);

    static CubismCore load(String libraryPath) {
        return Native.load(libraryPath, CubismCore.class);
    }

    class Flags {
        static final byte VISIBLE = 1;
        static final byte ADDITIVE = 1;
        static final byte MULTIPLICATIVE = 2;
        static final byte INVERTED_MASK = 4;
    }
}