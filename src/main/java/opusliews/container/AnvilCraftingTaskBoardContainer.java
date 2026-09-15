package opusliews.container;

import necesse.engine.network.NetworkClient;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ItemRegistry;
import necesse.inventory.container.Container;
import necesse.inventory.container.customAction.ContentCustomAction;
import necesse.inventory.container.customAction.IntCustomAction;
import necesse.inventory.recipe.Recipe;
import necesse.inventory.recipe.Recipes;
import necesse.inventory.recipe.Tech;
import necesse.level.gameObject.container.CraftingStationObject;
import necesse.level.maps.Level;
import opusliews.object.AnvilCraftingTaskBoardObjectEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;


public class AnvilCraftingTaskBoardContainer extends Container {
	public final AnvilCraftingTaskBoardObjectEntity boardEntity;
	public final IntCustomAction addTaskAction;
	public final IntCustomAction deleteTaskAction;
	public final ContentCustomAction moveTaskAction;
	public final ContentCustomAction updateTaskAction;
	public final ContentCustomAction setTaskPausedAction;

	public AnvilCraftingTaskBoardContainer(NetworkClient client, int uniqueSeed, AnvilCraftingTaskBoardObjectEntity boardEntity) {
		super(client, uniqueSeed);
		this.boardEntity = boardEntity;

		addTaskAction = (IntCustomAction)registerAction(new IntCustomAction() {
			@Override
			protected void run(int itemID) {
				if (isItemCraftableByLinkedAnvil(itemID)) {
					boardEntity.addTask(itemID);
				}
			}
		});

		deleteTaskAction = (IntCustomAction)registerAction(new IntCustomAction() {
			@Override
			protected void run(int index) {
				boardEntity.removeTask(index);
			}
		});

		moveTaskAction = (ContentCustomAction)registerAction(new ContentCustomAction() {
			@Override
			protected void run(Packet content) {
				PacketReader reader = new PacketReader(content);
				boardEntity.moveTask(reader.getNextInt(), reader.getNextInt());
			}
		});

		updateTaskAction = (ContentCustomAction)registerAction(new ContentCustomAction() {
			@Override
			protected void run(Packet content) {
				PacketReader reader = new PacketReader(content);
				boardEntity.updateTask(reader.getNextInt(), reader.getNextByteUnsigned(), reader.getNextInt());
			}
		});

		setTaskPausedAction = (ContentCustomAction)registerAction(new ContentCustomAction() {
			@Override
			protected void run(Packet content) {
				PacketReader reader = new PacketReader(content);
				boardEntity.setTaskPaused(reader.getNextInt(), reader.getNextBoolean());
			}
		});
	}

	public void moveTask(int from, int to) {
		Packet packet = new Packet();
		PacketWriter writer = new PacketWriter(packet);
		writer.putNextInt(from);
		writer.putNextInt(to);
		moveTaskAction.runAndSend(packet);
	}

	public void updateTask(int index, int conditionType, int amount) {
		Packet packet = new Packet();
		PacketWriter writer = new PacketWriter(packet);
		writer.putNextInt(index);
		writer.putNextByteUnsigned(conditionType);
		writer.putNextInt(amount);
		updateTaskAction.runAndSend(packet);
	}

	public void setTaskPaused(int index, boolean paused) {
		Packet packet = new Packet();
		PacketWriter writer = new PacketWriter(packet);
		writer.putNextInt(index);
		writer.putNextBoolean(paused);
		setTaskPausedAction.runAndSend(packet);
	}

	public necesse.level.maps.LevelObject getLinkedAnvil() {
		return boardEntity.getValidLinkedAnvilObject();
	}

	public boolean hasValidLinkedAnvil() {
		return getLinkedAnvil() != null;
	}

	public List<Recipe> getCraftableRecipes() {
		necesse.level.maps.LevelObject anvil = getLinkedAnvil();
		if (anvil == null || !(anvil.object instanceof CraftingStationObject)) {
			return new ArrayList<>();
		}

		CraftingStationObject station = (CraftingStationObject)anvil.object;
		Tech[] techs = station.getCraftingTechs();
		return Recipes.streamRecipes()
				.filter(recipe -> Arrays.stream(techs).anyMatch(recipe::matchTech))
				.collect(Collectors.toList());
	}

	public List<Integer> getCraftableItemIDs() {
		necesse.level.maps.LevelObject anvil = getLinkedAnvil();
		if (anvil == null || !(anvil.object instanceof CraftingStationObject)) {
			return new ArrayList<>();
		}

		CraftingStationObject station = (CraftingStationObject)anvil.object;
		Tech[] techs = station.getCraftingTechs();
		HashSet<Integer> seen = new HashSet<>();

		return Recipes.streamRecipes()
				.filter(recipe -> Arrays.stream(techs).anyMatch(recipe::matchTech))
				.map(recipe -> recipe.resultItem.item.getID())
				.filter(id -> id >= 0 && ItemRegistry.getItem(id) != null)
				.filter(seen::add)
				.sorted((a, b) -> new necesse.inventory.InventoryItem(ItemRegistry.getItem(a)).getItemDisplayName().compareToIgnoreCase(new necesse.inventory.InventoryItem(ItemRegistry.getItem(b)).getItemDisplayName()))
				.collect(Collectors.toList());
	}

	private boolean isItemCraftableByLinkedAnvil(int itemID) {
		return getCraftableItemIDs().contains(itemID);
	}

	@Override
	public boolean isValid(ServerClient client) {
		if (!super.isValid(client) || boardEntity.removed()) {
			return false;
		}

		Level level = client.getLevel();
		return level.getObject(boardEntity.tileX, boardEntity.tileY).isInInteractRange(
				level,
				boardEntity.tileX,
				boardEntity.tileY,
				client.playerMob
		);
	}
}
