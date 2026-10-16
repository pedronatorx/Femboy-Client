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
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import io.github.theamethyx.femboyclient.client.FemboyClient;
import io.github.theamethyx.femboyclient.client.combat.AttackTiming;
import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.gui.UiContext;
import io.github.theamethyx.femboyclient.client.gui.widget.AttributeSwapPanel;
import io.github.theamethyx.femboyclient.client.gui.widget.Widget;
import io.github.theamethyx.femboyclient.client.hud.HudAnchor;
import io.github.theamethyx.femboyclient.client.hud.HudLine;
import io.github.theamethyx.femboyclient.client.mixin.MultiPlayerGameModeAccessor;
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
		SPEAR("spear", "Spear reach", "Click at a target beyond your normal reach but within the spear's (up to 4.5 blocks): "
				+ "jab with the spear and switch straight back.", Items.IRON_SPEAR, 500, false),
		SPEAR_LUNGE("spear_lunge", "Spear lunge", "Hold attack with food or wind charges in hand: jab with your Lunge spear again "
				+ "and again to dash forward. Singleplayer and servers in your own network only.", Items.WIND_CHARGE, 150, false),
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
			if (rule != Rule.SPEAR_LUNGE) {
				list.add(1, targets);
			}

			return list;
		}

		/** Why the rule cannot run here, or {@code null}. */
		public String unavailableReason() {
			if (rule == Rule.SPEAR_LUNGE && !FemboyClient.get().modules().environment().testEnvironment()) {
				return Module.TEST_ENVIRONMENT_ONLY;
			}

			return null;
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

	static final Set<String> DEFAULT_LUNGE_ITEMS = Set.of("minecraft:wind_charge", "minecraft:bread", "minecraft:cooked_beef",
			"minecraft:cooked_porkchop", "minecraft:golden_carrot", "minecraft:baked_potato", "minecraft:cooked_chicken", "minecraft:apple");

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
	private final BooleanSetting lungeAnyItem;
	private final IdSetSetting lungeItems;
	private final BooleanSetting lungeRepeat;
	private Set<Item> lungeSet = Set.of();
	private int lunges;
	private Set<Item> customSet = Set.of();
	private List<IdSetSetting.Choice> itemChoices;

	private Pending inProgress;
	private Pending pending;
	private SwapRecord last;
	private String lastSkip;
	private LocalPlayer owner;

	@Override
	public Tier tier() {
		return Tier.AUTOMATION;
	}

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
		lungeItems = settings().add(new IdSetSetting("lunge_items", "Hold to lunge",
				"Items in your hand that make holding attack lunge. Items without weapon attack speed recharge in 5 ticks, "
						+ "which the server uses for the jab when you switch to the spear only for it.", DEFAULT_LUNGE_ITEMS)).panelOnly();
		lungeItems.choices(this::itemChoices);
		lungeItems.onChange(ids -> lungeSet = resolveItems(ids));
		lungeSet = resolveItems(lungeItems.get());
		lungeAnyItem = settings().add(new BooleanSetting("lunge_any_item", "Any non-weapon item",
				"Lunge while holding anything that is not a weapon, including an empty hand.", false)).panelOnly();
		lungeRepeat = settings().add(new BooleanSetting("lunge_repeat", "Repeat while held",
				"Keep lunging while attack is held, once per cooldown. Off: one lunge per click.", true)).panelOnly();
		rules.get(Rule.SPEAR_LUNGE).conditions.add(lungeItems);
		rules.get(Rule.SPEAR_LUNGE).conditions.add(lungeAnyItem);
		rules.get(Rule.SPEAR_LUNGE).conditions.add(lungeRepeat);

		rules.get(Rule.AXE).matcher = stack -> stack.is(ItemTags.AXES);
		rules.get(Rule.MACE).matcher = stack -> stack.is(Items.MACE);
		rules.get(Rule.SPEAR).matcher = stack -> stack.is(ItemTags.SPEARS) && stack.has(DataComponents.PIERCING_WEAPON);
		rules.get(Rule.SPEAR_LUNGE).matcher = stack -> stack.is(ItemTags.SPEARS) && stack.has(DataComponents.PIERCING_WEAPON) && lungeLevel(stack) > 0;
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
			// The spear parts jab instead of hitting; they run from the click (spear reach) and from the tick (lunge).
			if (rule.rule == Rule.SPEAR || rule.rule == Rule.SPEAR_LUNGE || !rule.enabled.get() || !conditionsMet(rule, player, target)) {
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

	// ----- spear: reach jab and lunge -------------------------------------------------------------

	private int lungeLevel(ItemStack stack) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return 0;
		}

		return player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.LUNGE)
				.map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, stack)).orElse(0);
	}

	/** Number of lunge jabs sent since the module started (for the panel and tests). */
	public int lunges() {
		return lunges;
	}

	/**
	 * Called by MinecraftMixin when you click attack, before vanilla handles it. When the spear-reach rule applies, jabs with
	 * the spear (switching to it and straight back) and returns {@code true} so vanilla's own click is skipped.
	 */
	public boolean onAttackClick() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		RuleSettings lunge = rules.get(Rule.SPEAR_LUNGE);
		if (player != null && lunge.enabled.get() && lunge.unavailableReason() == null && lungeTrigger(player.getMainHandItem())
				&& findSlot(player.getInventory(), lunge) >= 0) {
			// While holding a lunge item, clicks belong to the lunge (sent from the tick), not to a vanilla swing.
			return true;
		}

		RuleSettings rule = rules.get(Rule.SPEAR);
		if (player == null || client.gameMode == null || pending != null || !rule.enabled.get() || client.hitResult instanceof EntityHitResult
				|| (skipWhileUsing.get() && player.isUsingItem()) || rule.remainingCooldown() > 0) {
			return false;
		}


		Inventory inventory = player.getInventory();
		int slot = findSlot(inventory, rule);
		if (slot < 0) {
			return false;
		}

		ItemStack spear = inventory.getItem(slot);
		AttackRange range = spear.get(DataComponents.ATTACK_RANGE);
		if (range == null) {
			return false;
		}

		HitResult hit = range.getClosesetHit(player, 1.0f, entity -> PiercingWeapon.canHitEntity(player, entity) && rule.targets.get().matches(entity));
		if (!(hit instanceof EntityHitResult entityHit)) {
			return false;
		}

		jab(player, slot, rule, entityHit.getEntity().getDisplayName().getString());
		return true;
	}

	private boolean lungeTrigger(ItemStack held) {
		if (held.isEmpty()) {
			return lungeAnyItem.get();
		}

		if (lungeSet.contains(held.getItem())) {
			return true;
		}

		return lungeAnyItem.get() && !AttackTiming.isWeapon(held);
	}

	private boolean lungeKeyWasDown;

	private void tickLunge(Minecraft client, LocalPlayer player) {
		RuleSettings rule = rules.get(Rule.SPEAR_LUNGE);
		boolean down = client.options.keyAttack.isDown() && client.screen == null;
		boolean fresh = down && !lungeKeyWasDown;
		lungeKeyWasDown = down;
		if (!rule.enabled.get() || !down || (!fresh && !lungeRepeat.get()) || pending != null || client.gameMode == null) {
			return;
		}

		if (rule.unavailableReason() != null || !lungeTrigger(player.getMainHandItem()) || rule.remainingCooldown() > 0) {
			return;
		}

		if (player.isPassenger() || player.isFallFlying() || player.isInWater()) {
			lastSkip = "Lunge does not work while riding, gliding or in water";
			return;
		}

		int slot = findSlot(player.getInventory(), rule);
		if (slot < 0) {
			lastSkip = "no spear with Lunge in hotbar";
			return;
		}

		jab(player, slot, rule, "lunge");
		lunges++;
	}

	/** Switches to the spear in {@code slot}, jabs, and switches straight back in the same tick. */
	private void jab(LocalPlayer player, int slot, RuleSettings rule, String target) {
		Minecraft client = Minecraft.getInstance();
		Inventory inventory = player.getInventory();
		int original = inventory.getSelectedSlot();
		ItemStack spear = inventory.getItem(slot);
		PiercingWeapon piercing = spear.get(DataComponents.PIERCING_WEAPON);
		long now = System.currentTimeMillis();
		inventory.setSelectedSlot(slot);
		client.gameMode.piercingAttack(piercing);
		player.swing(InteractionHand.MAIN_HAND);
		inventory.setSelectedSlot(original);
		((MultiPlayerGameModeAccessor) client.gameMode).femboyclient$ensureHasSentCarriedItem();
		readyAt.put(rule.rule, now + rule.cooldown.intValue());
		last = new SwapRecord(rule.rule, spear.getHoverName().getString(), original, slot, target, now, true, inventory.getSelectedSlot() == original,
				"jabbed and switched back in the same tick");
	}

	@Override
	protected void onTick() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			tickLunge(Minecraft.getInstance(), player);
		}

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
