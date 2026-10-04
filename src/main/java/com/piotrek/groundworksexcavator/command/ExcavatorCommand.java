package com.piotrek.groundworksexcavator.command;

import com.mojang.brigadier.CommandDispatcher;
import com.piotrek.groundworksexcavator.arm.ArmKinematics.BucketPose;
import com.piotrek.groundworksexcavator.entity.GroundworksExcavatorEntity;
import com.piotrek.groundworksexcavator.integration.groundworks.GroundworksExcavationAdapter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Diagnostic and inspection command for the tracked excavator.
 */
public final class ExcavatorCommand {

    private ExcavatorCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("excavator")
                        .then(Commands.literal("debug")
                                .executes(ctx -> showDebug(ctx.getSource())))
                        .then(Commands.literal("autotrench")
                                .executes(ctx -> startAutoTrench(ctx.getSource()))
                                .then(Commands.literal("start")
                                        .executes(ctx -> startAutoTrench(ctx.getSource())))
                                .then(Commands.literal("stop")
                                        .executes(ctx -> stopAutoTrench(ctx.getSource())))
                                .then(Commands.literal("status")
                                        .executes(ctx -> showAutoTrenchStatus(ctx.getSource()))))
        );
    }

    private static int showDebug(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only players can inspect excavator status."));
            return 0;
        }

        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof GroundworksExcavatorEntity excavator)) {
            source.sendFailure(Component.literal("You must be seated in an excavator cab to run this command."));
            return 0;
        }

        BucketPose pose = excavator.getCurrentBucketPose();
        String lipStr = pose != null ? String.format("%.2f, %.2f, %.2f", pose.lip().x, pose.lip().y, pose.lip().z) : "N/A";
        String edgeStr = pose != null ? String.format("%.2f, %.2f, %.2f", pose.cuttingEdge().x, pose.cuttingEdge().y, pose.cuttingEdge().z) : "N/A";

        source.sendSuccess(() -> Component.literal(String.format("""
                === Excavator Debug Telemetry ===
                Operator: %s
                Position: %.2f, %.2f, %.2f
                Left Track: %.3f | Right Track: %.3f
                Turntable Yaw: %.1f° | Undercarriage Yaw: %.1f°
                Boom: %.1f° | Stick: %.1f° | Bucket: %.1f°
                Pitch: %.1f° | Roll: %.1f°
                Material: %s (%d / %d units = %.3f m³)
                Cutting Edge: [%s]
                Bucket Lip:   [%s]
                Removed Last Tick: %d units
                Deposited Last Tick: %d units
                Active States: Digging=%b, Dumping=%b
                Auto Trench: %b | Phase: %s | Completed sections: %d
                =================================""",
                player.getName().getString(),
                excavator.getX(), excavator.getY(), excavator.getZ(),
                excavator.getTrackLeftSpeed(), excavator.getTrackRightSpeed(),
                excavator.getUpperYaw(), excavator.getYRot(),
                excavator.getBoomAngle(), excavator.getStickAngle(), excavator.getBucketAngle(),
                excavator.getVehiclePitch(), excavator.getVehicleRoll(),
                excavator.getBucketMaterial().name(), excavator.getStoredUnits(), excavator.getBucketCapacity(),
                GroundworksExcavationAdapter.unitsToCubicMeters(excavator.getStoredUnits()),
                edgeStr,
                lipStr,
                excavator.getLastExcavatedUnits(),
                excavator.getLastDepositedUnits(),
                excavator.isDigging(), excavator.isDumping(),
                excavator.isAutoTrenchActive(), excavator.getAutoTrenchPhase(),
                excavator.getAutoTrenchCompletedSections()
        )), false);

        return 1;
    }

    private static int startAutoTrench(CommandSourceStack source) {
        GroundworksExcavatorEntity excavator = occupiedExcavator(source);
        if (excavator == null) return 0;
        excavator.startAutoTrench();
        source.sendSuccess(() -> Component.literal(
                "Automatic 1x1 trench test started. Dismount to stop it immediately."), false);
        return 1;
    }

    private static int stopAutoTrench(CommandSourceStack source) {
        GroundworksExcavatorEntity excavator = occupiedExcavator(source);
        if (excavator == null) return 0;
        excavator.stopAutoTrench();
        source.sendSuccess(() -> Component.literal("Automatic trench test stopped."), false);
        return 1;
    }

    private static int showAutoTrenchStatus(CommandSourceStack source) {
        GroundworksExcavatorEntity excavator = occupiedExcavator(source);
        if (excavator == null) return 0;
        source.sendSuccess(() -> Component.literal(String.format(
                "Auto trench: active=%b, phase=%s, completed=%d",
                excavator.isAutoTrenchActive(), excavator.getAutoTrenchPhase(),
                excavator.getAutoTrenchCompletedSections())), false);
        return 1;
    }

    private static GroundworksExcavatorEntity occupiedExcavator(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only a seated player can run this command."));
            return null;
        }
        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof GroundworksExcavatorEntity excavator)
                || !excavator.isDriver(player)) {
            source.sendFailure(Component.literal(
                    "You must be seated in the excavator cab to run this command."));
            return null;
        }
        return excavator;
    }
}
