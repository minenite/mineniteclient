package net.minenite.client.gun;

/** Optional Iris: Complementary owns world lighting when a pack is active. */
public final class ShaderHooks {
	private static Boolean cached;
	private static long lastCheckMs;

	private ShaderHooks() {
	}

	public static boolean packInUse() {
		long now = System.currentTimeMillis();
		if (cached != null && now - lastCheckMs < 1500L) {
			return cached;
		}
		lastCheckMs = now;
		try {
			Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			Object api = apiClass.getMethod("getInstance").invoke(null);
			cached = (Boolean) apiClass.getMethod("isShaderPackInUse").invoke(api);
		} catch (Throwable ignored) {
			cached = false;
		}
		return cached;
	}
}
