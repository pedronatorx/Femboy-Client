package io.github.theamethyx.femboyclient.client.world;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

import io.github.theamethyx.femboyclient.client.ClientTicks;

/**
 * The entities the client has loaded, nearest first, built at most once per client tick and shared by every module that
 * needs them (Entity ESP, Trackers, Hitboxes...). Before this, each of those walked the whole entity list and computed
 * every distance on its own, some of them every frame.
 *
 * <p>Contains living and non-living entities that are not removed, except your own player and the camera entity.
 * Distances are measured from your player's position at the time of the build. The lists handed out are views of the
 * current build and must not be kept across ticks; positions should still be read from the entities (they move between
 * ticks).
 */
public final class EntityIndex {
	private static ClientLevel builtLevel;
	private static long builtTick = -1;
	private static Entity[] entities = new Entity[0];
	private static double[] distances = new double[0];
	private static int size;
	private static int builds;

	private EntityIndex() {
	}

	/** Forgets the current build, e.g. when the world changes. */
	public static void invalidate() {
		builtLevel = null;
		builtTick = -1;
		Arrays.fill(entities, 0, size, null);
		size = 0;
	}

	private static void ensureBuilt() {
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		long tick = ClientTicks.count();
		if (level == builtLevel && tick == builtTick) {
			return;
		}

		Arrays.fill(entities, 0, size, null);
		size = 0;
		builtLevel = level;
		builtTick = tick;
		if (level == null || client.player == null) {
			return;
		}

		builds++;
		Entity self = client.player;
		Entity camera = client.getCameraEntity();
		double px = self.getX();
		double py = self.getY();
		double pz = self.getZ();
		for (Entity entity : level.entitiesForRendering()) {
			if (entity == self || entity == camera || entity.isRemoved()) {
				continue;
			}

			if (size == entities.length) {
				int capacity = Math.max(64, size * 2);
				entities = Arrays.copyOf(entities, capacity);
				distances = Arrays.copyOf(distances, capacity);
			}

			double dx = entity.getX() - px;
			double dy = entity.getY() - py;
			double dz = entity.getZ() - pz;
			entities[size] = entity;
			distances[size] = dx * dx + dy * dy + dz * dz;
			size++;
		}

		sortByDistance();
	}

	/** Insertion sort on the parallel arrays: entity lists are short and nearly sorted from one tick to the next. */
	private static void sortByDistance() {
		for (int i = 1; i < size; i++) {
			Entity entity = entities[i];
			double distance = distances[i];
			int j = i - 1;
			while (j >= 0 && distances[j] > distance) {
				entities[j + 1] = entities[j];
				distances[j + 1] = distances[j];
				j--;
			}

			entities[j + 1] = entity;
			distances[j + 1] = distance;
		}
	}

	/** Entities within {@code maxDistance} blocks of your player (at the last build), nearest first. */
	public static List<Entity> within(double maxDistance) {
		ensureBuilt();
		double limit = maxDistance * maxDistance;
		int low = 0;
		int high = size;
		while (low < high) {
			int mid = (low + high) >>> 1;
			if (distances[mid] <= limit) {
				low = mid + 1;
			} else {
				high = mid;
			}
		}

		return view(low);
	}

	/** Every indexed entity, nearest first. */
	public static List<Entity> all() {
		ensureBuilt();
		return view(size);
	}

	/** Squared distance of the {@code i}-th entity of the current build (same order as the lists). */
	public static double distanceSquared(int i) {
		return distances[i];
	}

	/** How many times the index was built (for tests: one build per tick, however many modules ask). */
	public static int builds() {
		return builds;
	}

	private static List<Entity> view(int count) {
		Entity[] snapshot = entities;
		return new AbstractList<>() {
			@Override
			public Entity get(int index) {
				if (index < 0 || index >= count) {
					throw new IndexOutOfBoundsException(index);
				}

				return snapshot[index];
			}

			@Override
			public int size() {
				return count;
			}
		};
	}
}
