package opusliews.container;

import java.util.Collection;
import java.awt.Point;
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
import necesse.entity.objectEntity.ObjectEntity;
import necesse.entity.objectEntity.ProcessingForgeObjectEntity;
import necesse.inventory.InventoryItem;
import necesse.inventory.PlayerTempInventory;
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
import opusliews.crafting.CraftingTime;
import opusliews.forge.ForgeRequirementSystem;
import opusliews.network.PacketCrudeAnvilOutput;
import opusliews.object.CrudeAnvilObject;
import opusliews.object.CrudeAnvilObjectEntity;

public class CrudeAnvilContainer extends CraftingStationContainer {
	public final PlayerTempInventory outputInventory;
	public final int OUTPUT_SLOT;
	public final CrudeAnvilObjectEntity stationEntity;
	public final PointCustomAction setForge;
	public final BooleanCustomAction setSelectingForge;

	private boolean crafting;
	private int craftingRecipeID = -1;
	private int craftingRecipeHash;
	private long craftingStartTime;
	private long craftingDurationMs = CraftingTime.DEFAULT_TIME_MS;
	private boolean selectingForge;

	public CrudeAnvilContainer(NetworkClient client, int uniqueSeed, SettlementDataEvent settlement, LevelObject anvil, PacketReader reader) {
		super(client, uniqueSeed, settlement, anvil, reader);

		ObjectEntity objectEntity = anvil.getObjectEntity();
		if (!(objectEntity instanceof CrudeAnvilObjectEntity)) {
			throw new IllegalStateException("Crude Anvil is missing its forge-link object entity");
		}
		stationEntity = (CrudeAnvilObjectEntity)objectEntity;

		setForge = registerAction(new PointCustomAction() {
			@Override
			protected void run(int x, int y) {
				if (client.isServer()) applyForgeLink(x, y);
			}
		});

		setSelectingForge = registerAction(new BooleanCustomAction() {
			@Override
			protected void run(boolean value) {
				selectingForge = value;
			}
		});

		Packet tempInventoryContent = reader.getNextContentPacket();
		outputInventory = client.playerMob.getInv().applyTempInventoryPacket(tempInventoryContent, (player, size, invID) -> new PlayerTempInventory(player, size, invID) {
			@Override
			public boolean shouldDispose() {
				return CrudeAnvilContainer.this.isClosed();
			}
		});
		outputInventory.filter = (slot, item) -> item == null;

		OUTPUT_SLOT = addSlot(new ExtractOnlyContainerSlot(outputInventory, 0));
		addQuickTransferOption(OUTPUT_SLOT, OUTPUT_SLOT, CLIENT_HOTBAR_START, CLIENT_HOTBAR_END);
		addQuickTransferOption(OUTPUT_SLOT, OUTPUT_SLOT, CLIENT_INVENTORY_START, CLIENT_INVENTORY_END);
	}

	@Override
	public int applyCraftingAction(int recipeID, int recipeHash, int craftAmount, boolean transferToInventory) {
		if (crafting) return 0;

		Recipe recipe = getRecipe(recipeID);
		if (recipe == null) {
			GameLog.warn.println(client.playerMob.getDisplayName() + " tried to craft a non-existing Crude Anvil recipe with id " + recipeID);
			return 0;
		}
		if (recipeHash != recipe.getRecipeHash()) return 0;

		Collection inventories = getCraftInventories();
		if (!canCraftRecipe(recipe, inventories, false).canCraft()) return 0;
		if (ForgeRequirementSystem.requiresRunningForge(recipe)) {
			if (client.isServer() && !ForgeRequirementSystem.ensureRunningForge(
					stationEntity, client.playerMob, recipe, inventories, CraftingTime.get(recipe))) return 0;
			if (client.isClient()) {
				ForgeRequirementSystem.Status status = ForgeRequirementSystem.getStatus(stationEntity, recipe, inventories);
				if (status == ForgeRequirementSystem.Status.NO_LINKED_FORGE || status == ForgeRequirementSystem.Status.NO_FUEL) return 0;
			}
		}

		crafting = true;
		craftingRecipeID = recipeID;
		craftingRecipeHash = recipeHash;
		craftingStartTime = System.currentTimeMillis();
		craftingDurationMs = CraftingTime.get(recipe);
		return 1;
	}

	@Override
	public void tick() {
		super.tick();
		if (!crafting || System.currentTimeMillis() - craftingStartTime < craftingDurationMs) return;
		if (client.isServer()) completeCraftServer();
		cancelCraft();
		if (client.isClient()) GlobalData.updateCraftable();
	}

	@Override
	public boolean isValid(ServerClient client) {
		Level level = client.getLevel();
		if (level == null || !level.isTileWithinBounds(objectX, objectY)) return false;
		if (!(level.getObject(objectX, objectY) instanceof CrudeAnvilObject)) return false;
		if (level.entityManager.getObjectEntity(objectX, objectY) != stationEntity) return false;
		return selectingForge || level.getObject(objectX, objectY).isInInteractRange(level, objectX, objectY, client.playerMob);
	}

	private void applyForgeLink(int x, int y) {
		Level level = client.playerMob.getLevel();
		if (level == null || level != stationEntity.getLevel()) return;

		LevelObject object = level.getLevelObject(x, y);
		LevelObject master = object == null ? null : (LevelObject)object.getMasterLevelObject().orElse(object);
		if (master == null || !stationEntity.isWithinStorageLinkRange(master.tileX, master.tileY)) return;

		Point target = new Point(master.tileX, master.tileY);
		if (stationEntity.hasLinkedForge(target)) {
			stationEntity.removeLinkedForge(target);
			return;
		}

		if (!(master.getObjectEntity() instanceof ProcessingForgeObjectEntity)) return;
		stationEntity.addLinkedForge(target);
	}

	private void completeCraftServer() {
		Recipe recipe = getRecipe(craftingRecipeID);
		if (recipe == null || recipe.getRecipeHash() != craftingRecipeHash) return;

		Collection inventories = getCraftInventories();
		if (!canCraftRecipe(recipe, inventories, false).canCraft()) return;
		if (ForgeRequirementSystem.requiresRunningForge(recipe)
				&& !ForgeRequirementSystem.ensureRunningForge(stationEntity, client.playerMob, recipe, inventories, 250L)) return;

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

		ejectOutput();
		resultItem.setNew(true);
		outputInventory.setItem(0, resultItem);
		getSlot(OUTPUT_SLOT).markDirty();

		ServerClient serverClient = client.getServerClient();
		serverClient.sendPacket(new PacketCrudeAnvilOutput(resultItem));
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
		level.entityManager.pickups.add(item.getPickupEntity(level, objectX * 32.0F + 16.0F, objectY * 32.0F + 16.0F));
		outputInventory.clearSlot(0);
		outputInventory.markDirty(0);
	}

	public float getCraftProgress() {
		if (!crafting) return 0.0F;
		if (craftingDurationMs <= 0L) return 1.0F;
		return Math.min(1.0F, (float)(System.currentTimeMillis() - craftingStartTime) / (float)craftingDurationMs);
	}

	private void cancelCraft() {
		crafting = false;
		craftingRecipeID = -1;
		craftingRecipeHash = 0;
		craftingStartTime = 0L;
		craftingDurationMs = CraftingTime.DEFAULT_TIME_MS;
	}

	@Override
	public void onClose() {
		cancelCraft();
		super.onClose();
	}
}
