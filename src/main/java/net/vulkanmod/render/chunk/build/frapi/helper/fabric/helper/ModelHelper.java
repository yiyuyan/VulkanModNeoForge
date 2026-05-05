package net.vulkanmod.render.chunk.build.frapi.helper.fabric.helper;

import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public final class ModelHelper {
    /** @see #faceFromIndex(int) */
    private static final Direction[] FACES = Arrays.copyOf(Direction.values(), 7);

    /** Result from {@link #toFaceIndex(Direction)} for null values. */
    public static final int NULL_FACE_ID = 6;

    private ModelHelper() { }

    /**
     * Convenient way to encode faces that may be null.
     * Null is returned as {@link #NULL_FACE_ID}.
     * Use {@link #faceFromIndex(int)} to retrieve encoded face.
     */
    public static int toFaceIndex(@Nullable Direction face) {
        return face == null ? NULL_FACE_ID : face.get3DDataValue();
    }


    @Nullable
    public static Direction faceFromIndex(int faceIndex) {
        return FACES[faceIndex];
    }
}