package io.github.theamethyx.femboyclient.core.module;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * Whether an address belongs to your own machine or a private network: loopback, LAN (10/8, 172.16/12,
 * 192.168/16), link-local, the shared range VPNs such as Tailscale use (100.64/10) and IPv6 unique local
 * addresses (fc00::/7). Test-environment modules run only in singleplayer or on servers at such addresses.
 */
public final class PrivateNetwork {
	private PrivateNetwork() {
	}

	public static boolean isPrivate(InetAddress address) {
		if (address == null) {
			return false;
		}

		if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
			return true;
		}

		byte[] bytes = address.getAddress();
		if (address instanceof Inet4Address) {
			int first = bytes[0] & 0xFF;
			int second = bytes[1] & 0xFF;
			return first == 100 && second >= 64 && second <= 127;
		}

		if (address instanceof Inet6Address) {
			return (bytes[0] & 0xFE) == 0xFC;
		}

		return false;
	}
}
