package com.openwormmc.client;

import com.openwormmc.gui.BrainScreen;
import com.openwormmc.net.BrainPackets;
import com.openwormmc.registry.ModRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;

public class OpenWormClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        EntityModelLayerRegistry.registerModelLayer(CElegansRenderer.LAYER, CElegansRenderer::createModel);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(ModRegistry.C_ELEGANS, CElegansRenderer::new);
        KeyBinding key = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.openwormmc.brain", GLFW.GLFW_KEY_K, "category.openwormmc"));
        final String[][] names = new String[1][];
        ClientPlayNetworking.registerGlobalReceiver(BrainPackets.SNAPSHOT, (client, handler, buf, sender) -> {
            buf.readInt(); int n = buf.readInt(); double[] v = new double[n]; for (int i = 0; i < n; i++) v[i] = buf.readShort() / 10.0;
            int seg = buf.readInt(); float[] d = new float[seg], ve = new float[seg]; for (int i = 0; i < seg; i++) { d[i] = buf.readFloat(); ve[i] = buf.readFloat(); }
            float[] st = new float[6]; for (int i = 0; i < 6; i++) st[i] = buf.readFloat();
            var s = new BrainScreen.ClientSnapshot(); s.v = v; s.dorsal = d; s.ventral = ve; s.stimulus = st; s.active = buf.readInt(); s.timeMs = buf.readDouble(); s.hz = buf.readDouble(); s.connections = buf.readInt(); s.uptime = buf.readDouble();
            BrainScreen.SNAP = s; });
        ClientTickEvents.END_CLIENT_TICK.register(c -> { while (key.wasPressed()) { if (names[0] == null) names[0] = new com.openwormmc.connectome.Connectome().neurons; c.setScreen(new BrainScreen(names[0])); } });
    }
}
