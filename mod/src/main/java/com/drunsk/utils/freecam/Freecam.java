package com.drunsk.utils.freecam;

import com.drunsk.DrunskConfig;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;

/**
 * Freecam controller. Port of net.xolt.freecam.Freecam (MIT) for MC 26.2,
 * without tripods; settings read from DrunskConfig. Toggle key handled in
 * DrunskClient. / Контроллер фрикама. Порт net.xolt.freecam.Freecam (MIT)
 * под 26.2, без триподов; настройки — из DrunskConfig.
 */
public final class Freecam {

    private static final Minecraft MC = Minecraft.getInstance();

    private static boolean enabled = false;
    private static boolean disableNextTick = false;
    private static FreeCamera freeCamera;
    private static CameraType rememberedF5 = null;

    private Freecam() {
    }

    /** Runs at START_CLIENT_TICK: suppress player input while detached. / На START_CLIENT_TICK: глушим ввод игрока. */
    public static void preTick(Minecraft mc) {
        if (disableNextTick && isEnabled()) {
            toggle();
        }
        disableNextTick = false;

        if (isEnabled() && mc.player != null && mc.player.input instanceof KeyboardInput) {
            ClientInput input = new ClientInput();
            Input presses = mc.player.input.keyPresses;
            // keep only sneak so the player does not walk/swim away
            // / оставляем только присед, чтобы игрок не уплыл
            input.keyPresses = new Input(false, false, false, false, false, presses.shift(), false);
            mc.player.input = input;
        }
    }

    public static void onDisconnect() {
        if (isEnabled()) {
            toggle();
        }
    }

    public static void toggle() {
        if (enabled) {
            onDisableFreecam();
            enabled = false;
            onDisabled();
        } else {
            onEnableFreecam();
            enabled = true;
        }
    }

    private static void onEnableFreecam() {
        onEnable();
        freeCamera = new FreeCamera(-420);
        moveToPlayer();
        freeCamera.spawn();
        MC.setCameraEntity(freeCamera);
        DrunskConfig.util.freecam = true;
        sendOverlayMessage(Component.translatable("drunsk.utils.freecam.on"));
    }

    private static void onDisableFreecam() {
        onDisable();
        DrunskConfig.util.freecam = false;
        if (MC.player != null) {
            sendOverlayMessage(Component.translatable("drunsk.utils.freecam.off"));
        }
    }

    private static void onEnable() {
        MC.smartCull = false;
        rememberedF5 = MC.options.getCameraType();
        if (MC.gameRenderer.mainCamera().isDetached()) {
            MC.options.setCameraType(CameraType.FIRST_PERSON);
        }
    }

    private static void onDisable() {
        MC.smartCull = true;
        MC.setCameraEntity(MC.player);
        if (freeCamera != null) {
            freeCamera.despawn();
            freeCamera.input = new ClientInput();
            freeCamera = null;
        }
        if (MC.player != null) {
            MC.player.input = new KeyboardInput(MC.options);
        }
    }

    private static void onDisabled() {
        if (rememberedF5 != null) {
            MC.options.setCameraType(rememberedF5);
            rememberedF5 = null;
        }
    }

    public static void moveToPlayer() {
        if (freeCamera == null || MC.player == null) return;
        freeCamera.copyPosition(MC.player);
        freeCamera.applyPerspective(Perspective.INSIDE, false);
    }

    public static void moveToEntity(Entity entity) {
        if (freeCamera == null) return;
        if (entity == null) moveToPlayer();
        else freeCamera.copyPosition(entity);
    }

    public static void disableNextTick() {
        disableNextTick = true;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static FreeCamera getFreeCamera() {
        return freeCamera;
    }

    public static boolean shouldFreezePlayer() {
        return DrunskConfig.util.freecamFreeze;
    }

    public static boolean shouldPreventInteractions() {
        // interactions always come from the real player's position while detached
        // / взаимодействие всегда идёт от реального игрока, пока камера отсоединена
        return true;
    }

    public static boolean shouldShowPlayer() {
        return DrunskConfig.util.freecamShowPlayer;
    }

    public static boolean shouldHideHand() {
        return DrunskConfig.util.freecamHideHand;
    }

    public static boolean shouldDisableOnDamage() {
        return DrunskConfig.util.freecamDisableOnDamage;
    }

    private static void sendOverlayMessage(Component message) {
        if (MC.player != null) MC.player.sendOverlayMessage(message);
    }
}
