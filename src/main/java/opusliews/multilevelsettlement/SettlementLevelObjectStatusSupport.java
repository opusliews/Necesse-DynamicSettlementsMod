package opusliews.multilevelsettlement;

import necesse.engine.network.PacketWriter;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.entity.objectEntity.interfaces.OEInventory;
import necesse.level.maps.LevelObject;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementWorkstationObject;

/** Bridges vanilla settlement-aware object containers to a non-canonical settlement level. */
public final class SettlementLevelObjectStatusSupport {
	private SettlementLevelObjectStatusSupport() {
	}

	public static ServerSettlementData resolveSettlement(Level level, int tileX, int tileY) {
		if (level == null || !level.isServer() || level.getServer() == null) return null;
		SettlementLevelDomain domain = SettlementMultiLevelSystem.findDomain(level.getServer(), level.getIdentifier(), tileX, tileY);
		return domain == null ? null : domain.getSettlement();
	}

	/**
	 * Writes the vanilla SettlementContainerObjectStatusManager payload for a logical
	 * settlement level. Returns true only when the current level is a non-canonical
	 * level handled by the multi-level settlement system.
	 */
	public static boolean writeCustomContent(ServerSettlementData settlement, Level level, int tileX, int tileY, PacketWriter writer) {
		if (level == null || writer == null) return false;
		if (settlement == null) settlement = resolveSettlement(level, tileX, tileY);
		if (settlement == null || settlement.getLevel() == null) return false;
		if (level.getIdentifier().equals(settlement.getLevel().getIdentifier())) return false;

		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (domain == null || domain.getLevelType(level.getIdentifier()) == null || !domain.isTileWithinBounds(level.getIdentifier(), tileX, tileY)) return false;

		LevelObject levelObject = level.getLevelObject(tileX, tileY);
		LevelObject master = (LevelObject)levelObject.getMasterLevelObject().orElse(null);
		if (master == null) {
			writer.putNextBoolean(false);
			writer.putNextBoolean(false);
			return true;
		}

		writer.putNextBoolean(true);
		writer.putNextInt(master.tileX);
		writer.putNextInt(master.tileY);

		ObjectEntity objectEntity = level.entityManager.getObjectEntity(master.tileX, master.tileY);
		if (objectEntity instanceof OEInventory) {
			OEInventory inventory = (OEInventory)objectEntity;
			if (inventory.getSettlementStorage() == null) {
				writer.putNextBoolean(false);
			}
			else {
				writer.putNextBoolean(true);
				writer.putNextBoolean(SettlementLevelStorageManager.getStorage(settlement, level.getIdentifier(), master.tileX, master.tileY) != null);
			}
		}
		else {
			writer.putNextBoolean(false);
		}

		if (master.object instanceof SettlementWorkstationObject) {
			writer.putNextBoolean(true);
			writer.putNextBoolean(SettlementLevelStorageManager.getWorkstation(settlement, level.getIdentifier(), master.tileX, master.tileY) != null);
		}
		else {
			writer.putNextBoolean(false);
		}
		return true;
	}
}
