package io.github.theamethyx.femboyclient.client.gui;

/**
 * A value that eases towards a target at a frame-rate-independent speed. When animations are
 * disabled in the options it jumps straight to the target.
 */
public final class Anim {
	/** Global animation speed (Theme Studio): 1 normal, 2 twice as fast. */
	public static float SPEED = 1;
	private final float speed;
	private float value;
	private float target;

	/** @param speed roughly "how many times per second the remaining distance shrinks by e" */
	public Anim(float initial, float speed) {
		this.value = initial;
		this.target = initial;
		this.speed = speed;
	}

	public float update(float target, float frameSeconds, boolean animate) {
		this.target = target;
		if (!animate) {
			value = target;
		} else {
			value += (target - value) * (1 - (float) Math.exp(-speed * SPEED * frameSeconds));
			if (Math.abs(target - value) < 0.001f) {
				value = target;
			}
		}

		return value;
	}

	public float get() {
		return value;
	}

	public void snap(float to) {
		value = to;
		target = to;
	}

	public boolean settled() {
		return value == target;
	}
}
