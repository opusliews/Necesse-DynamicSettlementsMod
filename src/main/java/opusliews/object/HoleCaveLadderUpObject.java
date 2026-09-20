package opusliews.object;

import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.network.packet.PacketChangeObject;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ObjectRegistry;
import necesse.engine.registries.TileRegistry;
import necesse.engine.util.LevelIdentifier;
import necesse.entity.mobs.Attacker;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.objectEntity.LadderUpObjectEntity;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.entity.objectEntity.PortalObjectEntity;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawOptions.texture.TextureDrawOptions;
import necesse.gfx.drawables.LevelSortedDrawable;
import necesse.gfx.drawables.OrderableDrawables;
import necesse.gfx.gameTexture.GameSprite;
import necesse.gfx.gameTexture.GameTexture;
import necesse.inventory.item.toolItem.ToolType;
import necesse.inventory.lootTable.LootTable;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import necesse.level.maps.light.GameLight;
import opusliews.deephole.DeepHoleSystem;
import opusliews.tile.CharcoalPitSystem;
import opusliews.tile.DeepHoleTile;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static opusliews.charcoal.CharcoalProductionZone.cardinalOffsets;

public class HoleCaveLadderUpObject extends GameObject {
	public static final String stringID = "holecaveladderup";

	private GameTexture texture;

	public HoleCaveLadderUpObject() {
		this.mapColor = new Color(138, 109, 37);
		this.toolType = ToolType.AXE;
		this.toolTier = 0.0F;
		this.objectHealth = 40;
		this.drawDamage = true;
		this.isLightTransparent = true;
		this.lightLevel = 75;
	}

	@Override
	public GameMessage getNewLocalization() {
		return new LocalMessage("object", "caveladder");
	}

	@Override
	public void loadTextures() {
		super.loadTextures();
		texture = GameTexture.fromFile("objects/ladderup");
	}

	@Override
	public LootTable getLootTable(Level level, int layerID, int tileX, int tileY) {
		return ObjectRegistry.getObject(HoleCaveLadderObject.stringID).getLootTable(level, layerID, tileX, tileY);
	}

	@Override
	public void addDrawables(List list, OrderableDrawables tileList, Level level, int tileX, int tileY,
			TickManager tickManager, GameCamera camera, PlayerMob perspective) {
		GameLight light = level.getLightLevel(tileX, tileY);
		int drawX = camera.getTileDrawX(tileX) - texture.getWidth() / 2 + 16;
		int drawY = camera.getTileDrawY(tileY) - (texture.getHeight() - 32) + 32;
		TextureDrawOptions options = texture.initDraw()
				.section(0, texture.getWidth(), 32, texture.getHeight())
				.light(light)
				.pos(drawX, drawY);

		list.add(new LevelSortedDrawable(this, tileX, tileY) {
			@Override
			public int getSortY() {
				return 16;
			}

			@Override
			public void draw(TickManager tickManager) {
				options.draw();
			}
		});
	}

	@Override
	public void drawPreview(Level level, int tileX, int tileY, int rotation, float alpha, PlayerMob player, GameCamera camera) {
		GameLight light = level.getLightLevel(tileX, tileY);
		int drawX = camera.getTileDrawX(tileX) - texture.getWidth() / 2 + 16;
		int drawY = camera.getTileDrawY(tileY) - (texture.getHeight() - 32) + 32;
		texture.initDraw().section(0, texture.getWidth(), 32, texture.getHeight()).light(light).alpha(alpha).draw(drawX, drawY);
	}

	@Override
	public String canPlace(Level level, int layerID, int x, int y, int rotation, boolean byPlayer, boolean ignoreOtherLayers) {
		if (!level.isBasicCaveLevel() && !level.isDeepCaveLevel()) return "invalidlevel";

		String error = super.canPlace(level, layerID, x, y, rotation, byPlayer, ignoreOtherLayers);
		if (error != null) return error;
		if (hasAdjacentShaftEntrance(level, x, y)) return "tilecovered";

		if (level.isServer()) {
			LevelIdentifier upperIdentifier = DeepHoleSystem.getUpperLevelIdentifier(level);
			if (upperIdentifier == null) return "invalidlevel";
			Level upper = level.getServer().world.getLevel(upperIdentifier);
			if (upper == null) return "invalidlevel";

			upper.regionManager.ensureTilesAreLoaded(x - 1, y - 1, x + 1, y + 1);
			if (DeepHoleSystem.isShaftTransitionAt(upper, x, y)) return "tilecovered";
			if (hasAdjacentPit(upper, x, y)) return "tilecovered";
			if (upper.preventsLadderPlacement(x, y) != null) return "tilecovered";
		}

		return null;
	}

	@Override
	public void placeObject(Level level, int layerID, int x, int y, int rotation, boolean byPlayer) {
		super.placeObject(level, layerID, x, y, rotation, byPlayer);
		if (!level.isServer() || layerID != 0) return;

		LevelIdentifier upperIdentifier = DeepHoleSystem.getUpperLevelIdentifier(level);
		if (upperIdentifier == null) return;
		Level upper = level.getServer().world.getLevel(upperIdentifier);
		if (upper == null) return;

		upper.regionManager.ensureTileIsLoaded(x, y);
		int deepHoleID = TileRegistry.getTileID(DeepHoleTile.stringID);
		int ladderID = ObjectRegistry.getObjectID(HoleCaveLadderObject.stringID);

		if (upper.getTileID(x, y) == deepHoleID && upper.getObjectID(x, y) == ladderID) return;

		if (upper.getObjectID(x, y) != 0) upper.entityManager.destroyObjectOverride(0, x, y);
		upper.setTile(x, y, deepHoleID);
		upper.sendTileUpdatePacket(x, y);

		GameObject ladder = ObjectRegistry.getObject(ladderID);
		ladder.placeObject(upper, x, y, 0, true);
		level.getServer().network.sendToClientsWithTile(new PacketChangeObject(upper, 0, x, y, ladderID), upper, x, y);
	}

	private static boolean hasAdjacentShaftEntrance(Level level, int tileX, int tileY) {
		int customLadderUpID = ObjectRegistry.getObjectID(HoleCaveLadderUpObject.stringID);
		int vanillaLadderUpID = ObjectRegistry.getObjectID("ladderup");
		int vanillaDeepLadderID = ObjectRegistry.getObjectID("deepcaveladder");
		int ceilingLightID = ObjectRegistry.getObjectID(DeepHoleCeilingLightObject.stringID);

		for (int[] offset : cardinalOffsets) {
			int checkX = tileX + offset[0];
			int checkY = tileY + offset[1];

			if (!level.isTileWithinBounds(checkX, checkY)) {
				continue;
			}

			int objectID = level.getObjectID(checkX, checkY);
			if (objectID == customLadderUpID || objectID == vanillaLadderUpID || objectID == vanillaDeepLadderID || objectID == ceilingLightID) return true;
		}

		return false;
	}


	private static boolean hasAdjacentPit(Level level, int tileX, int tileY) {
		for (int[] offset : cardinalOffsets) {
			int checkX = tileX + offset[0];
			int checkY = tileY + offset[1];
			if (level.isTileWithinBounds(checkX, checkY) && CharcoalPitSystem.isPitTile(level, checkX, checkY)) return true;
		}
		return false;
	}

	@Override
	public boolean onDamaged(Level level, int layerID, int x, int y, int damage, Attacker attacker, ServerClient client, boolean showEffect, int mouseX, int mouseY) {
		if (DeepHoleSystem.isShaftTransitionAt(level, x, y)) return false;
		return super.onDamaged(level, layerID, x, y, damage, attacker, client, showEffect, mouseX, mouseY);
	}

	@Override
	public void doExplosionDamage(Level level, int layerID, int tileX, int tileY, int damage, float toolTier, Attacker attacker, ServerClient client) {
		if (DeepHoleSystem.isShaftTransitionAt(level, tileX, tileY)) return;
		super.doExplosionDamage(level, layerID, tileX, tileY, damage, toolTier, attacker, client);
	}

	@Override
	public boolean canInteract(Level level, int x, int y, PlayerMob player) {
		return true;
	}

	@Override
	public void interact(Level level, int x, int y, PlayerMob player) {
		if (level.isServer() && player.isServerClient()) {
			ObjectEntity objectEntity = level.entityManager.getObjectEntity(x, y);
			if (objectEntity instanceof PortalObjectEntity) {
				((PortalObjectEntity)objectEntity).use(level.getServer(), player.getServerClient());
			}
		}
		super.interact(level, x, y, player);
	}

	@Override
	public void onDestroyed(Level level, int layerID, int x, int y, Attacker attacker, ServerClient client, ArrayList itemsDropped) {
		if (level.isServer()) {
			ObjectEntity objectEntity = level.entityManager.getObjectEntity(x, y);
			if (objectEntity instanceof PortalObjectEntity) {
				PortalObjectEntity portal = (PortalObjectEntity)objectEntity;
				if (level.getServer().world.levelExists(portal.getDestinationIdentifier())) {
					Level surface = level.getServer().world.getLevel(portal.getDestinationIdentifier());
					surface.regionManager.ensureTileIsLoaded(portal.destinationTileX, portal.destinationTileY);
					if (surface.getObjectID(portal.destinationTileX, portal.destinationTileY) == ObjectRegistry.getObjectID(HoleCaveLadderObject.stringID)) {
						surface.setObject(portal.destinationTileX, portal.destinationTileY, 0);
						surface.replaceObjectEntity(portal.destinationTileX, portal.destinationTileY);
						level.getServer().network.sendToClientsWithTile(
								new PacketChangeObject(surface, 0, portal.destinationTileX, portal.destinationTileY, 0),
								surface,
								portal.destinationTileX,
								portal.destinationTileY
						);

						if (surface.isBasicCaveLevel() && surface.getTileID(portal.destinationTileX, portal.destinationTileY) == TileRegistry.getTileID(DeepHoleTile.stringID)) {
							surface.setTile(portal.destinationTileX, portal.destinationTileY, TileRegistry.dirtID);
							surface.sendTileUpdatePacket(portal.destinationTileX, portal.destinationTileY);
						}
					}
				}
			}
		}
		super.onDestroyed(level, layerID, x, y, attacker, client, itemsDropped);
	}

	@Override
	public ObjectEntity getNewObjectEntity(Level level, int x, int y) {
		LevelIdentifier upperIdentifier = DeepHoleSystem.getUpperLevelIdentifier(level);
		if (upperIdentifier == null) upperIdentifier = LevelIdentifier.SURFACE_IDENTIFIER;
		return new LadderUpObjectEntity(
				stringID,
				level,
				x,
				y,
				upperIdentifier,
				ObjectRegistry.getObjectID(HoleCaveLadderObject.stringID),
				texture == null ? null : new GameSprite(texture, 0, 0, 32)
		);
	}

}
