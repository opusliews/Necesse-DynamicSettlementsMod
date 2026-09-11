package opus.breaching;

public final class ZombieBreachingDamageLimiter {
	private static final ThreadLocal<Integer> bypassDepth = new ThreadLocal<>();

	private ZombieBreachingDamageLimiter() {
	}

	public static void beginBypass() {
		Integer depth = bypassDepth.get();
		bypassDepth.set(depth == null ? 1 : depth + 1);
	}

	public static void endBypass() {
		Integer depth = bypassDepth.get();
		if (depth == null || depth <= 1) {
			bypassDepth.remove();
		}
		else {
			bypassDepth.set(depth - 1);
		}
	}

	public static boolean isBypassing() {
		Integer depth = bypassDepth.get();
		return depth != null && depth > 0;
	}
}
