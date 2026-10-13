package io.github.theamethyx.nova.client.scenario;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Deterministic fixtures for the visualization modules: registered test entities and containers,
 * one container behind a wall, plus an unregistered decoy cow and chest that must <em>not</em> be
 * highlighted (negative controls).
 */
public final class VisualFixtureScenario implements TestScenario {
	public static final String ID = "visual_fixtures";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String name() {
		return "Visual fixtures";
	}

	@Override
	public String description() {
		return "Builds a 13x6x9 test area: two registered test entities, two registered containers (one behind a wall) "
				+ "and an unregistered decoy cow and chest. Fixture ESP and Fixture Containers must highlight only the registered ones.";
	}

	@Override
	public BlockPos footprint() {
		return new BlockPos(13, 6, 9);
	}

	@Override
	public Build build(ServerLevel level, BlockPos origin) {
		Build build = new Build();
		BoundingBox area = BoundingBox.fromCorners(origin, origin.offset(12, 5, 8));
		build.regions.add(area);

		// Clear the area and lay a floor so the fixtures are visible and reproducible.
		for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(12, 5, 8))) {
			level.setBlockAndUpdate(pos, pos.getY() == origin.getY() ? Blocks.SMOOTH_STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
		}

		BlockPos floor = origin.above();
		spawn(level, build, EntityType.ARMOR_STAND, floor.offset(2, 0, 2), "Fixture: armor stand", true);
		spawn(level, build, EntityType.COW, floor.offset(5, 0, 2), "Fixture: cow", true);
		spawn(level, build, EntityType.COW, floor.offset(8, 0, 2), "Decoy cow (not a fixture)", false);

		container(level, build, floor.offset(2, 0, 6), Blocks.CHEST, "Fixture: chest", true);
		container(level, build, floor.offset(10, 0, 6), Blocks.BARREL, "Fixture: barrel behind wall", true);
		container(level, build, floor.offset(6, 0, 6), Blocks.CHEST, "Decoy chest (not a fixture)", false);

		// A small stone wall in front of the barrel to exercise through-wall rendering.
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = 0; dy <= 2; dy++) {
				level.setBlockAndUpdate(floor.offset(10 + dx, dy, 5), Blocks.STONE_BRICKS.defaultBlockState());
			}
		}

		build.groundTruth.addProperty("registeredEntities", 2);
		build.groundTruth.addProperty("registeredContainers", 2);
		build.groundTruth.addProperty("decoyEntities", 1);
		build.groundTruth.addProperty("decoyContainers", 1);
		build.notes.add("Decoys are labelled in-world and are intentionally not registered.");
		return build;
	}

	private static void spawn(ServerLevel level, Build build, EntityType<?> type, BlockPos pos, String label, boolean fixture) {
		Entity entity = type.create(level, EntitySpawnReason.COMMAND);
		if (entity == null) {
			throw new IllegalStateException("Could not create " + type);
		}

		entity.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
		entity.setCustomName(Component.literal(label));
		entity.setCustomNameVisible(true);
		entity.setInvulnerable(true);
		entity.addTag(fixture ? "nova_fixture" : "nova_decoy");
		if (entity instanceof Mob mob) {
			mob.setNoAi(true);
			mob.setPersistenceRequired();
		}

		level.addFreshEntity(entity);
		build.entities.add(new EntityRecord(entity.getUUID(), label, fixture));
	}

	private static void container(ServerLevel level, Build build, BlockPos pos, net.minecraft.world.level.block.Block block, String label, boolean fixture) {
		level.setBlockAndUpdate(pos, block.defaultBlockState());
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (blockEntity instanceof Container container) {
			container.setItem(0, new ItemStack(fixture ? Items.DIAMOND : Items.COBBLESTONE, 3));
		}

		build.containers.add(new ContainerRecord(pos.immutable(), label, fixture));
	}
}
