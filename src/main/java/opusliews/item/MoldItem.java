package opusliews.item;

import necesse.engine.localization.Localization;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.network.gameNetworkData.GNDItem;
import necesse.engine.network.gameNetworkData.GNDItemEnchantment;
import necesse.engine.util.GameBlackboard;
import necesse.engine.util.GameRandom;
import necesse.engine.util.GameUtils;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.gameTexture.GameSprite;
import necesse.gfx.gameTexture.GameTexture;
import necesse.gfx.gameTooltips.ListGameTooltips;
import necesse.inventory.InventoryItem;
import necesse.inventory.enchants.Enchantable;
import necesse.inventory.enchants.ItemEnchantment;
import necesse.inventory.item.Item;
import necesse.inventory.item.miscItem.AscendedShardItem;
import necesse.engine.registries.EnchantmentRegistry;
import opusliews.DSEnchantmentRegistry;
import opusliews.durability.ItemDurabilitySystem;
import opusliews.logging.Logging;

import java.util.Collections;
import java.util.Set;

public class MoldItem extends FiredClayMatItem implements Enchantable {
	public static final int wizardEnchantPrice = 5000;
	private static final int wizardPreMultiplierPrice = 7693;
	private static final String enchantmentGndKey = "enchantment";

	private GameTexture[] shineFrames;

	public MoldItem(int stackSize, Item.Rarity rarity) {
		super(stackSize, rarity);
	}

	@Override
	protected void loadItemTextures() {
		super.loadItemTextures();
		shineFrames = AscendedShardItem.addShine(itemTexture);
	}

	@Override
	public GameSprite getItemSprite(InventoryItem item, PlayerMob perspective) {
		if (!hasUnbreaking(item) || shineFrames == null || shineFrames.length == 0) {
			return super.getItemSprite(item, perspective);
		}

		long time = perspective == null ? System.currentTimeMillis() : perspective.getLocalTime();
		int frame = GameUtils.getAnim(time, shineFrames.length * 2, shineFrames.length * 250);
		return frame >= shineFrames.length ? new GameSprite(itemTexture) : new GameSprite(shineFrames[frame]);
	}

	@Override
	public boolean isEnchantable(InventoryItem item) {
		return item != null
				&& item.getAmount() == 1
				&& ItemDurabilitySystem.isFullDurability(item)
				&& getEnchantmentID(item) <= 0;
	}

	@Override
	public String getIsEnchantableError(InventoryItem item) {
		if (item == null) return null;
		if (item.getAmount() != 1) return Localization.translate("itemtooltip", "moldenchantoneatatime");
		if (!ItemDurabilitySystem.isFullDurability(item)) return Localization.translate("itemtooltip", "moldenchantfullhealth");
		if (getEnchantmentID(item) > 0) return Localization.translate("itemtooltip", "moldenchantalreadyenchanted");
		return null;
	}

	@Override
	public void setEnchantment(InventoryItem item, int enchantment) {
		if (item == null) return;
		if (enchantment != DSEnchantmentRegistry.unbreakingID) {
			Logging.logMessage("[MoldEnchant] Rejected invalid enchantment item=" + item.item.getStringID() + " enchantmentID=" + enchantment);
			return;
		}
		if (item.getAmount() != 1 || !ItemDurabilitySystem.isFullDurability(item)) {
			Logging.logMessage("[MoldEnchant] Rejected enchantment item=" + item.item.getStringID()
					+ " amount=" + item.getAmount()
					+ " durability=" + ItemDurabilitySystem.getDurability(item) + "/" + ItemDurabilitySystem.getMaxDurability(item));
			return;
		}

		item.getGndData().setItem(enchantmentGndKey, new GNDItemEnchantment(enchantment));
		Logging.logMessage("[MoldEnchant] Applied Unbreaking item=" + item.item.getStringID());
	}

	@Override
	public int getEnchantmentID(InventoryItem item) {
		if (item == null) return -1;
		GNDItem enchantment = item.getGndData().getItem(enchantmentGndKey);
		GNDItemEnchantment enchantmentItem = GNDItemEnchantment.convertEnchantmentID(enchantment);
		item.getGndData().setItem(enchantmentGndKey, enchantmentItem);
		return enchantmentItem.getRegistryID();
	}

	@Override
	public void clearEnchantment(InventoryItem item) {
		if (item != null) item.getGndData().setItem(enchantmentGndKey, (GNDItem)null);
	}

	@Override
	public ItemEnchantment getEnchantment(InventoryItem item) {
		ItemEnchantment noEnchant = EnchantmentRegistry.getEnchantment(0);
		return EnchantmentRegistry.getEnchantment(getEnchantmentID(item), ItemEnchantment.class, noEnchant);
	}

	@Override
	public boolean isValidEnchantment(InventoryItem item, ItemEnchantment enchantment) {
		return enchantment != null && enchantment.getID() == DSEnchantmentRegistry.unbreakingID;
	}

	@Override
	public Set<Integer> getValidEnchantmentIDs(InventoryItem item) {
		return Collections.singleton(DSEnchantmentRegistry.unbreakingID);
	}

	@Override
	public ItemEnchantment getRandomEnchantment(GameRandom random, InventoryItem item) {
		return EnchantmentRegistry.getEnchantment(DSEnchantmentRegistry.unbreakingID);
	}

	@Override
	public int getEnchantCost(InventoryItem item) {
		return wizardEnchantPrice;
	}

	@Override
	public int getRandomEnchantCost(InventoryItem item, GameRandom random, int qualityOfLifeLevel, int happinessLevel) {
		// MageContainer applies a final 0.65 multiplier. 7693 becomes exactly 5000 after
		// that current vanilla calculation, deliberately bypassing happiness/QoL variance.
		return wizardPreMultiplierPrice;
	}

	@Override
	public GameMessage getLocalization(InventoryItem item) {
		ItemEnchantment enchantment = getEnchantment(item);
		if (enchantment != null && enchantment.getID() == DSEnchantmentRegistry.unbreakingID) {
			return new LocalMessage("enchantment", "format", "enchantment", enchantment.getLocalization(), "item", super.getLocalization(item));
		}
		return super.getLocalization(item);
	}

	@Override
	public ListGameTooltips getTooltips(InventoryItem item, PlayerMob perspective, GameBlackboard blackboard) {
		ListGameTooltips tooltips = super.getTooltips(item, perspective, blackboard);
		if (hasUnbreaking(item)) {
			tooltips.add(Localization.translate("itemtooltip", "moldunbreakingtip"));
		}
		return tooltips;
	}

	public static boolean hasUnbreaking(InventoryItem item) {
		if (item == null || !(item.item instanceof MoldItem)) return false;
		return ((MoldItem)item.item).getEnchantmentID(item) == DSEnchantmentRegistry.unbreakingID;
	}
}
