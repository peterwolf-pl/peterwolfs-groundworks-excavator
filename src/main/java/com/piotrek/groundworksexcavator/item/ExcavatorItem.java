package com.piotrek.groundworksexcavator.item;

import com.piotrek.groundworksexcavator.GroundworksExcavatorMod;
import com.piotrek.groundworksexcavator.entity.GroundworksExcavatorEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Item used to place a tracked excavator in the world.
 */
public class ExcavatorItem extends Item {

    public ExcavatorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        BlockPos clickedPos = context.getClickedPos();
        Direction clickedFace = context.getClickedFace();
        BlockPos spawnPos = clickedPos.relative(clickedFace);

        Vec3 spawnVec = new Vec3(
                spawnPos.getX() + 0.5D,
                spawnPos.getY(),
                spawnPos.getZ() + 0.5D
        );

        // Check if placement volume is blocked by solid blocks
        AABB bounds = GroundworksExcavatorMod.EXCAVATOR.getDimensions().makeBoundingBox(spawnVec);
        if (!level.noCollision(bounds)) {
            // Also check slightly higher
            bounds = bounds.move(0.0D, 0.5D, 0.0D);
            if (!level.noCollision(bounds)) {
                return InteractionResult.FAIL;
            }
            spawnVec = spawnVec.add(0.0D, 0.5D, 0.0D);
        }

        ServerLevel serverLevel = (ServerLevel) level;
        GroundworksExcavatorEntity excavator = GroundworksExcavatorMod.EXCAVATOR.create(
                serverLevel,
                EntitySpawnReason.SPAWN_ITEM_USE
        );

        if (excavator != null) {
            float playerYaw = context.getPlayer() != null ? context.getPlayer().getYRot() : 0.0F;
            excavator.snapTo(spawnVec.x, spawnVec.y, spawnVec.z, playerYaw, 0.0F);
            excavator.setYHeadRot(playerYaw);
            excavator.setYBodyRot(playerYaw);

            serverLevel.addFreshEntity(excavator);

            ItemStack itemStack = context.getItemInHand();
            Player player = context.getPlayer();
            if (player != null && !player.getAbilities().instabuild) {
                itemStack.shrink(1);
            }

            return InteractionResult.CONSUME;
        }

        return InteractionResult.FAIL;
    }
}
