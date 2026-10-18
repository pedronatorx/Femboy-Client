package io.github.theamethyx.femboyclient.core.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class RuleEngineTest {
	private long now = 100_000;

	private final class FakeContext implements RuleEngine.Context {
		String profile = "default";
		String dimension = "overworld";
		String environment = "singleplayer";
		int fps = 120;

		@Override public String profile() { return profile; }
		@Override public String dimension() { return dimension; }
		@Override public String environment() { return environment; }
		@Override public String holding() { return "weapon"; }
		@Override public String hudLayout() { return "Default"; }
		@Override public int fps() { return fps; }
		@Override public boolean profileExists(String name) { return List.of("default", "building", "pvp").contains(name); }
		@Override public boolean layoutExists(String name) { return name.equals("Default"); }
		@Override public boolean moduleExists(String id) { return List.of("material_list", "kill_aura").contains(id); }
		@Override public boolean isVisualModule(String id) { return id.equals("material_list"); }
	}

	private static Rule rule(String name, Rule.Trigger trigger, List<Rule.Condition> conditions, Rule.Action... actions) {
		return new Rule(name, true, trigger, true, conditions, List.of(actions), 10, false);
	}

	@Test
	void aRuleRunsWhenItsTriggerFiresAndConditionsHold() {
		RuleEngine engine = new RuleEngine(() -> now);
		FakeContext context = new FakeContext();
		List<String> done = new ArrayList<>();
		engine.setRules(List.of(rule("Building HUD", Rule.Trigger.PROFILE_CHANGED, List.of(new Rule.Condition(Rule.ConditionType.PROFILE_IS, "building")),
				new Rule.Action(Rule.ActionType.ENABLE_VISUAL_MODULE, "material_list"))));

		assertTrue(engine.fire(Rule.Trigger.PROFILE_CHANGED, context, (r, a) -> { done.add(a.value()); return null; }).isEmpty(), "wrong profile");
		context.profile = "building";
		assertTrue(engine.fire(Rule.Trigger.WORLD_JOINED, context, (r, a) -> { done.add(a.value()); return null; }).isEmpty(), "wrong trigger");
		List<RuleEngine.Execution> runs = engine.fire(Rule.Trigger.PROFILE_CHANGED, context, (r, a) -> { done.add(a.value()); return null; });

		assertEquals(List.of("material_list"), done);
		assertEquals(1, runs.size());
		assertEquals("Building HUD", engine.history().getFirst().rule());
	}

	@Test
	void anyMatchesOneConditionAndCooldownsLimitRepeats() {
		RuleEngine engine = new RuleEngine(() -> now);
		FakeContext context = new FakeContext();
		Rule any = new Rule("Either", true, Rule.Trigger.DIMENSION_CHANGED, false,
				List.of(new Rule.Condition(Rule.ConditionType.DIMENSION_IS, "nether"), new Rule.Condition(Rule.ConditionType.ENVIRONMENT_IS, "singleplayer")),
				List.of(new Rule.Action(Rule.ActionType.NOTIFY, "hi")), 10, false);
		engine.setRules(List.of(any));

		assertEquals(1, engine.fire(Rule.Trigger.DIMENSION_CHANGED, context, (r, a) -> null).size());
		now += 5_000;
		assertTrue(engine.fire(Rule.Trigger.DIMENSION_CHANGED, context, (r, a) -> null).isEmpty(), "within the cooldown");
		now += 6_000;
		assertEquals(1, engine.fire(Rule.Trigger.DIMENSION_CHANGED, context, (r, a) -> null).size());
	}

	@Test
	void rulesThatTriggerEachOtherCannotLoop() {
		RuleEngine engine = new RuleEngine(() -> now);
		FakeContext context = new FakeContext();
		engine.setRules(List.of(
				rule("A", Rule.Trigger.PROFILE_CHANGED, List.of(), new Rule.Action(Rule.ActionType.SWITCH_PROFILE, "pvp")),
				rule("B", Rule.Trigger.PROFILE_CHANGED, List.of(), new Rule.Action(Rule.ActionType.SWITCH_PROFILE, "building"))));
		int[] calls = {0};
		RuleEngine.Executor executor = new RuleEngine.Executor() {
			@Override
			public String execute(Rule rule, Rule.Action action) {
				calls[0]++;
				// Switching the profile fires the trigger again, as the client does.
				engine.fire(Rule.Trigger.PROFILE_CHANGED, context, this);
				return null;
			}
		};

		engine.fire(Rule.Trigger.PROFILE_CHANGED, context, executor);
		assertEquals(2, calls[0], "each rule ran once in the chain");
		assertFalse(engine.validate(context).isEmpty(), "the contradiction and the self-trigger are reported");
	}

	@Test
	void dryRunOnlyRecords() {
		RuleEngine engine = new RuleEngine(() -> now);
		Rule dry = rule("Dry", Rule.Trigger.WORLD_JOINED, List.of(), new Rule.Action(Rule.ActionType.SWITCH_PROFILE, "pvp")).withDryRun(true);
		engine.setRules(List.of(dry));
		List<RuleEngine.Execution> runs = engine.fire(Rule.Trigger.WORLD_JOINED, new FakeContext(), (r, a) -> {
			throw new AssertionError("dry run must not act");
		});

		assertTrue(runs.getFirst().dryRun());
		assertTrue(runs.getFirst().done().getFirst().startsWith("would switch"), runs.getFirst().done().toString());
	}

	@Test
	void theRateLimitHoldsBackBursts() {
		RuleEngine engine = new RuleEngine(() -> now);
		List<Rule> many = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			many.add(rule("R" + i, Rule.Trigger.COMBAT_STARTED, List.of(), new Rule.Action(Rule.ActionType.NOTIFY, "x")));
		}

		engine.setRules(many);
		List<RuleEngine.Execution> runs = engine.fire(Rule.Trigger.COMBAT_STARTED, new FakeContext(), (r, a) -> null);
		long ran = runs.stream().filter(run -> run.problem() == null).count();
		assertEquals(RuleEngine.MAX_PER_SECOND, ran);
		assertEquals(8 - RuleEngine.MAX_PER_SECOND, runs.size() - ran);
	}

	@Test
	void validationCatchesBadValuesAndNonVisualModules() {
		RuleEngine engine = new RuleEngine(() -> now);
		engine.setRules(List.of(rule("Bad", Rule.Trigger.WORLD_JOINED,
				List.of(new Rule.Condition(Rule.ConditionType.DIMENSION_IS, "moon"), new Rule.Condition(Rule.ConditionType.FPS_BELOW, "fast")),
				new Rule.Action(Rule.ActionType.ENABLE_VISUAL_MODULE, "kill_aura"), new Rule.Action(Rule.ActionType.SWITCH_PROFILE, "missing"))));

		List<String> problems = engine.validate(new FakeContext());
		assertEquals(4, problems.size(), problems::toString);
		assertTrue(problems.stream().anyMatch(p -> p.contains("not a visual module")));
	}

	@Test
	void rulesRoundTripThroughJsonAndExports() {
		RuleEngine engine = new RuleEngine(() -> now);
		engine.setRules(RuleEngine.defaults());
		List<String> warnings = new ArrayList<>();
		List<Rule> read = RuleEngine.parseExport(engine.export(), warnings);

		assertNotNull(read);
		assertTrue(warnings.isEmpty(), warnings::toString);
		assertEquals(engine.rules(), read);
		assertNull(RuleEngine.parseExport("{}", warnings));
		List<Rule> partial = RuleEngine.fromJson(com.google.gson.JsonParser.parseString("""
				[{"name": "x", "when": "NOPE"}, {"name": "y", "when": "WORLD_JOINED", "then": [{"type": "EXPLODE"}, {"type": "NOTIFY", "value": "hi"}]}]
				"""), warnings);
		assertEquals(1, partial.size());
		assertEquals(1, partial.getFirst().actions().size());
		assertEquals(3, warnings.size(), warnings::toString);
	}

	@Test
	void aDisabledEngineOrRuleDoesNothing() {
		RuleEngine engine = new RuleEngine(() -> now);
		engine.setRules(List.of(rule("A", Rule.Trigger.WORLD_JOINED, List.of(), new Rule.Action(Rule.ActionType.NOTIFY, "x")).withEnabled(false)));
		assertTrue(engine.fire(Rule.Trigger.WORLD_JOINED, new FakeContext(), (r, a) -> null).isEmpty());
		engine.setRules(List.of(rule("A", Rule.Trigger.WORLD_JOINED, List.of(), new Rule.Action(Rule.ActionType.NOTIFY, "x"))));
		engine.setEnabled(false);
		assertTrue(engine.fire(Rule.Trigger.WORLD_JOINED, new FakeContext(), (r, a) -> null).isEmpty());
	}
}
