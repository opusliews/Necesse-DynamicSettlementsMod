package opusliews.crafting;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import necesse.engine.GlobalData;
import necesse.engine.journal.listeners.CraftedRecipeJournalChallengeListener;
import necesse.engine.network.NetworkClient;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.JournalChallengeRegistry;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.inventory.Inventory;
import necesse.inventory.InventoryAddConsumer;
import necesse.inventory.InventoryItem;
import necesse.inventory.container.Container;
import necesse.inventory.container.slots.ExtractOnlyContainerSlot;
import necesse.inventory.recipe.ContainerRecipeCraftedEvent;
import necesse.inventory.recipe.Recipe;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.settler.romancePersonalities.PlayerRomanceManager;

public final class InventoryCraftingTime {
	private static final Map<Container, CraftState> states = new WeakHashMap<>();

	private InventoryCraftingTime() {
	}

	public static int tryStartCraft(
			Container container,
			int recipeID,
			int recipeHash,
			int craftAmount,
			boolean transferToInventory
	) {
		if (!isInventoryContainer(container)) return -1;

		CraftState state = getState(container);
		if (state.completing) return -1;
		if (state.crafting) return 0;
		if (craftAmount <= 0) return 0;

		Recipe recipe = container.getRecipe(recipeID);
		if (recipe == null || recipe.getRecipeHash() != recipeHash) return 0;
		if (!container.canCraftRecipe(recipe, container.getCraftInventories(), false).canCraft()) return 0;

		state.crafting = true;
		state.recipeID = recipeID;
		state.recipeHash = recipeHash;
		state.startTime = System.currentTimeMillis();
		state.durationMs = CraftingTime.get(recipe);
		return 1;
	}

	public static void tick(Container container) {
		if (!isInventoryContainer(container)) return;

		CraftState state = states.get(container);
		if (state == null || !state.crafting || state.completing) return;
		if (System.currentTimeMillis() - state.startTime < state.durationMs) return;

		state.completing = true;
		try {
			completeCraft(container, state);
		}
		finally {
			state.resetCraft();
		}

		if (container.client.isClient()) GlobalData.updateCraftable();
	}

	public static float getProgress(Container container) {
		if (!isInventoryContainer(container)) return 0.0F;

		CraftState state = states.get(container);
		if (state == null || !state.crafting) return 0.0F;
		if (state.durationMs <= 0L) return 1.0F;
		return Math.min(1.0F, (float)(System.currentTimeMillis() - state.startTime) / (float)state.durationMs);
	}

	public static int getOutputSlot(Container container) {
		if (!isInventoryContainer(container)) return -1;
		return getState(container).outputSlot;
	}

	public static void onInventoryClosed(PlayerMob player) {
		if (player == null) return;

		NetworkClient networkClient = player.getNetworkClient();
		if (networkClient == null) return;

		Container container;
		if (networkClient.isServer()) {
			container = networkClient.getServerClient().getContainer();
		} else if (networkClient.isClient() && player.getClient() != null) {
			container = player.getClient().getInventoryContainer();
		} else {
			return;
		}

		if (!isInventoryContainer(container)) return;
		CraftState state = states.get(container);
		if (state == null) return;

		state.resetCraft();
		collectOutput(container, state);
	}

	private static void completeCraft(Container container, CraftState state) {
		Recipe recipe = container.getRecipe(state.recipeID);
		if (recipe == null || recipe.getRecipeHash() != state.recipeHash) return;
		if (!container.canCraftRecipe(recipe, container.getCraftInventories(), false).canCraft()) return;

		ContainerRecipeCraftedEvent event = new ContainerRecipeCraftedEvent(
				recipe,
				recipe.craft(container.client.playerMob.getLevel(), container.client.playerMob, container.getCraftInventories()),
				container
		);
		recipe.submitCraftedEvent(event);

		InventoryItem resultItem = event.resultItem;
		if (resultItem == null) {
			event.itemsUsed.forEach(removed -> removed.revert());
			return;
		}

		collectOutput(container, state);
		resultItem.setNew(true);
		state.outputInventory.setItem(0, resultItem);
		container.getSlot(state.outputSlot).markDirty();

		if (container.client.isServer()) {
			ServerClient serverClient = container.client.getServerClient();
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
	}

	private static void collectOutput(Container container, CraftState state) {
		InventoryItem item = state.outputInventory.getItem(0);
		if (item == null) return;

		PlayerMob player = container.client.playerMob;
		player.getInv().addItem(item, true, "addback", (InventoryAddConsumer)null);
		if (item.getAmount() > 0 && container.client.isServer()) {
			Level level = player.getLevel();
			if (level != null) {
				level.entityManager.pickups.add(item.getPickupEntity(level, player.getX(), player.getY()));
			}
		}

		state.outputInventory.clearSlot(0);
		state.outputInventory.markDirty(0);
		container.getSlot(state.outputSlot).markDirty();
	}

	private static boolean isInventoryContainer(Container container) {
		return container != null && container.getClass() == Container.class && container.uniqueSeed == 0;
	}

	private static CraftState getState(Container container) {
		CraftState state = states.get(container);
		if (state != null) return state;

		state = new CraftState();
		state.outputInventory = new Inventory(1);
		state.outputInventory.filter = (slot, item) -> item == null;
		state.outputSlot = container.addSlot(new ExtractOnlyContainerSlot(state.outputInventory, 0));
		container.getCraftInventories().remove(state.outputInventory);
		container.addQuickTransferOption(state.outputSlot, state.outputSlot, container.CLIENT_HOTBAR_START, container.CLIENT_HOTBAR_END);
		container.addQuickTransferOption(state.outputSlot, state.outputSlot, container.CLIENT_INVENTORY_START, container.CLIENT_INVENTORY_END);
		states.put(container, state);
		return state;
	}

	private static final class CraftState {
		private boolean crafting;
		private boolean completing;
		private int recipeID = -1;
		private int recipeHash;
		private long startTime;
		private long durationMs = CraftingTime.DEFAULT_TIME_MS;
		private Inventory outputInventory;
		private int outputSlot = -1;

		private void resetCraft() {
			crafting = false;
			completing = false;
			recipeID = -1;
			recipeHash = 0;
			startTime = 0L;
			durationMs = CraftingTime.DEFAULT_TIME_MS;
		}
	}
}
