package opusliews.container;

import java.awt.Point;
import java.util.Collection;
import java.util.Iterator;
import necesse.engine.GameLog;
import necesse.engine.GlobalData;
import necesse.engine.journal.listeners.CraftedRecipeJournalChallengeListener;
import necesse.engine.network.NetworkClient;
import necesse.engine.network.PacketReader;
import necesse.engine.network.server.ServerClient;
import necesse.inventory.container.customAction.BooleanCustomAction;
import necesse.engine.registries.JournalChallengeRegistry;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.entity.objectEntity.interfaces.OEInventory;
import necesse.inventory.InventoryItem;
import necesse.inventory.container.customAction.PointCustomAction;
import necesse.inventory.container.object.CraftingStationContainer;
import necesse.inventory.container.settlement.events.SettlementDataEvent;
import necesse.inventory.container.slots.ExtractOnlyContainerSlot;
import necesse.inventory.recipe.ContainerRecipeCraftedEvent;
import necesse.inventory.recipe.Recipe;
import necesse.level.maps.Level;
import necesse.level.maps.LevelObject;
import necesse.level.maps.levelData.settlementData.settler.romancePersonalities.PlayerRomanceManager;
import opusliews.object.AnvilCraftingTaskBoardObjectEntity;
import opusliews.object.AnvilObjectEntity;

public class AnvilContainer extends CraftingStationContainer {
	public static final long CRAFT_TIME_MS = 2000L;

	public final AnvilObjectEntity anvilEntity;
	public final int OUTPUT_SLOT;
	public final PointCustomAction setInputStorage;
	public final PointCustomAction setOutputStorage;
	public final PointCustomAction setTaskBoard;
	public final BooleanCustomAction setSelectingLinkedElement;

	private boolean crafting;
	private int craftingRecipeID = -1;
	private int craftingRecipeHash;
	private long craftingStartTime;

	private boolean selectingStorage;

	public AnvilContainer(
			NetworkClient client,
			int uniqueSeed,
			SettlementDataEvent settlement,
			LevelObject levelObject,
			PacketReader reader
	) {
		super(client, uniqueSeed, settlement, levelObject, reader);

		ObjectEntity objectEntity = levelObject.getObjectEntity();
		if (!(objectEntity instanceof AnvilObjectEntity)) {
			throw new IllegalStateException("Anvil is missing its Dynamic Settlements object entity");
		}

		anvilEntity = (AnvilObjectEntity)objectEntity;
		OUTPUT_SLOT = addSlot(new ExtractOnlyContainerSlot(anvilEntity.inventory, 0));

		craftInventories.remove(anvilEntity.inventory);

		addQuickTransferOption(
				OUTPUT_SLOT,
				OUTPUT_SLOT,
				CLIENT_HOTBAR_START,
				CLIENT_HOTBAR_END
		);
		addQuickTransferOption(
				OUTPUT_SLOT,
				OUTPUT_SLOT,
				CLIENT_INVENTORY_START,
				CLIENT_INVENTORY_END
		);

		setInputStorage = registerAction(new PointCustomAction() {
			@Override
			protected void run(int x, int y) {
				if (client.isServer()) {
					applyStorageLink(true, x, y);
				}
			}
		});

		setOutputStorage = registerAction(new PointCustomAction() {
			@Override
			protected void run(int x, int y) {
				if (client.isServer()) {
					applyStorageLink(false, x, y);
				}
			}
		});

		setTaskBoard = registerAction(new PointCustomAction() {
			@Override
			protected void run(int x, int y) {
				if (client.isServer()) {
					applyTaskBoardLink(x, y);
				}
			}
		});

		setSelectingLinkedElement = registerAction(new BooleanCustomAction() {
			@Override
			protected void run(boolean value) {
				selectingStorage = value;
			}
		});
	}

	@Override
	public int applyCraftingAction(int recipeID, int recipeHash, int craftAmount, boolean transferToInventory) {
		// One player can only have one active craft in this container session.
		// craftAmount and transferToInventory are intentionally ignored, which
		// disables vanilla Shift/Craft-10/Craft-all behavior for the Iron Anvil.
		if (crafting) {
			return 0;
		}

		Recipe recipe = getRecipe(recipeID);
		if (recipe == null) {
			GameLog.warn.println(client.playerMob.getDisplayName()
					+ " tried to craft a non-existing Iron Anvil recipe with id " + recipeID);
			return 0;
		}

		if (recipeHash != recipe.getRecipeHash()) {
			return 0;
		}

		Collection inventories = getCraftInventories();
		if (!canCraftRecipe(recipe, inventories, false).canCraft()) {
			return 0;
		}

		crafting = true;
		craftingRecipeID = recipeID;
		craftingRecipeHash = recipeHash;
		craftingStartTime = System.currentTimeMillis();
		return 1;
	}

	@Override
	public void tick() {
		super.tick();

		if (!crafting || System.currentTimeMillis() - craftingStartTime < CRAFT_TIME_MS) {
			return;
		}

		if (client.isServer()) {
			completeCraftServer();
		}

		cancelCraft();
		if (client.isClient()) {
			GlobalData.updateCraftable();
		}
	}

	public void setSelectingStorage(boolean selectingStorage) {
		this.selectingStorage = selectingStorage;
	}

	public boolean isSelectingStorage() {
		return selectingStorage;
	}

	@Override
	public boolean isValid(ServerClient client) {
		if (!selectingStorage) {
			return super.isValid(client);
		}

		if (client.getLevel() != anvilEntity.getLevel()) {
			return false;
		}

		Level level = client.getLevel();
		return level.getObjectID(objectX, objectY) == craftingStationObject.getID();
	}

	private void completeCraftServer() {
		Recipe recipe = getRecipe(craftingRecipeID);
		if (recipe == null || recipe.getRecipeHash() != craftingRecipeHash) {
			return;
		}

		Collection inventories = getCraftInventories();

		// Inputs are deliberately not reserved. If anything was removed during
		// the two-second action, the craft simply ends without consuming or creating anything.
		if (!canCraftRecipe(recipe, inventories, false).canCraft()) {
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
			// If another mod cancels/changes the crafted result to null after ingredients were consumed, restore them.
			event.itemsUsed.forEach(removed -> removed.revert());
			return;
		}

		// Only a completed craft displaces the previous result.
		anvilEntity.ejectOutput();
		resultItem.setNew(true);
		anvilEntity.setOutput(resultItem);

		ServerClient serverClient = client.getServerClient();
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
				if (romanceManager != null) {
					romanceManager.onCraftedRecipe(serverClient, recipe, 1);
				}
			}
		}
	}

	private void applyStorageLink(boolean input, int x, int y) {
		Point current = input ? anvilEntity.getInputStorage() : anvilEntity.getOutputStorage();
		Point other = input ? anvilEntity.getOutputStorage() : anvilEntity.getInputStorage();

		// Clicking the currently selected storage toggles the link off, even if
		// that storage has since been removed or changed.
		if (current != null && current.x == x && current.y == y) {
			if (input) {
				anvilEntity.setInputStorage(null);
			} else {
				anvilEntity.setOutputStorage(null);
			}
			return;
		}

		LevelObject master = getStorageMaster(x, y);
		if (master == null) {
			return;
		}

		Point target = new Point(master.tileX, master.tileY);
		if (!anvilEntity.isWithinStorageLinkRange(target.x, target.y)) {
			return;
		}

		if (other != null && other.equals(target)) {
			return;
		}

		if (anvilEntity.isStorageUsedByOtherAnvil(target)) {
			return;
		}

		ObjectEntity targetEntity = master.getObjectEntity();
		if (!(targetEntity instanceof OEInventory) || targetEntity == anvilEntity) {
			return;
		}

		OEInventory inventory = (OEInventory)targetEntity;
		if (inventory.getInventory() == null || inventory.getSettlementStorage() == null) {
			return;
		}

		if (input) {
			anvilEntity.setInputStorage(target);
		} else {
			anvilEntity.setOutputStorage(target);
		}
	}

	private void applyTaskBoardLink(int x, int y) {
		Point current = anvilEntity.getTaskBoard();
		if (current != null && current.x == x && current.y == y) {
			anvilEntity.setTaskBoard(null);
			return;
		}

		LevelObject master = getStorageMaster(x, y);
		if (master == null) {
			return;
		}

		ObjectEntity targetEntity = master.getObjectEntity();
		if (!(targetEntity instanceof AnvilCraftingTaskBoardObjectEntity)) {
			return;
		}

		AnvilCraftingTaskBoardObjectEntity board = (AnvilCraftingTaskBoardObjectEntity)targetEntity;
		Point owner = board.getLinkedAnvil();
		if (owner != null && (owner.x != anvilEntity.tileX || owner.y != anvilEntity.tileY)) {
			return;
		}

		anvilEntity.setTaskBoard(new Point(master.tileX, master.tileY));
	}

	public LevelObject getStorageMaster(int x, int y) {
		LevelObject object = client.playerMob.getLevel().getLevelObject(x, y);
		if (object == null) {
			return null;
		}

		return (LevelObject)object.getMasterLevelObject().orElse(null);
	}

	public boolean isCrafting() {
		return crafting;
	}

	public float getCraftProgress() {
		if (!crafting) {
			return 0.0F;
		}

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
