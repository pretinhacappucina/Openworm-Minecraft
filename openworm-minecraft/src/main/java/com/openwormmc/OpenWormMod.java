package com.openwormmc;

import com.openwormmc.commands.OpenWormCommand;
import com.openwormmc.entity.CElegansEntity;
import com.openwormmc.entity.SimulationService;
import com.openwormmc.net.BrainPackets;
import com.openwormmc.registry.ModRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public class OpenWormMod implements ModInitializer {
    @Override public void onInitialize() {
        ModRegistry.init(); OpenWormCommand.register();
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> SimulationService.INSTANCE.shutdown());
        // every 5 ticks, send the nearest worm's snapshot to each player within 48 blocks (viewer only)
        ServerTickEvents.END_SERVER_TICK.register(server -> { if (server.getTicks() % 5 != 0) return;
            for (var p : server.getPlayerManager().getPlayerList())
                p.getWorld().getEntitiesByClass(CElegansEntity.class, p.getBoundingBox().expand(48), e -> true).stream()
                    .min(java.util.Comparator.comparingDouble(e -> e.squaredDistanceTo(p))).ifPresent(w -> BrainPackets.send(p, w)); });
    }
}
