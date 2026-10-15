package io.github.theamethyx.femboyclient.client.modules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.gui.widget.AttributeSwapPanel;
import io.github.theamethyx.femboyclient.client.gui.widget.Widget;
import io.github.theamethyx.femboyclient.client.hud.HudAnchor;
import io.github.theamethyx.femboyclient.client.hud.HudLine;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.EnumSetting;
import io.github.theamethyx.femboyclient.core.setting.IdSetSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;
import io.github.theamethyx.femboyclient.core.setting.Setting;

/**
 * Attribute swapping: when you attack, the hotbar slot is switched to a weapon chosen by rule (an axe
 * against a raised shield, a mace while falling, ...) just for that hit, then switched back.
 *
 * <p>How it works in 1.21.11: {@code MultiPlayerGameMode.attack} first sends the selected hotbar slot,
 * then the attack. {@code MultiPlayerGameModeMixin} selects the rule's slot just before that and, with
 * the default swap-back delay of 0, selects and sends the original slot again right after the attack in
 * the same client tick. The server handles the hit with the swapped item (its enchantments and special
 * effects) and never sees it during its own tick, so your attack charge is not reset by the swap.
 *
 * <p>Only hotbar slots are used; nothing in the inventory is moved. The client can confirm which slot
 * it selected and sent, but servers do not acknowledge slot changes, so the panel never claims more.
 */
public final class AutoAttributeSwapModule extends Module implements ClientHooks.HudBlock, ClientHooks.CustomControls {
	public enum TargetFilter implements EnumSetting.Labeled {
		ALL("Any target"), PLAYERS("Players only"), MOBS("Mobs only");

		private final String label;

		TargetFilter(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}

		boolean matches(Entity target) {
			return switch (this) {
				case ALL -> true;
				case PLAYERS -> target instanceof Player;
				case MOBS -> !(target instanceof Player);
			};
		}
	}

	/** The swap rules, checked in this order on every attack. */
	public enum Rule {
		AXE("axe", "Axe", "Disables a raised shield for 5 seconds.", Items.IRON_AXE, 1000, true),
		MACE("mace", "Mace", "Smash attack: extra damage the farther you fall.", Items.MACE, 0, true),
		SPEAR("spear", "Spear", "Spear enchantments and effects on a normal hit.", Items.IRON_SPEAR, 500, false),
		SWORD("sword", "Sword", "Sword enchantments such as Sweeping Edge and Fire Aspect.", Items.DIAMOND_SWORD, 0, false),
		TRIDENT("trident", "Trident", "Impaling and other trident enchantments.", Items.TRIDENT, 500, false),
		CUSTOM("custom", "Custom items", "Any items you pick from the list.", Items.NETHER_STAR, 500, false);

		private final String id;
		private final String label;
		private final String purpose;
		private final Item icon;
		private final int defaultCooldown;
		private final boolean defaultEnabled;

		Rule(String id, String label, String purpose, Item icon, int defaultCooldown, boolean defaultEnabled) {
			this.id = id;
			this.label = label;
			this.purpose = purpose;
			this.icon = icon;
			this.defaultCooldown = defaultCooldown;
			this.defaultEnabled = defaultEnabled;
		}

		public String label() {
			return label;
		}

		public String purpose() {
			return purpose;
		}

		public ItemStack icon() {
			return new ItemStack(icon);
		}
	}

	/** The settings of one rule, shown together in the rule's card. */
	public final class RuleSettings {
		final Rule rule;
		final BooleanSetting enabled;
		final NumberSetting cooldown;
		final EnumSetting<TargetFilter> targets;
		final List<Setting<?>> conditions = new ArrayList<>();
		private Predicate<ItemStack> matcher;

		RuleSettings(Rule rule) {
			this.rule = rule;
			enabled = settings().add(new BooleanSetting(rule.id + "_enabled", rule.label + ": enabled",
					"Swap to " + rule.label.toLowerCase(Locale.ROOT) + " when its conditions are met.", rule.defaultEnabled)).panelOnly();
			cooldown = settings().add(new NumberSetting(rule.id + "_cooldown", "Cooldown",
					"Minimum time between two swaps to this item. Hits during the cooldown are made with whatever you hold.",
					rule.defaultCooldown, 0, 10000, 50, "ms")).panelOnly();
			targets = settings().add(new EnumSetting<>(rule.id + "_targets", "Targets",
					"Which targets this rule applies to.", TargetFilter.ALL)).panelOnly();
		}

		public Rule rule() {
			return rule;
		}

		public BooleanSetting enabled() {
			return enabled;
		}

		/** Settings shown in the card below the toggle, in order. */
		public List<Setting<?>> controls() {
			List<Setting<?>> list = new ArrayList<>(conditions);
			list.add(0, cooldown);
			list.add(1, targets);
			return list;
		}

		public List<Setting<?>> all() {
			List<Setting<?>> list = new ArrayList<>(controls());
			list.add(0, enabled);
			return list;
		}

		public boolean matches(ItemStack stack) {
			return !stack.isEmpty() && matcher.test(stack);
		}

		/** Remaining cooldown in milliseconds, 0 when ready. */
		public long remainingCooldown() {
			return Math.max(0, readyAt.getOrDefault(rule, 0L) - System.currentTimeMillis());
		}

		/** Hotbar slot holding a matching item, or -1. */
		public int hotbarSlot() {
			LocalPlayer player = Minecraft.getInstance().player;
			return player == null ? -1 : findSlot(player.getInventory(), this);
		}
	}

	/** A swap that happened, as far as the client can confirm it. */
	public record SwapRecord(Rule rule, String item, int fromSlot, int toSlot, String target, long at, boolean usedForHit, boolean restored, String note) {
	}

	private record Pending(Rule rule, int originalSlot, int swappedSlot, String item, String target, long at, int ticksLeft) {
	}

	{ settings().section("Swapping"); }
	private final NumberSetting swapBackDelay = settings().add(new NumberSetting("swap_back_delay", "Swap back after",
			"0 switches back in the same tick as the hit, so the server never sees the swapped item during its tick and your "
					+ "attack charge is kept. Higher values hold the swapped item longer.", 0, 0, 10, 1, "ticks"));
	private final BooleanSetting skipWhileUsing = settings().add(new BooleanSetting("skip_while_using", "Not while using an item",
			"Don't swap while you are eating, drinking, blocking with a shield or drawing a bow.", true));
	private final BooleanSetting hud = settings().add(new BooleanSetting("hud", "Show on HUD",
			"Show the last swap and any cooldown in the bottom-right corner.", true));

	private final Map<Rule, RuleSettings> rules = new EnumMap<>(Rule.class);
	private final Map<Rule, Long> readyAt = new EnumMap<>(Rule.class);
	private final BooleanSetting axeShieldOnly;
	private final NumberSetting maceMinFall;
	private final IdSetSetting customItems;
	private Set<Item> customSet = Set.of();
	private List<IdSetSetting.Choice> itemChoices;

	private Pending inProgress;
	private Pending pending;
	private SwapRecord last;
	private String lastSkip;
	private LocalPlayer owner;

	public AutoAttributeSwapModule() {
		super("auto_attribute_swap", "Auto Attribute Swap",
				"Switches to the right weapon for a single hit (axe against shields, mace while falling, ...) and straight back.",
				ModuleCategory.COMBAT, false, NO_KEY);

		for (Rule rule : Rule.values()) {
			rules.put(rule, new RuleSettings(rule));
		}

		axeShieldOnly = settings().add(new BooleanSetting("axe_shield_only", "Only against a raised shield",
				"Swap to the axe only when the target is blocking with a shield. When off, every hit uses the axe.", true)).panelOnly();
		rules.get(Rule.AXE).conditions.add(axeShieldOnly);
		maceMinFall = settings().add(new NumberSetting("mace_min_fall", "Minimum fall distance",
				"Swap to the mace only after falling at least this far. The smash attack needs more than 1.5 blocks; 0 swaps on every hit.",
				1.5, 0, 20, 0.5, "blocks")).panelOnly();
		rules.get(Rule.MACE).conditions.add(maceMinFall);
		customItems = settings().add(new IdSetSetting("custom_items", "Items",
				"The items the custom rule swaps to. The first one found in your hotbar is used.", Set.of())).panelOnly();
		customItems.choices(this::itemChoices);
		customItems.onChange(ids -> customSet = resolveItems(ids));
		rules.get(Rule.CUSTOM).conditions.add(customItems);

		rules.get(Rule.AXE).matcher = stack -> stack.is(ItemTags.AXES);
		rules.get(Rule.MACE).matcher = stack -> stack.is(Items.MACE);
		rules.get(Rule.SPEAR).matcher = stack -> stack.is(ItemTags.SPEARS);
		rules.get(Rule.SWORD).matcher = stack -> stack.is(ItemTags.SWORDS);
		rules.get(Rule.TRIDENT).matcher = stack -> stack.is(Items.TRIDENT);
		rules.get(Rule.CUSTOM).matcher = stack -> customSet.contains(stack.getItem());
	}

	private static Set<Item> resolveItems(Set<String> ids) {
		Set<Item> items = new java.util.HashSet<>();
		for (String id : ids) {
			BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.Identifier.parse(id)).ifPresent(items::add);
		}

		return Set.copyOf(items);
	}

	private List<IdSetSetting.Choice> itemChoices() {
		if (itemChoices == null) {
			List<IdSetSetting.Choice> all = new ArrayList<>();
			for (Item item : BuiltInRegistries.ITEM) {
				ItemStack stack = new ItemStack(item);
				String group = stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SPEARS) || item == Items.MACE
						|| item == Items.TRIDENT ? "Weapons" : stack.has(net.minecraft.core.component.DataComponents.TOOL) ? "Tools" : "Other";
				all.add(new IdSetSetting.Choice(BuiltInRegistries.ITEM.getKey(item).toString(), item.getName(stack).getString(), group));
			}

			List<String> order = List.of("Weapons", "Tools", "Other");
			all.sort(Comparator.<IdSetSetting.Choice>comparingInt(c -> order.indexOf(c.group())).thenComparing(c -> c.label().toLowerCase(Locale.ROOT)));
			itemChoices = List.copyOf(all);
		}

		return itemChoices;
	}

	public List<RuleSettings> rules() {
		return List.copyOf(rules.values());
	}

	public RuleSettings rule(Rule rule) {
		return rules.get(rule);
	}

	public SwapRecord lastSwap() {
		return last;
	}

	/** Why the last attack did not swap, or null. */
	/** Whether a swapped weapon is still in hand, waiting to be switched back. */
	public boolean swapInProgress() {
		return pending != null;
	}

	public String lastSkip() {
		return lastSkip;
	}

	private static int findSlot(Inventory inventory, RuleSettings rule) {
		for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
			if (rule.matches(inventory.getItem(slot))) {
				return slot;
			}
		}

		return -1;
	}

	private boolean conditionsMet(RuleSettings rule, LocalPlayer player, Entity target) {
		if (!rule.targets.get().matches(target)) {
			return false;
		}

		return switch (rule.rule) {
			case AXE -> !axeShieldOnly.get() || target instanceof LivingEntity living && living.isBlocking();
			case MACE -> maceMinFall.get() == 0 || player.fallDistance >= maceMinFall.get() && !player.onGround();
			default -> true;
		};
	}

	// ----- called by MultiPlayerGameModeMixin --------------------------------------------------

	/** Just before the attack is sent: select the rule's slot if one applies. */
	public void beforeAttack(Player attacker, Entity target) {
		inProgress = null;
		if (!(attacker instanceof LocalPlayer player) || pending != null) {
			// A swap that is still waiting to be undone: never stack a second one on top.
			lastSkip = pending != null ? "previous swap not undone yet" : null;
			return;
		}

		if (skipWhileUsing.get() && player.isUsingItem()) {
			lastSkip = "using an item";
			return;
		}

		Inventory inventory = player.getInventory();
		int current = inventory.getSelectedSlot();
		long now = System.currentTimeMillis();
		lastSkip = null;
		for (RuleSettings rule : rules.values()) {
			if (!rule.enabled.get() || !conditionsMet(rule, player, target)) {
				continue;
			}

			if (rule.matches(inventory.getItem(current))) {
				lastSkip = "already holding " + rule.rule.label.toLowerCase(Locale.ROOT);
				return;
			}

			int slot = findSlot(inventory, rule);
			if (slot < 0) {
				lastSkip = "no " + rule.rule.label.toLowerCase(Locale.ROOT) + " in hotbar";
				continue;
			}

			if (rule.remainingCooldown() > 0) {
				lastSkip = rule.rule.label + " on cooldown";
				continue;
			}

			inventory.setSelectedSlot(slot);
			readyAt.put(rule.rule, now + rule.cooldown.intValue());
			inProgress = new Pending(rule.rule, current, slot, inventory.getItem(slot).getHoverName().getString(),
					target.getDisplayName().getString(), now, swapBackDelay.intValue());
			owner = player;
			return;
		}
	}

	/**
	 * Right after the attack was sent. {@code sendSlot} sends the currently selected slot to the server
	 * (vanilla's {@code ensureHasSentCarriedItem}).
	 */
	public void afterAttack(Player attacker, Runnable sendSlot) {
		Pending swap = inProgress;
		inProgress = null;
		if (swap == null || !(attacker instanceof LocalPlayer player)) {
			return;
		}

		Inventory inventory = player.getInventory();
		boolean used = inventory.getSelectedSlot() == swap.swappedSlot() && rules.get(swap.rule()).matches(inventory.getItem(swap.swappedSlot()));
		if (swap.ticksLeft() > 0) {
			pending = swap;
			last = new SwapRecord(swap.rule(), swap.item(), swap.originalSlot(), swap.swappedSlot(), swap.target(), swap.at(), used, false,
					"switching back in " + swap.ticksLeft() + " ticks");
			return;
		}

		inventory.setSelectedSlot(swap.originalSlot());
		sendSlot.run();
		boolean restored = inventory.getSelectedSlot() == swap.originalSlot();
		last = new SwapRecord(swap.rule(), swap.item(), swap.originalSlot(), swap.swappedSlot(), swap.target(), swap.at(), used, restored,
				restored ? "switched back in the same tick" : "could not switch back");
	}

	@Override
	protected void onTick() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (pending == null) {
			return;
		}

		if (player != owner || player == null) {
			// New world, respawn or disconnect: that inventory is gone, nothing to undo.
			pending = null;
			return;
		}

		Pending next = new Pending(pending.rule(), pending.originalSlot(), pending.swappedSlot(), pending.item(), pending.target(), pending.at(),
				pending.ticksLeft() - 1);
		if (next.ticksLeft() > 0) {
			pending = next;
			return;
		}

		finishPending(player);
	}

	private void finishPending(LocalPlayer player) {
		Pending swap = pending;
		pending = null;
		Inventory inventory = player.getInventory();
		boolean stillSwapped = inventory.getSelectedSlot() == swap.swappedSlot();
		if (stillSwapped) {
			// MultiPlayerGameMode.tick sends the change to the server.
			inventory.setSelectedSlot(swap.originalSlot());
		}

		last = new SwapRecord(swap.rule(), swap.item(), swap.originalSlot(), swap.swappedSlot(), swap.target(), swap.at(),
				last != null && last.usedForHit(), stillSwapped, stillSwapped ? "switched back" : "you changed slots meanwhile; left as you chose");
	}

	@Override
	protected void onDisable() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (pending != null && player == owner && player != null) {
			finishPending(player);
		}

		pending = null;
		inProgress = null;
	}

	// ----- GUI and HUD -----------------------------------------------------------------------

	public static String slotName(int slot) {
		return "slot " + (slot + 1);
	}

	public String describeLast() {
		if (last == null) {
			return "No swaps yet.";
		}

		long seconds = (System.currentTimeMillis() - last.at()) / 1000;
		return String.format(Locale.ROOT, "%s (%s) for the hit on %s, %s ago: %s, %s. Servers don't confirm slot changes; this is what your client selected and sent.",
				last.item(), slotName(last.toSlot()), last.target(), seconds < 60 ? seconds + " s" : seconds / 60 + " min",
				last.usedForHit() ? "held during the hit" : "NOT held during the hit", last.note());
	}

	@Override
	public List<Widget> createControls(UiContext ui, Runnable refresh) {
		return List.of(new AttributeSwapPanel(this));
	}

	@Override
	public HudAnchor hudAnchor() {
		return HudAnchor.BOTTOM_RIGHT;
	}

	@Override
	public void buildHud(List<HudLine> lines) {
		if (!hud.get()) {
			return;
		}

		for (RuleSettings rule : rules.values()) {
			long remaining = rule.remainingCooldown();
			if (rule.enabled.get() && remaining > 0) {
				lines.add(HudLine.of(rule.rule.label, String.format(Locale.ROOT, "%.1f s", remaining / 1000.0), Theme.WARNING));
			}
		}

		if (last != null && System.currentTimeMillis() - last.at() < 3000) {
			lines.add(HudLine.of("Swapped", last.item() + (last.restored() ? " and back" : ""), last.usedForHit() ? Theme.SUCCESS : Theme.ERROR));
		}
	}
}
