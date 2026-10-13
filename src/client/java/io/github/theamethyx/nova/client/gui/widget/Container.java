package io.github.theamethyx.nova.client.gui.widget;

import java.util.ArrayList;
import java.util.List;

import io.github.theamethyx.nova.client.gui.UiContext;

/** A widget holding children; renders them in order and gives events to the topmost first. */
public class Container extends Widget {
	protected final List<Widget> children = new ArrayList<>();

	public <W extends Widget> W add(W widget) {
		children.add(widget);
		return widget;
	}

	public void clear() {
		children.clear();
	}

	public List<Widget> children() {
		return children;
	}

	/** All widgets of a type inside this container, depth first (used by automated UI tests). */
	public <W extends Widget> List<W> descendants(Class<W> type) {
		List<W> found = new ArrayList<>();
		for (Widget child : children) {
			if (type.isInstance(child)) {
				found.add(type.cast(child));
			}

			if (child instanceof Container container) {
				found.addAll(container.descendants(type));
			}
		}

		return found;
	}

	@Override
	public void render(UiContext ui) {
		for (Widget child : children) {
			child.render(ui);
		}
	}

	@Override
	public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
		for (int i = children.size() - 1; i >= 0; i--) {
			if (children.get(i).mouseClicked(ui, mouseX, mouseY, button)) {
				return true;
			}
		}

		return false;
	}

	@Override
	public boolean mouseScrolled(UiContext ui, double mouseX, double mouseY, double amount) {
		for (int i = children.size() - 1; i >= 0; i--) {
			Widget child = children.get(i);
			if (child.contains(mouseX, mouseY) && child.mouseScrolled(ui, mouseX, mouseY, amount)) {
				return true;
			}
		}

		return false;
	}

	// Keyboard input is deliberately not broadcast to children: only the focused widget receives
	// keys (routed by NovaScreen). Broadcasting let unfocused text fields swallow Escape and typing.
}
