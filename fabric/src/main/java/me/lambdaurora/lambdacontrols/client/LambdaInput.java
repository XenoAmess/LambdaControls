/*
 * Copyright © 2020 LambdAurora <aurora42lambda@gmail.com>
 *
 * This file is part of LambdaControls.
 *
 * Licensed under the MIT license. For more information,
 * see the LICENSE file.
 */

package me.lambdaurora.lambdacontrols.client;

import me.lambdaurora.lambdacontrols.LambdaControlsFeature;
import me.lambdaurora.lambdacontrols.client.compat.LambdaControlsCompat;
import me.lambdaurora.lambdacontrols.client.controller.ButtonBinding;
import me.lambdaurora.lambdacontrols.client.controller.Controller;
import me.lambdaurora.lambdacontrols.client.controller.InputManager;
import me.lambdaurora.lambdacontrols.client.gui.ControllerControlsScreen;
import me.lambdaurora.lambdacontrols.client.gui.TouchscreenOverlay;
import me.lambdaurora.lambdacontrols.client.mixin.AdvancementsScreenAccessor;
import me.lambdaurora.lambdacontrols.client.mixin.CreativeInventoryScreenAccessor;
import me.lambdaurora.lambdacontrols.client.mixin.EntryListWidgetAccessor;
import me.lambdaurora.lambdacontrols.client.util.ContainerScreenAccessor;
import me.lambdaurora.spruceui.SpruceLabelWidget;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.ParentElement;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.advancement.AdvancementTab;
import net.minecraft.client.gui.screen.advancement.AdvancementsScreen;
import net.minecraft.client.gui.screen.ingame.ContainerScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.gui.screen.world.WorldListWidget;
import net.minecraft.client.gui.widget.AbstractPressableButtonWidget;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.container.Slot;
import net.minecraft.container.SlotActionType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import org.aperlambda.lambdacommon.utils.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static me.lambdaurora.lambdacontrols.client.controller.ButtonBinding.axisAsButton;
import static me.lambdaurora.lambdacontrols.client.controller.InputManager.INPUT_MANAGER;
import static org.lwjgl.glfw.GLFW.*;

/**
 * Represents the LambdaControls' input handler.
 *
 * @author LambdAurora
 * @version 1.2.0
 * @since 1.0.0
 */
public class LambdaInput
{
    private static final Map<Integer, Integer> BUTTON_COOLDOWNS  = new HashMap<>();
    private final        LambdaControlsConfig  config;
    // Cooldowns
    private              int                   actionGuiCooldown = 0;
    private              int                   ignoreNextA       = 0;
    // Sneak state.
    private              double                prevTargetYaw     = 0.0;
    private              double                prevTargetPitch   = 0.0;
    private              double                targetYaw         = 0.0;
    private              double                targetPitch       = 0.0;
    private              float                 prevXAxis         = 0.F;
    private              float                 prevYAxis         = 0.F;
    private              int                   targetMouseX      = 0;
    private              int                   targetMouseY      = 0;
    private              float                 mouseSpeedX       = 0.F;
    private              float                 mouseSpeedY       = 0.F;

    public LambdaInput(@NotNull LambdaControlsClient mod)
    {
        this.config = mod.config;
    }

    /**
     * This method is called every Minecraft tick.
     *
     * @param client The client instance.
     */
    public void onTick(@NotNull MinecraftClient client)
    {
        this.prevTargetYaw = this.targetYaw;
        this.prevTargetPitch = this.targetPitch;

        // Handles the key bindings.
        if (LambdaControlsClient.BINDING_LOOK_UP.isPressed()) {
            this.handleLook(client, GLFW_GAMEPAD_AXIS_RIGHT_Y, 0.8F, 2);
        } else if (LambdaControlsClient.BINDING_LOOK_DOWN.isPressed()) {
            this.handleLook(client, GLFW_GAMEPAD_AXIS_RIGHT_Y, 0.8F, 1);
        }
        if (LambdaControlsClient.BINDING_LOOK_LEFT.isPressed()) {
            this.handleLook(client, GLFW_GAMEPAD_AXIS_RIGHT_X, 0.8F, 2);
        } else if (LambdaControlsClient.BINDING_LOOK_RIGHT.isPressed()) {
            this.handleLook(client, GLFW_GAMEPAD_AXIS_RIGHT_X, 0.8F, 1);
        }

        INPUT_MANAGER.tick(client);
    }

    /**
     * This method is called every Minecraft tick for controller input update.
     *
     * @param client The client instance.
     */
    public void onControllerTick(@NotNull MinecraftClient client)
    {
        BUTTON_COOLDOWNS.entrySet().stream().filter(entry -> entry.getValue() > 0).forEach(entry -> BUTTON_COOLDOWNS.put(entry.getKey(), entry.getValue() - 1));
        // Decreases the cooldown for GUI actions.
        if (this.actionGuiCooldown > 0)
            --this.actionGuiCooldown;

        InputManager.updateStates();

        Controller controller = this.config.getController();
        if (controller.isConnected()) {
            GLFWGamepadState state = controller.getState();
            this.fetchButtonInput(client, state, false);
            this.fetchAxeInput(client, state, false);
        }
        this.config.getSecondController().filter(Controller::isConnected)
                .ifPresent(joycon -> {
                    GLFWGamepadState state = joycon.getState();
                    this.fetchButtonInput(client, state, true);
                    this.fetchAxeInput(client, state, true);
                });

        boolean allowInput = true;

        if (client.currentScreen instanceof ControllerControlsScreen && ((ControllerControlsScreen) client.currentScreen).focusedBinding != null)
            allowInput = false;

        if (allowInput)
            InputManager.updateBindings(client);

        if (this.ignoreNextA > 0)
            this.ignoreNextA--;

        if (client.currentScreen instanceof ControllerControlsScreen && InputManager.STATES.entrySet().parallelStream().map(Map.Entry::getValue).allMatch(ButtonState::isUnpressed)) {
            ControllerControlsScreen screen = (ControllerControlsScreen) client.currentScreen;
            if (screen.focusedBinding != null && !screen.waiting) {
                int[] buttons = new int[screen.currentButtons.size()];
                for (int i = 0; i < screen.currentButtons.size(); i++)
                    buttons[i] = screen.currentButtons.get(i);
                screen.focusedBinding.setButton(buttons);
                screen.focusedBinding = null;
            }
        }
    }

    /**
     * This method is called before the screen is rendered.
     *
     * @param client The client instance.
     * @param screen The screen to render.
     */
    public void onPreRenderScreen(@NotNull MinecraftClient client, @NotNull Screen screen)
    {
        if (!isScreenInteractive(screen)) {
            INPUT_MANAGER.updateMousePosition(client);
        }
    }

    /**
     * This method is called when Minecraft renders.
     *
     * @param client The client instance.
     */
    public void onRender(@NotNull MinecraftClient client)
    {
        if ((client.currentScreen == null || client.currentScreen instanceof TouchscreenOverlay) &&
                (this.prevTargetYaw != this.targetYaw || this.prevTargetPitch != this.targetPitch)) {
            float deltaYaw = (float) ((this.targetYaw - client.player.prevYaw) * client.getTickDelta());
            float deltaPitch = (float) ((this.targetPitch - client.player.prevPitch) * client.getTickDelta());
            float rotationYaw = client.player.prevYaw + deltaYaw;
            float rotationPitch = client.player.prevPitch + deltaPitch;
            client.player.yaw = rotationYaw;
            client.player.pitch = MathHelper.clamp(rotationPitch, -90.F, 90.F);
            if (client.player.isRiding()) {
                client.player.getVehicle().copyPositionAndRotation(client.player);
            }
            client.getTutorialManager().onUpdateMouse(deltaPitch, deltaYaw);
        }
    }

    /**
     * This method is called when a Screen is opened.
     *
     * @param client       The client instance.
     * @param windowWidth  The window width.
     * @param windowHeight The window height.
     */
    public void onScreenOpen(@NotNull MinecraftClient client, int windowWidth, int windowHeight)
    {
        if (client.currentScreen == null) {
            this.mouseSpeedX = this.mouseSpeedY = 0.0F;
            INPUT_MANAGER.resetMousePosition(windowWidth, windowHeight);
        }
    }

    private void fetchButtonInput(@NotNull MinecraftClient client, @NotNull GLFWGamepadState gamepadState, boolean leftJoycon)
    {
        ByteBuffer buffer = gamepadState.buttons();
        for (int i = 0; i < buffer.limit(); i++) {
            int btn = leftJoycon ? ButtonBinding.controller2Button(i) : i;
            boolean btnState = buffer.get() == (byte) 1;
            ButtonState state = ButtonState.NONE;
            ButtonState previousState = InputManager.STATES.getOrDefault(btn, ButtonState.NONE);

            if (btnState != previousState.isPressed()) {
                state = btnState ? ButtonState.PRESS : ButtonState.RELEASE;
                this.handleButton(client, btn, btnState ? 0 : 1, btnState);
                if (btnState)
                    BUTTON_COOLDOWNS.put(btn, 5);
            } else if (btnState) {
                state = ButtonState.REPEAT;
                if (BUTTON_COOLDOWNS.getOrDefault(btn, 0) == 0) {
                    BUTTON_COOLDOWNS.put(btn, 5);
                    this.handleButton(client, btn, 2, true);
                }
            }

            InputManager.STATES.put(btn, state);
        }
    }

    private void fetchAxeInput(@NotNull MinecraftClient client, @NotNull GLFWGamepadState gamepadState, boolean leftJoycon)
    {
        FloatBuffer buffer = gamepadState.axes();
        for (int i = 0; i < buffer.limit(); i++) {
            int axis = leftJoycon ? ButtonBinding.controller2Button(i) : i;
            float value = buffer.get();
            float absValue = Math.abs(value);

            if (i == GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y)
                value *= -1.0F;

            int state = value > this.config.getDeadZone() ? 1 : (value < -this.config.getDeadZone() ? 2 : 0);
            this.handleAxe(client, axis, value, absValue, state);
        }
    }

    private void handleButton(@NotNull MinecraftClient client, int button, int action, boolean state)
    {
        if (client.currentScreen instanceof ControllerControlsScreen) {
            ControllerControlsScreen screen = (ControllerControlsScreen) client.currentScreen;
            if (screen.focusedBinding != null) {
                if (action == 0 && !screen.currentButtons.contains(button)) {
                    screen.currentButtons.add(button);

                    int[] buttons = new int[screen.currentButtons.size()];
                    for (int i = 0; i < screen.currentButtons.size(); i++)
                        buttons[i] = screen.currentButtons.get(i);
                    screen.focusedBinding.setButton(buttons);

                    screen.waiting = false;
                }
                return;
            }
        }

        if (action == 0 || action == 2) {
            if (client.currentScreen != null && isScreenInteractive(client.currentScreen)
                    && (button == GLFW.GLFW_GAMEPAD_BUTTON_DPAD_UP || button == GLFW.GLFW_GAMEPAD_BUTTON_DPAD_DOWN
                    || button == GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT || button == GLFW.GLFW_GAMEPAD_BUTTON_DPAD_RIGHT)) {
                if (this.actionGuiCooldown == 0) {
                    if (button == GLFW.GLFW_GAMEPAD_BUTTON_DPAD_UP) {
                        this.changeFocus(client.currentScreen, false);
                    } else if (button == GLFW.GLFW_GAMEPAD_BUTTON_DPAD_DOWN) {
                        this.changeFocus(client.currentScreen, true);
                    } else if (button == GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT) {
                        this.handleLeftRight(client.currentScreen, false);
                    } else {
                        this.handleLeftRight(client.currentScreen, true);
                    }
                }
                return;
            }
        }

        if (action == 1) {
            if (button == GLFW.GLFW_GAMEPAD_BUTTON_A && client.currentScreen != null) {
                if (this.actionGuiCooldown == 0) {
                    Element focused = client.currentScreen.getFocused();
                    if (focused != null && isScreenInteractive(client.currentScreen)) {
                        if (this.handleAButton(client.currentScreen, focused)) {
                            this.actionGuiCooldown = 5; // Prevent to press too quickly the focused element, so we have to skip 5 ticks.
                            return;
                        }
                    }
                }
            }

            if (client.currentScreen instanceof ContainerScreen && client.interactionManager != null && client.player != null) {
                double x = client.mouse.getX() * (double) client.getWindow().getScaledWidth() / (double) client.getWindow().getWidth();
                double y = client.mouse.getY() * (double) client.getWindow().getScaledHeight() / (double) client.getWindow().getHeight();
                Slot slot = ((ContainerScreenAccessor) client.currentScreen).lambdacontrols_getSlotAt(x, y);
                SlotActionType slotAction = SlotActionType.PICKUP;
                if (button == GLFW.GLFW_GAMEPAD_BUTTON_A && slot != null) {
                    if (client.currentScreen instanceof CreativeInventoryScreen) {
                        if (((CreativeInventoryScreenAccessor) client.currentScreen).lambdacontrols_isCreativeInventorySlot(slot))
                            slotAction = SlotActionType.CLONE;
                    }
                    client.interactionManager.clickSlot(((ContainerScreen) client.currentScreen).getContainer().syncId, slot.id, GLFW.GLFW_MOUSE_BUTTON_1, slotAction, client.player);
                    client.player.playerContainer.sendContentUpdates();
                    this.actionGuiCooldown = 5;
                    return;
                } else if (button == GLFW.GLFW_GAMEPAD_BUTTON_B) {
                    client.player.closeContainer();
                    return;
                } else if (button == GLFW.GLFW_GAMEPAD_BUTTON_X && slot != null) {
                    client.interactionManager.clickSlot(((ContainerScreen) client.currentScreen).getContainer().syncId, slot.id, GLFW.GLFW_MOUSE_BUTTON_2, SlotActionType.PICKUP, client.player);
                    return;
                } else if (button == GLFW.GLFW_GAMEPAD_BUTTON_Y && slot != null) {
                    client.interactionManager.clickSlot(((ContainerScreen) client.currentScreen).getContainer().syncId, slot.id, GLFW.GLFW_MOUSE_BUTTON_1, SlotActionType.QUICK_MOVE, client.player);
                    return;
                }
            } else if (button == GLFW.GLFW_GAMEPAD_BUTTON_B) {
                if (client.currentScreen != null) {
                    client.currentScreen.onClose();
                    return;
                }
            }
        }

        if (button == GLFW.GLFW_GAMEPAD_BUTTON_A && client.currentScreen != null && !isScreenInteractive(client.currentScreen) && this.actionGuiCooldown == 0 && this.ignoreNextA == 0) {
            double mouseX = client.mouse.getX() * (double) client.getWindow().getScaledWidth() / (double) client.getWindow().getWidth();
            double mouseY = client.mouse.getY() * (double) client.getWindow().getScaledHeight() / (double) client.getWindow().getHeight();
            if (action == 0) {
                client.currentScreen.mouseClicked(mouseX, mouseY, GLFW.GLFW_MOUSE_BUTTON_1);
            } else if (action == 1) {
                client.currentScreen.mouseReleased(mouseX, mouseY, GLFW.GLFW_MOUSE_BUTTON_1);
            }
            this.actionGuiCooldown = 5;
        }
    }

    private void handleAxe(@NotNull MinecraftClient client, int axis, float value, float absValue, int state)
    {
        int asButtonState = value > 0.5F ? 1 : (value < -0.5F ? 2 : 0);

        if (axis == GLFW_GAMEPAD_AXIS_LEFT_TRIGGER || axis == GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER || axis == ButtonBinding.controller2Button(GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER) ||
                axis == ButtonBinding.controller2Button(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER))
            if (asButtonState == 2)
                asButtonState = 0;

        {
            boolean currentPlusState = asButtonState == 1;
            boolean currentMinusState = asButtonState == 2;
            ButtonState previousPlusState = InputManager.STATES.getOrDefault(axisAsButton(axis, true), ButtonState.NONE);
            ButtonState previousMinusState = InputManager.STATES.getOrDefault(axisAsButton(axis, false), ButtonState.NONE);

            if (currentPlusState != previousPlusState.isPressed()) {
                InputManager.STATES.put(axisAsButton(axis, true), currentPlusState ? ButtonState.PRESS : ButtonState.RELEASE);
                if (currentPlusState)
                    BUTTON_COOLDOWNS.put(axisAsButton(axis, true), 5);
            } else if (currentPlusState) {
                InputManager.STATES.put(axisAsButton(axis, true), ButtonState.REPEAT);
                if (BUTTON_COOLDOWNS.getOrDefault(axisAsButton(axis, true), 0) == 0) {
                    BUTTON_COOLDOWNS.put(axisAsButton(axis, true), 5);
                }
            }

            if (currentMinusState != previousMinusState.isPressed()) {
                InputManager.STATES.put(axisAsButton(axis, false), currentMinusState ? ButtonState.PRESS : ButtonState.RELEASE);
                if (currentMinusState)
                    BUTTON_COOLDOWNS.put(axisAsButton(axis, false), 5);
            } else if (currentMinusState) {
                InputManager.STATES.put(axisAsButton(axis, false), ButtonState.REPEAT);
                if (BUTTON_COOLDOWNS.getOrDefault(axisAsButton(axis, false), 0) == 0) {
                    BUTTON_COOLDOWNS.put(axisAsButton(axis, false), 5);
                }
            }
        }

        double deadZone = this.config.getDeadZone();

        if (client.currentScreen instanceof ControllerControlsScreen) {
            ControllerControlsScreen screen = (ControllerControlsScreen) client.currentScreen;
            if (screen.focusedBinding != null) {
                if (asButtonState != 0 && !screen.currentButtons.contains(axisAsButton(axis, asButtonState == 1))) {

                    screen.currentButtons.add(axisAsButton(axis, asButtonState == 1));

                    int[] buttons = new int[screen.currentButtons.size()];
                    for (int i = 0; i < screen.currentButtons.size(); i++)
                        buttons[i] = screen.currentButtons.get(i);
                    screen.focusedBinding.setButton(buttons);

                    screen.waiting = false;
                }
                return;
            }
        } else if (client.currentScreen instanceof CreativeInventoryScreen) {
            if (axis == GLFW_GAMEPAD_AXIS_RIGHT_Y) {
                CreativeInventoryScreen screen = (CreativeInventoryScreen) client.currentScreen;
                CreativeInventoryScreenAccessor accessor = (CreativeInventoryScreenAccessor) screen;
                if (accessor.lambdacontrols_hasScrollbar() && absValue >= deadZone) {
                    screen.mouseScrolled(0.0, 0.0, -value);
                }
                return;
            }
        } else if (client.currentScreen instanceof AdvancementsScreen) {
            if (axis == GLFW_GAMEPAD_AXIS_RIGHT_X || axis == GLFW_GAMEPAD_AXIS_RIGHT_Y) {
                AdvancementsScreen screen = (AdvancementsScreen) client.currentScreen;
                AdvancementsScreenAccessor accessor = (AdvancementsScreenAccessor) screen;
                if (absValue >= deadZone) {
                    AdvancementTab tab = accessor.getSelectedTab();
                    tab.move(axis == GLFW_GAMEPAD_AXIS_RIGHT_X ? -value * 5.0 : 0.0, axis == GLFW_GAMEPAD_AXIS_RIGHT_Y ? -value * 5.0 : 0.0);
                }
                return;
            }
        }

        if (client.currentScreen == null) {
            // Handles the look direction.
            this.handleLook(client, axis, (float) (absValue / (1.0 - this.config.getDeadZone())), state);
        } else {
            boolean allowMouseControl = true;

            if (this.actionGuiCooldown == 0 && this.config.isMovementAxis(axis) && isScreenInteractive(client.currentScreen)) {
                if (this.config.isForwardButton(axis, false, asButtonState)) {
                    allowMouseControl = this.changeFocus(client.currentScreen, false);
                } else if (this.config.isBackButton(axis, false, asButtonState)) {
                    allowMouseControl = this.changeFocus(client.currentScreen, true);
                } else if (this.config.isLeftButton(axis, false, asButtonState)) {
                    allowMouseControl = this.handleLeftRight(client.currentScreen, false);
                } else if (this.config.isRightButton(axis, false, asButtonState)) {
                    allowMouseControl = this.handleLeftRight(client.currentScreen, true);
                }
            }

            float movementX = 0.0F;
            float movementY = 0.0F;

            if (this.config.isBackButton(axis, false, (value > 0 ? 1 : 2))) {
                movementY = absValue;
            } else if (this.config.isForwardButton(axis, false, (value > 0 ? 1 : 2))) {
                movementY = -absValue;
            } else if (this.config.isLeftButton(axis, false, (value > 0 ? 1 : 2))) {
                movementX = -absValue;
            } else if (this.config.isRightButton(axis, false, (value > 0 ? 1 : 2))) {
                movementX = absValue;
            }

            if (client.currentScreen != null && allowMouseControl) {
                boolean moving = Math.abs(movementY) >= deadZone || Math.abs(movementX) >= deadZone;
                if (moving) {
                /*
                    Updates the target mouse position when the initial movement stick movement is detected.
                    It prevents the cursor to jump to the old target mouse position if the user moves the cursor with the mouse.
                 */
                    if (Math.abs(prevXAxis) < deadZone && Math.abs(prevYAxis) < deadZone) {
                        INPUT_MANAGER.resetMouseTarget(client);
                    }

                    if (Math.abs(movementX) >= deadZone)
                        this.mouseSpeedX = movementX;
                    else
                        this.mouseSpeedX = 0.F;

                    if (Math.abs(movementY) >= deadZone)
                        this.mouseSpeedY = movementY;
                    else
                        this.mouseSpeedY = 0.F;
                } else {
                    this.mouseSpeedX = 0.F;
                    this.mouseSpeedY = 0.F;
                }

                if (Math.abs(this.mouseSpeedX) >= .05F || Math.abs(this.mouseSpeedY) >= .05F) {
                    InputManager.queueMoveMousePosition(this.mouseSpeedX * this.config.getMouseSpeed(), this.mouseSpeedY * this.config.getMouseSpeed());
                }

                this.moveMouseToClosestSlot(client, client.currentScreen);
            }

            this.prevXAxis = movementX;
            this.prevYAxis = movementY;
        }
    }

    private boolean handleAButton(@NotNull Screen screen, @NotNull Element focused)
    {
        if (focused instanceof AbstractPressableButtonWidget) {
            AbstractPressableButtonWidget widget = (AbstractPressableButtonWidget) focused;
            widget.playDownSound(MinecraftClient.getInstance().getSoundManager());
            widget.onPress();
            return true;
        } else if (focused instanceof SpruceLabelWidget) {
            ((SpruceLabelWidget) focused).onPress();
            return true;
        } else if (focused instanceof WorldListWidget) {
            WorldListWidget list = (WorldListWidget) focused;
            list.method_20159().ifPresent(WorldListWidget.Entry::play);
            return true;
        } else if (focused instanceof MultiplayerServerListWidget) {
            MultiplayerServerListWidget list = (MultiplayerServerListWidget) focused;
            MultiplayerServerListWidget.Entry entry = list.getSelected();
            if (entry instanceof MultiplayerServerListWidget.LanServerEntry || entry instanceof MultiplayerServerListWidget.ServerEntry) {
                ((MultiplayerScreen) screen).select(entry);
                ((MultiplayerScreen) screen).connect();
            }
        } else if (focused instanceof ParentElement) {
            Element childFocused = ((ParentElement) focused).getFocused();
            if (childFocused != null)
                return this.handleAButton(screen, childFocused);
        }
        return false;
    }

    /**
     * Handles the left and right buttons.
     *
     * @param screen The current screen.
     * @param right  True if the right button is pressed, else false.
     */
    private boolean handleLeftRight(@NotNull Screen screen, boolean right)
    {
        Element focused = screen.getFocused();
        if (focused != null)
            if (this.handleRightLeftElement(focused, right))
                return this.changeFocus(screen, right);
        return true;
    }

    private boolean handleRightLeftElement(@NotNull Element element, boolean right)
    {
        if (element instanceof SliderWidget) {
            SliderWidget slider = (SliderWidget) element;
            slider.keyPressed(right ? 262 : 263, 0, 0);
            this.actionGuiCooldown = 2; // Prevent to press too quickly the focused element, so we have to skip 5 ticks.
            return false;
        } else if (element instanceof AlwaysSelectedEntryListWidget) {
            ((EntryListWidgetAccessor) element).lambdacontrols_moveSelection(right ? 1 : -1);
            return false;
        } else if (element instanceof ParentElement) {
            ParentElement entryList = (ParentElement) element;
            Element focused = entryList.getFocused();
            if (focused == null)
                return true;
            return this.handleRightLeftElement(focused, right);
        }
        return true;
    }

    /**
     * Handles the look direction input.
     *
     * @param client The client isntance.
     * @param axis   The axis to change.
     * @param value  The value of the look.
     * @param state  The state.
     */
    public void handleLook(@NotNull MinecraftClient client, int axis, float value, int state)
    {
        // Handles the look direction.
        if (client.player != null) {
            double powValue = Math.pow(value, 4.0);
            if (axis == GLFW_GAMEPAD_AXIS_RIGHT_Y) {
                if (state == 2) {
                    this.targetPitch = client.player.pitch - this.config.getRightYAxisSign() * (this.config.getRotationSpeed() * powValue) * 0.33D;
                    this.targetPitch = MathHelper.clamp(this.targetPitch, -90.0D, 90.0D);
                } else if (state == 1) {
                    this.targetPitch = client.player.pitch + this.config.getRightYAxisSign() * (this.config.getRotationSpeed() * powValue) * 0.33D;
                    this.targetPitch = MathHelper.clamp(this.targetPitch, -90.0D, 90.0D);
                }
            }
            if (axis == GLFW_GAMEPAD_AXIS_RIGHT_X) {
                if (state == 2) {
                    this.targetYaw = client.player.yaw - this.config.getRightXAxisSign() * (this.config.getRotationSpeed() * powValue) * 0.33D;
                } else if (state == 1) {
                    this.targetYaw = client.player.yaw + this.config.getRightXAxisSign() * (this.config.getRotationSpeed() * powValue) * 0.33D;
                }
            }
        }
    }

    private boolean changeFocus(@NotNull Screen screen, boolean down)
    {
        if (!screen.changeFocus(down)) {
            if (screen.changeFocus(down)) {
                this.actionGuiCooldown = 5;
                return false;
            }
            return true;
        } else {
            this.actionGuiCooldown = 5;
            return false;
        }
    }

    private static boolean isScreenInteractive(@NotNull Screen screen)
    {
        return !(screen instanceof AdvancementsScreen || screen instanceof ContainerScreen || LambdaControlsCompat.requireMouseOnScreen(screen));
    }

    // Inspired from https://github.com/MrCrayfish/Controllable/blob/1.14.X/src/main/java/com/mrcrayfish/controllable/client/ControllerInput.java#L686.
    private void moveMouseToClosestSlot(@NotNull MinecraftClient client, @Nullable Screen screen)
    {
        // Makes the mouse attracted to slots. This helps with selecting items when using a controller.
        if (screen instanceof ContainerScreen) {
            ContainerScreen inventoryScreen = (ContainerScreen) screen;
            ContainerScreenAccessor accessor = (ContainerScreenAccessor) inventoryScreen;
            int guiLeft = accessor.getX();
            int guiTop = accessor.getY();
            int mouseX = (int) (targetMouseX * (double) client.getWindow().getScaledWidth() / (double) client.getWindow().getWidth());
            int mouseY = (int) (targetMouseY * (double) client.getWindow().getScaledHeight() / (double) client.getWindow().getHeight());

            // Finds the closest slot in the GUI within 14 pixels.
            Optional<Pair<Slot, Double>> closestSlot = inventoryScreen.getContainer().slots.parallelStream()
                    .map(slot -> {
                        int x = guiLeft + slot.xPosition + 8;
                        int y = guiTop + slot.yPosition + 8;

                        // Distance between the slot and the cursor.
                        double distance = Math.sqrt(Math.pow(x - mouseX, 2) + Math.pow(y - mouseY, 2));
                        return Pair.of(slot, distance);
                    }).filter(entry -> entry.value <= 14.0)
                    .min(Comparator.comparingDouble(p -> p.value));

            if (closestSlot.isPresent()) {
                Slot slot = closestSlot.get().key;
                if (slot.hasStack() || !client.player.inventory.getMainHandStack().isEmpty()) {
                    int slotCenterXScaled = guiLeft + slot.xPosition + 8;
                    int slotCenterYScaled = guiTop + slot.yPosition + 8;
                    int slotCenterX = (int) (slotCenterXScaled / ((double) client.getWindow().getScaledWidth() / (double) client.getWindow().getWidth()));
                    int slotCenterY = (int) (slotCenterYScaled / ((double) client.getWindow().getScaledHeight() / (double) client.getWindow().getHeight()));
                    double deltaX = slotCenterX - targetMouseX;
                    double deltaY = slotCenterY - targetMouseY;

                    if (mouseX != slotCenterXScaled || mouseY != slotCenterYScaled) {
                        this.targetMouseX += deltaX * 0.75;
                        this.targetMouseY += deltaY * 0.75;
                    } else {
                        this.mouseSpeedX *= 0.3F;
                        this.mouseSpeedY *= 0.3F;
                    }
                    this.mouseSpeedX *= .75F;
                    this.mouseSpeedY *= .75F;
                } else {
                    this.mouseSpeedX *= .1F;
                    this.mouseSpeedY *= .1F;
                }
            } else {
                this.mouseSpeedX *= .3F;
                this.mouseSpeedY *= .3F;
            }
        } else {
            this.mouseSpeedX = 0.F;
            this.mouseSpeedY = 0.F;
        }
    }

    public static Direction getMoveDirection(@Nullable BlockPos lastPos, @NotNull BlockPos newPos)
    {
        if (lastPos == null)
            return null;
        BlockPos vector = newPos.subtract(lastPos);
        if (vector.getX() > 0)
            return Direction.EAST;
        else if (vector.getX() < 0)
            return Direction.WEST;
        else if (vector.getZ() > 0)
            return Direction.SOUTH;
        else if (vector.getZ() < 0)
            return Direction.NORTH;
        else if (vector.getY() > 0)
            return Direction.UP;
        else if (vector.getY() < 0)
            return Direction.DOWN;
        return null;
    }

    /**
     * Returns a nullable block hit result if front placing is possible.
     *
     * @param client The client instance.
     * @return A block hit result if front placing is possible.
     */
    public static @Nullable BlockHitResult tryFrontPlace(@NotNull MinecraftClient client)
    {
        if (!LambdaControlsFeature.FRONT_BLOCK_PLACING.isAvailable())
            return null;
        if (client.player != null && client.crosshairTarget != null && client.crosshairTarget.getType() == HitResult.Type.MISS && client.player.onGround && client.player.pitch > 35.0F) {
            if (client.player.isRiding())
                return null;
            BlockPos playerPos = client.player.getBlockPos().down();
            BlockPos targetPos = new BlockPos(client.crosshairTarget.getPos()).subtract(playerPos);
            BlockPos vector = new BlockPos(MathHelper.clamp(targetPos.getX(), -1, 1), 0, MathHelper.clamp(targetPos.getZ(), -1, 1));
            BlockPos blockPos = playerPos.add(vector);

            Direction direction = client.player.getHorizontalFacing();

            BlockState state = client.world.getBlockState(blockPos);
            if (!state.isAir())
                return null;
            BlockState adjacentBlockState = client.world.getBlockState(blockPos.offset(direction.getOpposite()));
            if (adjacentBlockState.isAir() || adjacentBlockState.getBlock() instanceof FluidBlock || (vector.getX() == 0 && vector.getZ() == 0)) {
                return null;
            }

            return new BlockHitResult(client.crosshairTarget.getPos(), direction, blockPos, false);
        }
        return null;
    }

    public static @NotNull BlockHitResult withSideForFrontPlace(@NotNull BlockHitResult result, @Nullable ItemStack stack)
    {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem))
            return result;
        return withSideForFrontPlace(result, Block.getBlockFromItem(stack.getItem()));
    }

    public static @NotNull BlockHitResult withSideForFrontPlace(@NotNull BlockHitResult result, @NotNull Block block)
    {
        if (block instanceof SlabBlock)
            result = result.withSide(Direction.DOWN);
        return result;
    }
}
