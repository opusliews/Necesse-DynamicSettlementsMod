package opusliews.object;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.List;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.localization.Localization;
import necesse.engine.registries.TileRegistry;
import necesse.engine.network.server.ServerClient;
import necesse.entity.mobs.Attacker;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawOptions.DrawOptions;
import necesse.gfx.drawables.OrderableDrawables;
import necesse.gfx.gameTexture.GameTexture;
import necesse.inventory.item.toolItem.ToolType;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import necesse.level.maps.light.GameLight;
import opusliews.deephole.DeepHoleSystem;
import opusliews.tile.DeepHoleTile;
import opusliews.tile.ShallowHoleTile;

public class HoleCaveLadderObject extends GameObject {
	public static final String stringID = "holecaveladder";

	private GameTexture texture;

	public HoleCaveLadderObject() {
		super();
		this.toolType = ToolType.ALL;
		this.toolTier = 0.0F;
		this.objectHealth = 40;
		this.drawDamage = true;
		this.attackThrough = true;
		this.isLightTransparent = true;
		this.collision = new Rectangle(0, 0, 0, 0);
		this.mapColor = new Color(138, 109, 37);
		this.setItemCategory("objects", "misc");
		this.setCraftingCategory("objects", "misc");
	}

	@Override
	public void loadTextures() {
		super.loadTextures();

		texture = GameTexture.fromFile("objects/ds_ladderdown");
	}

	@Override
	public GameTexture generateItemTexture() {
		GameTexture source = GameTexture.fromFile("objects/ds_ladderdown", true);
		GameTexture item = new GameTexture("hole cave ladder item", 32, 32);
		item.copy(source, 0, 0, 0, 0, Math.min(32, source.getWidth()), Math.min(32, source.getHeight()));
		item.makeFinal();
		return item;
	}

	@Override
	public String canPlace(Level level, int layerID, int x, int y, int rotation, boolean byPlayer, boolean ignoreOtherLayers) {
		if (layerID != 0) return "wrongtile";
		if (DeepHoleSystem.isShaftTransitionAt(level, x, y)) return "tilecovered";
		if (level.getObjectID(x, y) != 0) return "tilecovered";

		int tileID = level.getTileID(x, y);
		int shallowHoleID = TileRegistry.getTileID(ShallowHoleTile.stringID);
		int deepHoleID = TileRegistry.getTileID(DeepHoleTile.stringID);
		return tileID == shallowHoleID || tileID == deepHoleID ? null : "wrongtile";
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
	public boolean isValid(Level level, int layerID, int x, int y) {
		if (layerID != 0) return false;
		int tileID = level.getTileID(x, y);
		return tileID == TileRegistry.getTileID(ShallowHoleTile.stringID)
				|| tileID == TileRegistry.getTileID(DeepHoleTile.stringID);
	}

	@Override
	public boolean canInteract(Level level, int x, int y, PlayerMob player) {
		return true;
	}

	@Override
	public String getInteractTip(Level level, int x, int y, PlayerMob perspective, boolean debug) {
		return Localization.translate("controls", "usetip");
	}

	@Override
	public void interact(Level level, int x, int y, PlayerMob player) {
		if (level.isServer() && DeepHoleSystem.tryStartSafeLadderDescent(player, x, y)) return;
		super.interact(level, x, y, player);
	}

	@Override
	public void addDrawables(List list, OrderableDrawables tileList, Level level, int tileX, int tileY,
			TickManager tickManager, GameCamera camera, PlayerMob perspective) {
		GameLight light = level.getLightLevel(tileX, tileY);
		int drawX = camera.getTileDrawX(tileX) - texture.getWidth() / 2 + 16;
		int drawY = camera.getTileDrawY(tileY) - (texture.getHeight() - 32);

		DrawOptions options = texture.initDraw()
				.addObjectDamageOverlay(this, level, tileX, tileY)
				.light(light)
				.pos(drawX, drawY);

		tileList.add(tm -> options.draw());
	}

	@Override
	public void drawPreview(Level level, int tileX, int tileY, int rotation, float alpha, PlayerMob player, GameCamera camera) {
		int drawX = camera.getTileDrawX(tileX) - texture.getWidth() / 2 + 16;
		int drawY = camera.getTileDrawY(tileY) - (texture.getHeight() - 32);
		texture.initDraw().alpha(alpha).pos(drawX, drawY).draw();
	}
}
