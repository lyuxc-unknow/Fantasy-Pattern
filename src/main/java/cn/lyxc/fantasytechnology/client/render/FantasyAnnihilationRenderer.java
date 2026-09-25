package cn.lyxc.fantasytechnology.client.render;

import cn.lyxc.fantasytechnology.blockentity.FantasyAnnihilationBlockEntity;
import cn.lyxc.fantasytechnology.config.FTClientConfig;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.annotation.ParametersAreNonnullByDefault;

/** A contained four-dimensional lattice, with a dark nucleus and luminous, folding cells. */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class FantasyAnnihilationRenderer implements BlockEntityRenderer<FantasyAnnihilationBlockEntity> {
    private static final double TAU = Math.PI * 2;
    private static final int ICE = 0x87EDFF;
    private static final int IRIS = 0xAC92FF;
    private static final int PEARL = 0xE5FAFF;
    private static final int GOLD = 0xFFD6A0;

    private static final RenderType NUCLEUS = RenderType.create("fantasy_technology_annihilation_nucleus",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1536,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .createCompositeState(false));

    // SRC_ALPHA + ONE makes intersecting cells order-independent. Keep depth testing against the
    // housing/world, but never let a transparent face hide another cell by writing depth.
    private static final RenderType LIGHT = RenderType.create("fantasy_technology_annihilation_lattice",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 32768,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    private static final float[] RESTING_VERTICES = new float[TesseractGeometry.VERTEX_COUNT * 4];
    static {
        TesseractGeometry.project(0, RESTING_VERTICES);
    }

    private final BlockEntityRenderDispatcher dispatcher;
    // Renderers are shared by all instances and called on the render thread. Reuse scratch storage.
    private final float[] vertices = new float[TesseractGeometry.VERTEX_COUNT * 4];
    private final Vector3f camera = new Vector3f();
    private final Vector3f right = new Vector3f();
    private final Vector3f up = new Vector3f();
    private final Vector3f side = new Vector3f();

    public FantasyAnnihilationRenderer(BlockEntityRendererProvider.Context context) {
        dispatcher = context.getBlockEntityRenderDispatcher();
    }

    @Override
    public AABB getRenderBoundingBox(FantasyAnnihilationBlockEntity blockEntity) {
        return new AABB(blockEntity.getBlockPos());
    }

    @Override
    public void render(FantasyAnnihilationBlockEntity blockEntity, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight, int packedOverlay) {
        var level = blockEntity.getLevel();
        if (level == null) {
            return;
        }

        boolean animated = FTClientConfig.ANNIHILATION_EFFECTS.get() && blockEntity.isStructureFormed();
        // Keep time in double until each angle is reduced: old worlds still get smooth partial ticks.
        double time = animated ? level.getGameTime() + (double) partialTick : 0;
        if (animated) {
            TesseractGeometry.project(time, vertices);
        }
        float[] points = animated ? vertices : RESTING_VERTICES;
        var pos = blockEntity.getBlockPos();
        var view = dispatcher.camera.getPosition();
        camera.set((float) (view.x - pos.getX() - 0.5), (float) (view.y - pos.getY() - 0.5),
                (float) (view.z - pos.getZ() - 0.5));
        right.set(1, 0, 0).rotate(dispatcher.camera.rotation());
        up.set(0, 1, 0).rotate(dispatcher.camera.rotation());
        boolean detailed = animated && camera.lengthSquared() < 24 * 24;

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        Matrix4f pose = poseStack.last().pose();

        // Submit the opaque nucleus before requesting the light buffer; switching custom render
        // types flushes the shared buffer. Do not retain a consumer across that switch.
        drawNucleus(buffer.getBuffer(NUCLEUS), pose, points);
        VertexConsumer light = buffer.getBuffer(LIGHT);
        float pulse = animated ? 0.90f + 0.10f * Mth.sin(phase(time, 0.025)) : 0.58f;
        if (detailed) {
            drawCells(light, pose, points, pulse);
        }
        drawLattice(light, pose, points, pulse, detailed, time);
        drawNucleusEdges(light, pose, points, pulse, detailed);
        poseStack.popPose();
    }

    private void drawLattice(VertexConsumer out, Matrix4f pose, float[] points, float pulse,
            boolean detailed, double time) {
        for (int edge = 0; edge < TesseractGeometry.EDGES.length; edge++) {
            int a = TesseractGeometry.EDGES[edge][0] * 4;
            int b = TesseractGeometry.EDGES[edge][1] * 4;
            float depth = (points[a + 3] + points[b + 3]) * 0.5f;
            int color = mix(ICE, IRIS, depth);
            float alpha = (0.48f + depth * 0.28f) * pulse;
            beam(out, pose, points[a], points[a + 1], points[a + 2],
                    points[b], points[b + 1], points[b + 2], 0.0042f, color, alpha, detailed);

            if (detailed) {
                // A faint smaller image adds depth without competing with the primary 32 edges.
                beam(out, pose, points[a] * 0.62f, points[a + 1] * 0.62f, points[a + 2] * 0.62f,
                        points[b] * 0.62f, points[b + 1] * 0.62f, points[b + 2] * 0.62f,
                        0.0018f, color, 0.12f * pulse, false);
                if (edge % 4 == 0) {
                    drawTraveller(out, pose, points, a, b, time, edge, color);
                }
            }
        }

        if (detailed) {
            for (int i = 0; i < TesseractGeometry.VERTEX_COUNT; i++) {
                int v = i * 4;
                float size = 0.006f + points[v + 3] * 0.003f;
                glint(out, pose, points[v], points[v + 1], points[v + 2], size * 2.8f,
                        mix(ICE, IRIS, points[v + 3]), 0.075f * pulse);
                glint(out, pose, points[v], points[v + 1], points[v + 2], size, PEARL, 0.85f);
            }
        }
    }

    private static void drawCells(VertexConsumer out, Matrix4f pose, float[] points, float pulse) {
        for (int[] face : TesseractGeometry.FACES) {
            // All 24 square faces are projected from 4D; they fold continuously with the edges.
            for (int vertex : face) {
                int v = vertex * 4;
                float depth = points[v + 3];
                vertex(out, pose, points[v], points[v + 1], points[v + 2],
                        mix(ICE, IRIS, depth), (0.012f + depth * 0.015f) * pulse);
            }
        }
    }

    private void drawTraveller(VertexConsumer out, Matrix4f pose, float[] points, int a, int b,
            double time, int index, int color) {
        float progress = (float) ((time * 0.008 + index * 0.137) % 1);
        float fade = Mth.sin(progress * Mth.PI);
        float tail = Math.max(0, progress - 0.13f);
        float x = Mth.lerp(progress, points[a], points[b]);
        float y = Mth.lerp(progress, points[a + 1], points[b + 1]);
        float z = Mth.lerp(progress, points[a + 2], points[b + 2]);
        beam(out, pose, Mth.lerp(tail, points[a], points[b]),
                Mth.lerp(tail, points[a + 1], points[b + 1]),
                Mth.lerp(tail, points[a + 2], points[b + 2]), x, y, z,
                0.006f, color, 0.75f * fade, true);
        glint(out, pose, x, y, z, 0.007f, PEARL, fade);
    }

    private static void drawNucleus(VertexConsumer out, Matrix4f pose, float[] points) {
        // One of the tesseract's cubic cells, shrunk into a dark, faceted seed. Its actual
        // projected faces write depth, so the far side of the lattice disappears behind it.
        for (int i = 0; i < TesseractGeometry.CUBE_FACES.length; i++) {
            int color = i % 3 == 0 ? 0x172538 : i % 3 == 1 ? 0x101525 : 0x28243C;
            for (int vertex : TesseractGeometry.CUBE_FACES[i]) {
                int v = vertex * 4;
                vertex(out, pose, points[v] * 0.24f, points[v + 1] * 0.24f,
                        points[v + 2] * 0.24f, color, 1);
            }
        }
    }

    private void drawNucleusEdges(VertexConsumer out, Matrix4f pose, float[] points, float pulse,
            boolean detailed) {
        for (int[] edge : TesseractGeometry.EDGES) {
            if (edge[1] >= 8) {
                continue;
            }
            int a = edge[0] * 4;
            int b = edge[1] * 4;
            beam(out, pose, points[a] * 0.245f, points[a + 1] * 0.245f, points[a + 2] * 0.245f,
                    points[b] * 0.245f, points[b + 1] * 0.245f, points[b + 2] * 0.245f,
                    0.0022f, GOLD, 0.62f * pulse, detailed);
        }
    }

    /** Camera-facing ribbons stay thin and legible from every direction, including straight above. */
    private void beam(VertexConsumer out, Matrix4f pose, float ax, float ay, float az,
            float bx, float by, float bz, float width, int color, float alpha, boolean halo) {
        float dx = bx - ax;
        float dy = by - ay;
        float dz = bz - az;
        if (dx * dx + dy * dy + dz * dz < 1.0e-10f) {
            return;
        }
        side.set(dx, dy, dz).cross(camera.x - (ax + bx) * 0.5f,
                camera.y - (ay + by) * 0.5f, camera.z - (az + bz) * 0.5f);
        if (side.lengthSquared() < 1.0e-10f) {
            side.set(right);
        } else {
            side.normalize();
        }
        if (halo) {
            // Two gradients taper to zero instead of drawing a visibly rectangular glow band.
            ribbon(out, pose, ax, ay, az, bx, by, bz, 0, width * 4, color, alpha * 0.18f, 0);
            ribbon(out, pose, ax, ay, az, bx, by, bz, -width * 4, 0, color, 0, alpha * 0.18f);
        }
        ribbon(out, pose, ax, ay, az, bx, by, bz, -width * 0.5f, width * 0.5f, color, alpha, alpha);
    }

    private void ribbon(VertexConsumer out, Matrix4f pose, float ax, float ay, float az,
            float bx, float by, float bz, float lo, float hi, int color, float loAlpha, float hiAlpha) {
        vertex(out, pose, ax + side.x * lo, ay + side.y * lo, az + side.z * lo, color, loAlpha);
        vertex(out, pose, bx + side.x * lo, by + side.y * lo, bz + side.z * lo, color, loAlpha);
        vertex(out, pose, bx + side.x * hi, by + side.y * hi, bz + side.z * hi, color, hiAlpha);
        vertex(out, pose, ax + side.x * hi, ay + side.y * hi, az + side.z * hi, color, hiAlpha);
    }

    private void glint(VertexConsumer out, Matrix4f pose, float x, float y, float z,
            float size, int color, float alpha) {
        vertex(out, pose, x + up.x * size, y + up.y * size, z + up.z * size, color, alpha);
        vertex(out, pose, x + right.x * size, y + right.y * size, z + right.z * size, color, alpha);
        vertex(out, pose, x - up.x * size, y - up.y * size, z - up.z * size, color, alpha);
        vertex(out, pose, x - right.x * size, y - right.y * size, z - right.z * size, color, alpha);
    }

    private static float phase(double time, double speed) {
        return (float) (time * speed % TAU);
    }

    private static int mix(int a, int b, float amount) {
        int r = (int) Mth.lerp(amount, (a >> 16) & 255, (b >> 16) & 255);
        int g = (int) Mth.lerp(amount, (a >> 8) & 255, (b >> 8) & 255);
        int blue = (int) Mth.lerp(amount, a & 255, b & 255);
        return (r << 16) | (g << 8) | blue;
    }

    private static void vertex(VertexConsumer out, Matrix4f pose, float x, float y, float z,
            int color, float alpha) {
        out.addVertex(pose, x, y, z).setColor((color >> 16) & 255, (color >> 8) & 255,
                color & 255, (int) (Mth.clamp(alpha, 0, 1) * 255));
    }
}
