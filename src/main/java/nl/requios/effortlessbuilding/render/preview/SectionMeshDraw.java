package nl.requios.effortlessbuilding.render.preview;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import nl.requios.effortlessbuilding.buildpipeline.SableCompat;
import org.joml.Matrix4f;

/**
 * Shared GPU-draw helper for all cached preview meshes.
 *
 * <p>Every cached mesh stores its vertices in <b>section-local</b> space
 * (0–16 on each axis, same convention as vanilla chunks) so buffers stay
 * small and reusable. At draw time each section is placed back into the world
 * with {@link SableCompat#translateToBlock}, which handles both the plain
 * case ({@code origin - camera}) and Sable contraptions (global origin plus
 * rotation/scale).</p>
 *
 * <p>The per-section model-view is {@code eventModelView * sectionPose}:
 * the event matrix carries the camera <i>rotation</i> (it has no translation),
 * the section pose carries the camera-<i>relative</i> translation. Passing
 * only the pose stack (as the old code did) drops the rotation and the mesh
 * sticks to the screen like a HUD element.</p>
 */
public final class SectionMeshDraw {
    private SectionMeshDraw() {
    }

    /** One uploaded chunk-section of a cached mesh. */
    public record Section(BlockPos origin, VertexBuffer buffer) {
    }

    /**
     * One tessellated chunk-section that has NOT touched GL yet. Built on any
     * thread (pure CPU vertex data) and uploaded on the render thread.
     *
     * <p><b>Ownership is the whole game here:</b> the {@code MeshData} is a
     * <i>view</i> into the backing's native memory — reading it after
     * {@code backing.close()} throws (or worse). So the backing travels WITH
     * the mesh and is closed exactly once, either right after a successful
     * upload or as part of {@link #discard}. Closing it early (like vanilla's
     * synchronous try-with-resources would) while the mesh is still pending
     * is a guaranteed crash; never closing it leaks native memory until the
     * game dies. Both already happened — this record exists so neither can.</p>
     */
    public record PendingSection(BlockPos origin, MeshData mesh, ByteBufferBuilder backing) {
        /** Frees the mesh view AND its native memory. Call on every discard path. */
        public void discard() {
            // Result closes are idempotent (closed flag); backing close is
            // idempotent too (pointer check) — safe even partially consumed.
            this.mesh.close();
            this.backing.close();
        }
    }

    /**
     * Draws every section with the given render type. Binds the shader state,
     * draws, then always unbinds — even on exceptions — so later vanilla
     * passes never inherit our buffers.
     */
    public static void drawAll(List<Section> sections, Level level,
                               double camX, double camY, double camZ,
                               Matrix4f baseModelView, Matrix4f projectionMatrix,
                               RenderType renderType) {
        if (sections.isEmpty()) {
            return;
        }

        renderType.setupRenderState();
        ShaderInstance shader = RenderSystem.getShader();
        try {
            if (shader == null) {
                return;
            }
            // Reused across sections: rebuilt only when the shape changes.
            PoseStack sectionPose = new PoseStack();
            for (Section section : sections) {
                sectionPose.pushPose();
                try {
                    SableCompat.translateToBlock(sectionPose, level, section.origin(), camX, camY, camZ);
                    Matrix4f sectionModelView = new Matrix4f(baseModelView).mul(sectionPose.last().pose());
                    section.buffer().bind();
                    section.buffer().drawWithShader(sectionModelView, projectionMatrix, shader);
                } finally {
                    sectionPose.popPose();
                }
            }
        } finally {
            VertexBuffer.unbind();
            renderType.clearRenderState();
        }
    }

    /** Closes every buffer in the list and empties it. Safe to call twice. */
    public static void closeAll(List<Section> sections) {
        for (Section section : sections) {
            section.buffer().close();
        }
        sections.clear();
    }

    /** Frees every pending (never uploaded) mesh in the list and empties it. */
    public static void discardAllPending(List<PendingSection> pending) {
        for (PendingSection section : pending) {
            section.discard();
        }
        pending.clear();
    }

    /**
     * Uploads one pending section to GL. Must run on the render thread.
     * The upload copies vertices to the GPU synchronously, so the backing
     * memory is freed right after (mirroring vanilla's build-then-close
     * pattern, just split across the handoff).
     */
    public static Section upload(PendingSection pending) {
        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        buffer.bind();
        try {
            buffer.upload(pending.mesh());
        } catch (RuntimeException failure) {
            buffer.close();
            pending.discard();
            throw failure;
        } finally {
            VertexBuffer.unbind();
        }
        // MeshData is consumed by the upload; now free the native memory it
        // was viewing. Order matters: never close before a successful upload.
        pending.backing().close();
        return new Section(pending.origin(), buffer);
    }
}
