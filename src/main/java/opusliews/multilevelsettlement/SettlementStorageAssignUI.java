package opusliews.multilevelsettlement;

import java.awt.Color;
import java.awt.Point;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import necesse.engine.gameTool.GameToolManager;
import necesse.engine.input.InputEvent;
import necesse.engine.localization.message.LocalMessage;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.entity.objectEntity.interfaces.OEInventory;
import necesse.gfx.drawOptions.DrawOptions;
import necesse.gfx.forms.presets.containerComponent.settlement.SelectTileGameTool;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementAssignWorkForm;
import necesse.level.maps.LevelObject;
import necesse.level.maps.TilePosition;
import opusliews.network.PacketOpenSettlementStorage;
import opusliews.network.PacketSettlementStorageRequest;
import opusliews.network.PacketToggleSettlementStorage;

/**
 * Adapts vanilla's Assign Storage UI to a non-canonical settlement level.
 *
 * Surface storage continues to use vanilla events/actions. On a cave level we mirror the
 * level-local storage positions into SettlementAssignWorkForm.storagePositions, which lets
 * vanilla HUD drawing, eyeball visibility and configuration affordances keep working.
 */
public final class SettlementStorageAssignUI {
	private static final Map<String, ArrayList<Point>> visibleStorage = new HashMap<>();
	private static final Map<String, Boolean> customLevel = new HashMap<>();
	private static final Field storagePositionsField;
	private static final Method updateHudElementsMethod;
	private static WeakReference<SettlementAssignWorkForm> activeForm = new WeakReference<>(null);

	static {
		try {
			storagePositionsField = SettlementAssignWorkForm.class.getDeclaredField("storagePositions");
			storagePositionsField.setAccessible(true);
			updateHudElementsMethod = SettlementAssignWorkForm.class.getDeclaredMethod("updateHudElements");
			updateHudElementsMethod.setAccessible(true);
		}
		catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	private SettlementStorageAssignUI() {
	}

	public static void onInit(SettlementAssignWorkForm form) {
		activeForm = new WeakReference<>(form);
		form.client.network.sendPacket(new PacketSettlementStorageRequest(form.container.getSettlementUniqueID()));
	}

	public static void applySync(int settlementUniqueID, int levelIdentifierHashCode, boolean isCustomLevel, List<Point> points) {
		String key = key(settlementUniqueID, levelIdentifierHashCode);
		customLevel.put(key, isCustomLevel);
		visibleStorage.put(key, new ArrayList<>(points));
		SettlementAssignWorkForm form = activeForm.get();
		if (form != null && form.container.getSettlementUniqueID() == settlementUniqueID
				&& form.client.getLevel().getIdentifierHashCode() == levelIdentifierHashCode) {
			applyPositions(form);
			refresh(form);
		}
	}

	public static boolean usesLevelAwareStorage(SettlementAssignWorkForm form) {
		if (form == null || form.client.getLevel() == null) return false;
		return Boolean.TRUE.equals(customLevel.get(key(form.container.getSettlementUniqueID(), form.client.getLevel().getIdentifierHashCode())));
	}

	public static void beforeHudUpdate(SettlementAssignWorkForm form) {
		if (usesLevelAwareStorage(form)) applyPositions(form);
	}

	public static boolean startAssignTool(SettlementAssignWorkForm form) {
		if (!usesLevelAwareStorage(form)) return false;
		GameToolManager.clearGameTools(form);
		GameToolManager.setGameTool(new SelectTileGameTool(form.client.getLevel(), new LocalMessage("ui", "settlementassignstorage")) {
			@Override
			public DrawOptions getIconTexture(Color color, int drawX, int drawY) {
				return form.getInterfaceStyle().storage.initDraw().color(color).posMiddle(drawX, drawY);
			}

			@Override
			public boolean onSelected(InputEvent event, TilePosition pos) {
				if (pos == null) return true;
				if (!event.state) return false;
				LevelObject master = (LevelObject)pos.object().getMasterLevelObject().orElse(null);
				if (master == null) return false;
				form.client.network.sendPacket(new PacketToggleSettlementStorage(
						form.client.getLevel().getIdentifierHashCode(), master.tileX, master.tileY,
						form.container.getSettlementUniqueID()));
				return true;
			}

			@Override
			public necesse.engine.localization.message.GameMessage isValidTile(TilePosition pos) {
				lastHoverBounds = null;
				LevelObject master = (LevelObject)pos.object().getMasterLevelObject().orElse(null);
				if (master == null) return new LocalMessage("ui", "settlementnotinventory");
				lastHoverBounds = master.getMultiTile().getTileRectangle(master.tileX, master.tileY);
				if (getVisiblePositions(form).contains(new Point(master.tileX, master.tileY))) {
					return new LocalMessage("ui", "settlementalreadyinventory");
				}
				ObjectEntity objectEntity = level.entityManager.getObjectEntity(master.tileX, master.tileY);
				if (objectEntity instanceof OEInventory) {
					if (((OEInventory)objectEntity).getSettlementStorage() != null) return null;
					return new LocalMessage("ui", "settlementcannotinventory");
				}
				return new LocalMessage("ui", "settlementnotinventory");
			}
		}, form);
		return true;
	}

	public static boolean openStorageConfig(SettlementAssignWorkForm form, int tileX, int tileY) {
		if (!usesLevelAwareStorage(form)) return false;
		form.requestedStorageConfigPos = new Point(tileX, tileY);
		form.client.network.sendPacket(new PacketOpenSettlementStorage(
				form.client.getLevel().getIdentifierHashCode(), tileX, tileY,
				form.container.getSettlementUniqueID()));
		return true;
	}

	public static void refreshCurrentForm(necesse.engine.network.client.Client client) {
		SettlementAssignWorkForm form = activeForm.get();
		if (form != null && form.client == client) {
			applyPositions(form);
			refresh(form);
		}
	}

	@SuppressWarnings("unchecked")
	private static ArrayList<Point> getVisiblePositions(SettlementAssignWorkForm form) {
		if (form == null || form.client.getLevel() == null) return new ArrayList<>();
		ArrayList<Point> points = visibleStorage.get(key(form.container.getSettlementUniqueID(), form.client.getLevel().getIdentifierHashCode()));
		return points == null ? new ArrayList<>() : points;
	}

	private static void applyPositions(SettlementAssignWorkForm form) {
		if (!usesLevelAwareStorage(form)) return;
		try {
			storagePositionsField.set(form, new ArrayList<>(getVisiblePositions(form)));
		}
		catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	private static void refresh(SettlementAssignWorkForm form) {
		try {
			updateHudElementsMethod.invoke(form);
		}
		catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	private static String key(int settlementUniqueID, int levelIdentifierHashCode) {
		return settlementUniqueID + ":" + levelIdentifierHashCode;
	}
}
