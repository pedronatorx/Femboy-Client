package io.github.theamethyx.femboyclient.client.gui.page;

import java.util.ArrayList;
import java.util.List;

import io.github.theamethyx.femboyclient.client.gui.GuiState;
import io.github.theamethyx.femboyclient.client.gui.HelpTopics;
import io.github.theamethyx.femboyclient.client.gui.Icon;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.gui.widget.ScrollList;
import io.github.theamethyx.femboyclient.client.gui.widget.TextField;
import io.github.theamethyx.femboyclient.client.gui.widget.Widget;

/**
 * Settings › Help: the guides and the glossary from {@link HelpTopics}, with a search field. Guides open and close with a
 * click (the first one starts open); while searching, every match is shown open.
 */
public class HelpPage extends Page {
	private final TextField search;
	private final ScrollList content;
	private String query = "";

	public HelpPage(UiContext ui) {
		super(ui);
		search = add(new TextField("Search the help", 40).icon(Icon.SEARCH).onChange(text -> {
			query = text.strip();
			rebuild();
		}));
		search.tooltip("Type words to find guides and terms that contain all of them.");
		content = add(new ScrollList(ui, 2, 0));
	}

	@Override
	public String title() {
		return "Settings";
	}

	@Override
	public String navLabel() {
		return "Help";
	}

	@Override
	public String subtitle() {
		return "A short guide to the client and a glossary of terms.";
	}

	@Override
	public Icon icon() {
		return Icon.INFO;
	}

	/** The help search field (used by automated UI tests). */
	public TextField searchField() {
		return search;
	}

	/** Shows the topics matching {@code text}, as if typed. */
	public void setQuery(String text) {
		search.setText(text);
		query = text.strip();
		rebuild();
	}

	/** Titles of the topics currently listed (used by automated UI tests). */
	public List<String> shownTitles() {
		return content.children().stream().filter(widget -> widget instanceof TopicRow).map(widget -> ((TopicRow) widget).topic.title()).toList();
	}

	@Override
	public void onShow() {
		rebuild();
	}

	@Override
	protected void onLayout() {
		search.setBounds(x, y, Math.min(260, width), 15);
		content.setBounds(x, y + 15 + Theme.SPACE_M, width, height - 15 - Theme.SPACE_M);
		rebuild();
	}

	private void rebuild() {
		List<Widget> rows = new ArrayList<>();
		List<HelpTopics.Topic> found = HelpTopics.search(query);
		boolean guides = false;
		boolean terms = false;
		for (HelpTopics.Topic topic : found) {
			if (!topic.term() && !guides) {
				rows.add(new Caption("Guides"));
				guides = true;
			}

			if (topic.term() && !terms) {
				rows.add(new Caption("Glossary"));
				terms = true;
			}

			rows.add(new TopicRow(topic));
		}

		if (found.isEmpty()) {
			rows.add(new ModulesPage.EmptyState("Nothing in the help matches \"" + query + "\". Try another word, or search the whole client with Ctrl+K."));
		}

		content.setChildren(rows);
	}

	@Override
	public void render(UiContext ui) {
		search.render(ui);
		ui.pushClip(content.x(), content.y(), content.width(), content.height());
		content.render(ui);
		ui.popClip();
	}

	private static final class Caption extends Widget {
		private final String text;

		Caption(String text) {
			this.text = text;
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return 16;
		}

		@Override
		public void render(UiContext ui) {
			ui.text(text, x + 2, y + 6, Theme.TEXT_SECONDARY);
			ui.rect(x + 2, y + 15, width - 4, 1, Theme.BORDER);
		}
	}

	/** A guide (opens on click) or a glossary term (always open: one line). */
	private final class TopicRow extends Widget {
		private final HelpTopics.Topic topic;

		TopicRow(HelpTopics.Topic topic) {
			this.topic = topic;
		}

		private boolean open() {
			if (topic.term() || !query.isEmpty()) {
				return true;
			}

			Boolean open = GuiState.openSections.get("help/" + topic.title());
			return open == null ? topic == HelpTopics.GUIDES.getFirst() : open;
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			if (topic.term()) {
				return Math.max(14, ui.wrappedHeight(topic.body(), width - 130) + 4);
			}

			return 16 + (open() ? ui.wrappedHeight(topic.body(), width - 24) + 6 : 0);
		}

		@Override
		public void render(UiContext ui) {
			boolean hot = hovered(ui);
			if (topic.term()) {
				ui.textClipped(topic.title(), x + 6, y + 3, 112, Theme.TEXT);
				ui.wrapped(topic.body(), x + 124, y + 3, width - 130, Theme.TEXT_SECONDARY);
				return;
			}

			if (hot) {
				ui.roundRect(x, y, width, 15, Theme.withAlpha(Theme.SURFACE_HOVER, 0.7f), Theme.RADIUS_CONTROL);
				ui.pointer();
			}

			ui.icon(open() ? Icon.CHEVRON_DOWN : Icon.CHEVRON_RIGHT, x + 4, y + 4, 7, Theme.TEXT_MUTED);
			ui.text(topic.title(), x + 15, y + 4, Theme.TEXT);
			if (open()) {
				ui.wrapped(topic.body(), x + 15, y + 17, width - 24, Theme.TEXT_SECONDARY);
			}
		}

		@Override
		public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
			if (topic.term() || !query.isEmpty() || button != 0 || !UiContext.inside(mouseX, mouseY, x, y, width, 15)) {
				return false;
			}

			GuiState.openSections.put("help/" + topic.title(), !open());
			return true;
		}
	}
}
