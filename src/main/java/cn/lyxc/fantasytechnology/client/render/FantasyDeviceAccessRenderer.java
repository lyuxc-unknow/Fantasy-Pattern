package cn.lyxc.fantasytechnology.client.render;

import cn.lyxc.fantasytechnology.blockentity.FantasyDeviceAccessBlockEntity;
import cn.lyxc.fantasytechnology.config.FTClientConfig;
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
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

import javax.annotation.ParametersAreNonnullByDefault;

/// Draws the device access block's celestial display inside the block: a globe of nested shells with two
/// counter-rotating star rings sweeping around it. See {@link CelestialGeometry} for the shapes.
///
/// The whole display is one additive pass of position-coloured quads: no texture, no lighting and no sorting. The
/// block's own model is an open cage of corner clamps and edge rails, so the display is seen through its gaps and
/// the cage reads as the housing it turns inside.
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class FantasyDeviceAccessRenderer implements BlockEntityRenderer<FantasyDeviceAccessBlockEntity> {

    /// One frame is 739 quads of four position-coloured vertices, so 48 KiB holds it without the buffer regrowing.
    private static final int BUFFER_SIZE = 48 * 1024;

    private static final RenderType DISPLAY = RenderType.create("fantasy_technology_device_access_display",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, BUFFER_SIZE,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    // Depth is tested but never written, so the display is hidden by solid blocks without its own
                    // quads hiding each other - which is exactly what an additive pass wants.
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    /// Cold end of the tone gradient, for the dark side of the globe.
    private static final int COLD = 0x3A2E96;
    /// Middle of it, where the lit limb and the ring stars sit.
    private static final int GLOW = 0x6FE4FF;
    /// Hot end, for the nucleus and the sparkles on the rings.
    private static final int HOT = 0xFFF6E2;

    /// Alpha is a second, flatter reading of the same tone: the dark side of the globe stays a suggestion and the
    /// nucleus is the one thing that is genuinely bright.
    private static final float MIN_ALPHA = 0.040f;
    private static final float ALPHA_RANGE = 0.075f;

    /// The display turns inside one block, so it comes into view at ordinary block range rather than from a distance.
    private static final int VIEW_DISTANCE = 64;

    // Renderers are shared by every block of the type and only ever called on the render thread, so one scratch
    // buffer serves them all. Every device access block shows the same display at the same moment, which is why the
    // frame is computed once per game tick rather than once per block.
    private final float[] frame = new float[CelestialGeometry.STRIDE];
    private double frameTime = Double.NaN;

    public FantasyDeviceAccessRenderer(BlockEntityRendererProvider.Context context) {
    }

    /// The rings reach a little under half a block from the block's centre, so the bounding box is barely larger than
    /// the block itself. It is written from the geometry rather than as a unit cube so the display is never culled
    /// while any of it is still on screen.
    @Override
    public AABB getRenderBoundingBox(FantasyDeviceAccessBlockEntity blockEntity) {
        var pos = blockEntity.getBlockPos();
        double centerX = pos.getX() + 0.5;
        double centerY = pos.getY() + 0.5;
        double centerZ = pos.getZ() + 0.5;
        double reach = CelestialGeometry.MAX_REACH;
        return new AABB(centerX - reach, centerY - reach, centerZ - reach,
                centerX + reach, centerY + reach, centerZ + reach);
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    @Override
    public void render(FantasyDeviceAccessBlockEntity blockEntity, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int visibleLight, int packedOverlay) {
        var level = blockEntity.getLevel();
        if (level == null || !FTClientConfig.DEVICE_ACCESS_EFFECTS.get()) {
            return;
        }

        // Time stays exact in double and only the angles are reduced, so old worlds keep smooth partial ticks.
        double time = level.getGameTime() + (double) partialTick;
        if (time != frameTime) {
            CelestialGeometry.project(time, frame);
            frameTime = time;
        }

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        Matrix4f pose = poseStack.last().pose();
        VertexConsumer out = buffer.getBuffer(DISPLAY);
        for (int vertex = 0; vertex < CelestialGeometry.STRIDE; vertex += 4) {
            vertex(out, pose, frame[vertex], frame[vertex + 1], frame[vertex + 2], frame[vertex + 3]);
        }
        poseStack.popPose();
    }

    private static void vertex(VertexConsumer out, Matrix4f pose, float x, float y, float z, float tone) {
        int color = tone < 0.5f ? mix(COLD, GLOW, tone * 2) : mix(GLOW, HOT, tone * 2 - 1);
        out.addVertex(pose, x, y, z).setColor((color >> 16) & 255, (color >> 8) & 255, color & 255,
                (int) ((MIN_ALPHA + ALPHA_RANGE * tone) * 255));
    }

    private static int mix(int from, int to, float amount) {
        int r = (int) Mth.lerp(amount, (from >> 16) & 255, (to >> 16) & 255);
        int g = (int) Mth.lerp(amount, (from >> 8) & 255, (to >> 8) & 255);
        int b = (int) Mth.lerp(amount, from & 255, to & 255);
        return (r << 16) | (g << 8) | b;
    }
}