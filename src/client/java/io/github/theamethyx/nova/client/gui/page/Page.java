package io.github.theamethyx.nova.client.gui.page;

import io.github.theamethyx.nova.client.NovaClient;
import io.github.theamethyx.nova.client.gui.Icon;
import io.github.theamethyx.nova.client.gui.UiContext;
import io.github.theamethyx.nova.client.gui.widget.Container;

/**
 * One screen of the ClickGUI (Dashboard, Modules, ...). Pages are containers laid out inside the
 * content area; they rebuild their widgets in {@link #onLayout()} and may refresh in {@link #tick()}.
 */
public abstract class Page extends Container {
	protected final UiContext ui;
	protected final NovaClient nova;

	protected Page(UiContext ui) {
		this.ui = ui;
		this.nova = NovaClient.get();
	}

	public abstract String title();

	/** Short name for the sidebar; defaults to the title. */
	public String navLabel() {
		return title();
	}

	/** One sentence explaining what the page is for, shown under the title. */
	public abstract String subtitle();

	public abstract Icon icon();

	/** Called every client tick while the page is visible. */
	public void tick() {
	}

	/** Called when the page becomes visible. */
	public void onShow() {
	}
}
