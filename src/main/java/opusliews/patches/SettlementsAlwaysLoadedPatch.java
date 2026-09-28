package opusliews.patches;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.world.worldData.SettlementsWorldData;
import net.bytebuddy.asm.Advice;
import opusliews.logging.Logging;

/**
 * Ensures player-owned settlements are instantiated on server startup even when no
 * player starts near them. Once instantiated, the existing ensureRegionsLoaded
 * patches keep their surface and cave settlement regions resident and ticking.
 */
@ModMethodPatch(target = SettlementsWorldData.class, name = "tick", arguments = {})
public class SettlementsAlwaysLoadedPatch {
	private static final Set<SettlementsWorldData> initialized = Collections.newSetFromMap(new WeakHashMap<>());

	@Advice.OnMethodEnter
	public static void onEnter(@Advice.This SettlementsWorldData settlements) {
		ensureOwnedSettlementsLoaded(settlements);
	}

	public static void ensureOwnedSettlementsLoaded(SettlementsWorldData settlements) {
		if (settlements == null || !settlements.isServer()) return;
		synchronized (initialized) {
			if (!initialized.add(settlements)) return;
		}

		try {
			settlements.streamSettlements()
					.filter(data -> data != null && data.getOwnerAuth() != -1L)
					.mapToInt(data -> data.uniqueID)
					.forEach(uniqueID -> {
						if (settlements.getOrLoadServerData(uniqueID) != null && Logging.logEnabled) {
							Logging.logMessage("[MultiLevelSettlement] Startup-loaded owned settlement=" + uniqueID);
						}
					});
		}
		catch (Throwable error) {
			synchronized (initialized) {
				initialized.remove(settlements);
			}
			if (Logging.logEnabled) Logging.logMessage("[MultiLevelSettlement] Failed startup-loading owned settlements error=" + error.getClass().getSimpleName() + ": " + error.getMessage());
		}
	}
}
