package com.piotrek.groundworksexcavator;

import com.piotrek.groundworks.api.material.GranularMaterial;
import com.piotrek.groundworks.api.material.GranularMaterialRegistry;
import com.piotrek.groundworksexcavator.material.BucketMaterialContainer;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BucketContainerTest {

    @BeforeAll
    static void init() {
        GranularMaterialRegistry.bootstrap();
    }

    @Test
    @DisplayName("Empty bucket starts with 0 units and default capacity 256")
    void testEmptyBucket() {
        BucketMaterialContainer bucket = new BucketMaterialContainer();
        assertEquals(256, bucket.capacity());
        assertEquals(0, bucket.storedUnits());
        assertEquals(256, bucket.remainingCapacity());
        assertTrue(bucket.isEmpty());
        assertTrue(bucket.hasRoom());
        assertEquals(0, bucket.materialId());
        assertEquals(0.0F, bucket.fillRatio());
    }

    @Test
    @DisplayName("Capacity limits: inserting 300 units into 256 bucket accepts 256 and rejects 44")
    void testCapacityEnforcement() {
        BucketMaterialContainer bucket = new BucketMaterialContainer(256);
        int accepted = bucket.acceptMaterial(GranularMaterialRegistry.DIRT, 300);

        assertEquals(256, accepted, "Must accept exactly up to capacity");
        assertEquals(256, bucket.storedUnits());
        assertEquals(0, bucket.remainingCapacity());
        assertFalse(bucket.hasRoom());
        assertFalse(bucket.isEmpty());
        assertEquals(1.0F, bucket.fillRatio());

        // Subsequent additions are rejected
        int overflow = bucket.acceptMaterial(GranularMaterialRegistry.DIRT, 50);
        assertEquals(0, overflow, "Excess material must be rejected when full");
    }

    @Test
    @DisplayName("Material conservation: excavated units match bucket additions exactly")
    void testMaterialConservation() {
        BucketMaterialContainer bucket = new BucketMaterialContainer(256);
        int excavatedFromTerrain = 64;

        int addedToBucket = bucket.acceptMaterial(GranularMaterialRegistry.DIRT, excavatedFromTerrain);
        assertEquals(64, addedToBucket);
        assertEquals(64, bucket.storedUnits());
        assertEquals(GranularMaterialRegistry.DIRT.id(), bucket.materialId());
    }

    @Test
    @DisplayName("Deposit extraction: depositing 32 units from 128 leaves exactly 96")
    void testDepositExtraction() {
        BucketMaterialContainer bucket = new BucketMaterialContainer(256);
        bucket.acceptMaterial(GranularMaterialRegistry.DIRT, 128);
        assertEquals(128, bucket.storedUnits());

        int extracted = bucket.extractMaterial(32);
        assertEquals(32, extracted);
        assertEquals(96, bucket.storedUnits(), "128 - 32 must equal 96");

        // Drain remaining 96
        int remaining = bucket.extractMaterial(100);
        assertEquals(96, remaining);
        assertEquals(0, bucket.storedUnits());
        assertTrue(bucket.isEmpty());
        assertEquals(GranularMaterial.EMPTY, bucket.storedMaterial());
    }

    @Test
    @DisplayName("Material mismatch rejection: dirt bucket rejects sand")
    void testRejectMaterialMismatch() {
        BucketMaterialContainer bucket = new BucketMaterialContainer(256);
        bucket.acceptMaterial(GranularMaterialRegistry.DIRT, 100);

        int rejectedSand = bucket.acceptMaterial(GranularMaterialRegistry.SAND, 50);
        assertEquals(0, rejectedSand, "Cannot mix sand into a dirt bucket");
        assertEquals(100, bucket.storedUnits());
        assertEquals(GranularMaterialRegistry.DIRT, bucket.storedMaterial());

        int rejectedGravel = bucket.acceptMaterial(GranularMaterialRegistry.GRAVEL, 20);
        assertEquals(0, rejectedGravel, "Cannot mix gravel into a dirt bucket");
    }

    @Test
    @DisplayName("Direct reset and clear operations preserve container consistency")
    void testClearAndDirectSet() {
        BucketMaterialContainer bucket = new BucketMaterialContainer(256);
        bucket.setDirect(GranularMaterialRegistry.GRAVEL, 183);
        assertEquals(183, bucket.storedUnits());
        assertEquals(GranularMaterialRegistry.GRAVEL, bucket.storedMaterial());

        bucket.clear();
        assertEquals(0, bucket.storedUnits());
        assertTrue(bucket.isEmpty());
        assertEquals(GranularMaterial.EMPTY, bucket.storedMaterial());
    }

    @Test
    @DisplayName("Save/load roundtrip: bucket with 183 gravel units reloads with 183 gravel units")
    void testSaveAndLoadRoundtrip() {
        BucketMaterialContainer original = new BucketMaterialContainer(256);
        original.acceptMaterial(GranularMaterialRegistry.GRAVEL, 183);

        TagValueOutput output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        original.save(output);
        CompoundTag tag = output.buildResult();

        HolderLookup.Provider lookup = HolderLookup.Provider.create(java.util.stream.Stream.empty());
        ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, lookup, tag);
        BucketMaterialContainer loaded = new BucketMaterialContainer();
        loaded.load(input);

        assertEquals(256, loaded.capacity());
        assertEquals(183, loaded.storedUnits());
        assertEquals(GranularMaterialRegistry.GRAVEL, loaded.storedMaterial());
        assertEquals(GranularMaterialRegistry.GRAVEL.id(), loaded.materialId());
    }

    @Test
    @DisplayName("Switching to 2x capacity bucket (512 units) expands capacity and preserves material")
    void testLargeBucketCapacityExpansion() {
        BucketMaterialContainer bucket = new BucketMaterialContainer(256);
        bucket.acceptMaterial(GranularMaterialRegistry.DIRT, 256);
        assertEquals(256, bucket.storedUnits());
        assertFalse(bucket.hasRoom());

        // Switch to large bucket (512 units)
        bucket.setCapacity(512);
        assertEquals(512, bucket.capacity());
        assertEquals(256, bucket.storedUnits());
        assertTrue(bucket.hasRoom());
        assertEquals(256, bucket.remainingCapacity());

        // Fill remaining 256 units
        int added = bucket.acceptMaterial(GranularMaterialRegistry.DIRT, 300);
        assertEquals(256, added);
        assertEquals(512, bucket.storedUnits());
        assertEquals(1.0F, bucket.fillRatio());
    }
}
