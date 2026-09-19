package opusliews.object;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
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
import opusliews.tile.DeepHoleTile;

public class HoleCaveLadderUpObject extends GameObject {
	public static final String stringID = "holecaveladderup";

	private GameTexture texture;

	public HoleCaveLadderUpObject() {
		this.mapColor = new Color(138, 109, 37);
		this.toolType = ToolType.ALL;
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
		if (!level.getIdentifier().equals(LevelIdentifier.CAVE_IDENTIFIER)) return "invalidlevel";

		String error = super.canPlace(level, layerID, x, y, rotation, byPlayer, ignoreOtherLayers);
		if (error != null) return error;

		if (level.isServer()) {
			Level surface = level.getServer().world.getLevel(LevelIdentifier.SURFACE_IDENTIFIER);
			if (surface == null) return "invalidlevel";

			surface.regionManager.ensureTileIsLoaded(x, y);
			if (DeepHoleSystem.isShaftTransitionAt(surface, x, y)) return "tilecovered";
			if (surface.preventsLadderPlacement(x, y) != null) return "tilecovered";
		}

		return null;
	}

	@Override
	public void placeObject(Level level, int layerID, int x, int y, int rotation, boolean byPlayer) {
		super.placeObject(level, layerID, x, y, rotation, byPlayer);
		if (!level.isServer() || layerID != 0) return;

		Level surface = level.getServer().world.getLevel(LevelIdentifier.SURFACE_IDENTIFIER);
		if (surface == null) return;

		surface.regionManager.ensureTileIsLoaded(x, y);
		int deepHoleID = TileRegistry.getTileID(DeepHoleTile.stringID);
		int ladderID = ObjectRegistry.getObjectID(HoleCaveLadderObject.stringID);

		if (surface.getTileID(x, y) == deepHoleID && surface.getObjectID(x, y) == ladderID) return;

		if (surface.getObjectID(x, y) != 0) {
			surface.entityManager.destroyObjectOverride(0, x, y);
		}

		surface.setTile(x, y, deepHoleID);
		surface.sendTileUpdatePacket(x, y);

		GameObject ladder = ObjectRegistry.getObject(ladderID);
		ladder.placeObject(surface, x, y, 0, true);
		level.getServer().network.sendToClientsWithTile(
				new PacketChangeObject(surface, 0, x, y, ladderID),
				surface,
				x,
				y
		);
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
					}
				}
			}
		}
		super.onDestroyed(level, layerID, x, y, attacker, client, itemsDropped);
	}

	@Override
	public ObjectEntity getNewObjectEntity(Level level, int x, int y) {
		return new LadderUpObjectEntity(
				stringID,
				level,
				x,
				y,
				LevelIdentifier.SURFACE_IDENTIFIER,
				ObjectRegistry.getObjectID(HoleCaveLadderObject.stringID),
				texture == null ? null : new GameSprite(texture, 0, 0, 32)
		);
	}
}
