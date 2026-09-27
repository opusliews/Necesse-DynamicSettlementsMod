package opusliews.multilevelsettlement;

import necesse.engine.registries.SettlerThoughtRegistry;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.level.gameObject.GameObject;
import necesse.level.gameObject.HappinessObject;
import necesse.level.gameObject.furniture.SettlerBedObject;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.LevelSettler;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementBed;
import necesse.level.maps.levelData.settlementData.SettlementRoom;
import necesse.level.maps.levelData.settlementData.settler.SettlerThoughtsList;
import necesse.level.maps.levelData.settlementData.settler.thoughts.SettlerThought;

public class SettlementCaveBed extends SettlementBed {
	private final Level caveLevel;
	private final SettlementCaveRoom caveRoom;

	public SettlementCaveBed(ServerSettlementData data, Level caveLevel, SettlementCaveRoom caveRoom, int tileX, int tileY) {
		super(data, tileX, tileY);
		this.caveLevel = caveLevel;
		this.caveRoom = caveRoom;
	}

	public Level getBedLevel() {
		return caveLevel;
	}

	@Override
	public boolean isValidBed() {
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(data);
		if (domain == null || !domain.isTileWithinBounds(caveLevel.getIdentifier(), tileX, tileY)) return false;
		GameObject object = caveLevel.getObject(tileX, tileY);
		return object instanceof SettlerBedObject && ((SettlerBedObject)object).isMasterBedObject(caveLevel, tileX, tileY);
	}

	@Override
	public SettlementRoom getRoom() {
		return caveRoom;
	}

	@Override
	public SettlerThoughtsList getSettlerThoughts() {
		SettlementRoom room = getRoom();
		if (room != null) {
			SettlerThoughtsList thoughts = room.getBaseThoughts();
			LevelSettler settler = getSettler();
			if (settler != null) {
				HappinessObject happinessObject = room.getHappinessObjectForSettler(settler);
				if (happinessObject != null) {
					thoughts = new SettlerThoughtsList(thoughts);
					thoughts.addThought("happinessobject_" + ((GameObject)happinessObject).getStringID());
				}
			}
			return thoughts;
		}
		return new SettlerThoughtsList(new SettlerThought[]{SettlerThoughtRegistry.bedOutsideThought});
	}

	@Override
	public int getHappinessScore() {
		SettlementRoom room = getRoom();
		return room == null ? SettlerThoughtRegistry.bedOutsideThought.getStackedHappinessModifier((HumanMob)null, 1) : room.getHappinessScore();
	}

	void setAssignedSettler(LevelSettler settler) {
		this.settler = settler;
		this.isLocked = false;
	}
}
