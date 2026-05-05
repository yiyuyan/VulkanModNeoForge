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

package net.vulkanmod.render.chunk.build.frapi.helper.fabric.interfaces;

import net.minecraft.util.TriState;
import net.vulkanmod.render.chunk.build.frapi.helper.fabric.QuadSpriteBaker;
import net.vulkanmod.render.chunk.build.frapi.helper.fabric.ShadeMode;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;
import org.joml.Vector2fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;


public interface MutableQuadView extends QuadView {
    /**
     * When enabled, causes texture to appear with no rotation. This is the default and does not have to be specified
     * explicitly. Can be overridden by other rotation flags.
     * Pass in bakeFlags parameter to {@link #spriteBake(TextureAtlasSprite, int)}.
     */
    int BAKE_ROTATE_NONE = 0;

    /**
     * When enabled, causes texture to appear rotated 90 degrees clockwise.
     * Pass in bakeFlags parameter to {@link #spriteBake(TextureAtlasSprite, int)}.
     */
    int BAKE_ROTATE_90 = 1;

    /**
     * When enabled, causes texture to appear rotated 180 degrees.
     * Pass in bakeFlags parameter to {@link #spriteBake(TextureAtlasSprite, int)}.
     */
    int BAKE_ROTATE_180 = 2;

    /**
     * When enabled, causes texture to appear rotated 270 degrees clockwise.
     * Pass in bakeFlags parameter to {@link #spriteBake(TextureAtlasSprite, int)}.
     */
    int BAKE_ROTATE_270 = 3;

    /**
     * When enabled, texture coordinates are assigned based on vertex positions and the
     * {@linkplain #nominalFace() nominal face}.
     * Any existing UV coordinates will be replaced and the {@link #BAKE_NORMALIZED} flag will be ignored.
     * Pass in bakeFlags parameter to {@link #spriteBake(TextureAtlasSprite, int)}.
     *
     * <p>UV lock derives texture coordinates based on {@linkplain #nominalFace() nominal face} by projecting the quad
     * onto it, even when the quad is not co-planar with it. This flag is ignored if the normal face is {@code null}.
     */
    int BAKE_LOCK_UV = 4;

    /**
     * When enabled, U texture coordinates for the given sprite are
     * flipped as part of baking. Can be useful for some randomization
     * and texture mapping scenarios. Results are different from what
     * can be obtained via rotation and both can be applied. Any
     * rotation is applied before this flag.
     * Pass in bakeFlags parameter to {@link #spriteBake(TextureAtlasSprite, int)}.
     */
    int BAKE_FLIP_U = 8;

    /**
     * Same as {@link #BAKE_FLIP_U} but for V coordinate.
     */
    int BAKE_FLIP_V = 16;

    /**
     * UV coordinates by default are assumed to be 0-16 scale for consistency
     * with conventional Minecraft model format. This is scaled to 0-1 during
     * baking before interpolation. Model loaders that already have 0-1 coordinates
     * can avoid wasteful multiplication/division by passing 0-1 coordinates directly.
     * Pass in bakeFlags parameter to {@link #spriteBake(TextureAtlasSprite, int)}.
     */
    int BAKE_NORMALIZED = 32;

    /**
     * Sets the geometric vertex position for the given vertex,
     * relative to block origin, (0,0,0). Minecraft rendering is designed
     * for models that fit within a single block space and is recommended
     * that coordinates remain in the 0-1 range, with multi-block meshes
     * split into multiple per-block models.
     *
     * <p>The default value for all vertices is {@code 0.0f}.
     */
    MutableQuadView pos(int vertexIndex, float x, float y, float z);

    /**
     * Sets the geometric position for the given vertex. Only use this method if you already have a {@link Vector3f}.
     * Otherwise, use {@link #pos(int, float, float, float)}.
     */
    default MutableQuadView pos(int vertexIndex, Vector3f pos) {
        return pos(vertexIndex, pos.x, pos.y, pos.z);
    }

    /**
     * Sets the geometric position for the given vertex. Only use this method if you already have a {@link Vector3fc}.
     * Otherwise, use {@link #pos(int, float, float, float)}.
     */
    default MutableQuadView pos(int vertexIndex, Vector3fc pos) {
        return pos(vertexIndex, pos.x(), pos.y(), pos.z());
    }

    /**
     * Sets the color in ARGB format (0xAARRGGBB) for the given vertex.
     *
     * <p>The default value for all vertices is {@code 0xFFFFFFFF}.
     */
    MutableQuadView color(int vertexIndex, int color);

    /**
     * Sets the color in ARGB format (0xAARRGGBB) for all vertices at once.
     *
     * @see #color(int, int)
     */
    default MutableQuadView color(int c0, int c1, int c2, int c3) {
        color(0, c0);
        color(1, c1);
        color(2, c2);
        color(3, c3);
        return this;
    }

    /**
     * Sets the texture coordinates for the given vertex.
     *
     * <p>The default value for all vertices is {@code 0.0f}.
     */
    MutableQuadView uv(int vertexIndex, float u, float v);

    /**
     * Sets the texture coordinates for the given vertex. Only use this method if you already have a {@link Vector2f}.
     * Otherwise, use {@link #uv(int, float, float)}.
     */
    default MutableQuadView uv(int vertexIndex, Vector2f uv) {
        return uv(vertexIndex, uv.x, uv.y);
    }

    /**
     * Sets the texture coordinates for the given vertex. Only use this method if you already have a {@link Vector2fc}.
     * Otherwise, use {@link #uv(int, float, float)}.
     */
    default MutableQuadView uv(int vertexIndex, Vector2fc uv) {
        return uv(vertexIndex, uv.x(), uv.y());
    }

    /**
     * Sets the texture coordinates for all vertices using the given sprite. Can handle UV locking, rotation,
     * interpolation, etc. Control this behavior by passing additive combinations of the BAKE_ flags defined in this
     * interface.
     */
    default MutableQuadView spriteBake(TextureAtlasSprite sprite, int bakeFlags) {
        QuadSpriteBaker.bakeSprite(this, sprite, bakeFlags);
        return this;
    }

    /**
     * Sets the minimum lightmap value for the given vertex. Input values will override lightmap values computed from
     * world state if input values are higher. Exposed for completeness but some rendering implementations with
     * non-standard lighting model may not honor it.
     *
     * <p>For emissive rendering, prefer using {@link #emissive(boolean)}.
     *
     * <p>The default value for all vertices is {@code 0}.
     */
    MutableQuadView lightmap(int vertexIndex, int lightmap);

    /**
     * Sets the lightmap value for all vertices at once.
     *
     * <p>For emissive rendering, prefer using {@link #emissive(boolean)}.
     *
     * @see #lightmap(int, int)
     */
    default MutableQuadView lightmap(int l0, int l1, int l2, int l3) {
        lightmap(0, l0);
        lightmap(1, l1);
        lightmap(2, l2);
        lightmap(3, l3);
        return this;
    }

    /**
     * Sets the normal vector for the given vertex. The {@linkplain #faceNormal() face normal} is used when no vertex
     * normal is provided. Models that have per-vertex normals should include them to get correct lighting when it
     * matters.
     */
    MutableQuadView normal(int vertexIndex, float x, float y, float z);

    /**
     * Sets the normal vector for the given vertex. Only use this method if you already have a {@link Vector3f}.
     * Otherwise, use {@link #normal(int, float, float, float)}.
     */
    default MutableQuadView normal(int vertexIndex, Vector3f normal) {
        return normal(vertexIndex, normal.x, normal.y, normal.z);
    }

    /**
     * Sets the normal vector for the given vertex. Only use this method if you already have a {@link Vector3fc}.
     * Otherwise, use {@link #normal(int, float, float, float)}.
     */
    default MutableQuadView normal(int vertexIndex, Vector3fc normal) {
        return normal(vertexIndex, normal.x(), normal.y(), normal.z());
    }

    /**
     * Sets the nominal face, which provides a hint to the renderer about the facing of this quad. It is not required,
     * but if set, should be the expected value of {@link #lightFace()}. It may be used to shortcut geometric analysis,
     * if the provided value was correct; otherwise, it is ignored.
     *
     * <p>The nominal face is also used for {@link #spriteBake(TextureAtlasSprite, int)} with {@link #BAKE_LOCK_UV}.
     *
     * <p>When {@link #cullFace(Direction)} is called, it also sets the nominal face.
     *
     * <p>The default value is {@code null}.
     *
     * <p>This property is transient. It is set to the same value as {@link #lightFace()} when a quad is decoded.
     */
    MutableQuadView nominalFace(@Nullable Direction face);

    /**
     * Sets the cull face. This quad will not be rendered if its cull face is non-null and the block is occluded by
     * another block in the direction of the cull face.
     *
     * <p>The cull face is different from {@link BakedQuad#direction()}, which is equivalent to {@link #lightFace()}. The
     * light face is computed based on geometry and must be non-null.
     *
     * <p>When called, sets {@link #nominalFace(Direction)} to the same value.
     *
     * <p>The default value is {@code null}.
     *
     * <p>This property is respected only in block contexts. It will not have an effect in other contexts.
     */
    MutableQuadView cullFace(@Nullable Direction face);


    MutableQuadView renderLayer(@Nullable ChunkSectionLayer renderLayer);


    MutableQuadView emissive(boolean emissive);


    MutableQuadView diffuseShade(boolean shade);


    MutableQuadView ambientOcclusion(TriState ao);


    MutableQuadView glint(@Nullable ItemStackRenderState.FoilType glint);


    MutableQuadView shadeMode(ShadeMode mode);

    /**
     * Sets the tint index, which is used to retrieve the tint color.
     *
     * <p>The default value is {@code -1}.
     */
    MutableQuadView tintIndex(int tintIndex);

    /**
     * Sets the tag, which is an arbitrary integer that is meant to be encoded into {@link Mesh}es to later allow
     * performing conditional transformation or filtering on their quads.
     *
     * <p>The default value is {@code 0}.
     */
    MutableQuadView tag(int tag);

    /**
     * Copies all quad data and properties from the given {@link QuadView} to this quad.
     *
     * <p>Calling this method does not emit this quad.
     */
    MutableQuadView copyFrom(QuadView quad);

    /**
     * Sets this quad's vertex data for all vertices using data in given array, starting at the given index. The array
     * must have at least {@link #VANILLA_QUAD_STRIDE} elements starting at the given index. The format of the data must
     * be the same as {@link BakedQuad#vertices()}. This quad's lightmap values and normals will be set even though
     * vanilla does not decode them from packed vertex data.
     *
     * <p>Prefer using {@link #fromBakedQuad(BakedQuad)} instead if you have a {@link BakedQuad}.
     *
     * <p>Calling this method does not emit this quad.
     */
    MutableQuadView fromVanilla(int[] vertexData, int startIndex);

    /**
     * Sets all applicable data and properties of this quad as specified by the given {@link BakedQuad}. This quad's
     * lightmap values and normals will be set even though vanilla does not decode them from packed vertex data. The
     * {@linkplain BakedQuad#lightEmission() baked quad's light emission} will be applied to the lightmap values from
     * the vertex data after copying.
     *
     * <p>Calling this method does not emit this quad.
     */
    MutableQuadView fromBakedQuad(BakedQuad quad);
}
