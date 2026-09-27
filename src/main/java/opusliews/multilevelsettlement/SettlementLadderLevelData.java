package opusliews.multilevelsettlement;

import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.LevelData;
import opusliews.logging.Logging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class SettlementLadderLevelData extends LevelData {
	public static final String managerKey = "opussettlementladderdata";
	private final ArrayList<SettlementLadderLink> links = new ArrayList<>();

	public static SettlementLadderLevelData get(Level surfaceLevel, boolean createNewIfNull) {
		if (surfaceLevel == null) {
			if (Logging.logEnabled) Logging.logMessage("[SettlementLadder] Cannot get ladder data for null surface level");
			return null;
		}

		LevelData existing = surfaceLevel.getLevelData(managerKey);
		if (existing instanceof SettlementLadderLevelData) return (SettlementLadderLevelData)existing;
		if (existing != null) {
			if (Logging.logEnabled) Logging.logMessage("[SettlementLadder] Level data key collision level=" + surfaceLevel.getIdentifier() + " class=" + existing.getClass().getName());
			return null;
		}
		if (!createNewIfNull) return null;

		SettlementLadderLevelData data = new SettlementLadderLevelData();
		surfaceLevel.addLevelData(managerKey, data);
		if (Logging.logEnabled) Logging.logMessage("[SettlementLadder] Created ladder level data level=" + surfaceLevel.getIdentifier());
		return data;
	}

	public synchronized List<SettlementLadderLink> getLinks(int settlementUniqueID) {
		ArrayList<SettlementLadderLink> result = new ArrayList<>();
		for (SettlementLadderLink link : links) {
			if (link.settlementUniqueID == settlementUniqueID) result.add(link);
		}
		return Collections.unmodifiableList(result);
	}

	public synchronized SettlementLadderLink findLink(int settlementUniqueID, SettlementLevelType levelType, int tileX, int tileY) {
		for (SettlementLadderLink link : links) {
			if (link.settlementUniqueID == settlementUniqueID && link.matches(levelType, tileX, tileY)) return link;
		}
		return null;
	}

	public synchronized boolean addLink(SettlementLadderLink link) {
		if (link == null) return false;
		if (findLink(link.settlementUniqueID, SettlementLevelType.SURFACE, link.surfaceTileX, link.surfaceTileY) != null) return false;
		if (findLink(link.settlementUniqueID, SettlementLevelType.CAVE, link.caveTileX, link.caveTileY) != null) return false;
		links.add(link);
		return true;
	}

	public synchronized boolean removeLink(SettlementLadderLink link) {
		return link != null && links.remove(link);
	}

	public synchronized int removeLinksAt(SettlementLevelDomain domain, SettlementLevelType levelType, int tileX, int tileY) {
		if (domain == null || levelType == null) return 0;
		int removed = 0;
		Iterator<SettlementLadderLink> iterator = links.iterator();
		while (iterator.hasNext()) {
			SettlementLadderLink link = iterator.next();
			if (link.settlementUniqueID == domain.getSettlementUniqueID() && link.matches(levelType, tileX, tileY)) {
				iterator.remove();
				removed++;
			}
		}
		return removed;
	}

	@Override
	public synchronized void addSaveData(SaveData save) {
		super.addSaveData(save);
		for (SettlementLadderLink link : links) {
			SaveData linkSave = new SaveData("SETTLEMENT_LADDER");
			linkSave.addInt("settlementUniqueID", link.settlementUniqueID);
			linkSave.addInt("surfaceTileX", link.surfaceTileX);
			linkSave.addInt("surfaceTileY", link.surfaceTileY);
			linkSave.addInt("caveTileX", link.caveTileX);
			linkSave.addInt("caveTileY", link.caveTileY);
			save.addSaveData(linkSave);
		}
	}

	@Override
	public synchronized void applyLoadData(LoadData save) {
		super.applyLoadData(save);
		links.clear();
		for (LoadData linkSave : save.getLoadDataByName("SETTLEMENT_LADDER")) {
			int settlementUniqueID = linkSave.getInt("settlementUniqueID", 0, false);
			int surfaceTileX = linkSave.getInt("surfaceTileX", 0, false);
			int surfaceTileY = linkSave.getInt("surfaceTileY", 0, false);
			int caveTileX = linkSave.getInt("caveTileX", surfaceTileX, false);
			int caveTileY = linkSave.getInt("caveTileY", surfaceTileY, false);
			if (settlementUniqueID == 0) {
				if (Logging.logEnabled) Logging.logMessage("[SettlementLadder] Ignoring saved ladder with invalid settlement ID at surface=" + surfaceTileX + "," + surfaceTileY);
				continue;
			}
			links.add(new SettlementLadderLink(settlementUniqueID, surfaceTileX, surfaceTileY, caveTileX, caveTileY));
		}
		if (Logging.logEnabled && !links.isEmpty()) Logging.logMessage("[SettlementLadder] Loaded " + links.size() + " designated ladder links level=" + level.getIdentifier());
	}
}
