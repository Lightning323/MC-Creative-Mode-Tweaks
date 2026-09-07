package nl.requios.effortlessbuilding.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;

import java.util.ArrayList;
import java.util.Objects;

import nl.requios.effortlessbuilding.AllIcons;
import nl.requios.effortlessbuilding.buildmode.BuildModeEnum;
import nl.requios.effortlessbuilding.buildmode.BuildModes;
import nl.requios.effortlessbuilding.buildmode.BuildSettings;
import nl.requios.effortlessbuilding.buildmode.ModeOptions;
import nl.requios.effortlessbuilding.network.PacketHandler;
import org.lightning323.creative_mode_tweaks.client.ClientModEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import org.lightning323.creative_mode_tweaks.Config;
import org.joml.Vector4f;

public class RadialMenu extends Screen {
    public static final RadialMenu instance = new RadialMenu();
    private final Vector4f radialButtonColor = new Vector4f(0.0F, 0.0F, 0.0F, 0.5F);
    private final Vector4f sideButtonColor = new Vector4f(0.5F, 0.5F, 0.5F, 0.5F);
    private final Vector4f disabledSideButtonColor = new Vector4f(0.25F, 0.25F, 0.25F, 0.45F);
    private final Vector4f disabledHighlightColor = new Vector4f(0.35F, 0.35F, 0.35F, 0.55F);
    private final Vector4f highlightColor = new Vector4f(0.6F, 0.8F, 1.0F, 0.6F);
    private final Vector4f selectedColor = new Vector4f(0.0F, 0.5F, 1.0F, 0.5F);
    private final Vector4f highlightSelectedColor = new Vector4f(0.2F, 0.7F, 1.0F, 0.7F);
    private final int whiteTextColor = -1;
    private final int watermarkTextColor = -2004318072;
    private final int descriptionTextColor = -578254712;
    private final int optionTextColor = -286331137;
    private final double ringInnerEdge = (double) 30.0F;
    private final double ringOuterEdge = (double) 65.0F;
    private final double categoryLineWidth = (double) 2.0F;
    private final double textDistance = (double) 75.0F;
    private final double buttonDistance = (double) 105.0F;
    private final float fadeSpeed = 0.4F;
    private final int buildModeDescriptionHeight = 100;
    private final int actionDescriptionWidth = 200;
    public BuildModeEnum switchTo = null;
    public ModeOptions.ActionEnum doAction = null;
    public boolean performedActionUsingMouse;
    private float visibility;

    public RadialMenu() {
        super(Component.translatable("creative_mode_tweaks.screen.radial_menu"));
    }

    public boolean isVisible() {
        return Minecraft.getInstance().screen instanceof RadialMenu;
    }

    protected void init() {
        super.init();
        this.performedActionUsingMouse = false;
        this.visibility = 0.0F;
    }

    public void tick() {
        super.tick();
        if (!ClientModEvents.isKeyDown(ClientModEvents.KEY_OPEN_RADIAL_MENU)) {
            this.onClose();
        }

    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        BuildModeEnum currentBuildMode = BuildModes.CLIENT.getBuildMode();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 200.0F);
        this.visibility += 0.4F * partialTicks;
        if (this.visibility > 1.0F) {
            this.visibility = 1.0F;
        }

        double scale = 0.8 + 0.2 * (double) this.visibility;
        int bgColor = (int) (this.visibility * 150.0F) << 24;
        graphics.fill(0, 0, this.width, this.height, bgColor);
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(770, 771, 1, 0);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        double middleX = (double) this.width / (double) 2.0F;
        double middleY = (double) this.height / (double) 2.0F;
        int mouseXX = (int) (this.minecraft.mouseHandler.xpos() * (double) this.minecraft.getWindow().getGuiScaledWidth() / (double) this.minecraft.getWindow().getScreenWidth());
        int mouseYY = (int) (this.minecraft.mouseHandler.ypos() * (double) this.minecraft.getWindow().getGuiScaledHeight() / (double) this.minecraft.getWindow().getScreenHeight());
        double mouseXCenter = (double) mouseXX - middleX;
        double mouseYCenter = (double) mouseYY - middleY;
        double mouseRadians = Math.atan2(mouseYCenter, mouseXCenter);
        double quarterCircle = (Math.PI / 2D);
        if (mouseRadians < (-Math.PI / 2D)) {
            mouseRadians += (Math.PI * 2D);
        }

        ArrayList<MenuRegion> modes = new ArrayList();
        ArrayList<MenuButton> buttons = new ArrayList();

        for (BuildModeEnum mode : BuildModeEnum.values()) {
            modes.add(new MenuRegion(mode));
        }

        buttons.add(new MenuButton(ModeOptions.ActionEnum.OPEN_MODIFIER_SETTINGS, (double) -157.0F, (double) -13.0F, Direction.UP));
        buttons.add(new MenuButton(ModeOptions.ActionEnum.UNDO, (double) -131.0F, (double) -13.0F, Direction.UP));
        buttons.add(new MenuButton(ModeOptions.ActionEnum.REDO, (double) -105.0F, (double) -13.0F, Direction.UP));

        MenuButton replaceBtn = new MenuButton(ModeOptions.ActionEnum.CYCLE_REPLACE_MODE, (double) -157.0F, (double) 13.0F, Direction.DOWN);
        ModeOptions.ActionEnum currentReplaceAction = BuildSettings.CLIENT.getReplaceModeActionEnum();
        replaceBtn.iconOverride = currentReplaceAction.icon;
        replaceBtn.name = I18n.get("creative_mode_tweaks.action.replace_mode", new Object[0]);
        replaceBtn.subtitle = I18n.get(currentReplaceAction.getNameKey(), new Object[0]);
        replaceBtn.description = I18n.exists(currentReplaceAction.getDescriptionKey()) ? I18n.get(currentReplaceAction.getDescriptionKey(), new Object[0]) : "";
        buttons.add(replaceBtn);

        MenuButton angelPlacementBtn = new MenuButton(ModeOptions.ActionEnum.TOGGLE_ANGEL_PLACEMENT, (double) -131.0F, (double) 13.0F, Direction.DOWN);
        boolean angelPlacementEnabled = BuildSettings.CLIENT.isAngelPlacementEnabled();
        angelPlacementBtn.iconOverride = angelPlacementEnabled ? AllIcons.ANGEL_PLACEMENT_ON : AllIcons.ANGEL_PLACEMENT_OFF;
        angelPlacementBtn.subtitle = I18n.get(angelPlacementEnabled ? "options.on" : "options.off", new Object[0]);
        angelPlacementBtn.enabled = this.minecraft.player == null || Config.isAngelPlacementAllowed(this.minecraft.player);
        buttons.add(angelPlacementBtn);
        ModeOptions.OptionEnum[] options = currentBuildMode.options;

        for (int i = 0; i < options.length; ++i) {
            for (int j = 0; j < options[i].actions.length; ++j) {
                ModeOptions.ActionEnum action = options[i].actions[j];
                buttons.add(new MenuButton(action, (double) 105.0F + (double) (j * 26), (double) (-13 + i * 39), Direction.DOWN));
            }
        }

        this.switchTo = null;
        this.doAction = null;
        this.drawRadialButtonBackgrounds(currentBuildMode, buffer, middleX, middleY, mouseXCenter, mouseYCenter, mouseRadians, (Math.PI / 2D), modes, scale);
        this.drawSideButtonBackgrounds(buffer, middleX, middleY, mouseXCenter, mouseYCenter, buttons, scale);
        MeshData meshData = buffer.buildOrThrow();
        BufferUploader.drawWithShader(meshData);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        this.drawIcons(graphics, middleX, middleY, modes, buttons, scale);
        this.drawTexts(graphics, currentBuildMode, middleX, middleY, modes, buttons, options, mouseXX, mouseYY, scale);
        graphics.pose().popPose();
    }

    private void drawRadialButtonBackgrounds(BuildModeEnum currentBuildMode, BufferBuilder buffer, double middleX, double middleY, double mouseXCenter, double mouseYCenter, double mouseRadians, double quarterCircle, ArrayList<MenuRegion> modes, double scale) {
        if (!modes.isEmpty()) {
            int totalModes = Math.max(3, modes.size());
            double fragment = 0.015707963267948967;
            double fragment2 = 0.007853981633974483;
            double radiansPerObject = (Math.PI * 2D) / (double) totalModes;
            double innerEdge = (double) 30.0F * scale;
            double outerEdge = (double) 65.0F * scale;

            for (int i = 0; i < modes.size(); ++i) {
                MenuRegion menuRegion = (MenuRegion) modes.get(i);
                double beginRadians = (double) i * radiansPerObject - quarterCircle;
                double endRadians = (double) (i + 1) * radiansPerObject - quarterCircle;
                menuRegion.x1 = Math.cos(beginRadians);
                menuRegion.x2 = Math.cos(endRadians);
                menuRegion.y1 = Math.sin(beginRadians);
                menuRegion.y2 = Math.sin(endRadians);
                double x1m1 = Math.cos(beginRadians + 0.015707963267948967) * innerEdge;
                double x2m1 = Math.cos(endRadians - 0.015707963267948967) * innerEdge;
                double y1m1 = Math.sin(beginRadians + 0.015707963267948967) * innerEdge;
                double y2m1 = Math.sin(endRadians - 0.015707963267948967) * innerEdge;
                double x1m2 = Math.cos(beginRadians + 0.007853981633974483) * outerEdge;
                double x2m2 = Math.cos(endRadians - 0.007853981633974483) * outerEdge;
                double y1m2 = Math.sin(beginRadians + 0.007853981633974483) * outerEdge;
                double y2m2 = Math.sin(endRadians - 0.007853981633974483) * outerEdge;
                boolean isSelected = currentBuildMode.ordinal() == i;
                boolean isMouseInQuad = this.inTriangle(x1m1, y1m1, x2m2, y2m2, x2m1, y2m1, mouseXCenter, mouseYCenter) || this.inTriangle(x1m1, y1m1, x1m2, y1m2, x2m2, y2m2, mouseXCenter, mouseYCenter);
                boolean isHighlighted = beginRadians <= mouseRadians && mouseRadians <= endRadians && isMouseInQuad;
                Vector4f color = this.radialButtonColor;
                if (isSelected) {
                    color = this.selectedColor;
                }

                if (isHighlighted) {
                    color = this.highlightColor;
                }

                if (isSelected && isHighlighted) {
                    color = this.highlightSelectedColor;
                }

                if (isHighlighted) {
                    menuRegion.highlighted = true;
                    this.switchTo = menuRegion.mode;
                }

                buffer.addVertex((float) (middleX + x1m1), (float) (middleY + y1m1), (float) this.getBlitOffset()).setColor(color.x(), color.y(), color.z(), color.w());
                buffer.addVertex((float) (middleX + x2m1), (float) (middleY + y2m1), (float) this.getBlitOffset()).setColor(color.x(), color.y(), color.z(), color.w());
                buffer.addVertex((float) (middleX + x2m2), (float) (middleY + y2m2), (float) this.getBlitOffset()).setColor(color.x(), color.y(), color.z(), color.w());
                buffer.addVertex((float) (middleX + x1m2), (float) (middleY + y1m2), (float) this.getBlitOffset()).setColor(color.x(), color.y(), color.z(), color.w());
                color = menuRegion.mode.category.color;
                double categoryLineOuterEdge = (double) 32.0F * scale;
                double x1m3 = Math.cos(beginRadians + 0.015707963267948967) * categoryLineOuterEdge;
                double x2m3 = Math.cos(endRadians - 0.015707963267948967) * categoryLineOuterEdge;
                double y1m3 = Math.sin(beginRadians + 0.015707963267948967) * categoryLineOuterEdge;
                double y2m3 = Math.sin(endRadians - 0.015707963267948967) * categoryLineOuterEdge;
                buffer.addVertex((float) (middleX + x1m1), (float) (middleY + y1m1), (float) this.getBlitOffset()).setColor(color.x(), color.y(), color.z(), color.w());
                buffer.addVertex((float) (middleX + x2m1), (float) (middleY + y2m1), (float) this.getBlitOffset()).setColor(color.x(), color.y(), color.z(), color.w());
                buffer.addVertex((float) (middleX + x2m3), (float) (middleY + y2m3), (float) this.getBlitOffset()).setColor(color.x(), color.y(), color.z(), color.w());
                buffer.addVertex((float) (middleX + x1m3), (float) (middleY + y1m3), (float) this.getBlitOffset()).setColor(color.x(), color.y(), color.z(), color.w());
            }
        }

    }

    private void drawSideButtonBackgrounds(BufferBuilder buffer, double middleX, double middleY, double mouseXCenter, double mouseYCenter, ArrayList<MenuButton> buttons, double scale) {
        for (MenuButton btn : buttons) {
            double bx1 = btn.x1 * scale;
            double bx2 = btn.x2 * scale;
            double by1 = btn.y1 * scale;
            double by2 = btn.y2 * scale;
            boolean isHighlighted = bx1 <= mouseXCenter && bx2 >= mouseXCenter && by1 <= mouseYCenter && by2 >= mouseYCenter;
            boolean isSelected = btn.enabled && (btn.action == ModeOptions.getBuildSpeed() || btn.action == ModeOptions.getFill() || btn.action == ModeOptions.getCubeFill() || btn.action == ModeOptions.getRaisedEdge() || btn.action == ModeOptions.getLineThickness() || btn.action == ModeOptions.getCircleStart() || btn.action == ModeOptions.getPointBuild() || btn.action == ModeOptions.getMeshFace() || btn.action == ModeOptions.getPyramidSides() || btn.action == ModeOptions.ActionEnum.CYCLE_REPLACE_MODE && BuildSettings.CLIENT.getReplaceMode() != BuildSettings.ReplaceMode.ONLY_AIR || btn.action == ModeOptions.ActionEnum.TOGGLE_ANGEL_PLACEMENT && BuildSettings.CLIENT.isAngelPlacementEnabled());
            Vector4f color;
            if (!btn.enabled) {
                color = isHighlighted ? this.disabledHighlightColor : this.disabledSideButtonColor;
            } else {
                color = this.sideButtonColor;
                if (isSelected) {
                    color = this.selectedColor;
                }

                if (isHighlighted) {
                    color = this.highlightColor;
                }

                if (isSelected && isHighlighted) {
                    color = this.highlightSelectedColor;
                }
            }

            if (isHighlighted) {
                btn.highlighted = true;
                if (btn.enabled) {
                    this.doAction = btn.action;
                }
            }

            buffer.addVertex((float) (middleX + bx1), (float) (middleY + by1), (float) this.getBlitOffset()).setColor(color.x(), color.y(), color.z(), color.w());
            buffer.addVertex((float) (middleX + bx1), (float) (middleY + by2), (float) this.getBlitOffset()).setColor(color.x(), color.y(), color.z(), color.w());
            buffer.addVertex((float) (middleX + bx2), (float) (middleY + by2), (float) this.getBlitOffset()).setColor(color.x(), color.y(), color.z(), color.w());
            buffer.addVertex((float) (middleX + bx2), (float) (middleY + by1), (float) this.getBlitOffset()).setColor(color.x(), color.y(), color.z(), color.w());
        }

    }

    private void drawIcons(GuiGraphics graphics, double middleX, double middleY, ArrayList<MenuRegion> modes, ArrayList<MenuButton> buttons, double scale) {
        graphics.pose().pushPose();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        for (MenuRegion menuRegion : modes) {
            double x = (menuRegion.x1 + menuRegion.x2) * (double) 0.5F * (double) 49.25F * scale;
            double y = (menuRegion.y1 + menuRegion.y2) * (double) 0.5F * (double) 49.25F * scale;
            menuRegion.mode.icon.render(graphics, (int) (middleX + x - (double) 8.0F), (int) (middleY + y - (double) 8.0F));
        }

        for (MenuButton button : buttons) {
            double x = (button.x1 + button.x2) / (double) 2.0F * scale;
            double y = (button.y1 + button.y2) / (double) 2.0F * scale;
            button.getIcon().render(graphics, (int) (middleX + x - (double) 8.0F), (int) (middleY + y - (double) 8.0F));
        }

        graphics.pose().popPose();
    }

    private void drawTexts(GuiGraphics graphics, BuildModeEnum currentBuildMode, double middleX, double middleY, ArrayList<MenuRegion> modes, ArrayList<MenuButton> buttons, ModeOptions.OptionEnum[] options, int mouseX, int mouseY, double scale) {
        for (int i = 0; i < currentBuildMode.options.length; ++i) {
            ModeOptions.OptionEnum option = options[i];
            graphics.drawString(this.font, I18n.get(option.name, new Object[0]), (int) (middleX + (double) 105.0F * scale - (double) 9.0F), (int) middleY - 37 + i * 39, -286331137, true);
        }

//      String credits = "Effortless Building";
//      graphics.drawString(this.font, credits, this.width - this.font.width(credits) - 4, this.height - 10, -2004318072, true);

        for (MenuRegion menuRegion : modes) {
            if (menuRegion.highlighted) {
                double x = (menuRegion.x1 + menuRegion.x2) * (double) 0.5F;
                double y = (menuRegion.y1 + menuRegion.y2) * (double) 0.5F;
                int fixed_x = (int) (x * (double) 75.0F * scale);
                int var10000 = (int) (y * (double) 75.0F * scale);
                Objects.requireNonNull(this.font);
                int fixed_y = var10000 - 9 / 2;
                String text = I18n.get(menuRegion.mode.getNameKey(), new Object[0]);
                if (x <= -0.2) {
                    fixed_x -= this.font.width(text);
                } else if (-0.2 <= x && x <= 0.2) {
                    fixed_x -= this.font.width(text) / 2;
                }

                graphics.drawString(this.font, text, (int) middleX + fixed_x, (int) middleY + fixed_y, -1, true);
                graphics.drawString(this.font, text, (int) middleX + fixed_x, (int) middleY + fixed_y, -1, true);
                text = I18n.get(menuRegion.mode.getDescriptionKey(), new Object[0]);
                graphics.drawString(this.font, text, (int) (middleX - (double) ((float) this.font.width(text) / 2.0F)), (int) middleY + 100, -578254712, true);
            }
        }

        for (MenuButton button : buttons) {
            if (button.highlighted) {
                ArrayList<Component> tooltip = new ArrayList();
                tooltip.add(Component.literal(button.name).withStyle(ChatFormatting.AQUA));
                if (!button.subtitle.isEmpty()) {
                    tooltip.add(Component.literal(button.subtitle).withStyle(ChatFormatting.WHITE));
                }

                if (!button.description.isEmpty()) {
                    String[] paragraphs = button.description.split("\n");

                    for (int pi = 0; pi < paragraphs.length; ++pi) {
                        String paragraph = paragraphs[pi];
                        if (paragraph.isEmpty()) {
                            tooltip.add(Component.empty());
                        } else {
                            for (FormattedText line : this.font.getSplitter().splitLines(paragraph, 200, Style.EMPTY)) {
                                tooltip.add(Component.literal(line.getString()).withStyle(ChatFormatting.GRAY));
                            }
                        }
                    }
                }

                graphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
            }
        }

    }

    private boolean inTriangle(double x1, double y1, double x2, double y2, double x3, double y3, double x, double y) {
        double ab = (x1 - x) * (y2 - y) - (x2 - x) * (y1 - y);
        double bc = (x2 - x) * (y3 - y) - (x3 - x) * (y2 - y);
        double ca = (x3 - x) * (y1 - y) - (x1 - x) * (y3 - y);
        return this.sign(ab) == this.sign(bc) && this.sign(bc) == this.sign(ca);
    }

    private int sign(double n) {
        return n > (double) 0.0F ? 1 : -1;
    }

    private double getBlitOffset() {
        return (double) 0.0F;
    }

    public boolean isPauseScreen() {
        return false;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        this.performAction(true);
        return super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    public void onClose() {
        super.onClose();
        if (!this.performedActionUsingMouse) {
            this.performAction(false);
        }

    }

    private void performAction(boolean fromMouseClick) {
        if (this.switchTo != null) {
            playRadialMenuSound();
            BuildModes.CLIENT.setBuildMode(this.switchTo);

            if (this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(Component.translatable(this.switchTo.getNameKey()), true);
            }

            if (fromMouseClick) {
                this.performedActionUsingMouse = true;
            }
        }

        ModeOptions.ActionEnum action = this.doAction;
        if (action != null) {
            playRadialMenuSound();
            if (action == ModeOptions.ActionEnum.OPEN_MODIFIER_SETTINGS) {
                this.performedActionUsingMouse = true;
                this.minecraft.setScreen(new ModifiersScreen());
                return;
            }

            ModeOptions.performAction(this.minecraft.player, action);
            if (fromMouseClick) {
                this.performedActionUsingMouse = true;
            }
        }

    }

    public static void playRadialMenuSound() {
        float volume = 0.1F;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.1F));
    }

    private static class MenuButton {
        public final ModeOptions.ActionEnum action;
        public AllIcons iconOverride;
        public double x1;
        public double x2;
        public double y1;
        public double y2;
        public boolean highlighted;
        public boolean enabled = true;
        public String name;
        public String subtitle = "";
        public String description = "";
        public Direction textSide;

        public MenuButton(ModeOptions.ActionEnum action, double x, double y, Direction textSide) {
            this.name = I18n.get(action.getNameKey(), new Object[0]);
            if (action == ModeOptions.ActionEnum.UNDO) {
                String var10001 = this.description;
                this.description = var10001 + "[Ctrl+" + ClientModEvents.KEY_UNDO.getTranslatedKeyMessage().getString() + "]";
            } else if (action == ModeOptions.ActionEnum.REDO) {
                String var7 = this.description;
                this.description = var7 + "[Ctrl+" + ClientModEvents.KEY_REDO.getTranslatedKeyMessage().getString() + "]";
            } else if (action == ModeOptions.ActionEnum.OPEN_MODIFIER_SETTINGS) {
                String var8 = this.description;
                this.description = var8 + "[" + ClientModEvents.KEY_OPEN_MODIFIERS_SCREEN.getTranslatedKeyMessage().getString() + "]";
            }

            if (I18n.exists(action.getDescriptionKey())) {
                this.description = I18n.get(action.getDescriptionKey(), new Object[0]);
            }

            this.action = action;
            this.x1 = x - (double) 10.0F;
            this.x2 = x + (double) 10.0F;
            this.y1 = y - (double) 10.0F;
            this.y2 = y + (double) 10.0F;
            this.textSide = textSide;
        }

        public AllIcons getIcon() {
            return this.iconOverride != null ? this.iconOverride : this.action.icon;
        }
    }

    static class MenuRegion {
        public final BuildModeEnum mode;
        public double x1;
        public double x2;
        public double y1;
        public double y2;
        public boolean highlighted;

        public MenuRegion(BuildModeEnum mode) {
            this.mode = mode;
        }
    }
}
