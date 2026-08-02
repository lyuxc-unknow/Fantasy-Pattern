package cn.lyxc.fantasytechnology.client.render;

import cn.lyxc.fantasytechnology.blockentity.FantasyAnnihilationBlockEntity;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import javax.annotation.ParametersAreNonnullByDefault;


/// World renderer for the fantasy annihilation block while the multiblock is formed: six rainbow square frames, one
/// floating outside each face of the block, like opened panels of a cut-open cube. Each frame lies parallel to its
/// face and spins around the face normal, with every other frame spinning in the opposite direction (clockwise /
/// counter-clockwise). Only the square outlines are drawn - thin rectangles lying in the frame's plane, so no interior
/// fill or ghosting is ever visible - and all frames cycle through the rainbow together.
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class FantasyAnnihilationRenderer implements BlockEntityRenderer<FantasyAnnihilationBlockEntity> {

    /// The eight rainbow colors, cycled through together.
    private static final float[][] RAINBOW = {
            { 1.00f, 0.28f, 0.28f }, // red
            { 1.00f, 0.62f, 0.15f }, // orange
            { 1.00f, 0.90f, 0.25f }, // yellow
            { 0.35f, 1.00f, 0.35f }, // green
            { 0.25f, 0.95f, 1.00f }, // cyan
            { 0.35f, 0.50f, 1.00f }, // blue
            { 0.70f, 0.35f, 1.00f }, // violet
            { 1.00f, 0.35f, 0.80f } // magenta
    };

    /// Square edge length and distance of the face frames from the block center (block surface is at 0.5).
    private static final float SQUARE_SIZE = 3.0f;
    private static final float FACE_DISTANCE = 0.55f;
    /// Slow spin speed (around the face normal).
    private static final float SPIN_SPEED = 0.1f;
    /// One full turn; both animation phases are angles and are folded back into this range, see {@link #render}.
    private static final double TAU = Math.PI * 2;

    /// Position and outward normal of each of the six face frames, as {@code px, py, pz, nx, ny, nz}.
    private static final float[][] FACES = {
            { 0f, FACE_DISTANCE, 0f, 0f, 1f, 0f }, // +Y
            { 0f, -FACE_DISTANCE, 0f, 0f, -1f, 0f }, // -Y
            { FACE_DISTANCE, 0f, 0f, 1f, 0f, 0f }, // +X
            { -FACE_DISTANCE, 0f, 0f, -1f, 0f, 0f }, // -X
            { 0f, 0f, FACE_DISTANCE, 0f, 0f, 1f }, // +Z
            { 0f, 0f, -FACE_DISTANCE, 0f, 0f, -1f } // -Z
    };

    /// Glowing unlit rendering like {@link RenderType#lightning()}. Three of the states carry the look:
    /// <ul>
    /// <li>the {@code COLOR_WRITE} mask leaves depth writes off, so clouds, end portals and everything else behind a
    /// frame stay visible when looking through it;</li>
    /// <li>depth testing is disabled as well, so the three frames on the far side of the block are not swallowed by
    /// the block itself and every frame is visible from every angle. The trade-off is that the frames also show
    /// through terrain; switch this to {@code LEQUAL_DEPTH_TEST} if they should be occluded normally instead;</li>
    /// <li>culling is disabled so a frame is drawn from either side. Because the frames are flat in-plane rectangles
    /// (no tilt, no cross-section quads), their front and back edges project onto the same screen position, so they
    /// still read as one clean outline, never as a filled or doubled frame.</li>
    /// </ul>
    private static final RenderType GLOW = RenderType.create("fantasy_technology_annihilation_glow",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 8192,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setLayeringState(RenderStateShard.NO_LAYERING)
                    .createCompositeState(false));

    /// Scratch space for the corners of the frame currently being drawn; only the render thread ever touches it.
    private final float[] corners = new float[12];

    public FantasyAnnihilationRenderer(BlockEntityRendererProvider.Context context) {
    }

    /// The frames float well outside the block's own chunk section, and sections are culled before
    /// {@link #getRenderBoundingBox} is ever consulted - so looking up (or otherwise moving the block out of view)
    /// would cut the whole effect. Reporting the block entity as renderable off screen puts it into the level
    /// renderer's global set instead, which is re-populated by every section rebuild and therefore survives a level
    /// renderer reset (F3+A, a render distance change, a resource reload).
    @Override
    public boolean shouldRenderOffScreen(FantasyAnnihilationBlockEntity blockEntity) {
        return true;
    }

    /// A frame corner sits about 2.2 blocks from the block center: {@link #FACE_DISTANCE} along the face normal, plus
    /// half a diagonal of the square out from there. Global block entities are still frustum-culled against this box
    /// in {@code ClientHooks.isBlockEntityRendererVisible}, so it has to cover them.
    @Override
    public AABB getRenderBoundingBox(FantasyAnnihilationBlockEntity blockEntity) {
        return new AABB(blockEntity.getBlockPos()).inflate(3.0, 3.0, 3.0);
    }

    @Override
    public void render(FantasyAnnihilationBlockEntity blockEntity, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (!blockEntity.isStructureFormed()) {
            return;
        }
        Level level = blockEntity.getLevel();
        if (level == null) {
            return;
        }

        // The game time is a long, and narrowing it straight to a float would lose tick resolution past 2^24 ticks
        // (about nine days of continuous running), at which point the animation stutters and eventually freezes. The
        // phases are therefore derived in double and folded into a single turn before they become floats.
        double time = level.getGameTime() + partialTick;
        float spin = (float) (time * SPIN_SPEED % TAU);
        float pulse = 0.8f + 0.2f * (0.5f + 0.5f * Mth.sin((float) (time * 0.2 % TAU)));
        // All frames cycle through the rainbow together.
        int colorShift = (int) ((long) (time * 0.4) % RAINBOW.length);

        poseStack.pushPose();
        // Everything orbits around the center of the block.
        poseStack.translate(0.5, 0.5, 0.5);

        VertexConsumer glow = buffer.getBuffer(GLOW);

        // Six square frames, one outside each face of the block, each spinning around its face normal. Every other
        // frame spins in the opposite direction (clockwise / counter-clockwise).
        for (int i = 0; i < FACES.length; i++) {
            float[] f = FACES[i];
            float dir = (i % 2 == 0) ? spin : -spin;
            drawSpinningSquareFrame(glow, poseStack, corners, f[0], f[1], f[2], f[3], f[4], f[5], dir,
                    (i + colorShift) % RAINBOW.length, pulse);
        }

        poseStack.popPose();
    }

    /// Computes the four corners of a spinning square frame at a fixed position with the given face normal into
    /// {@code out} (12 floats, x,y,z × 4). The frame lies in the plane perpendicular to the normal (one of the block's
    /// face directions), so front and back edges project onto the same screen position and only one clean outline is
    /// ever visible. The frame spins around its normal.
    private static void squareCorners(float[] out, float px, float py, float pz,
            float nx, float ny, float nz, float spinAngle) {
        // A reference direction not parallel to the normal, for building the first in-plane axis.
        float refX = 0f;
        float refY = 1f;
        float refZ = 0f;
        if (Math.abs(ny) > 0.9f) {
            refX = 1f;
            refY = 0f;
            refZ = 0f;
        }
        // First in-plane axis: normalize(ref × normal).
        float e1x = refY * nz - refZ * ny;
        float e1y = refZ * nx - refX * nz;
        float e1z = refX * ny - refY * nx;
        float e1len = Mth.sqrt(e1x * e1x + e1y * e1y + e1z * e1z);
        e1x /= e1len;
        e1y /= e1len;
        e1z /= e1len;
        // Second in-plane axis: normal × e1.
        float e2x = ny * e1z - nz * e1y;
        float e2y = nz * e1x - nx * e1z;
        float e2z = nx * e1y - ny * e1x;

        // Spin the in-plane axes around the normal.
        float ca = Mth.cos(spinAngle);
        float sa = Mth.sin(spinAngle);
        float a1x = e1x * ca + e2x * sa;
        float a1y = e1y * ca + e2y * sa;
        float a1z = e1z * ca + e2z * sa;
        float a2x = -e1x * sa + e2x * ca;
        float a2y = -e1y * sa + e2y * ca;
        float a2z = -e1z * sa + e2z * ca;

        float half = SQUARE_SIZE / 2f;
        out[0] = px + a1x * half + a2x * half;
        out[1] = py + a1y * half + a2y * half;
        out[2] = pz + a1z * half + a2z * half;
        out[3] = px + a1x * half - a2x * half;
        out[4] = py + a1y * half - a2y * half;
        out[5] = pz + a1z * half - a2z * half;
        out[6] = px - a1x * half - a2x * half;
        out[7] = py - a1y * half - a2y * half;
        out[8] = pz - a1z * half - a2z * half;
        out[9] = px - a1x * half + a2x * half;
        out[10] = py - a1y * half + a2y * half;
        out[11] = pz - a1z * half + a2z * half;
    }

    /// The four bright wireframe edges of one square frame, in its rainbow color.
    private static void drawSpinningSquareFrame(VertexConsumer consumer, PoseStack poseStack, float[] corners,
            float px, float py, float pz, float nx, float ny, float nz, float spinAngle, int colorIndex,
            float pulse) {
        squareCorners(corners, px, py, pz, nx, ny, nz, spinAngle);
        float[] c = corners;
        float[] rgb = RAINBOW[colorIndex];
        // Edges are thin rectangles lying exactly in the square's plane, so they project onto the same position as
        // the far-side edges and no ghosting/double outline can appear.
        drawFrameEdge(consumer, poseStack, c[0], c[1], c[2], c[3], c[4], c[5], px, py, pz, 0.14f, rgb, pulse);
        drawFrameEdge(consumer, poseStack, c[3], c[4], c[5], c[6], c[7], c[8], px, py, pz, 0.14f, rgb, pulse);
        drawFrameEdge(consumer, poseStack, c[6], c[7], c[8], c[9], c[10], c[11], px, py, pz, 0.14f, rgb, pulse);
        drawFrameEdge(consumer, poseStack, c[9], c[10], c[11], c[0], c[1], c[2], px, py, pz, 0.14f, rgb, pulse);
    }

    /// A thin rectangle from edge endpoint A to B, extruded in-plane toward and away from the square's center by half
    /// the width - a clean frame edge with no cross-section ghosting. {@link #GLOW} draws without culling, so the
    /// winding does not matter and the edge is visible from either side.
    private static void drawFrameEdge(VertexConsumer consumer, PoseStack poseStack,
            float ax, float ay, float az, float bx, float by, float bz,
            float cx, float cy, float cz, float width, float[] rgb, float alpha) {
        // In-plane direction perpendicular to the edge: from the square center to the edge midpoint.
        float mx = (ax + bx) / 2f - cx;
        float my = (ay + by) / 2f - cy;
        float mz = (az + bz) / 2f - cz;
        float ml = Mth.sqrt(mx * mx + my * my + mz * mz);
        if (ml < 1e-4f) {
            return;
        }
        mx /= ml;
        my /= ml;
        mz /= ml;
        float w = width / 2f;
        drawFace(consumer, poseStack,
                ax + mx * w, ay + my * w, az + mz * w,
                ax - mx * w, ay - my * w, az - mz * w,
                bx - mx * w, by - my * w, bz - mz * w,
                bx + mx * w, by + my * w, bz + mz * w,
                rgb, alpha);
    }

    /// A solid quad through four arbitrary 3D points.
    private static void drawFace(VertexConsumer consumer, PoseStack poseStack,
            float x0, float y0, float z0, float x1, float y1, float z1,
            float x2, float y2, float z2, float x3, float y3, float z3,
            float[] rgb, float alpha) {
        var pose = poseStack.last().pose();
        consumer.addVertex(pose, x0, y0, z0).setColor(rgb[0], rgb[1], rgb[2], alpha);
        consumer.addVertex(pose, x1, y1, z1).setColor(rgb[0], rgb[1], rgb[2], alpha);
        consumer.addVertex(pose, x2, y2, z2).setColor(rgb[0], rgb[1], rgb[2], alpha);
        consumer.addVertex(pose, x3, y3, z3).setColor(rgb[0], rgb[1], rgb[2], alpha);
    }
}
