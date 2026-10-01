package opusliews.object;

import necesse.entity.mobs.friendly.human.GuardHumanMob;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.level.maps.Level;

import opusliews.network.PacketCraftingStationSound;

public class WorkstationObjectEntity extends DynamicCraftingStationObjectEntity {
	public static final String TYPE = "dynamicworkstation";

	public WorkstationObjectEntity(Level level, int tileX, int tileY) {
		super(level, TYPE, tileX, tileY);
	}

	@Override
	public String getTaskBoardTextureKey() {
		return "workstation";
	}

	@Override
	public boolean supportsSettlerCraftingTasks() {
		return true;
	}

	@Override
	public boolean canSettlerPerformCrafting(HumanMob worker) {
		return worker != null && !(worker instanceof GuardHumanMob);
	}

	@Override
	public void playSettlerCraftingWorkEffect() {
		if (!getLevel().isServer() || getLevel().getServer() == null) return;
		getLevel().getServer().network.sendToClientsWithTile(
				new PacketCraftingStationSound(tileX, tileY, PacketCraftingStationSound.WORKSTATION),
				getLevel(),
				tileX,
				tileY
		);
	}
}
