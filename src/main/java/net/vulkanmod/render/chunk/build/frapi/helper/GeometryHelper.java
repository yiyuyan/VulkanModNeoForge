/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.vulkanmod.render.chunk.build.frapi.helper;

import static net.minecraft.util.Mth.equal;

import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.vulkanmod.render.chunk.build.frapi.mesh.QuadViewImpl;
import org.joml.Vector3fc;

/**
 * Static routines of general utility for renderer implementations.
 * Renderers are not required to use these helpers, but they were
 * designed to be usable without the default renderer.
 */
public abstract class GeometryHelper {
	private GeometryHelper() { }

	/** set when a quad touches all four corners of a unit cube. */
	public static final int CUBIC_FLAG = 1;

	/** set when a quad is parallel to (but not necessarily on) a its light face. */
	public static final int AXIS_ALIGNED_FLAG = CUBIC_FLAG << 1;

	/** set when a quad is coplanar with its light face. Implies {@link #AXIS_ALIGNED_FLAG} */
	public static final int LIGHT_FACE_FLAG = AXIS_ALIGNED_FLAG << 1;

	/** how many bits quad header encoding should reserve for encoding geometry flags. */
	public static final int FLAG_BIT_COUNT = 3;

	private static final float EPS_MIN = 0.0001f;
	private static final float EPS_MAX = 1.0f - EPS_MIN;

	/**
	 * Analyzes the quad and returns a value with some combination
	 * of {@link #AXIS_ALIGNED_FLAG}, {@link #LIGHT_FACE_FLAG} and {@link #CUBIC_FLAG}.
	 * Intended use is to optimize lighting when the geometry is regular.
	 * Expects convex quads with all points co-planar.
	 */
	public static int computeShapeFlags(QuadViewImpl quad) {
		Direction lightFace = quad.lightFace();
		int bits = 0;

		if (isQuadParallelToFace(lightFace, quad)) {
			bits |= AXIS_ALIGNED_FLAG;

			if (isParallelQuadOnFace(lightFace, quad)) {
				bits |= LIGHT_FACE_FLAG;
			}
		}

		if (isQuadCubic(lightFace, quad)) {
			bits |= CUBIC_FLAG;
		}

		return bits;
	}

	/**
	 * Returns true if quad is parallel to the given face.
	 * Does not validate quad winding order.
	 * Expects convex quads with all points co-planar.
	 */
	public static boolean isQuadParallelToFace(Direction face, QuadViewImpl quad) {
		int i = face.getAxis().ordinal();
		final float val = quad.posByIndex(0, i);
		return equal(val, quad.posByIndex(1, i)) && equal(val, quad.posByIndex(2, i)) && equal(val, quad.posByIndex(3, i));
	}


	public static boolean isParallelQuadOnFace(Direction lightFace, QuadViewImpl quad) {
		final float x = quad.posByIndex(0, lightFace.getAxis().ordinal());
		return lightFace.getAxisDirection() == AxisDirection.POSITIVE ? x >= EPS_MAX : x <= EPS_MIN;
	}

	/**
	 * Returns true if quad is truly a quad (not a triangle) and fills a full block cross-section.
	 * If known to be true, allows use of a simpler/faster AO lighting algorithm.
	 *
	 * <p>Does not check if quad is actually coplanar with the light face, nor does it check that all
	 * quad vertices are coplanar with each other.
	 *
	 * <p>Expects convex quads with all points co-planar.
	 */
	public static boolean isQuadCubic(Direction lightFace, QuadViewImpl quad) {
		int a, b;

		switch (lightFace) {
		case EAST:
		case WEST:
			a = 1;
			b = 2;
			break;
		case UP:
		case DOWN:
			a = 0;
			b = 2;
			break;
		case SOUTH:
		case NORTH:
			a = 1;
			b = 0;
			break;
		default:
			// handle WTF case
			return false;
		}

		return confirmSquareCorners(a, b, quad);
	}


	private static boolean confirmSquareCorners(int aCoordinate, int bCoordinate, QuadViewImpl quad) {
		int flags = 0;

		for (int i = 0; i < 4; i++) {
			final float a = quad.posByIndex(i, aCoordinate);
			final float b = quad.posByIndex(i, bCoordinate);

			if (a <= EPS_MIN) {
				if (b <= EPS_MIN) {
					flags |= 1;
				} else if (b >= EPS_MAX) {
					flags |= 2;
				} else {
					return false;
				}
			} else if (a >= EPS_MAX) {
				if (b <= EPS_MIN) {
					flags |= 4;
				} else if (b >= EPS_MAX) {
					flags |= 8;
				} else {
					return false;
				}
			} else {
				return false;
			}
		}

		return flags == 15;
	}

	public static Direction lightFace(QuadViewImpl quad) {
		final Vector3fc normal = quad.faceNormal();
        return switch (GeometryHelper.longestAxis(normal)) {
            case X -> normal.x() > 0 ? Direction.EAST : Direction.WEST;
            case Y -> normal.y() > 0 ? Direction.UP : Direction.DOWN;
            case Z -> normal.z() > 0 ? Direction.SOUTH : Direction.NORTH;
            default ->
                // handle WTF case
                    Direction.UP;
        };
	}

	/**
	 * Simple 4-way compare, doesn't handle NaN values.
	 */
	public static float min(float a, float b, float c, float d) {
		final float x = Math.min(a, b);
		final float y = Math.min(c, d);
		return Math.min(x, y);
	}

	/**
	 * Simple 4-way compare, doesn't handle NaN values.
	 */
	public static float max(float a, float b, float c, float d) {
		final float x = Math.max(a, b);
		final float y = Math.max(c, d);
		return Math.max(x, y);
	}

	/**
	 * @see #longestAxis(float, float, float)
	 */
	public static Axis longestAxis(Vector3fc vec) {
		return longestAxis(vec.x(), vec.y(), vec.z());
	}

	/**
	 * Identifies the largest (max absolute magnitude) component (X, Y, Z) in the given vector.
	 */
	public static Axis longestAxis(float normalX, float normalY, float normalZ) {
		Axis result = Axis.Y;
		float longest = Math.abs(normalY);
		float a = Math.abs(normalX);

		if (a > longest) {
			result = Axis.X;
			longest = a;
		}

		return Math.abs(normalZ) > longest
				? Axis.Z : result;
	}
}
