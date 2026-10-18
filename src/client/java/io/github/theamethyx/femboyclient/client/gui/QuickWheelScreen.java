package io.github.theamethyx.femboyclient.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import io.github.theamethyx.femboyclient.client.ClientKeys;
import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.hud.Notifications;
import io.github.theamethyx.femboyclient.core.module.Module;

/**
 * The quick toggle wheel: while its key is held, up to eight favorite (then recently used) modules sit around the
 * crosshair, plus "previous profile" when there is one. Point at one with the mouse and release the key (or click) to use
 * it; release in the middle to do nothing. The game is not paused.
 */
public final class QuickWheelScreen extends Screen {
	private static final int SLOTS = 8;
	private static final int RADIUS = 70;
	private static final int DEAD_ZONE = 18;

	private final FemboyClient femboy = FemboyClient.get();
	private final List<Slot> slots = new ArrayList<>();
	private final long openedAt = System.currentTimeMillis();
	private UiContext ui;
	private int hovered = -1;
	private boolean done;

	private record Slot(String label, Icon icon, Module module, Runnable action) {
	}

	public QuickWheelScreen() {
		super(Component.literal("Quick toggle wheel"));
		QuickAccess quick = femboy.quickAccess();
		for (Module module : quick.favorites()) {
			if (slots.size() < SLOTS) slots.add(moduleSlot(module));
		}

		for (Module module : quick.recent()) {
			if (slots.size() < SLOTS && !quick.isFavorite(module)) slots.add(moduleSlot(module));
		}

		String previous = femboy.config().previousProfile();
		if (previous != null && slots.size() < SLOTS) {
			slots.add(new Slot("Profile " + previous, Icon.UNDO, null, () -> {
				String problem = femboy.config().revertProfile();
				Notifications.post(Notifications.Category.PROFILE, "Profile", problem == null ? "back to " + previous : problem, problem == null ? Theme.SUCCESS : Theme.ERROR);
			}));
		}
	}

	private Slot moduleSlot(Module module) {
		return new Slot(module.name(), Icon.byName(module.category().icon()), module, () -> {
			femboy.modules().toggle(module);
			Module.Availability availability = femboy.modules().availability(module);
			String state = module.isEnabled() ? availability.available() ? "on" : "on, but " + availability.reason() : "off";
			Notifications.post(module.isEnabled() && !availability.available() ? Notifications.Category.ENVIRONMENT : Notifications.Category.MODULE,
					module.name(), state, module.isEnabled() ? Theme.SUCCESS : Theme.TEXT_MUTED);
		});
	}

	@Override
	protected void init() {
		if (ui == null) {
			ui = new UiContext(font);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
	}

	/** Labels in slot order (used by automated tests). */
	public List<String> labels() {
		return slots.stream().map(Slot::label).toList();
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		ui.setScreenSize(width, height);
		ui.beginFrame(graphics, mouseX, mouseY, 0, false);
		int cx = width / 2;
		int cy = height / 2;
		hovered = slotAt(mouseX, mouseY);
		float appear = femboy.options().animations.get() ? Math.min(1, (System.currentTimeMillis() - openedAt) / 120f) : 1;

		ui.glow(cx, cy, RADIUS + 50, Theme.withAlpha(Theme.ACCENT, 0.25f * appear));
		ui.roundRect(cx - 14, cy - 14, 28, 28, Theme.withAlpha(Theme.PANEL, 0.85f * appear), 14);
		ui.icon(Icon.CLOSE, cx - 4, cy - 4, 8, Theme.withAlpha(Theme.TEXT_MUTED, appear));

		if (slots.isEmpty()) {
			String hint = "Star modules (Home or a module's settings) to put them on the wheel.";
			int w = ui.textWidth(hint) + 12;
			ui.roundRect(cx - w / 2, cy + 24, w, 14, Theme.withAlpha(Theme.PANEL, 0.9f), 4);
			ui.textCentered(hint, cx, cy + 27, Theme.TEXT_SECONDARY);
		}

		for (int i = 0; i < slots.size(); i++) {
			Slot slot = slots.get(i);
			double angle = angle(i);
			float distance = RADIUS * (femboy.options().reduceMotion.get() ? 1 : 0.7f + 0.3f * appear);
			int sx = cx + (int) Math.round(Math.cos(angle) * distance);
			int sy = cy + (int) Math.round(Math.sin(angle) * distance);
			boolean hot = i == hovered;
			boolean on = slot.module() != null && slot.module().isEnabled();
			int w = Math.max(54, Math.min(110, ui.textWidth(slot.label()) + 24));
			int h = 22;
			ui.shadow(sx - w / 2, sy - h / 2, w, h, 6, 0.7f * appear);
			int fill = hot ? Theme.lerp(Theme.SURFACE_ACTIVE, Theme.ACCENT, 0.35f) : Theme.SURFACE;
			ui.roundRect(sx - w / 2, sy - h / 2, w, h, Theme.withAlpha(fill, 0.95f * appear), 6);
			if (on) {
				ui.roundRect(sx - w / 2, sy - h / 2 + 4, 2, h - 8, Theme.withAlpha(Theme.SUCCESS, appear), 1);
			}

			ui.icon(slot.icon(), sx - w / 2 + 6, sy - 4, 8, Theme.withAlpha(hot ? Theme.TEXT : Theme.ACCENT, appear));
			ui.textClipped(slot.label(), sx - w / 2 + 17, sy - 4, w - 21, Theme.withAlpha(hot ? Theme.TEXT : Theme.TEXT_SECONDARY, appear));
		}

		String hint = hovered >= 0 ? "Release to use: " + slots.get(hovered).label() : "Point at an entry and release the key";
		ui.textCentered(hint, cx, cy + RADIUS + 26, Theme.withAlpha(Theme.TEXT, appear));
		ui.endFrame(false, width, height);
	}

	private double angle(int index) {
		return -Math.PI / 2 + 2 * Math.PI * index / Math.max(1, slots.size());
	}

	private int slotAt(double mouseX, double mouseY) {
		double dx = mouseX - width / 2.0;
		double dy = mouseY - height / 2.0;
		if (slots.isEmpty() || dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE) {
			return -1;
		}

		double angle = Math.atan2(dy, dx);
		int best = -1;
		double bestDiff = Double.MAX_VALUE;
		for (int i = 0; i < slots.size(); i++) {
			double diff = Math.abs(Math.atan2(Math.sin(angle - angle(i)), Math.cos(angle - angle(i))));
			if (diff < bestDiff) {
				bestDiff = diff;
				best = i;
			}
		}

		return best;
	}

	@Override
	public void tick() {
		// The key may be released before the screen saw a key event; check it directly.
		var key = InputConstants.getKey(ClientKeys.quickWheel.saveString());
		if (!done && key.getType() == InputConstants.Type.KEYSYM && !InputConstants.isKeyDown(minecraft.getWindow(), key.getValue())) {
			finish();
		}
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (ClientKeys.quickWheel.matches(event)) {
			finish();
			return true;
		}

		return super.keyReleased(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		hovered = slotAt(event.x(), event.y());
		finish();
		return true;
	}

	/** Uses the slot under the mouse (if any) and closes. */
	public void finish() {
		if (done) {
			return;
		}

		done = true;
		int index = hovered;
		onClose();
		if (index >= 0 && index < slots.size()) {
			slots.get(index).action().run();
		}
	}

	/** Uses a slot by index (used by automated tests). */
	public void choose(int index) {
		hovered = index;
		finish();
	}
}
