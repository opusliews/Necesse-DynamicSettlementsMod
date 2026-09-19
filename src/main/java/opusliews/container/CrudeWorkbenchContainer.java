package opusliews.container;

import java.util.Collection;
import java.util.Iterator;
import necesse.engine.GameLog;
import necesse.engine.GlobalData;
import necesse.engine.journal.listeners.CraftedRecipeJournalChallengeListener;
import necesse.engine.network.NetworkClient;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.registries.JournalChallengeRegistry;
import necesse.engine.network.server.ServerClient;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.PlayerTempInventory;
import necesse.inventory.container.object.CraftingStationContainer;
import necesse.inventory.container.settlement.events.SettlementDataEvent;
import necesse.inventory.container.slots.ExtractOnlyContainerSlot;
import necesse.inventory.recipe.ContainerRecipeCraftedEvent;
import necesse.inventory.recipe.Recipe;
import necesse.level.gameObject.TreeStumpObject;
import necesse.level.maps.Level;
import necesse.level.maps.LevelObject;
import necesse.level.maps.levelData.settlementData.settler.romancePersonalities.PlayerRomanceManager;
import opusliews.earlygame.CrudeWorkbenchFeature;
import opusliews.network.PacketCrudeWorkbenchOutput;
import opusliews.logging.Logging;

public class CrudeWorkbenchContainer extends CraftingStationContainer {
	public static final long CRAFT_TIME_MS = 2000L;

	public final PlayerTempInventory outputInventory;
	public final int OUTPUT_SLOT;

	private boolean crafting;
	private int craftingRecipeID = -1;
	private int craftingRecipeHash;
	private long craftingStartTime;

	public CrudeWorkbenchContainer(NetworkClient client, int uniqueSeed, SettlementDataEvent settlement, LevelObject stump, PacketReader reader) {
		super(client, uniqueSeed, settlement, makeVirtualStation(stump), reader);

		Packet tempInventoryContent = reader.getNextContentPacket();
		outputInventory = client.playerMob.getInv().applyTempInventoryPacket(tempInventoryContent, (player, size, invID) -> new PlayerTempInventory(player, size, invID) {
			@Override
			public boolean shouldDispose() {
				return CrudeWorkbenchContainer.this.isClosed();
			}
		});
		outputInventory.filter = (slot, item) -> item == null;

		Logging.logMessage("[CrudeWorkbench] " + (client.isServer() ? "SERVER" : "CLIENT")
				+ " opened output inventory id=" + outputInventory.getInventoryID()
				+ " size=" + outputInventory.getSize());

		OUTPUT_SLOT = addSlot(new ExtractOnlyContainerSlot(outputInventory, 0));
		addQuickTransferOption(OUTPUT_SLOT, OUTPUT_SLOT, CLIENT_HOTBAR_START, CLIENT_HOTBAR_END);
		addQuickTransferOption(OUTPUT_SLOT, OUTPUT_SLOT, CLIENT_INVENTORY_START, CLIENT_INVENTORY_END);
	}

	private static LevelObject makeVirtualStation(LevelObject stump) {
		return LevelObject.custom(
				stump.level,
				stump.layerID,
				stump.tileX,
				stump.tileY,
				CrudeWorkbenchFeature.proxyObject,
				(byte)0,
				false
		);
	}

	@Override
	public int applyCraftingAction(int recipeID, int recipeHash, int craftAmount, boolean transferToInventory) {
		if (crafting) return 0;

		Recipe recipe = getRecipe(recipeID);
		if (recipe == null) {
			GameLog.warn.println(client.playerMob.getDisplayName()
					+ " tried to craft a non-existing Crude Workbench recipe with id " + recipeID);
			return 0;
		}

		if (recipeHash != recipe.getRecipeHash()) return 0;

		Collection inventories = getCraftInventories();
		if (!canCraftRecipe(recipe, inventories, false).canCraft()) return 0;

		crafting = true;
		craftingRecipeID = recipeID;
		craftingRecipeHash = recipeHash;
		craftingStartTime = System.currentTimeMillis();
		Logging.logMessage("[CrudeWorkbench] " + (client.isServer() ? "SERVER" : "CLIENT")
				+ " started craft recipeID=" + recipeID + " hash=" + recipeHash);
		return 1;
	}

	@Override
	public void tick() {
		super.tick();

		if (!crafting || System.currentTimeMillis() - craftingStartTime < CRAFT_TIME_MS) return;

		if (client.isServer()) completeCraftServer();

		cancelCraft();
		if (client.isClient()) GlobalData.updateCraftable();
	}

	@Override
	public boolean isValid(ServerClient client) {
		Level level = client.getLevel();
		if (level == null || !level.isTileWithinBounds(objectX, objectY)) return false;

		return level.getObject(objectX, objectY) instanceof TreeStumpObject
				&& level.getObject(objectX, objectY).isInInteractRange(level, objectX, objectY, client.playerMob);
	}

	private void completeCraftServer() {
		Logging.logMessage("[CrudeWorkbench] SERVER completing recipeID=" + craftingRecipeID);

		Recipe recipe = getRecipe(craftingRecipeID);
		if (recipe == null) {
			Logging.logMessage("[CrudeWorkbench] SERVER completion failed: recipe not found");
			return;
		}
		if (recipe.getRecipeHash() != craftingRecipeHash) {
			Logging.logMessage("[CrudeWorkbench] SERVER completion failed: recipe hash changed");
			return;
		}

		Collection inventories = getCraftInventories();
		if (!canCraftRecipe(recipe, inventories, false).canCraft()) {
			Logging.logMessage("[CrudeWorkbench] SERVER completion failed: ingredients no longer available");
			return;
		}

		ContainerRecipeCraftedEvent event = new ContainerRecipeCraftedEvent(
				recipe,
				recipe.craft(client.playerMob.getLevel(), client.playerMob, (Iterable)inventories),
				this
		);
		recipe.submitCraftedEvent(event);

		InventoryItem resultItem = event.resultItem;
		if (resultItem == null) {
			Logging.logMessage("[CrudeWorkbench] SERVER completion failed: crafted event result is null");
			event.itemsUsed.forEach(removed -> removed.revert());
			return;
		}

		Logging.logMessage("[CrudeWorkbench] SERVER crafted " + resultItem.item.getStringID()
				+ " x" + resultItem.getAmount());

		ejectOutput();
		resultItem.setNew(true);
		outputInventory.setItem(0, resultItem);
		getSlot(OUTPUT_SLOT).markDirty();
		Logging.logMessage("[CrudeWorkbench] SERVER output slot now contains="
				+ (outputInventory.getItem(0) == null ? "null" : outputInventory.getItem(0).item.getStringID())
				+ " inventoryID=" + outputInventory.getInventoryID());

		ServerClient serverClient = client.getServerClient();
		serverClient.sendPacket(new PacketCrudeWorkbenchOutput(resultItem));
		serverClient.newStats.crafted_items.increment(1);
		JournalChallengeRegistry.handleListeners(
				serverClient,
				CraftedRecipeJournalChallengeListener.class,
				challenge -> challenge.onCraftedRecipe(serverClient, recipe, 1)
		);

		if (!serverClient.adventureParty.isEmpty()) {
			Iterator iterator = serverClient.adventureParty.getMobs().iterator();
			while (iterator.hasNext()) {
				HumanMob settler = (HumanMob)iterator.next();
				PlayerRomanceManager romanceManager = settler.getRomanceManager(serverClient, false);
				if (romanceManager != null) romanceManager.onCraftedRecipe(serverClient, recipe, 1);
			}
		}
	}

	private void ejectOutput() {
		InventoryItem item = outputInventory.getItem(0);
		if (item == null) return;

		Level level = client.playerMob.getLevel();
		level.entityManager.pickups.add(item.getPickupEntity(
				level,
				(float)(objectX * 32 + 16),
				(float)(objectY * 32 + 16)
		));
		outputInventory.clearSlot(0);
		outputInventory.markDirty(0);
	}

	public float getCraftProgress() {
		if (!crafting) return 0.0F;
		return Math.min(1.0F, (float)(System.currentTimeMillis() - craftingStartTime) / (float)CRAFT_TIME_MS);
	}

	private void cancelCraft() {
		crafting = false;
		craftingRecipeID = -1;
		craftingRecipeHash = 0;
		craftingStartTime = 0L;
	}

	@Override
	public void onClose() {
		cancelCraft();
		super.onClose();
	}
}
