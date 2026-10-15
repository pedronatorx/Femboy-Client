package io.github.theamethyx.femboyclient.core.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.jupiter.api.Test;

import io.github.theamethyx.femboyclient.core.diag.DiagnosticLogger;
import io.github.theamethyx.femboyclient.core.diag.PerformanceMonitor;

class PrivateNetworkTest {
	private static boolean isPrivate(String literal) throws UnknownHostException {
		return PrivateNetwork.isPrivate(InetAddress.getByName(literal));
	}

	@Test
	void ownMachineLanAndVpnAddressesArePrivate() throws UnknownHostException {
		for (String address : new String[] {"127.0.0.1", "127.5.4.3", "::1", "10.0.0.5", "172.16.0.1", "172.31.255.254", "192.168.1.20",
				"169.254.10.1", "100.64.0.1", "100.101.102.103", "100.127.255.254", "fd7a:115c:a1e0::1", "fe80::1"}) {
			assertTrue(isPrivate(address), address);
		}
	}

	@Test
	void publicAddressesAreNot() throws UnknownHostException {
		for (String address : new String[] {"8.8.8.8", "172.32.0.1", "172.15.255.255", "100.63.255.255", "100.128.0.1", "192.169.0.1",
				"11.0.0.1", "2001:4860:4860::8888", "1.1.1.1"}) {
			assertFalse(isPrivate(address), address);
		}

		assertFalse(PrivateNetwork.isPrivate(null));
	}

	static final class AutomationModule extends Module {
		AutomationModule() {
			super("automation_test", "Automation Test", "", ModuleCategory.AUTOMATION, false, Module.NO_KEY);
		}

		@Override
		public boolean testEnvironmentOnly() {
			return true;
		}
	}

	@Test
	void testEnvironmentModulesRunOnlyInTestEnvironments() {
		boolean[] test = {false};
		EnvironmentPolicy environment = new EnvironmentPolicy() {
			@Override
			public boolean inWorld() {
				return true;
			}

			@Override
			public String describe() {
				return "server";
			}

			@Override
			public boolean testEnvironment() {
				return test[0];
			}
		};
		ModuleManager manager = new ModuleManager(new DiagnosticLogger(), new PerformanceMonitor(), environment);
		AutomationModule module = manager.register(new AutomationModule());

		manager.setEnabled(module, true);
		assertTrue(module.isEnabled());
		assertFalse(module.isRunning(), "enabled but not running on a public server");
		assertEquals(Module.TEST_ENVIRONMENT_ONLY, manager.availability(module).reason());

		test[0] = true;
		manager.tick();
		assertTrue(module.isRunning(), "runs once connected to a test environment");

		test[0] = false;
		manager.tick();
		assertFalse(module.isRunning(), "stops when leaving it");
	}
}
