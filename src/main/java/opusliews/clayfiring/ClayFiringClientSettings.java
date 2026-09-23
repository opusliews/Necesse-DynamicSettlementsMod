package opusliews.clayfiring;

import java.util.LinkedHashMap;
import java.util.Map;
import opusliews.forms.ClayFiringSettingsForm;

public final class ClayFiringClientSettings {
	public static final LinkedHashMap<String, Integer> targets = new LinkedHashMap<>();
	public static boolean repeatForever;
	public static ClayFiringSettingsForm openForm;

	private ClayFiringClientSettings() {
	}

	public static void apply(Map<String, Integer> newTargets, boolean repeat) {
		targets.clear();
		if (newTargets != null) {
			for (Map.Entry<String, Integer> entry : newTargets.entrySet()) {
				targets.put(entry.getKey(), Math.max(0, entry.getValue() == null ? 0 : entry.getValue()));
			}
		}
		repeatForever = repeat;
		if (openForm != null && !openForm.isDisposed()) openForm.applySettings(targets, repeatForever);
	}
}
