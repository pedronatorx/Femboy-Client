package io.github.theamethyx.nova.client.gui.widget;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

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

	@Override
	public boolean keyPressed(UiContext ui, KeyEvent event) {
		for (int i = children.size() - 1; i >= 0; i--) {
			if (children.get(i).keyPressed(ui, event)) {
				return true;
			}
		}

		return false;
	}

	@Override
	public boolean charTyped(UiContext ui, CharacterEvent event) {
		for (int i = children.size() - 1; i >= 0; i--) {
			if (children.get(i).charTyped(ui, event)) {
				return true;
			}
		}

		return false;
	}
}
