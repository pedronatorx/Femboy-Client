package io.github.theamethyx.femboyclient.client.modules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;

import io.github.theamethyx.femboyclient.client.gui.Theme;
import io.github.theamethyx.femboyclient.client.hud.HudAnchor;
import io.github.theamethyx.femboyclient.client.hud.HudLine;
import io.github.theamethyx.femboyclient.client.module.ClientHooks;
import io.github.theamethyx.femboyclient.core.module.Module;
import io.github.theamethyx.femboyclient.core.module.ModuleCategory;
import io.github.theamethyx.femboyclient.core.setting.BooleanSetting;
import io.github.theamethyx.femboyclient.core.setting.EnumSetting;
import io.github.theamethyx.femboyclient.core.setting.NumberSetting;

/** Your active potion effects with level and remaining time; harmful ones in red, expiring ones highlighted. */
public final class EffectsModule extends Module implements ClientHooks.HudBlock {
	private static final String[] ROMAN = {"", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

	private final EnumSetting<HudAnchor> anchor = settings().add(new EnumSetting<>("anchor", "Position",
			"Screen corner for this block.", HudAnchor.TOP_RIGHT));
	private final NumberSetting expiring = settings().add(new NumberSetting("expiring", "Expiring warning",
			"Effects with this little time left are highlighted.", 10, 0, 60, 1, "s"));
	private final BooleanSetting harmful = settings().add(new BooleanSetting("harmful", "Show harmful effects",
			"Also list effects such as Poison and Weakness (in red).", true));

	@Override
	public Tier tier() {
		return Tier.ASSIST;
	}

	public EffectsModule() {
		super("effects", "Effects", "Your active potion effects with level and remaining time; expiring and harmful ones highlighted.",
				ModuleCategory.INVENTORY, false, NO_KEY);
	}

	@Override
	public HudAnchor hudAnchor() {
		return anchor.get();
	}

	@Override
	public void buildHud(List<HudLine> lines) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}

		List<MobEffectInstance> effects = new ArrayList<>(player.getActiveEffects());
		effects.sort(Comparator.comparingInt(e -> e.isInfiniteDuration() ? Integer.MAX_VALUE : e.getDuration()));
		for (MobEffectInstance effect : effects) {
			boolean bad = effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL;
			if (bad && !harmful.get()) {
				continue;
			}

			int amplifier = effect.getAmplifier();
			String name = effect.getEffect().value().getDisplayName().getString()
					+ (amplifier > 0 ? " " + (amplifier < ROMAN.length ? ROMAN[amplifier] : String.valueOf(amplifier + 1)) : "");
			String time;
			boolean ending = false;
			if (effect.isInfiniteDuration()) {
				time = "∞";
			} else {
				int seconds = effect.getDuration() / 20;
				time = String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
				ending = seconds <= expiring.get();
			}

			lines.add(HudLine.of(name, time, bad ? Theme.ERROR : ending ? Theme.WARNING : Theme.TEXT));
		}
	}
}
