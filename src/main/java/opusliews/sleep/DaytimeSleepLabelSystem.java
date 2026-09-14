package opusliews.sleep;

import necesse.engine.localization.Localization;
import necesse.engine.world.WorldEntity;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.forms.components.FormLabel;
import necesse.gfx.forms.presets.containerComponent.SleepContainerForm;
import opusliews.logging.Logging;

public final class DaytimeSleepLabelSystem {
	private static final ThreadLocal<FormLabel> activeLabel = new ThreadLocal<>();
	private static long lastDebugLogNanos;

	private DaytimeSleepLabelSystem() {
	}

	public static void beginDraw(SleepContainerForm form, PlayerMob perspective) {
		activeLabel.remove();

		if (form == null || perspective == null) {
			logState("draw reached with missing form/perspective");
			return;
		}

		WorldEntity worldEntity = perspective.getWorldEntity();
		if (worldEntity == null) {
			logState("draw reached with no WorldEntity");
			return;
		}

		if (!worldEntity.isNight()) {
			activeLabel.set(form.label);
			logState("daytime draw armed, isSleeping=" + worldEntity.isSleeping());
		}
		else {
			logState("nighttime draw, leaving vanilla waiting text");
		}
	}

	public static void beforeLabelDraw(FormLabel label) {
		FormLabel target = activeLabel.get();
		if (target == null || label != target) {
			return;
		}

		label.setText(Localization.translate("ui", "staringattheceiling"));
		logState("replaced sleep header immediately before FormLabel.draw");
	}

	public static void endDraw() {
		activeLabel.remove();
	}

	private static void logState(String message) {
		if (!Logging.logEnabled) {
			return;
		}

		long now = System.nanoTime();
		if (now - lastDebugLogNanos < 1_000_000_000L) {
			return;
		}

		lastDebugLogNanos = now;
		Logging.logMessage("DaytimeSleepLabel: " + message);
	}
}
