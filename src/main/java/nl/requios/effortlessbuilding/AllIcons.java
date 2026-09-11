package nl.requios.effortlessbuilding;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import java.awt.Color;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class AllIcons {
    public static final ResourceLocation ICON_ATLAS = ResourceLocation.fromNamespaceAndPath("creative_mode_tweaks", "textures/gui/icons.png");
    public static final int ICON_ATLAS_SIZE = 256;
    private static int x = 0;
    private static int y = -1;
    private int iconX;
    private int iconY;
    public static final AllIcons I_MODIFIERS = newRow();
    public static final AllIcons I_UNDO = next();
    public static final AllIcons I_REDO = next();
    public static final AllIcons I_REPLACE = next();
    public static final AllIcons I_REPLACE_AIR = next();
    public static final AllIcons I_REPLACE_BLOCKS_AND_AIR = next();
    public static final AllIcons I_REPLACE_BLOCKS = next();
    public static final AllIcons I_REPLACE_OFFHAND_FILTERED = next();
    public static final AllIcons I_PROTECT_TILE_ENTITIES = next();
    public static final AllIcons I_CLIENT_SETTINGS = next();
    public static final AllIcons I_SERVER_SETTINGS = next();
    public static final AllIcons I_DISABLE = newRow();
    public static final AllIcons I_SINGLE = next();
    public static final AllIcons I_LINE = next();
    public static final AllIcons I_WALL = next();
    public static final AllIcons I_FLOOR = next();
    public static final AllIcons I_CUBE = next();
    public static final AllIcons I_DIAGONAL_LINE = next();
    public static final AllIcons I_DIAGONAL_WALL = next();
    public static final AllIcons I_SLOPED_FLOOR = next();
    public static final AllIcons I_CIRCLE = next();
    public static final AllIcons I_CYLINDER = next();
    public static final AllIcons I_SPHERE = next();
    public static final AllIcons I_PYRAMID = next();
    public static final AllIcons I_CONE = next();
    public static final AllIcons I_DOME = next();
    public static final AllIcons I_MESH = next();
    public static final AllIcons I_NORMAL_SPEED = newRow();
    public static final AllIcons I_FAST_SPEED = next();
    public static final AllIcons I_FILLED = next();
    public static final AllIcons I_HOLLOW = next();
    public static final AllIcons I_CUBE_FILLED = next();
    public static final AllIcons I_CUBE_HOLLOW = next();
    public static final AllIcons I_CUBE_SKELETON = next();
    public static final AllIcons I_SHORT_EDGE = next();
    public static final AllIcons I_LONG_EDGE = next();
    public static final AllIcons I_CIRCLE_START_CORNER = next();
    public static final AllIcons I_CIRCLE_START_CENTER = next();
    public static final AllIcons I_THICKNESS_1 = next();
    public static final AllIcons I_THICKNESS_3 = next();
    public static final AllIcons I_THICKNESS_5 = next();
    public static final AllIcons I_PLAYER = newRow();
    public static final AllIcons I_BLOCK_CENTER = next();
    public static final AllIcons I_BLOCK_CORNER = next();
    public static final AllIcons I_HIDE_LINES = next();
    public static final AllIcons I_SHOW_LINES = next();
    public static final AllIcons I_HIDE_AREAS = next();
    public static final AllIcons I_SHOW_AREAS = next();
    public static final AllIcons I_X_OFF = next();
    public static final AllIcons I_X_ON = next();
    public static final AllIcons I_Y_OFF = next();
    public static final AllIcons I_Y_ON = next();
    public static final AllIcons I_Z_OFF = next();
    public static final AllIcons I_Z_ON = next();
    public static final AllIcons I_ALTERNATE_OFF = next();
    public static final AllIcons I_ALTERNATE_ON = next();
    public static final AllIcons ANGEL_PLACEMENT_ON = newRow();
    public static final AllIcons ANGEL_PLACEMENT_OFF = next();
    public static final AllIcons I_TWO_POINT = next();
    public static final AllIcons I_THREE_POINT = next();
    public static final AllIcons I_FOUR_POINT = next();
    public static final AllIcons I_MESH_TRIANGLE = next();
    public static final AllIcons I_MESH_QUAD = next();
    public static final AllIcons I_EYE_ON = next();
    public static final AllIcons I_EYE_OFF = next();
    public static final AllIcons I_NOCLIP_ON = next();
    public static final AllIcons I_NOCLIP_OFF = next();
    public static final AllIcons I_ALIGN_AUTO = next();
    public static final AllIcons I_ALIGN_VERTICAL = next();
    public static final AllIcons I_ALIGN_HORIZONTAL = next();
    public static final AllIcons I_FILL_MODE = next();


    public AllIcons(int x, int y) {
        this.iconX = x * 16;
        this.iconY = y * 16;
    }

    private static AllIcons next() {
        return new AllIcons(++x, y);
    }

    private static AllIcons newRow() {
        x = 0;
        return new AllIcons(0, ++y);
    }

    public void bind() {
        RenderSystem.setShaderTexture(0, ICON_ATLAS);
    }

    public void render(GuiGraphics graphics, int x, int y) {
        this.bind();
        graphics.blit(ICON_ATLAS, x, y, 0, (float) this.iconX, (float) this.iconY, 16, 16, 256, 256);
    }

    public void render(PoseStack ms, MultiBufferSource buffer, int color) {
        VertexConsumer builder = buffer.getBuffer(RenderType.textSeeThrough(ICON_ATLAS));
        Matrix4f matrix = ms.last().pose();
        Color rgb = new Color(color);
        int light = 15728880;
        Vec3 vec1 = new Vec3((double) 0.0F, (double) 0.0F, (double) 0.0F);
        Vec3 vec2 = new Vec3((double) 0.0F, (double) 1.0F, (double) 0.0F);
        Vec3 vec3 = new Vec3((double) 1.0F, (double) 1.0F, (double) 0.0F);
        Vec3 vec4 = new Vec3((double) 1.0F, (double) 0.0F, (double) 0.0F);
        float u1 = (float) this.iconX * 1.0F / 256.0F;
        float u2 = (float) (this.iconX + 16) * 1.0F / 256.0F;
        float v1 = (float) this.iconY * 1.0F / 256.0F;
        float v2 = (float) (this.iconY + 16) * 1.0F / 256.0F;
        this.vertex(builder, matrix, vec1, rgb, u1, v1, light);
        this.vertex(builder, matrix, vec2, rgb, u1, v2, light);
        this.vertex(builder, matrix, vec3, rgb, u2, v2, light);
        this.vertex(builder, matrix, vec4, rgb, u2, v1, light);
    }

    private void vertex(VertexConsumer builder, Matrix4f matrix, Vec3 vec, Color rgb, float u, float v, int light) {
        builder.addVertex(matrix, (float) vec.x, (float) vec.y, (float) vec.z).setColor(rgb.getRed(), rgb.getGreen(), rgb.getBlue(), 255).setUv(u, v).setLight(light);
    }
}
