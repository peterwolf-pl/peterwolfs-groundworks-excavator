package com.piotrek.groundworksexcavator.excavation;

import com.piotrek.groundworksexcavator.integration.rtf.RealisticTreeFellingAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RealisticTreeFellingAdapterTest {

    @Test
    @DisplayName("Adapter constants and tree info records function properly")
    void testAdapterBasics() {
        assertEquals("peterwolfs_realistic_tree_felling", RealisticTreeFellingAdapter.MOD_ID);

        net.minecraft.core.BlockPos base = new net.minecraft.core.BlockPos(10, 64, 20);
        java.util.Set<net.minecraft.core.BlockPos> blocks = java.util.Set.of(base, base.above());
        RealisticTreeFellingAdapter.TreeInfo info = new RealisticTreeFellingAdapter.TreeInfo(base, 2, blocks, null);

        assertEquals(base, info.base());
        assertEquals(2, info.height());
        assertTrue(info.contains(base));
        assertTrue(info.contains(base.above()));
        assertFalse(info.contains(base.above(2)));
    }
}
