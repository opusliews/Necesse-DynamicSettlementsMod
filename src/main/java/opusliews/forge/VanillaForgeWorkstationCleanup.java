package opusliews.forge;

import necesse.level.gameObject.ProcessingForgeObject;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementWorkstation;
import opusliews.logging.Logging;
import opusliews.multilevelsettlement.SettlementLevelStorageManager;
import opusliews.multilevelsettlement.SettlementLevelWorkstation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Removes obsolete vanilla settlement workstation registrations for processing forges.
 * Dynamic Settlements' custom crafting-board assignments are unrelated and untouched.
 */
public final class VanillaForgeWorkstationCleanup {
    private static final Map<ServerSettlementData, Long> lastChecks = Collections.synchronizedMap(new WeakHashMap<>());

    private VanillaForgeWorkstationCleanup() {}

    public static boolean isForge(Level level, int tileX, int tileY) {
        return level != null && level.getObject(tileX, tileY) instanceof ProcessingForgeObject;
    }

    public static void tick(ServerSettlementData settlement) {
        if (settlement == null || settlement.getLevel() == null || !settlement.getLevel().isServer()) return;
        long now = settlement.getLevel().getTime();
        synchronized (lastChecks) {
            Long last = lastChecks.get(settlement);
            if (last != null && now >= last && now - last < 1000L) return;
            lastChecks.put(settlement, now);
        }
        try {
            for (SettlementWorkstation workstation : new ArrayList<>(SettlementLevelStorageManager.getWorkstations(settlement))) {
                if (workstation == null) continue;
                Level level = workstation instanceof SettlementLevelWorkstation
                        ? ((SettlementLevelWorkstation) workstation).getLevel() : settlement.getLevel();
                if (!isForge(level, workstation.tileX, workstation.tileY)) continue;
                if (level == settlement.getLevel()) {
                    settlement.storageManager.removeWorkstation(workstation.tileX, workstation.tileY, true);
                } else {
                    SettlementLevelStorageManager.removeWorkstation(settlement, level.getIdentifier(), workstation.tileX, workstation.tileY);
                }
                if (Logging.logEnabled) Logging.logMessage("[CraftingForgeJob] Removed legacy vanilla forge workstation settlement="
                        + settlement.uniqueID + " level=" + level.getIdentifier()
                        + " tile=" + workstation.tileX + "," + workstation.tileY
                        + " obsoleteRecipes=" + workstation.recipes.size());
            }
        } catch (Exception error) {
            Logging.logMessage("[CraftingForgeJob] Failed to clean obsolete vanilla forge workstations: " + error);
        }
    }
}
