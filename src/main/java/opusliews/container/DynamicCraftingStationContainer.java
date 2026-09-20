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
import necesse.engine.registries.JournalChallengeRegistry;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.entity.objectEntity.interfaces.OEInventory;
import necesse.inventory.InventoryItem;
import necesse.inventory.container.customAction.BooleanCustomAction;
import necesse.inventory.container.customAction.PointCustomAction;
import necesse.inventory.container.object.CraftingStationContainer;
import necesse.inventory.container.settlement.events.SettlementDataEvent;
import necesse.inventory.container.slots.ExtractOnlyContainerSlot;
import necesse.inventory.recipe.ContainerRecipeCraftedEvent;
import necesse.inventory.recipe.Recipe;
import necesse.level.maps.Level;
import necesse.level.maps.LevelObject;
import necesse.level.maps.levelData.settlementData.settler.romancePersonalities.PlayerRomanceManager;
import opusliews.object.DynamicCraftingStationObjectEntity;

public class DynamicCraftingStationContainer extends CraftingStationContainer {
	public static final long CRAFT_TIME_MS = 2000L;

	public final DynamicCraftingStationObjectEntity stationEntity;
	public final int OUTPUT_SLOT;
	public final PointCustomAction setInputStorage;
	public final PointCustomAction setOutputStorage;
	public final PointCustomAction setTaskBoard;
	public final BooleanCustomAction setSelectingLinkedElement;

	private final String stationName;
	private boolean crafting;
	private int craftingRecipeID = -1;
	private int craftingRecipeHash;
	private long craftingStartTime;
	private boolean selectingStorage;

	public DynamicCraftingStationContainer(
			NetworkClient client,
			int uniqueSeed,
			SettlementDataEvent settlement,
			LevelObject levelObject,
			PacketReader reader
	) {
		super(client, uniqueSeed, settlement, getMasterLevelObject(levelObject), reader);

		LevelObject master = getMasterLevelObject(levelObject);
		this.stationName = master.object.getLocalization().translate();

		ObjectEntity objectEntity = master.getObjectEntity();
		if (!(objectEntity instanceof DynamicCraftingStationObjectEntity)) {
			throw new IllegalStateException(stationName + " is missing its Dynamic Settlements object entity");
		}

		stationEntity = (DynamicCraftingStationObjectEntity)objectEntity;
		OUTPUT_SLOT = addSlot(new ExtractOnlyContainerSlot(stationEntity.inventory, 0));
		craftInventories.remove(stationEntity.inventory);

		addQuickTransferOption(OUTPUT_SLOT, OUTPUT_SLOT, CLIENT_HOTBAR_START, CLIENT_HOTBAR_END);
		addQuickTransferOption(OUTPUT_SLOT, OUTPUT_SLOT, CLIENT_INVENTORY_START, CLIENT_INVENTORY_END);

		setInputStorage = registerAction(new PointCustomAction() {
			@Override
			protected void run(int x, int y) {
				if (client.isServer()) applyStorageLink(true, x, y);
			}
		});

		setOutputStorage = registerAction(new PointCustomAction() {
			@Override
			protected void run(int x, int y) {
				if (client.isServer()) applyStorageLink(false, x, y);
			}
		});

		setTaskBoard = registerAction(new PointCustomAction() {
			@Override
			protected void run(int x, int y) {
				if (client.isServer()) applyTaskBoardLink(x, y);
			}
		});

		setSelectingLinkedElement = registerAction(new BooleanCustomAction() {
			@Override
			protected void run(boolean value) {
				selectingStorage = value;
			}
		});
	}


	public static LevelObject getMasterLevelObject(LevelObject levelObject) {
		if (levelObject == null) throw new IllegalArgumentException("levelObject cannot be null");
		return (LevelObject)levelObject.getMasterLevelObject().orElse(levelObject);
	}

	@Override
	public int applyCraftingAction(int recipeID, int recipeHash, int craftAmount, boolean transferToInventory) {
		if (crafting || !isCurrentStationEntity()) return 0;

		Recipe recipe = getRecipe(recipeID);
		if (recipe == null) {
			GameLog.warn.println(client.playerMob.getDisplayName()
					+ " tried to craft a non-existing " + stationName + " recipe with id " + recipeID);
			return 0;
		}

		if (recipeHash != recipe.getRecipeHash()) return 0;

		Collection inventories = getCraftInventories();
		if (!canCraftRecipe(recipe, inventories, false).canCraft()) return 0;
		if (!stationEntity.canStartPlayerCraft(client.playerMob, recipe)) return 0;

		crafting = true;
		craftingRecipeID = recipeID;
		craftingRecipeHash = recipeHash;
		craftingStartTime = System.currentTimeMillis();

		if (client.isServer()) stationEntity.onPlayerCraftStarted(client.playerMob, recipe);
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

	public void setSelectingStorage(boolean selectingStorage) {
		this.selectingStorage = selectingStorage;
	}

	public boolean isSelectingStorage() {
		return selectingStorage;
	}

	@Override
	public boolean isValid(ServerClient client) {
		if (!selectingStorage) return super.isValid(client);
		if (client.getLevel() != stationEntity.getLevel()) return false;

		Level level = client.getLevel();
		return level.getObjectID(objectX, objectY) == craftingStationObject.getID();
	}

	private void completeCraftServer() {
		if (!isCurrentStationEntity()) return;
		Recipe recipe = getRecipe(craftingRecipeID);
		if (recipe == null || recipe.getRecipeHash() != craftingRecipeHash) return;

		Collection inventories = getCraftInventories();
		if (!canCraftRecipe(recipe, inventories, false).canCraft()) return;
		if (!stationEntity.canCompletePlayerCraft(client.playerMob, recipe)) return;

		ContainerRecipeCraftedEvent event = new ContainerRecipeCraftedEvent(
				recipe,
				recipe.craft(client.playerMob.getLevel(), client.playerMob, (Iterable)inventories),
				this
		);
		recipe.submitCraftedEvent(event);

		InventoryItem resultItem = event.resultItem;
		if (resultItem == null) {
			event.itemsUsed.forEach(removed -> removed.revert());
			return;
		}

		stationEntity.ejectOutput();
		resultItem.setNew(true);
		stationEntity.setOutput(resultItem);
		stationEntity.onPlayerCraftCompleted(client.playerMob, recipe, resultItem);

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
				if (romanceManager != null) romanceManager.onCraftedRecipe(serverClient, recipe, 1);
			}
		}
	}

	private void applyStorageLink(boolean input, int x, int y) {
		if (!isCurrentStationEntity()) return;
		LevelObject master = getStorageMaster(x, y);
		if (master == null) return;

		Point target = new Point(master.tileX, master.tileY);
		if (!stationEntity.isWithinStorageLinkRange(target.x, target.y)) return;

		if (input && stationEntity.hasInputStorage(target)) {
			stationEntity.removeInputStorage(target);
			return;
		}

		if (!input && stationEntity.hasOutputStorage(target)) {
			stationEntity.removeOutputStorage(target);
			return;
		}

		ObjectEntity targetEntity = master.getObjectEntity();
		if (!(targetEntity instanceof OEInventory) || targetEntity == stationEntity) return;

		OEInventory inventory = (OEInventory)targetEntity;
		if (inventory.getInventory() == null || inventory.getSettlementStorage() == null) return;

		if (input) {
			if (stationEntity.isStorageInputForOtherStation(target)) return;
			stationEntity.addInputStorage(target);
		} else {
			stationEntity.addOutputStorage(target);
		}
	}

	private void applyTaskBoardLink(int x, int y) {
		if (!isCurrentStationEntity()) return;
		Point current = stationEntity.getTaskBoard();
		if (current != null && current.x == x && current.y == y) {
			stationEntity.setTaskBoard(null);
			return;
		}

		LevelObject master = getStorageMaster(x, y);
		if (master == null) return;

		ObjectEntity targetEntity = master.getObjectEntity();
		if (!stationEntity.canUseTaskBoard(targetEntity)) return;

		Point owner = stationEntity.getLinkedStationForTaskBoard(targetEntity);
		if (owner != null && (owner.x != stationEntity.tileX || owner.y != stationEntity.tileY)) return;

		stationEntity.setTaskBoard(new Point(master.tileX, master.tileY));
	}

	private boolean isCurrentStationEntity() {
		if (stationEntity.removed()) return false;
		Level level = client.playerMob.getLevel();
		if (level == null || level != stationEntity.getLevel()) return false;
		LevelObject current = level.getLevelObject(objectX, objectY);
		LevelObject master = current == null ? null : (LevelObject)current.getMasterLevelObject().orElse(current);
		return master != null && master.getObjectEntity() == stationEntity;
	}

	public LevelObject getStorageMaster(int x, int y) {
		LevelObject object = client.playerMob.getLevel().getLevelObject(x, y);
		if (object == null) return null;
		return (LevelObject)object.getMasterLevelObject().orElse(null);
	}

	public boolean isCrafting() {
		return crafting;
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
