package opusliews.object;

import necesse.engine.localization.Localization;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.entity.mobs.friendly.human.humanShop.BlacksmithHumanMob;
import necesse.level.maps.Level;
import opusliews.network.PacketAnvilCraftingSound;

public class AnvilObjectEntity extends DynamicCraftingStationObjectEntity {
	public static final String TYPE = "dynamicanvil";
	public static final int STORAGE_LINK_RADIUS = DynamicCraftingStationObjectEntity.STORAGE_LINK_RADIUS;

	public AnvilObjectEntity(Level level, int tileX, int tileY) {
		super(level, TYPE, tileX, tileY);
	}

	@Override
	public boolean supportsForgeLinks() {
		return true;
	}

	@Override
	public String getTaskBoardTextureKey() {
		return "anvil";
	}

	@Override
	public boolean supportsSettlerCraftingTasks() {
		return true;
	}

	@Override
	public boolean canSettlerPerformCrafting(HumanMob worker) {
		return worker instanceof BlacksmithHumanMob;
	}

	@Override
	public String getSettlerCraftingActivityText() {
		return Localization.translate("activities", "anvilcrafting");
	}

	@Override
	public String getSettlerCraftingWorkItemStringID() {
		return "constructionhammer";
	}

	@Override
	public void playSettlerCraftingWorkEffect() {
		if (!getLevel().isServer() || getLevel().getServer() == null) return;
		getLevel().getServer().network.sendToClientsWithTile(
				new PacketAnvilCraftingSound(tileX, tileY),
				getLevel(),
				tileX,
				tileY
		);
	}
}
