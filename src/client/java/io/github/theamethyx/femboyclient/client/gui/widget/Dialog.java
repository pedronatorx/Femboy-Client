package io.github.theamethyx.femboyclient.client.gui.widget;

import java.util.function.Function;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

import io.github.theamethyx.femboyclient.client.gui.Icon;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;

/**
 * A centered dialog over the menu, in two forms:
 * <ul>
 * <li>a prompt: a title, an explanation and a text field; Enter or the main button submits, and a problem (such as an
 * invalid name) is shown in the dialog instead of closing it;</li>
 * <li>a confirmation: a title and an explanation of exactly what will change or be lost, and a main button (red for
 * destructive actions) next to Cancel.</li>
 * </ul>
 * Escape or Cancel closes it without doing anything.
 */
public class Dialog extends Container {
	private final String title;
	private final String message;
	private final TextField field;
	private final Button confirm;
	private final Button cancel;
	private final Function<String, String> onSubmit;
	private String problem;

	private Dialog(String title, String message, TextField field, String confirmLabel, boolean danger, Function<String, String> onSubmit) {
		this.title = title;
		this.message = message;
		this.field = field;
		this.onSubmit = onSubmit;
		if (field != null) {
			add(field);
			field.onSubmit(text -> submit());
		}

		confirm = add(new Button(confirmLabel, danger ? Button.Style.DANGER : Button.Style.PRIMARY, this::submit));
		cancel = add(new Button("Cancel", Button.Style.SECONDARY, this::close));
	}

	/** A text prompt; {@code onSubmit} returns {@code null} when done, otherwise the problem to show. */
	public static Dialog prompt(String title, String message, String placeholder, String initial, int maxLength, String confirmLabel,
			Function<String, String> onSubmit) {
		TextField field = new TextField(placeholder, maxLength);
		field.setText(initial == null ? "" : initial);
		return new Dialog(title, message, field, confirmLabel, false, onSubmit);
	}

	/** A confirmation; {@code action} returns {@code null} when done, otherwise the problem to show. */
	public static Dialog confirm(String title, String message, String confirmLabel, boolean danger, java.util.function.Supplier<String> action) {
		return new Dialog(title, message, null, confirmLabel, danger, text -> action.get());
	}

	public TextField field() {
		return field;
	}

	public Button confirmButton() {
		return confirm;
	}

	public String problem() {
		return problem;
	}

	/** Restricts what can be typed into the prompt's field. */
	public Dialog allow(java.util.function.IntPredicate chars) {
		if (field != null) {
			field.allow(chars);
		}

		return this;
	}

	private UiContext ui;

	/** Shows the dialog centered on the screen (as the overlay) and gives the field the keyboard. */
	public Dialog open(UiContext ui) {
		this.ui = ui;
		int w = Math.min(280, ui.screenWidth() - 24);
		int h = 30 + ui.wrappedHeight(message, w - 20) + (field != null ? 22 : 0) + 30;
		setBounds((ui.screenWidth() - w) / 2, Math.max(10, (ui.screenHeight() - h) / 2 - 20), w, h);
		ui.openOverlay(this);
		if (field != null) {
			ui.focus(field);
		}

		return this;
	}

	/** Submits as if the main button was clicked (used by automated UI tests too). */
	public void submit() {
		String result = onSubmit.apply(field == null ? "" : field.text().strip());
		if (result == null) {
			close();
		} else {
			problem = result;
		}
	}

	private void close() {
		if (ui != null) {
			ui.closeOverlay();
			ui.focus(null);
		}
	}

	@Override
	protected void onLayout() {
		int by = y + height - 22;
		int cancelWidth = cancel.naturalWidth(uiOrNull()) + 10;
		int confirmWidth = confirm.naturalWidth(uiOrNull()) + 12;
		cancel.setBounds(x + width - 10 - cancelWidth, by, cancelWidth, 15);
		confirm.setBounds(cancel.x() - 6 - confirmWidth, by, confirmWidth, 15);
		if (field != null) {
			field.setBounds(x + 10, by - 24, width - 20, 15);
		}
	}

	private UiContext uiOrNull() {
		return ui;
	}

	@Override
	public void render(UiContext ui) {
		ui.rect(0, 0, ui.screenWidth(), ui.screenHeight(), 0x60000000);
		ui.shadow(x, y, width, height, Theme.RADIUS_PANEL + 2, 1.4f);
		ui.roundRect(x, y, width, height, Theme.PANEL, Theme.RADIUS_PANEL + 2);
		ui.outline(x, y, width, height, Theme.BORDER_STRONG, Theme.RADIUS_PANEL + 2);
		ui.textScaled(title, x + 10, y + 9, 1.1f, Theme.TEXT);
		ui.wrapped(message, x + 10, y + 24, width - 20, Theme.TEXT_SECONDARY);
		if (problem != null) {
			int py = confirm.y() - (field != null ? 36 : 12);
			ui.icon(Icon.WARNING, x + 10, py, 8, Theme.ERROR);
			ui.textClipped(problem, x + 21, py, width - 31, Theme.ERROR);
		}

		super.render(ui);
	}

	@Override
	public boolean mouseClicked(UiContext ui, double mouseX, double mouseY, int button) {
		if (!contains(mouseX, mouseY)) {
			return true; // clicks outside do nothing, so a half-typed name is not lost
		}

		return super.mouseClicked(ui, mouseX, mouseY, button) || true;
	}

	@Override
	public boolean keyPressed(UiContext ui, KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
			close();
			return true;
		}

		if (field == null && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
			submit();
			return true;
		}

		if (field != null) {
			ui.focus(field);
			field.keyPressed(ui, event);
		}

		return true;
	}

	@Override
	public boolean charTyped(UiContext ui, CharacterEvent event) {
		if (field != null) {
			ui.focus(field);
			return field.charTyped(ui, event);
		}

		return true;
	}
}
