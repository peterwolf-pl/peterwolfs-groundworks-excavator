package com.piotrek.groundworksexcavator.gametest;

import com.piotrek.groundworks.api.GroundworksApi;
import com.piotrek.groundworks.api.material.GranularMaterialRegistry;
import com.piotrek.groundworks.terrain.cell.GranularCell;
import com.piotrek.groundworksexcavator.GroundworksExcavatorMod;
import com.piotrek.groundworksexcavator.arm.ArmKinematics;
import com.piotrek.groundworksexcavator.entity.GroundworksExcavatorEntity;
import com.piotrek.groundworksexcavator.excavation.ArmTerrainContactController;
import com.piotrek.groundworksexcavator.integration.groundworks.GroundworksExcavationAdapter;
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
import net.minecraft.world.phys.Vec3;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

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

            // ── Scene 8: Surface skim displaces gravel into a conserved mound ─
            server.runOnServer(minecraftServer -> {
                ServerLevel level = minecraftServer.overworld();
                verifyArmContactGate(level);

                BlockPos source = new BlockPos(11, BASE_Y, 2);
                BlockPos destination = source.south();
                int deposited = GroundworksApi.depositWithOverflow(
                        level, source, GranularMaterialRegistry.GRAVEL, 512).unitsDeposited();
                if (deposited != 512) {
                    throw new AssertionError("Expected 512 gravel units, deposited " + deposited);
                }

                int before = unitsAt(level, source) + unitsAt(level, destination);
                int moved = 0;
                for (int pass = 0; pass < 16; pass++) {
                    double surfaceY = GroundworksExcavationAdapter.getSurfaceWorldY(
                            level, source, source.getX() + 0.5D, source.getZ() + 0.5D);
                    GroundworksExcavationAdapter.SurfaceDisplacement displacement =
                            GroundworksExcavationAdapter.displaceSurface(
                                    level,
                                    source,
                                    new Vec3(source.getX() + 0.5D, surfaceY - 0.01D, source.getZ() + 0.5D),
                                    destination,
                                    8);
                    moved += displacement.unitsMoved();
                }
                int after = unitsAt(level, source) + unitsAt(level, destination);
                if (moved <= 0 || before != after) {
                    throw new AssertionError(
                            "Surface push conservation failed: before=" + before
                                    + ", after=" + after + ", moved=" + moved);
                }

                GroundworksExcavatorEntity contactExcavator = new GroundworksExcavatorEntity(
                        GroundworksExcavatorMod.EXCAVATOR, level);
                contactExcavator.setPos(8.5D, BASE_Y, -4.5D);
                contactExcavator.setYRot(0.0F);
                level.addFreshEntity(contactExcavator);
            });
            context.waitTicks(80);
            server.runCommand("teleport @a 7.0 182.5 5.0 facing 11.5 180.5 2.5");
            context.waitTicks(10);
            capture(context, connection, "excavator_08_surface_push_mound");

            // ── Scene 9: Automatic one-block trench cycle ───────────────────
            final GroundworksExcavatorEntity[] autoHolder = new GroundworksExcavatorEntity[1];
            server.runOnServer(minecraftServer -> {
                ServerLevel level = minecraftServer.overworld();
                for (int z = -10; z <= -5; z++) {
                    level.setBlockAndUpdate(new BlockPos(12, BASE_Y - 2, z), Blocks.STONE.defaultBlockState());
                    level.setBlockAndUpdate(new BlockPos(12, BASE_Y - 1, z), Blocks.SAND.defaultBlockState());
                }

                GroundworksExcavatorEntity automatic = new GroundworksExcavatorEntity(
                        GroundworksExcavatorMod.EXCAVATOR, level);
                automatic.setPos(12.5D, BASE_Y, -8.5D);
                automatic.setYRot(0.0F);
                level.addFreshEntity(automatic);

                ServerPlayer player = minecraftServer.getPlayerList().getPlayers().getFirst();
                player.stopRiding();
                if (!player.startRiding(automatic)) {
                    throw new AssertionError("Test player could not enter automatic excavator");
                }
                automatic.startAutoTrench();
                autoHolder[0] = automatic;
            });
            context.waitTicks(500);
            server.runOnServer(minecraftServer -> {
                ServerLevel level = minecraftServer.overworld();
                GroundworksExcavatorEntity automatic = autoHolder[0];
                if (automatic == null || automatic.getAutoTrenchCompletedSections() < 1) {
                    throw new AssertionError("Automatic trench did not complete a dig/dump/reverse cycle: phase="
                            + (automatic == null ? "missing" : automatic.getAutoTrenchPhase())
                            + ", stored=" + (automatic == null ? -1 : automatic.getStoredUnits())
                            + ", joints=" + (automatic == null ? "missing"
                            : automatic.getUpperYaw() + "/" + automatic.getBoomAngle() + "/"
                            + automatic.getStickAngle() + "/" + automatic.getBucketAngle())
                            + ", position=" + (automatic == null ? "missing" : automatic.position()));
                }

                boolean excavated = false;
                int localSandUnits = 0;
                int rightSideUnits = 0;
                for (int x = 5; x <= 18; x++) {
                    for (int y = BASE_Y - 1; y <= BASE_Y + 3; y++) {
                        for (int z = -12; z <= -3; z++) {
                            BlockPos pos = new BlockPos(x, y, z);
                            if (level.getBlockState(pos).is(Blocks.SAND)) {
                                localSandUnits += GranularCell.TOTAL_UNITS;
                                continue;
                            }
                            GranularCell cell = GroundworksApi.queryCell(level, pos);
                            if (cell != null && !cell.isEmpty()) {
                                if (cell.materialId() != GranularMaterialRegistry.SAND.id()) {
                                    throw new AssertionError("Automatic trench changed sand into another material at " + pos);
                                }
                                localSandUnits += cell.unitCount();
                                if (x < 12) rightSideUnits += cell.unitCount();
                            }
                        }
                    }
                }
                for (int z = -10; z <= -5; z++) {
                    BlockPos trenchPos = new BlockPos(12, BASE_Y - 1, z);
                    GranularCell cell = GroundworksApi.queryCell(level, trenchPos);
                    if (!level.getBlockState(trenchPos).is(Blocks.SAND)
                            && (cell == null || cell.unitCount() < GranularCell.TOTAL_UNITS)) {
                        excavated = true;
                    }
                }
                if (!excavated) {
                    throw new AssertionError("Automatic trench cycle did not excavate the sand line");
                }
                if (rightSideUnits <= 0) {
                    throw new AssertionError("Automatic trench did not dump sand on the excavator's right side: phase="
                            + automatic.getAutoTrenchPhase() + ", completed="
                            + automatic.getAutoTrenchCompletedSections() + ", terrain="
                            + localSandUnits + ", bucket=" + automatic.getStoredUnits()
                            + ", position=" + automatic.position());
                }
                if (localSandUnits + automatic.getStoredUnits() != 6 * GranularCell.TOTAL_UNITS) {
                    throw new AssertionError("Automatic trench did not conserve its 3072 sand units: terrain="
                            + localSandUnits + ", bucket=" + automatic.getStoredUnits());
                }
                ServerPlayer player = minecraftServer.getPlayerList().getPlayers().getFirst();
                player.stopRiding();
            });
            context.waitTicks(2);
            server.runOnServer(minecraftServer -> {
                GroundworksExcavatorEntity automatic = autoHolder[0];
                if (automatic.isAutoTrenchActive()) {
                    throw new AssertionError("Automatic trench did not stop after the operator dismounted");
                }
            });
            server.runCommand("gamemode spectator @a");
            server.runCommand("teleport @a 19 184 -12 facing 10 179 -7");
            context.waitTicks(10);
            capture(context, connection, "excavator_09_automatic_trench");
        }
    }

    private static int unitsAt(ServerLevel level, BlockPos pos) {
        GranularCell cell = GroundworksApi.queryCell(level, pos);
        return cell == null ? 0 : cell.unitCount();
    }

    private static void verifyArmContactGate(ServerLevel level) {
        Vec3 base = new Vec3(32.5D, BASE_Y + 5.0D, 32.5D);
        ArmTerrainContactController.JointAngles clear =
                new ArmTerrainContactController.JointAngles(0.0F, 10.0F, -30.0F, -15.0F);
        ArmTerrainContactController.JointAngles penetrating =
                new ArmTerrainContactController.JointAngles(30.0F, 10.0F, -30.0F, -15.0F);

        Set<BlockPos> clearBlocks = ArmKinematics.computeArmCollisionSamples(
                        base, 0.0F, 0.0F, 0.0F,
                        clear.cabin(), clear.boom(), clear.stick())
                .stream()
                .map(BlockPos::containing)
                .collect(Collectors.toSet());
        BlockPos obstacle = ArmKinematics.computeArmCollisionSamples(
                        base, 0.0F, 0.0F, 0.0F,
                        penetrating.cabin(), penetrating.boom(), penetrating.stick())
                .stream()
                .map(BlockPos::containing)
                .filter(pos -> !clearBlocks.contains(pos))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No unique arm collision sample found"));
        level.setBlockAndUpdate(obstacle, Blocks.GRAVEL.defaultBlockState());

        ArmTerrainContactController.JointAngles blocked =
                ArmTerrainContactController.constrain(
                        level, base, 0.0F, 0.0F, 0.0F, 0, clear, penetrating);
        if (blocked.cabin() != clear.cabin()) {
            throw new AssertionError("Arm steel entered gravel at " + obstacle);
        }

        ArmTerrainContactController.JointAngles extracted =
                ArmTerrainContactController.constrain(
                        level, base, 0.0F, 0.0F, 0.0F, 0, penetrating, clear);
        if (extracted.cabin() != clear.cabin()) {
            throw new AssertionError("Arm could not extract from gravel at " + obstacle);
        }
        level.setBlockAndUpdate(obstacle, Blocks.AIR.defaultBlockState());

        ArmKinematics.BucketPose bucketPose = ArmKinematics.computeBucketPose(
                base, 0.0F, 0.0F, 0.0F,
                clear.cabin(), clear.boom(), clear.stick(), clear.bucket(), 0);
        BlockPos embeddedTooth = BlockPos.containing(bucketPose.teethPoints().getFirst());
        level.setBlockAndUpdate(embeddedTooth, Blocks.SAND.defaultBlockState());
        ArmTerrainContactController.JointAngles scoop =
                new ArmTerrainContactController.JointAngles(
                        clear.cabin(), clear.boom(), clear.stick(), clear.bucket() - 15.0F);
        ArmTerrainContactController.JointAngles acceptedScoop =
                ArmTerrainContactController.constrain(
                        level, base, 0.0F, 0.0F, 0.0F, 0, clear, scoop);
        if (acceptedScoop.bucket() != scoop.bucket()) {
            throw new AssertionError("Embedded bucket teeth blocked a valid sand scooping curl at "
                    + embeddedTooth);
        }
        level.setBlockAndUpdate(embeddedTooth, Blocks.AIR.defaultBlockState());

        Vec3 movedBase = base.add(1.0D, 0.0D, 0.0D);
        BlockPos chassisObstacle = ArmKinematics.computeArmCollisionSamples(
                        movedBase, 0.0F, 0.0F, 0.0F,
                        clear.cabin(), clear.boom(), clear.stick())
                .stream()
                .map(BlockPos::containing)
                .filter(pos -> !clearBlocks.contains(pos))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No unique chassis collision sample found"));
        level.setBlockAndUpdate(chassisObstacle, Blocks.GRAVEL.defaultBlockState());
        boolean chassisAllowed = ArmTerrainContactController.allowsMachineMotion(
                level,
                base, 0.0F, 0.0F, 0.0F,
                movedBase, 0.0F, 0.0F, 0.0F,
                0, clear);
        if (chassisAllowed) {
            throw new AssertionError("Chassis pushed arm steel into gravel at " + chassisObstacle);
        }
        level.setBlockAndUpdate(chassisObstacle, Blocks.AIR.defaultBlockState());
    }

    private static void configureWorld(TestServerContext server) {
        server.runCommand("time set 6000");
        server.runCommand("weather clear");
        server.runCommand("gamemode spectator @a");
    }

    private static void setupViewingPlatform(TestServerContext server) {
        server.runCommand("fill -16 " + (BASE_Y - 1) + " -16 16 " + (BASE_Y - 1) + " 16 minecraft:smooth_stone");
        server.runCommand("fill -16 " + BASE_Y + " -16 16 " + (BASE_Y + 12) + " 16 minecraft:air");
        // Place water pool directly in front of the excavator to verify water visibility through windshield!
        server.runCommand("fill -4 " + (BASE_Y - 1) + " 4 4 " + (BASE_Y - 1) + " 8 minecraft:water");
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
