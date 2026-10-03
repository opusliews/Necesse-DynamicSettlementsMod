package opusliews.zones;

import necesse.engine.gameTool.GameToolManager;
import necesse.engine.util.LevelIdentifier;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementAssignWorkForm;
import necesse.level.maps.levelData.settlementData.zones.SettlementWorkZone;
import opusliews.logging.Logging;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.WeakHashMap;

public final class SettlementWorkZoneClientUI {
	private static Field zonesField;
	private static Field overrideField;
	private static Field requestedConfigField;
	private static Method updateHudMethod;
	private static boolean reflectionFailed;
	private static final WeakHashMap<SettlementAssignWorkForm, Boolean> registeredForms = new WeakHashMap<>();
	private static final WeakHashMap<SettlementAssignWorkForm, Boolean> activeForms = new WeakHashMap<>();
	private static final WeakHashMap<SettlementAssignWorkForm, String> lastLoggedState = new WeakHashMap<>();

	private SettlementWorkZoneClientUI() {
	}

	public static synchronized void registerForm(SettlementAssignWorkForm form) {
		if (form == null) return;
		registeredForms.put(form, Boolean.TRUE);
		activeForms.put(form, form.containerForm != null && form.containerForm.isCurrent(form));
		reconcileCachedWorkZones(form);
	}

	public static synchronized void setFormActive(SettlementAssignWorkForm form, boolean active) {
		if (form == null) return;
		registeredForms.put(form, Boolean.TRUE);
		activeForms.put(form, active);
		if (Logging.logEnabled) {
			Logging.logMessage("[IndependentZonesDebug] Assign Work active=" + active
					+ " settlement=" + (form.container == null ? "null" : form.container.getSettlementUniqueID()));
		}
	}

	public static synchronized void unregisterForm(SettlementAssignWorkForm form) {
		if (form == null) return;
		registeredForms.remove(form);
		activeForms.remove(form);
		lastLoggedState.remove(form);
	}

	public static synchronized boolean isFormActive(SettlementAssignWorkForm form) {
		return form != null && Boolean.TRUE.equals(activeForms.get(form));
	}

	public static synchronized void applySyncedWorkZones(
			int settlementUniqueID,
			HashSet<Integer> previousCustomZoneIDs
	) {
		SettlementAssignWorkForm[] forms = registeredForms.keySet().toArray(new SettlementAssignWorkForm[0]);
		for (SettlementAssignWorkForm form : forms) {
			if (form == null || form.container == null || form.container.getSettlementUniqueID() != settlementUniqueID) continue;
			HashMap<Integer, SettlementWorkZone> zones = getZones(form);
			if (zones == null) continue;
			HashMap<Integer, SettlementWorkZone> syncedZones = SettlementZoneClientCache.getCustomWorkZones(settlementUniqueID);
			for (Integer uniqueID : previousCustomZoneIDs) {
				if (!syncedZones.containsKey(uniqueID)) zones.remove(uniqueID);
			}
			for (Map.Entry<Integer, SettlementWorkZone> entry : syncedZones.entrySet()) zones.put(entry.getKey(), entry.getValue());
			if (isFormActive(form)) {
				refreshHud(form);
			}
			else if (Logging.logEnabled) {
				Logging.logMessage("[IndependentZonesDebug] Applied client zone sync without HUD refresh because Assign Work is inactive settlement="
						+ settlementUniqueID);
			}
		}
	}

	/**
	 * Vanilla full work-zone events replace SettlementAssignWorkForm.settlementWorkZones with
	 * the surface manager's map. Re-merge the independently synced non-surface zones immediately
	 * before every HUD rebuild so packet ordering cannot lose or resurrect cross-level zones.
	 */
	public static synchronized void reconcileCachedWorkZones(SettlementAssignWorkForm form) {
		if (form == null || form.container == null) return;
		HashMap<Integer, SettlementWorkZone> zones = getZones(form);
		if (zones == null) return;
		int settlementUniqueID = form.container.getSettlementUniqueID();
		HashMap<Integer, SettlementWorkZone> customZones = SettlementZoneClientCache.getCustomWorkZones(settlementUniqueID);
		HashSet<Integer> authoritativeCustomIDs = new HashSet<>(customZones.keySet());

		// Any cached custom ID that is no longer authoritative must not survive as a ghost surface zone.
		HashSet<Integer> knownCustomIDs = SettlementZoneClientCache.getCustomWorkZoneIDs(settlementUniqueID);
		for (Integer uniqueID : knownCustomIDs) {
			if (!authoritativeCustomIDs.contains(uniqueID)) zones.remove(uniqueID);
		}
		for (Map.Entry<Integer, SettlementWorkZone> entry : customZones.entrySet()) zones.put(entry.getKey(), entry.getValue());

		logStateIfChanged(form, zones, customZones);
	}

	public static boolean startTool(SettlementAssignWorkForm form, int zoneID) {
		if (form == null || form.client == null || form.client.getLevel() == null) return true;
		reconcileCachedWorkZones(form);
		HashMap<Integer, SettlementWorkZone> zones = getZones(form);
		if (zones == null) return false;
		GameToolManager.clearGameTools(form);
		GameToolManager.setGameTool(new LevelAwareWorkZoneTool(form, zoneID, zones), form);
		setOverrideShowZones(form, true);
		return true;
	}

	@SuppressWarnings("unchecked")
	public static HashMap<Integer, SettlementWorkZone> getZones(SettlementAssignWorkForm form) {
		if (!ensureReflection()) return null;
		try {
			return (HashMap<Integer, SettlementWorkZone>)zonesField.get(form);
		}
		catch (ReflectiveOperationException | RuntimeException error) {
			log("Could not get work-zone map", error);
			return null;
		}
	}

	public static void setOverrideShowZones(SettlementAssignWorkForm form, boolean value) {
		if (!ensureReflection()) return;
		try {
			overrideField.setBoolean(form, value);
		}
		catch (ReflectiveOperationException | RuntimeException error) {
			log("Could not update overrideShowZones", error);
		}
	}

	public static void setRequestedConfig(SettlementAssignWorkForm form, int uniqueID) {
		if (!ensureReflection()) return;
		try {
			requestedConfigField.setInt(form, uniqueID);
		}
		catch (ReflectiveOperationException | RuntimeException error) {
			log("Could not update requested work-zone config", error);
		}
	}

	public static void refreshHud(SettlementAssignWorkForm form) {
		if (!ensureReflection()) return;
		try {
			updateHudMethod.invoke(form);
		}
		catch (ReflectiveOperationException | RuntimeException error) {
			log("Could not refresh work-zone HUD", error);
		}
	}

	private static synchronized void logStateIfChanged(
			SettlementAssignWorkForm form,
			HashMap<Integer, SettlementWorkZone> mergedZones,
			HashMap<Integer, SettlementWorkZone> customZones
	) {
		if (!Logging.logEnabled || form.client == null) return;
		LevelIdentifier currentLevel = form.client.getLevel() == null ? null : form.client.getLevel().getIdentifier();
		ArrayList<String> merged = new ArrayList<>();
		for (SettlementWorkZone zone : mergedZones.values()) {
			LevelIdentifier owner = SettlementZoneClientCache.getWorkOwner(form.container.getSettlementUniqueID(), zone.getUniqueID());
			merged.add(zone.getUniqueID() + ":" + zone.getStringID() + ":owner=" + (owner == null ? "surface(default)" : owner.stringID) + ":size=" + zone.size());
		}
		Collections.sort(merged);
		ArrayList<Integer> custom = new ArrayList<>(customZones.keySet());
		Collections.sort(custom);
		String state = "level=" + (currentLevel == null ? "null" : currentLevel.stringID) + " merged=" + merged + " customIDs=" + custom;
		String previous = lastLoggedState.get(form);
		if (!state.equals(previous)) {
			lastLoggedState.put(form, state);
			Logging.logMessage("[IndependentZonesDebug] Client form state settlement=" + form.container.getSettlementUniqueID() + " " + state);
		}
	}

	private static synchronized boolean ensureReflection() {
		if (reflectionFailed) return false;
		if (zonesField != null) return true;
		try {
			zonesField = SettlementAssignWorkForm.class.getDeclaredField("settlementWorkZones");
			zonesField.setAccessible(true);
			overrideField = SettlementAssignWorkForm.class.getDeclaredField("overrideShowZones");
			overrideField.setAccessible(true);
			requestedConfigField = SettlementAssignWorkForm.class.getDeclaredField("requestedWorkZoneConfigUniqueID");
			requestedConfigField.setAccessible(true);
			updateHudMethod = SettlementAssignWorkForm.class.getDeclaredMethod("updateHudElements");
			updateHudMethod.setAccessible(true);
			return true;
		}
		catch (ReflectiveOperationException | RuntimeException error) {
			reflectionFailed = true;
			log("Could not initialize work-zone UI reflection", error);
			return false;
		}
	}

	private static void log(String message, Throwable error) {
		if (Logging.logEnabled) Logging.logMessage("[IndependentZones] " + message + ": " + error.getClass().getSimpleName() + ": " + error.getMessage());
	}
}
