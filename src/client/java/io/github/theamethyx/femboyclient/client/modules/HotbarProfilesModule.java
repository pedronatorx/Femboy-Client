package io.github.theamethyx.femboyclient.client.modules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.combat.InventoryActions;
import io.github.theamethyx.femboyclient.client.gui.Icon;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.gui.widget.Button;
import io.github.theamethyx.femboyclient.client.gui.widget.Container;
import io.github.theamethyx.femboyclient.client.gui.widget.TextField;
import io.github.theamethyx.femboyclient.client.gui.widget.Widget;
import io.github.theamethyx.femboyclient.client.hud.Notifications;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;

/**
 * Saved hotbar layouts (which item goes in which of the 9 slots). Loading one moves the items from your inventory or
 * other hotbar slots into place with number-key swaps, the same clicks you would make. Layouts are shared by all
 * profiles; a layout named like a profile can load automatically when that profile becomes active.
 */
public final class HotbarProfilesModule extends Module implements ClientHooks.CustomControls {
	public static final String SECTION = "hotbar_layouts";
	private static final int MAX_LAYOUTS = 40;
	private static final Pattern NAME = Pattern.compile("[^\\p{Cntrl}]{1,32}");

	private final BooleanSetting withProfile = settings().add(new BooleanSetting("with_profile", "Load with profile",
			"When a Femboy Client profile becomes active and a layout has the same name, load that layout.", true));

	private String lastProfile;

	public HotbarProfilesModule() {
		super("hotbar_profiles", "Hotbar Profiles", "Save hotbar layouts and restore them with one click or with your profile.",
				ModuleCategory.INVENTORY, false, NO_KEY);
	}

	@Override
	public Tier tier() {
		return Tier.AUTOMATION;
	}

	// ----- storage ---------------------------------------------------------------------------

	/** Saved layouts by name; each is 9 item ids, "" for a slot that is left alone. */
	public Map<String, List<String>> layouts() {
		Map<String, List<String>> result = new LinkedHashMap<>();
		if (FemboyClient.get().config().section(SECTION) instanceof JsonObject json) {
			json.entrySet().forEach(entry -> {
				if (NAME.matcher(entry.getKey()).matches() && entry.getValue() instanceof JsonArray array && array.size() == 9) {
					List<String> slots = new ArrayList<>();
					array.forEach(element -> slots.add(element.isJsonPrimitive() ? element.getAsString() : ""));
					result.put(entry.getKey(), slots);
				}
			});
		}

		return result;
	}

	private void store(Map<String, List<String>> layouts) {
		JsonObject json = new JsonObject();
		layouts.forEach((name, slots) -> {
			JsonArray array = new JsonArray();
			slots.forEach(array::add);
			json.add(name, array);
		});
		FemboyClient.get().config().setSection(SECTION, json);
	}

	/** Saves the current hotbar under {@code rawName}. @return {@code null} on success, otherwise why not */
	public String save(String rawName) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return "Load a world first.";
		}

		String name = rawName.trim();
		if (!NAME.matcher(name).matches()) {
			return "Names need 1-32 visible characters.";
		}

		Map<String, List<String>> layouts = layouts();
		if (!layouts.containsKey(name) && layouts.size() >= MAX_LAYOUTS) {
			return "You can store at most " + MAX_LAYOUTS + " layouts.";
		}

		List<String> slots = new ArrayList<>();
		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			slots.add(stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
		}

		layouts.put(name, slots);
		store(layouts);
		return null;
	}

	public void delete(String name) {
		Map<String, List<String>> layouts = layouts();
		if (layouts.remove(name) != null) {
			store(layouts);
		}
	}

	// ----- loading ---------------------------------------------------------------------------

	/**
	 * Moves items into place for layout {@code name}.
	 *
	 * @return {@code null} when every slot could be filled, otherwise which items are missing
	 */
	public String load(String name) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		List<String> layout = layouts().get(name);
		if (player == null || client.gameMode == null || layout == null) {
			return "Nothing to load.";
		}

		if (player.containerMenu != player.inventoryMenu) {
			return "Close the open container first.";
		}

		Item[] wanted = new Item[9];
		for (int slot = 0; slot < 9; slot++) {
			Identifier id = layout.get(slot).isEmpty() ? null : Identifier.tryParse(layout.get(slot));
			wanted[slot] = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
		}

		List<String> missing = new ArrayList<>();
		for (int slot = 0; slot < 9; slot++) {
			Item item = wanted[slot];
			if (item == null || item == Items.AIR || player.getInventory().getItem(slot).is(item)) {
				continue;
			}

			int source = -1;
			// Another hotbar slot that does not need this item where it is.
			for (int other = 0; other < 9 && source < 0; other++) {
				if (other != slot && player.getInventory().getItem(other).is(item) && wanted[other] != item) {
					source = InventoryActions.HOTBAR_FIRST + other;
				}
			}

			if (source < 0) {
				source = InventoryActions.findMain(player, stack -> stack.is(item));
			}

			if (source < 0) {
				missing.add(item.getName(new ItemStack(item)).getString());
				continue;
			}

			InventoryActions.swapWithHotbar(player.inventoryMenu, source, slot);
		}

		return missing.isEmpty() ? null : "Not in your inventory: " + String.join(", ", missing);
	}

	@Override
	protected void onEnable() {
		lastProfile = FemboyClient.get().config().activeProfile();
	}

	@Override
	protected void onTick() {
		String profile = FemboyClient.get().config().activeProfile();
		if (profile != null && !profile.equals(lastProfile)) {
			lastProfile = profile;
			if (withProfile.get() && layouts().containsKey(profile)) {
				String problem = load(profile);
				Notifications.show("Hotbar", problem == null ? "Loaded layout " + profile : problem, problem == null ? Theme.ACCENT : Theme.WARNING);
			}
		}
	}

	// ----- GUI -------------------------------------------------------------------------------

	@Override
	public List<Widget> createControls(UiContext ui, Runnable refresh) {
		List<Widget> widgets = new ArrayList<>();
		widgets.add(new SaveRow(refresh));
		layouts().forEach((name, slots) -> widgets.add(new LayoutRow(name, slots, refresh)));
		return widgets;
	}

	private final class SaveRow extends Container {
		private final TextField name = add(new TextField("Layout name (e.g. a profile name)", 32));
		private final Button saveButton;
		private String message;

		SaveRow(Runnable refresh) {
			Runnable submit = () -> {
				message = save(name.text());
				if (message == null) {
					name.setText("");
					refresh.run();
				}
			};
			name.onSubmit(text -> submit.run());
			saveButton = add(new Button("Save hotbar", Button.Style.PRIMARY, submit).icon(Icon.PLUS));
			saveButton.enabledWhen(() -> !name.text().isBlank() && Minecraft.getInstance().player != null);
			saveButton.tooltip("Save which item is in each of your 9 hotbar slots under this name. An existing layout is replaced.");
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return message == null ? 15 : 27;
		}

		@Override
		protected void onLayout() {
			name.setBounds(x, y, width - 80, 15);
			saveButton.setBounds(x + width - 76, y, 76, 15);
		}

		@Override
		public void render(UiContext ui) {
			super.render(ui);
			if (message != null) {
				ui.textClipped(message, x + 2, y + 18, width - 4, Theme.ERROR);
			}
		}
	}

	private final class LayoutRow extends Container {
		private final String name;
		private final List<String> slots;
		private final Button loadButton;
		private final Button delete;
		private String message;

		LayoutRow(String name, List<String> slots, Runnable refresh) {
			this.name = name;
			this.slots = slots;
			loadButton = add(new Button("Load", Button.Style.SECONDARY, () -> message = load(name)));
			loadButton.enabledWhen(() -> Minecraft.getInstance().player != null);
			loadButton.tooltip("Move the items of this layout into your hotbar.");
			delete = add(new Button("", Button.Style.DANGER, () -> {
				delete(name);
				refresh.run();
			}).icon(Icon.TRASH));
			delete.tooltip("Delete this layout.");
		}

		@Override
		public int preferredHeight(UiContext ui, int width) {
			return message == null ? 24 : 34;
		}

		@Override
		protected void onLayout() {
			delete.setBounds(x + width - 21, y + 5, 18, 14);
			loadButton.setBounds(x + width - 64, y + 5, 40, 14);
		}

		@Override
		public void render(UiContext ui) {
			ui.roundRect(x, y, width, height, Theme.SURFACE);
			ui.textClipped(name, x + 6, y + 8, 90, Theme.TEXT);
			int ix = x + 100;
			for (String id : slots) {
				Identifier parsed = id.isEmpty() ? null : Identifier.tryParse(id);
				Item item = parsed == null ? Items.AIR : BuiltInRegistries.ITEM.getOptional(parsed).orElse(Items.AIR);
				if (ix + 16 > x + width - 68) {
					break;
				}

				ui.rect(ix, y + 3, 17, 18, Theme.SURFACE_HOVER);
				if (item != Items.AIR) {
					ui.graphics().renderItem(new ItemStack(item), ix, y + 4);
				}

				ix += 18;
			}

			if (message != null) {
				ui.textClipped(message, x + 6, y + 24, width - 12, Theme.WARNING);
			}

			super.render(ui);
		}
	}
}
