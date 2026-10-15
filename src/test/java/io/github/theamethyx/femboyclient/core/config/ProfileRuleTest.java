package io.github.theamethyx.femboyclient.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.theamethyx.femboyclient.core.config.ProfileRule.Dimension;
import io.github.theamethyx.femboyclient.core.config.ProfileRule.Trigger;

class ProfileRuleTest {
	private final List<ProfileRule> rules = List.of(
			new ProfileRule(Trigger.GLIDING, Dimension.ANY, "elytra"),
			new ProfileRule(Trigger.MACE, Dimension.NETHER, "mace_nether"),
			new ProfileRule(Trigger.MACE, Dimension.ANY, "mace"),
			new ProfileRule(Trigger.SWORD, Dimension.ANY, "sword"));

	@Test
	void firstMatchingRuleWins() {
		assertEquals("sword", ProfileRule.select(rules, Trigger.SWORD, false, Dimension.OVERWORLD));
		assertEquals("mace_nether", ProfileRule.select(rules, Trigger.MACE, false, Dimension.NETHER));
		assertEquals("mace", ProfileRule.select(rules, Trigger.MACE, false, Dimension.END));
		assertEquals("elytra", ProfileRule.select(rules, Trigger.SWORD, true, Dimension.OVERWORLD), "gliding rule is listed first");
	}

	@Test
	void noMatchKeepsTheCurrentProfile() {
		assertNull(ProfileRule.select(rules, Trigger.BOW, false, Dimension.OVERWORLD));
		assertNull(ProfileRule.select(rules, null, false, Dimension.OVERWORLD), "unclassified item");
		assertEquals("mace", ProfileRule.select(rules, Trigger.MACE, false, null), "custom dimensions only match 'any'");
	}

	@Test
	void profileNamesAreValidated() {
		assertThrows(IllegalArgumentException.class, () -> new ProfileRule(Trigger.AXE, Dimension.ANY, "../evil"));
	}
}
