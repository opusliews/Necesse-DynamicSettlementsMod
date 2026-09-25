package opusliews.object;

import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.localization.Localization;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.TileRegistry;
import necesse.entity.mobs.Attacker;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawOptions.DrawOptions;
import necesse.gfx.drawables.OrderableDrawables;
import necesse.gfx.gameTexture.GameTexture;
import necesse.inventory.item.toolItem.ToolType;
import necesse.inventory.lootTable.LootTable;
import necesse.inventory.lootTable.lootItem.LootItem;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import necesse.level.maps.light.GameLight;
import opusliews.logging.Logging;
import opusliews.tile.ShallowHoleTile;
import opusliews.trapdoor.TrapdoorSystem;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class TrapdoorObject extends GameObject {
	public static final String openStringID = "trapdoor";
	public static final String closedStringID = "closedtrapdoor";

	private final boolean closed;
	private GameTexture texture;

	public TrapdoorObject(boolean closed) {
		super();
		this.closed = closed;
		this.toolType = ToolType.AXE;
		this.toolTier = 0.0F;
		this.objectHealth = 40;
		this.drawDamage = true;
		this.attackThrough = false;
		this.isLightTransparent = true;
		this.mapColor = new Color(105, 71, 39);
		this.setItemCategory("objects", "misc");
		this.setCraftingCategory("objects", "misc");
	}

	public boolean isClosed() {
		return closed;
	}

	@Override
	public void loadTextures() {
		super.loadTextures();
		texture = GameTexture.fromFile(closed ? "objects/trapdoor_closed" : "objects/trapdoor_open");
	}

	@Override
	public GameTexture generateItemTexture() {
		GameTexture source = GameTexture.fromFile("objects/trapdoor_closed", true);
		GameTexture item = new GameTexture("trapdoor item", 32, 32);
		item.copy(source, 0, 0, 0, 0, 32, 32);
		item.makeFinal();
		return item;
	}

	@Override
	public String canPlace(Level level, int layerID, int x, int y, int rotation, boolean byPlayer, boolean ignoreOtherLayers) {
		if (closed) return "tilecovered";
		if (layerID != 0 || level.getTileID(x, y) != TileRegistry.getTileID(ShallowHoleTile.stringID)) return "wrongtile";
		if (level.getObjectID(x, y) != 0) return "tilecovered";
		return null	;
	}

	@Override
	public boolean isValid(Level level, int layerID, int x, int y) {
		return layerID == 0 && level.getTileID(x, y) == TileRegistry.getTileID(ShallowHoleTile.stringID);
	}

	@Override
	public void addDrawables(List list, OrderableDrawables tileList, Level level, int tileX, int tileY,
							 TickManager tickManager, GameCamera camera, PlayerMob perspective) {
		GameLight light = level.getLightLevel(tileX, tileY);
		int drawX = camera.getTileDrawX(tileX);
		int drawY = camera.getTileDrawY(tileY);

		DrawOptions options = texture.initDraw()
				.addObjectDamageOverlay(this, level, tileX, tileY)
				.light(light)
				.pos(drawX, drawY);

		tileList.add(tickManager1 -> options.draw());
	}

	@Override
	public void drawPreview(Level level, int tileX, int tileY, int rotation, float alpha, PlayerMob player, GameCamera camera) {
		texture.initDraw().alpha(alpha).pos(camera.getTileDrawX(tileX), camera.getTileDrawY(tileY)).draw();
	}

	@Override
	public void tick(Level level, int x, int y) {
		if (!closed || !level.isServer()) return;
		if (TrapdoorSystem.hasHiddenPlayerAt(level, x, y)) return;

		level.setObject(x, y, necesse.engine.registries.ObjectRegistry.getObjectID(openStringID), 0);
		level.sendObjectUpdatePacket(x, y);
	}

	@Override
	public boolean canInteract(Level level, int x, int y, PlayerMob player) {
		return player.getTileX() == x && player.getTileY() == y;
	}

	@Override
	public String getInteractTip(Level level, int x, int y, PlayerMob perspective, boolean debug) {
		return Localization.translate("controls", "usetip");
	}

	@Override
	public void interact(Level level, int x, int y, PlayerMob player) {
		if (Logging.logEnabled) {
			Logging.logMessage(
					"[TrapdoorObject] interact called: object=" + x + "," + y
							+ " player=" + player.getTileX() + "," + player.getTileY()
							+ " server=" + level.isServer()
							+ " closed=" + closed
							+ " hidden=" + TrapdoorSystem.isHidden(player)
			);
		}

		if (player.getTileX() != x || player.getTileY() != y) {
			if (Logging.logEnabled) Logging.logMessage("[TrapdoorObject] interact aborted: player is not on trapdoor tile");
			return;
		}

		if (!level.isServer()) {
			if (Logging.logEnabled) Logging.logMessage("[TrapdoorObject] interact client-side call ignored; waiting for server interaction");
			return;
		}

		if (closed) {
			if (TrapdoorSystem.isHidden(player)) {
				if (Logging.logEnabled) Logging.logMessage("[TrapdoorObject] Exiting trapdoor");
				TrapdoorSystem.exitTrapdoor(level, x, y, player);
			} else if (Logging.logEnabled) {
				Logging.logMessage("[TrapdoorObject] Closed trapdoor interaction ignored because player is not hidden");
			}
		} else {
			if (!TrapdoorSystem.isHidden(player)) {
				if (Logging.logEnabled) Logging.logMessage("[TrapdoorObject] Entering trapdoor");
				TrapdoorSystem.enterTrapdoor(level, x, y, player);
			} else if (Logging.logEnabled) {
				Logging.logMessage("[TrapdoorObject] Open trapdoor interaction ignored because player is already hidden");
			}
		}
	}

	@Override
	public LootTable getLootTable(Level level, int layerID, int tileX, int tileY) {
		return new LootTable(new LootItem(openStringID));
	}

	@Override
	public void doExplosionDamage(Level level, int layerID, int tileX, int tileY, int damage, float toolTier, Attacker attacker, ServerClient client) {
		level.entityManager.doObjectDamage(layerID, tileX, tileY, objectHealth, Float.MAX_VALUE, attacker, client);
	}

	@Override
	public void onDestroyed(Level level, int layerID, int x, int y, Attacker attacker, ServerClient client, ArrayList itemsDropped) {
		if (level.isServer()) TrapdoorSystem.revealHiddenPlayerAt(level, x, y);
		super.onDestroyed(level, layerID, x, y, attacker, client, itemsDropped);
	}
}
