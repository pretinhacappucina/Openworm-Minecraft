package com.openwormmc.net;

import com.openwormmc.brain.Brain;
import com.openwormmc.entity.CElegansEntity;
import com.openwormmc.entity.SimulationService;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** Server -> client snapshot for the viewer (membrane potentials as shorts in 0.1 mV, muscle drive, stats). UNCOMPILED. */
public final class BrainPackets {
    public static final Identifier SNAPSHOT = new Identifier("openwormmc", "snapshot");

    public static void send(ServerPlayerEntity player, CElegansEntity worm) {
        Brain b = worm.brain(); if (b == null) return; Brain.Snapshot s = b.snapshot();
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(worm.getId()); buf.writeInt(s.v.length);
        for (double v : s.v) buf.writeShort((int) Math.round(Math.max(-3000, Math.min(3000, v * 10))));
        buf.writeInt(s.muscleDrive.length); for (int i = 0; i < s.muscleDrive.length; i++) { buf.writeFloat((float) s.dorsal[i]); buf.writeFloat((float) s.ventral[i]); }
        for (double st : s.stimulus) buf.writeFloat((float) st);
        buf.writeInt(s.active); buf.writeDouble(s.timeMs); buf.writeDouble(SimulationService.INSTANCE.updateHz());
        buf.writeInt(b.cx.chemical.length + b.cx.neuromuscular.length + b.cx.gap.length); buf.writeDouble(SimulationService.INSTANCE.uptimeSeconds());
        ServerPlayNetworking.send(player, SNAPSHOT, buf);
    }
}
