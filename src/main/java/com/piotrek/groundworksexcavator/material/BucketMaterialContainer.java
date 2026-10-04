package com.piotrek.groundworksexcavator.material;

import com.piotrek.groundworks.api.container.IGranularContainer;
import com.piotrek.groundworks.api.material.GranularMaterial;
import com.piotrek.groundworks.api.material.GranularMaterialRegistry;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Authoritative bucket inventory container for {@link com.piotrek.groundworksexcavator.entity.GroundworksExcavatorEntity}.
 *
 * <p>Enforces:
 * <ul>
 *   <li>Strict integer material volume conservation (512 units = 1.0 m³).</li>
 *   <li>Single material type at a time (MVP rule: cannot mix sand and dirt).</li>
 *   <li>Capacity limits (default 256 units = 0.5 m³).</li>
 *   <li>Serialization via Minecraft 26.3 ValueInput / ValueOutput.</li>
 * </ul>
 */
public class BucketMaterialContainer implements IGranularContainer {

    public static final int DEFAULT_CAPACITY = 256;

    private int capacity;
    private int storedUnits;
    private GranularMaterial storedMaterial;

    public BucketMaterialContainer() {
        this(DEFAULT_CAPACITY);
    }

    public BucketMaterialContainer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive: " + capacity);
        }
        this.capacity = capacity;
        this.storedUnits = 0;
        this.storedMaterial = GranularMaterial.EMPTY;
    }

    @Override
    public int capacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        if (capacity <= 0) return;
        this.capacity = capacity;
        if (this.storedUnits > capacity) {
            this.storedUnits = capacity;
        }
    }

    @Override
    public int storedUnits() {
        return storedUnits;
    }

    @Override
    public GranularMaterial storedMaterial() {
        return storedMaterial != null ? storedMaterial : GranularMaterial.EMPTY;
    }

    public int materialId() {
        return storedMaterial != null ? storedMaterial.id() : 0;
    }

    public float fillRatio() {
        if (capacity <= 0) return 0.0F;
        return (float) storedUnits / (float) capacity;
    }

    public int remainingCapacity() {
        return Math.max(0, capacity - storedUnits);
    }

    @Override
    public int acceptMaterial(GranularMaterial material, int units) {
        if (units <= 0 || material == null || material.id() == 0) {
            return 0;
        }

        // If bucket already contains material, reject mismatched materials
        if (storedUnits > 0 && storedMaterial != null && storedMaterial.id() != material.id()) {
            return 0;
        }

        int available = capacity - storedUnits;
        if (available <= 0) {
            return 0;
        }

        int toAdd = Math.min(units, available);
        this.storedMaterial = material;
        this.storedUnits += toAdd;
        return toAdd;
    }

    @Override
    public int extractMaterial(int maxUnits) {
        if (maxUnits <= 0 || storedUnits <= 0) {
            return 0;
        }

        int toExtract = Math.min(maxUnits, storedUnits);
        this.storedUnits -= toExtract;

        if (this.storedUnits <= 0) {
            this.storedUnits = 0;
            this.storedMaterial = GranularMaterial.EMPTY;
        }

        return toExtract;
    }

    /**
     * Directly set state (used during network synchronization or test resets).
     */
    public void setDirect(GranularMaterial material, int units) {
        if (units <= 0 || material == null || material.id() == 0) {
            this.storedUnits = 0;
            this.storedMaterial = GranularMaterial.EMPTY;
        } else {
            this.storedMaterial = material;
            this.storedUnits = Math.min(units, capacity);
        }
    }

    public void clear() {
        this.storedUnits = 0;
        this.storedMaterial = GranularMaterial.EMPTY;
    }

    // ── Serialization ────────────────────────────────────────────────

    public void load(ValueInput input) {
        this.capacity = input.getIntOr("BucketCapacity", DEFAULT_CAPACITY);
        int matId = input.getIntOr("BucketMaterialId", 0);
        int units = input.getIntOr("BucketUnits", 0);

        if (units > 0 && matId > 0) {
            this.storedMaterial = GranularMaterialRegistry.byId(matId);
            this.storedUnits = Math.min(units, this.capacity);
        } else {
            this.storedMaterial = GranularMaterial.EMPTY;
            this.storedUnits = 0;
        }
    }

    public void save(ValueOutput output) {
        output.putInt("BucketCapacity", this.capacity);
        output.putInt("BucketMaterialId", this.materialId());
        output.putInt("BucketUnits", this.storedUnits);
    }
}
