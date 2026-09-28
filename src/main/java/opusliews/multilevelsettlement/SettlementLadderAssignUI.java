package opusliews.multilevelsettlement;

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.lang.ref.WeakReference;

import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.gameTool.GameToolManager;
import necesse.engine.input.InputEvent;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.util.EventVariable;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawOptions.DrawOptions;
import necesse.gfx.drawOptions.DrawOptionsList;
import necesse.gfx.drawables.SortedDrawable;
import necesse.gfx.forms.components.FormComponent;
import necesse.gfx.forms.components.FormContentBox;
import necesse.gfx.forms.components.FormFlow;
import necesse.gfx.forms.components.localComponents.FormLocalTextButton;
import necesse.gfx.forms.position.FormPositionContainer;
import necesse.gfx.forms.presets.containerComponent.settlement.SelectTileGameTool;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementAssignWorkForm;
import necesse.gfx.ui.HUD;
import necesse.level.maps.Level;
import necesse.level.maps.LevelObject;
import necesse.level.maps.TilePosition;
import necesse.level.maps.hudManager.HudDrawElement;
import necesse.level.maps.multiTile.MultiTile;
import opusliews.network.PacketSettlementLadderRequest;
import opusliews.network.PacketToggleSettlementLadder;

public final class SettlementLadderAssignUI {
	private static final int rowHeight = 40;
	private static final int ladderRowY = 75;
	private static final EventVariable<Boolean> hideLadders = new EventVariable<>(false);
	private static final Map<String, ArrayList<Point>> visibleEndpoints = new HashMap<>();
	private static final Field hudElementsField;
	private static final Method updateHudElementsMethod;
	private static WeakReference<SettlementAssignWorkForm> activeForm = new WeakReference<>(null);

	static {
		try {
			hudElementsField = SettlementAssignWorkForm.class.getDeclaredField("hudElements");
			hudElementsField.setAccessible(true);
			updateHudElementsMethod = SettlementAssignWorkForm.class.getDeclaredMethod("updateHudElements");
			updateHudElementsMethod.setAccessible(true);
		}
		catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	private SettlementLadderAssignUI() {
	}

	public static void addAssignButton(SettlementAssignWorkForm form, FormFlow flow, FormContentBox content) {
		for (Object object : content.getComponents()) {
			if (!(object instanceof FormPositionContainer)) continue;
			FormPositionContainer positioned = (FormPositionContainer)object;
			if (positioned.getY() >= ladderRowY) positioned.setY(positioned.getY() + rowHeight);
		}

		FormLocalTextButton button = content.addComponent(new FormLocalTextButton(
				"ui", "settlementassigncaveaccessladder", 16, ladderRowY, content.getWidth() - 32 - 32));
		button.onClicked(e -> startAssignTool(form));
		button.setLocalTooltip(new LocalMessage("ui", "settlementcaveaccessladdertip"));
		content.addComponent(form.getHideButton(
				button.getX() + button.getWidth(), button.getY(), hideLadders,
				new LocalMessage("ui", "hidebutton"), new LocalMessage("ui", "showbutton")));

		flow.next(rowHeight);
		int requiredHeight = 0;
		for (Object object : content.getComponents()) {
			if (!(object instanceof FormComponent)) continue;
			Rectangle bounds = ((FormComponent)object).getBoundingBox();
			requiredHeight = Math.max(requiredHeight, bounds.y + bounds.height + 8);
		}
		requiredHeight = Math.max(requiredHeight, form.work.getHeight());
		form.work.setHeight(requiredHeight);
		content.setHeight(requiredHeight);
		content.setContentBox(new Rectangle(0, 0, content.getWidth(), requiredHeight));
	}

	public static void onInit(SettlementAssignWorkForm form) {
		activeForm = new WeakReference<>(form);
		form.client.network.sendPacket(new PacketSettlementLadderRequest(form.container.getSettlementUniqueID()));
	}

	public static void applySync(int settlementUniqueID, int levelIdentifierHashCode, List<Point> points) {
		visibleEndpoints.put(key(settlementUniqueID, levelIdentifierHashCode), new ArrayList<>(points));
	}

	public static void refreshCurrentForm(necesse.engine.network.client.Client client) {
		SettlementAssignWorkForm form = activeForm.get();
		if (form != null && form.client == client) refresh(form);
	}

	public static void refresh(SettlementAssignWorkForm form) {
		try {
			updateHudElementsMethod.invoke(form);
		}
		catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public static void addHudElements(SettlementAssignWorkForm form) {
		if (!form.containerForm.isCurrent(form)) return;
		Level level = form.client.getLevel();
		ArrayList<Point> points = visibleEndpoints.get(key(form.container.getSettlementUniqueID(), level.getIdentifierHashCode()));
		if (points == null || points.isEmpty()) return;

		List<HudDrawElement> hudElements;
		try {
			hudElements = (List<HudDrawElement>)hudElementsField.get(form);
		}
		catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}

		for (Point tile : points) {
			LevelObject levelObject = level.getLevelObject(tile.x, tile.y);
			if (!SettlementLadderSystem.isSupportedLadderObject(levelObject.object)) continue;
			MultiTile multiTile = levelObject.getMultiTile();
			HudDrawElement hudElement = new HudDrawElement() {
				@Override
				public void addDrawables(List list, GameCamera camera, PlayerMob perspective) {
					if ((Boolean)hideLadders.get()) return;
					DrawOptionsList options = new DrawOptionsList();
					options.add(HUD.tileBoundOptions(camera, Color.WHITE, false, multiTile.getTileRectangle(tile.x, tile.y)));
					list.add(new SortedDrawable() {
						@Override public int getPriority() { return -1000000; }
						@Override public void draw(TickManager tickManager) { options.draw(); }
					});
				}
			};
			level.hudManager.addElement(hudElement);
			hudElements.add(hudElement);
		}
	}

	public static void startAssignTool(SettlementAssignWorkForm form) {
		GameToolManager.clearGameTools(form);
		GameToolManager.setGameTool(new SelectTileGameTool(form.client.getLevel(), new LocalMessage("ui", "settlementassigncaveaccessladder")) {
			@Override
			public DrawOptions getIconTexture(Color color, int drawX, int drawY) {
				return null;
			}

			@Override
			public boolean onSelected(InputEvent event, TilePosition pos) {
				if (pos == null) return true;
				if (!event.state) return false;
				LevelObject master = (LevelObject)pos.object().getMasterLevelObject().orElse(null);
				if (master == null) return false;
				form.client.network.sendPacket(new PacketToggleSettlementLadder(
						form.client.getLevel().getIdentifierHashCode(), master.tileX, master.tileY,
						true, form.container.getSettlementUniqueID()));
				return true;
			}

			@Override
			public necesse.engine.localization.message.GameMessage isValidTile(TilePosition pos) {
				lastHoverBounds = null;
				LevelObject master = (LevelObject)pos.object().getMasterLevelObject().orElse(null);
				if (master == null || !SettlementLadderSystem.isSupportedLadderObject(master.object)) {
					return new LocalMessage("ui", "settlementnotcaveaccessladder");
				}
				lastHoverBounds = master.getMultiTile().getTileRectangle(master.tileX, master.tileY);
				return null;
			}
		}, form);
	}

	private static String key(int settlementUniqueID, int levelIdentifierHashCode) {
		return settlementUniqueID + ":" + levelIdentifierHashCode;
	}
}
