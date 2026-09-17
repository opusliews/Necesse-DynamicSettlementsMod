package opusliews.logging;

public final class Logging {
	public static final boolean logEnabled = Boolean.parseBoolean(
			System.getenv().getOrDefault("DYNAMIC_SETTLEMENTS_LOGGING", "false")
	);

	private Logging() {
	}

	public static void logMessage(String log) {
		if (logEnabled) {
			System.out.println("SBLog: " + log);
		}
	}
}
