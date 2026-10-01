package opusliews.object;

import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.level.maps.Level;
import opusliews.mobs.CarpenterHumanMob;

import opusliews.network.PacketCraftingStationSound;

public class CarpentersBenchObjectEntity extends DynamicCraftingStationObjectEntity {
	public static final String TYPE = "dynamiccarpentersbench";

	public CarpentersBenchObjectEntity(Level level, int tileX, int tileY) {
		super(level, TYPE, tileX, tileY);
	}

	@Override
	public String getTaskBoardTextureKey() {
		return "carpenter";
	}

	@Override
	public boolean supportsSettlerCraftingTasks() {
		return true;
	}

	@Override
	public boolean canSettlerPerformCrafting(HumanMob worker) {
		return worker instanceof CarpenterHumanMob;
	}

	@Override
	public void playSettlerCraftingWorkEffect() {
		if (!getLevel().isServer() || getLevel().getServer() == null) return;
		getLevel().getServer().network.sendToClientsWithTile(
				new PacketCraftingStationSound(tileX, tileY, PacketCraftingStationSound.CARPENTER),
				getLevel(),
				tileX,
				tileY
		);
	}
}
