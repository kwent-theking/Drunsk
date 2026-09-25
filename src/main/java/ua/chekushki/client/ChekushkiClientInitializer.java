package ua.chekushki.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class ChekushkiClientInitializer {
    private static KeyMapping openKey;
    private static boolean keyWasDown;

    public static void onInitializeClient() {
        openKey = new KeyMapping("key.chekushki.open", GLFW.GLFW_KEY_L, "category.chekushki");
        keyWasDown = false;
    }

    public static KeyMapping getOpenKey() {
        return openKey;
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.screen != null) return;
        boolean down = openKey.isDown();
        if (down && !keyWasDown) minecraft.setScreen(new ChekushkiScreen());
        keyWasDown = down;
    }

    public static String nick(Minecraft minecraft) {
        if (minecraft.player == null) return "";
        return minecraft.player.getGameProfile().getName();
    }
}
