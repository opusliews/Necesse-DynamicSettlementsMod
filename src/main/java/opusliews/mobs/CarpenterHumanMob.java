package opusliews.mobs;

import java.util.ArrayList;
import java.util.List;

import necesse.engine.localization.message.GameMessage;
import necesse.engine.network.server.ServerClient;
import necesse.engine.util.GameRandom;
import necesse.entity.mobs.friendly.human.humanShop.HumanShop;
import necesse.entity.mobs.friendly.human.humanShop.SellingShopItem;
import necesse.inventory.InventoryItem;

import static opusliews.DSItemRegistry.nailStringID;

public class CarpenterHumanMob extends HumanShop {
	public CarpenterHumanMob() {
		super(500, 200, "carpenter");
		this.attackCooldown = 500;
		this.attackAnimTime = 500;
		this.setSwimSpeed(1.0F);
		this.equipmentInventory.setItem(6, new InventoryItem("coppersword"));
		this.shop.addSellingItem("safetyglasses", new SellingShopItem()).setStaticPriceBasedOnHappiness(75, 150, 20);
		this.shop.addSellingItem("carpentershirt", new SellingShopItem()).setStaticPriceBasedOnHappiness(75, 150, 20);
		this.shop.addSellingItem("carpenterboots", new SellingShopItem()).setStaticPriceBasedOnHappiness(75, 150, 20);
	}

	public List<InventoryItem> getRecruitItems(ServerClient client) {
		GameRandom random = new GameRandom(this.getSettlerSeed());

		ArrayList<InventoryItem> items = new ArrayList<>();

		items.add(new InventoryItem("coin", random.getIntBetween(250, 400)));

		if (random.getIntBetween(0, 9) < 5) {
			int plankRandom = random.getIntBetween(0, 3);
			String plankID;
			switch (plankRandom) {
				case 0:
					plankID = "oakplank";
					break;
				case 1:
					plankID = "spruceplank";
					break;
				case 2:
					plankID = "birchplank";
					break;
				case 3:
				default:
					plankID = "palmplank";
			}
			items.add(new InventoryItem(plankID, random.getIntBetween(20, 50)));
		}

		if (random.getIntBetween(0, 9) < 5) {
			items.add(new InventoryItem(nailStringID, random.getIntBetween(25, 50)));
		}

		return items;
	}

	@Override
	protected ArrayList<GameMessage> getMessages(ServerClient client) {
		return this.getLocalMessages("carpentertalk", 5);
	}
}
