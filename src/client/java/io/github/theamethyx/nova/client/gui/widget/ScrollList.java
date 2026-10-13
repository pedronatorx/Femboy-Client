package io.github.theamethyx.nova.client.gui.widget;

import java.util.List;

import io.github.theamethyx.nova.client.gui.Anim;
import io.github.theamethyx.nova.client.gui.Theme;
import io.github.theamethyx.nova.client.gui.UiContext;

/**
 * Vertical list that stacks children by their preferred height, clips them to its bounds and
 * scrolls with the mouse wheel or by dragging the scrollbar.
 */
public class ScrollList extends Container {
	private static final int SCROLLBAR = 3;
	private final UiContext ui;
	private final int gap;
	private final int padding;
	private final Anim scroll = new Anim(0, 22);
	private float scrollTarget;
	private int contentHeight;
	private boolean draggingBar;
	private double dragOffset;

	public ScrollList(UiContext ui, int gap, int padding) {
		this.ui = ui;
		this.gap = gap;
		this.padding = padding;
	}

	public void setChildren(List<? extends Widget> widgets) {
		children.clear();
		children.addAll(widgets);
		relayout(Math.round(scroll.get()));
	}

	public void scrollToTop() {
		scrollTarget = 0;
		scroll.snap(0);
	}

	private int maxScroll() {
		return Math.max(0, contentHeight - height);
	}

	private boolean scrollable() {
		return contentHeight > height;
	}

	@Override
	protected void onLayout() {
		relayout(Math.round(scroll.get()));
	}

	private void relayout(int offset) {
		int innerWidth = width - padding * 2 - (scrollable() ? SCROLLBAR + 2 : 0);
		int cy = y + padding - offset;
		int total = padding;

		int shown = 0;
		for (Widget child : children) {
			int h = child.preferredHeight(ui, innerWidth);
			child.setBounds(x + padding, cy, innerWidth, h);
			if (h <= 0) {
				// Hidden rows take no space and no gap.
				continue;
			}

			cy += h + gap;
			total += h + gap;
			shown++;
		}

		int newContent = total - (shown == 0 ? 0 : gap) + padding;
		boolean wasScrollable = scrollable();
		contentHeight = newContent;
		if (wasScrollable != scrollable()) {
			// The scrollbar appeared or disappeared, which changes the usable width.
			relayout(offset);
		}
	}

	@Override
	public void render(UiContext ui) {
		scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget));
		float offset = scroll.update(scrollTarget, ui.frameSeconds(), ui.animate());
		relayout(Math.round(offset));

		ui.pushClip(x, y, width, height);
		for (Widget child : children) {
			if (child.y() + child.height() >= y && child.y() <= y + height) {
				child.render(ui);
			}
		}

		ui.popClip();

		if (scrollable()) {
			int trackX = x + width - SCROLLBAR - 1;
			int barHeight = Math.max(14, height * height / contentHeight);
			int barY = y + Math.round((height - barHeight) * (offset / Math.max(1, maxScroll())));
			boolean hot = draggingBar || ui.hovered(trackX - 2, y, SCROLLBAR + 4, height);
			ui.rect(trackX, y + 1, SCROLLBAR, height - 2, 0x40000000);
			ui.roundRect(trackX, barY, SCROLLBAR, barHeight, hot ? Theme.ACCENT : Theme.BORDER_STRONG);
		}
	}

	@Override
	public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
		if (!contains(mouseX, mouseY)) {
			return false;
		}

		if (scrollable() && mouseX >= x + width - SCROLLBAR - 3) {
			int barHeight = Math.max(14, height * height / contentHeight);
			int barY = y + Math.round((height - barHeight) * (scroll.get() / Math.max(1, maxScroll())));
			dragOffset = mouseY >= barY && mouseY < barY + barHeight ? mouseY - barY : barHeight / 2.0;
			draggingBar = true;
			ui.capture(this);
			mouseDragged(ui, mouseX, mouseY, button);
			return true;
		}

		return super.mouseClicked(ui, mouseX, mouseY, button);
	}

	@Override
	public void mouseDragged(UiContext ui, double mouseX, double mouseY, int button) {
		if (draggingBar) {
			int barHeight = Math.max(14, height * height / contentHeight);
			double fraction = (mouseY - dragOffset - y) / Math.max(1, height - barHeight);
			scrollTarget = (float) (Math.max(0, Math.min(1, fraction)) * maxScroll());
			scroll.snap(scrollTarget);
		}
	}

	@Override
	public void mouseReleased(UiContext ui, double mouseX, double mouseY, int button) {
		draggingBar = false;
	}

	@Override
	public boolean mouseScrolled(UiContext ui, double mouseX, double mouseY, double amount) {
		if (super.mouseScrolled(ui, mouseX, mouseY, amount)) {
			return true;
		}

		if (!scrollable()) {
			return false;
		}

		scrollTarget = (float) Math.max(0, Math.min(maxScroll(), scrollTarget - amount * 24));
		return true;
	}
}
