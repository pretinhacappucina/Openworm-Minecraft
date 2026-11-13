package com.openwormmc.commands;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.openwormmc.brain.Brain;
import com.openwormmc.entity.CElegansEntity;
import com.openwormmc.entity.SimulationService;
import com.openwormmc.registry.ModRegistry;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;

/** /openworm spawn [n] | stats | brain | debug | feed | pause | resume. UNCOMPILED in the build environment. */
public final class OpenWormCommand {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((d, access, env) -> d.register(literal("openworm").requires(s -> s.hasPermissionLevel(2))
            .then(literal("spawn").executes(c -> spawn(c, 1)).then(argument("n", IntegerArgumentType.integer(1, 50)).executes(c -> spawn(c, IntegerArgumentType.getInteger(c, "n")))))
            .then(literal("stats").executes(OpenWormCommand::stats))
            .then(literal("brain").executes(OpenWormCommand::brain))
            .then(literal("debug").executes(c -> { SimulationService s = SimulationService.INSTANCE; s.debug = !s.debug; msg(c, "debug=" + s.debug); return 1; }))
            .then(literal("feed").executes(c -> { int k = 0; for (var w : worms(c)) { w.feedStimulus(); k++; } msg(c, "food stimulus sent to " + k + " worm(s)"); return k; }))
            .then(literal("pause").executes(c -> { SimulationService.INSTANCE.paused = true; msg(c, "paused"); return 1; }))
            .then(literal("resume").executes(c -> { SimulationService.INSTANCE.paused = false; msg(c, "resumed"); return 1; }))));
    }
    private static java.util.List<CElegansEntity> worms(CommandContext<ServerCommandSource> c) {
        var src = c.getSource(); return src.getWorld().getEntitiesByClass(CElegansEntity.class, new Box(src.getPosition(), src.getPosition()).expand(128), e -> true);
    }
    private static void msg(CommandContext<ServerCommandSource> c, String s) { c.getSource().sendFeedback(() -> Text.literal("[OpenWorm] " + s), false); }
    private static int spawn(CommandContext<ServerCommandSource> c, int n) {
        var src = c.getSource(); int ok = 0;
        for (int i = 0; i < n; i++) { var e = ModRegistry.C_ELEGANS.create(src.getWorld()); if (e == null) continue;
            e.refreshPositionAndAngles(src.getPosition().x + (i % 5) * 1.5, src.getPosition().y, src.getPosition().z + (i / 5) * 1.5, src.getWorld().random.nextFloat() * 360f, 0f);
            if (src.getWorld().spawnEntity(e)) ok++; }
        msg(c, "spawned " + ok + " C. elegans (max active brains " + SimulationService.INSTANCE.maxActiveBrains + ")"); return ok;
    }
    private static int stats(CommandContext<ServerCommandSource> c) {
        var s = SimulationService.INSTANCE; msg(c, String.format("brains=%d paused=%b updates/s=%.1f uptime=%.0fs", s.count(), s.paused, s.updateHz(), s.uptimeSeconds())); return 1;
    }
    private static int brain(CommandContext<ServerCommandSource> c) {
        var l = worms(c); if (l.isEmpty() || l.get(0).brain() == null) { msg(c, "no worm nearby"); return 0; }
        Brain b = l.get(0).brain(); var s = b.snapshot();
        msg(c, String.format("model=c302 C1-style (Cook 2019) neurons=%d chemical+neuromuscular synapses=%d gap=%d active=%d simTime=%.0fms",
            b.cx.neurons.length, b.net.chemicalSynapseCount(), b.net.gapJunctionCount(), s.active, s.timeMs)); return 1;
    }
}
