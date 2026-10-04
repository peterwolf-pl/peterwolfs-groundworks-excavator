package com.piotrek.groundworksexcavator.gametest;

import com.piotrek.groundworksexcavator.GroundworksExcavatorMod;
import com.piotrek.groundworksexcavator.entity.GroundworksExcavatorEntity;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

import java.nio.file.Path;

/**
 * Visual regression test suite for Peterwolf's Groundworks Excavator.
 *
 * <p>Validates:
 * <ul>
 *   <li>Panoramic safety glass windows & hollow ROPS safety cabin</li>
 *   <li>Operator seated inside the cab with full forward visibility on the work area</li>
 *   <li>Dual interchangeable buckets (Standard 256u vs Large Bulk 512u)</li>
 *   <li>Working excavation and dumping postures</li>
 * </ul>
 *
 * <p>Run with: {@code ./gradlew runClientGameTest}
 */
public final class ExcavatorVisualGameTest implements FabricClientGameTest {

    private static final int BASE_Y = 180;
    private static final Path SCREENSHOT_DIR = Path.of(
            System.getProperty("excavator.visualOutputDir", "visual-tests/current")
    ).toAbsolutePath().normalize();

    @Override
    public void runTest(ClientGameTestContext context) {
        context.restoreDefaultGameOptions();

        try (TestSingleplayerContext singleplayer = context.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {

            TestServerContext server = singleplayer.getServer();
            TestServerConnection connection = singleplayer.getConnection();

            configureWorld(server);
            setupViewingPlatform(server);

            // Spawn authoritative test excavator
            final GroundworksExcavatorEntity[] excavatorHolder = new GroundworksExcavatorEntity[1];
            server.runOnServer(minecraftServer -> {
                ServerLevel level = minecraftServer.overworld();
                GroundworksExcavatorEntity excavator = new GroundworksExcavatorEntity(
                        GroundworksExcavatorMod.EXCAVATOR, level
                );
                excavator.setPos(0.5D, BASE_Y, 0.5D);
                excavator.setYRot(0.0F);
                level.addFreshEntity(excavator);
                excavatorHolder[0] = excavator;
            });

            connection.waitForChunksRender();
            context.waitTicks(160);

            // ── Scene 1: Isometric Profile (Front-Left with Glass Windows) ────
            server.runCommand("teleport @a -4.8 182.8 5.2 -136 15");
            context.waitTicks(10);
            capture(context, connection, "excavator_01_profile_isometric");

            // ── Scene 2: Cabin & Flashing Yellow Warning Beacon Close-Up ─────
            server.runCommand("teleport @a -2.5 182.6 2.5 -135 8");
            context.waitTicks(10);
            capture(context, connection, "excavator_02_cab_and_beacon");

            // ── Scene 3: Rear Counterweight, Hazard Stripes & Exhaust ────────
            server.runCommand("teleport @a 2.8 182.6 -3.8 35 15");
            context.waitTicks(10);
            capture(context, connection, "excavator_03_counterweight_hazard");

            // ── Scene 4: Crawler Tracks & Suspension Rollers ─────────────────
            server.runCommand("teleport @a -4.2 181.2 0.5 -90 8");
            context.waitTicks(10);
            capture(context, connection, "excavator_04_tracks_and_rollers");

            // ── Scene 5: Operator Seated Inside Glass Cab (Exterior View) ─────
            server.runOnServer(minecraftServer -> {
                var players = minecraftServer.getPlayerList().getPlayers();
                if (!players.isEmpty()) {
                    players.get(0).startRiding(excavatorHolder[0]);
                }
            });
            context.waitTicks(10);
            server.runCommand("teleport @a -2.8 182.8 3.5 -135 12");
            context.waitTicks(10);
            capture(context, connection, "excavator_05_player_in_glass_cab");

            // ── Scene 6: Operator View through Front Windshield at Work Area ─
            // Camera placed at operator eye level inside cab looking out through windshield at boom & ground
            server.runCommand("teleport @a 0.0 182.35 1.5 0 10");
            context.waitTicks(10);
            capture(context, connection, "excavator_06_in_cab_work_area_view");

            // ── Scene 7: Large 2x Bulk Bucket Excavating Ground ──────────────
            server.runOnServer(minecraftServer -> {
                GroundworksExcavatorEntity ex = excavatorHolder[0];
                if (ex != null) {
                    ex.setBucketType(GroundworksExcavatorEntity.BUCKET_LARGE);
                    ex.setControlInputs(0.0F, 0.0F, 0.0F, -0.6F, 0.5F, -0.4F);
                }
            });
            context.waitTicks(15);
            server.runCommand("teleport @a 4.8 182.8 4.8 135 16");
            context.waitTicks(10);
            capture(context, connection, "excavator_07_large_bucket_digging");
        }
    }

    private static void configureWorld(TestServerContext server) {
        server.runCommand("time set 6000");
        server.runCommand("weather clear");
        server.runCommand("gamemode spectator @a");
    }

    private static void setupViewingPlatform(TestServerContext server) {
        server.runCommand("fill -16 " + (BASE_Y - 1) + " -16 16 " + (BASE_Y - 1) + " 16 minecraft:smooth_stone");
        server.runCommand("fill -16 " + BASE_Y + " -16 16 " + (BASE_Y + 12) + " 16 minecraft:air");
    }

    private static void capture(
            ClientGameTestContext context,
            TestServerConnection connection,
            String name
    ) {
        context.waitTicks(3);
        connection.waitForClientboundPackets();
        context.takeScreenshot(TestScreenshotOptions.of(name)
                .disableCounterPrefix()
                .withSize(854, 480)
                .withDestinationDir(SCREENSHOT_DIR));
    }
}
