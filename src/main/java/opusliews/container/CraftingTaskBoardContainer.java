package opusliews.container;

import necesse.engine.network.NetworkClient;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ItemRegistry;
import necesse.inventory.container.Container;
import necesse.inventory.container.customAction.ContentCustomAction;
import necesse.inventory.recipe.Recipe;
import necesse.inventory.recipe.Recipes;
import necesse.inventory.recipe.Tech;
import necesse.level.gameObject.container.CraftingStationObject;
import necesse.level.maps.Level;
import opusliews.object.CraftingTaskBoardObjectEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;


public class CraftingTaskBoardContainer extends Container {
	public final CraftingTaskBoardObjectEntity boardEntity;
	public final ContentCustomAction addTaskAction;
	public final ContentCustomAction deleteTaskAction;
	public final ContentCustomAction moveTaskAction;
	public final ContentCustomAction updateTaskAction;
	public final ContentCustomAction setTaskPausedAction;

	public CraftingTaskBoardContainer(NetworkClient client, int uniqueSeed, CraftingTaskBoardObjectEntity boardEntity) {
		super(client, uniqueSeed);
		this.boardEntity = boardEntity;

		addTaskAction = (ContentCustomAction)registerAction(new ContentCustomAction() {
			@Override
			protected void run(Packet content) {
				if (!isCurrentBoardEntity()) return;
				PacketReader reader = new PacketReader(content);
				int expectedRevision = reader.getNextInt();
				int itemID = reader.getNextInt();
				if (isItemCraftableByLinkedStation(itemID) && !boardEntity.addTask(expectedRevision, itemID) && client.isServer()) {
					boardEntity.syncContentNow();
				}
			}
		});

		deleteTaskAction = (ContentCustomAction)registerAction(new ContentCustomAction() {
			@Override
			protected void run(Packet content) {
				if (!isCurrentBoardEntity()) return;
				PacketReader reader = new PacketReader(content);
				if (!boardEntity.removeTask(reader.getNextInt(), reader.getNextInt()) && client.isServer()) boardEntity.syncContentNow();
			}
		});

		moveTaskAction = (ContentCustomAction)registerAction(new ContentCustomAction() {
			@Override
			protected void run(Packet content) {
				if (!isCurrentBoardEntity()) return;
				PacketReader reader = new PacketReader(content);
				if (!boardEntity.moveTask(reader.getNextInt(), reader.getNextInt(), reader.getNextInt()) && client.isServer()) boardEntity.syncContentNow();
			}
		});

		updateTaskAction = (ContentCustomAction)registerAction(new ContentCustomAction() {
			@Override
			protected void run(Packet content) {
				if (!isCurrentBoardEntity()) return;
				PacketReader reader = new PacketReader(content);
				if (!boardEntity.updateTask(reader.getNextInt(), reader.getNextInt(), reader.getNextByteUnsigned(), reader.getNextInt()) && client.isServer()) boardEntity.syncContentNow();
			}
		});

		setTaskPausedAction = (ContentCustomAction)registerAction(new ContentCustomAction() {
			@Override
			protected void run(Packet content) {
				if (!isCurrentBoardEntity()) return;
				PacketReader reader = new PacketReader(content);
				if (!boardEntity.setTaskPaused(reader.getNextInt(), reader.getNextInt(), reader.getNextBoolean()) && client.isServer()) boardEntity.syncContentNow();
			}
		});
	}

	public void addTask(int itemID) {
		Packet packet = new Packet();
		PacketWriter writer = new PacketWriter(packet);
		writer.putNextInt(boardEntity.getTaskRevision());
		writer.putNextInt(itemID);
		addTaskAction.runAndSend(packet);
	}

	public void deleteTask(int index) {
		Packet packet = new Packet();
		PacketWriter writer = new PacketWriter(packet);
		writer.putNextInt(boardEntity.getTaskRevision());
		writer.putNextInt(index);
		deleteTaskAction.runAndSend(packet);
	}

	public void moveTask(int from, int to) {
		Packet packet = new Packet();
		PacketWriter writer = new PacketWriter(packet);
		writer.putNextInt(boardEntity.getTaskRevision());
		writer.putNextInt(from);
		writer.putNextInt(to);
		moveTaskAction.runAndSend(packet);
	}

	public void updateTask(int index, int conditionType, int amount) {
		Packet packet = new Packet();
		PacketWriter writer = new PacketWriter(packet);
		writer.putNextInt(boardEntity.getTaskRevision());
		writer.putNextInt(index);
		writer.putNextByteUnsigned(conditionType);
		writer.putNextInt(amount);
		updateTaskAction.runAndSend(packet);
	}

	public void setTaskPaused(int index, boolean paused) {
		Packet packet = new Packet();
		PacketWriter writer = new PacketWriter(packet);
		writer.putNextInt(boardEntity.getTaskRevision());
		writer.putNextInt(index);
		writer.putNextBoolean(paused);
		setTaskPausedAction.runAndSend(packet);
	}

	public necesse.level.maps.LevelObject getLinkedStation() {
		return boardEntity.getValidLinkedStationObject();
	}

	public boolean hasValidLinkedStation() {
		return getLinkedStation() != null;
	}

	public List<Recipe> getCraftableRecipes() {
		necesse.level.maps.LevelObject anvil = getLinkedStation();
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
		necesse.level.maps.LevelObject anvil = getLinkedStation();
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

	private boolean isItemCraftableByLinkedStation(int itemID) {
		return getCraftableItemIDs().contains(itemID);
	}

	private boolean isCurrentBoardEntity() {
		if (boardEntity.removed()) return false;
		Level level = client.playerMob.getLevel();
		return level != null
				&& level == boardEntity.getLevel()
				&& level.entityManager.getObjectEntity(boardEntity.tileX, boardEntity.tileY) == boardEntity;
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
