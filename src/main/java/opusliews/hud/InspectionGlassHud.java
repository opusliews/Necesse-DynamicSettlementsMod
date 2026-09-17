package opusliews.hud;

import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.network.client.Client;
import necesse.engine.state.MainGame;
import necesse.engine.util.GameMath;
import necesse.entity.DamagedObjectEntity;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.Renderer;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawables.SortedDrawable;
import necesse.inventory.InventoryItem;
import necesse.level.gameObject.GameObject;
import necesse.level.gameTile.GameTile;
import necesse.level.maps.Level;
import opusliews.damage.DamageRepairLevelData;
import opusliews.damage.MaterialWeatheringClassifier;
import opusliews.damage.WeatheringMaterialTier;
import necesse.level.maps.hudManager.HudDrawElement;
import opusliews.network.PacketRequestInspectionGlassData;

import java.awt.Point;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class InspectionGlassHud {
	private static final Set<Level> addedLevels = Collections.newSetFromMap(new WeakHashMap<>());
	private static final Map<Level, Map<Long, Integer>> reinforcementByLevel = new WeakHashMap<>();
	private static final Map<Level, Long> nextRequestTimes = new WeakHashMap<>();
	private static final Map<Level, Boolean> activeByLevel = new WeakHashMap<>();
	private static final long requestInterval = 500L;
	private static final int inspectionRadius = 12;

	private InspectionGlassHud() {
	}


	public static void frameTick(MainGame mainGame) {
		if (mainGame == null || mainGame.getClient() == null) {
			return;
		}

		Client client = mainGame.getClient();
		Level level = client.getLevel();
		PlayerMob player = client.getPlayer();

		if (level == null || player == null) {
			return;
		}

		boolean active = isInspectionGlassSelected(player);
		activeByLevel.put(level, active);

		if (!active) {
			return;
		}

		requestServerData(level);
	}

	public static void ensureAdded(Level level) {
		if (level == null || addedLevels.contains(level)) {
			return;
		}

		HudDrawElement element = new HudDrawElement() {
			@Override
			public void addDrawables(
					List<SortedDrawable> list,
					GameCamera camera,
					PlayerMob perspective
			) {
				list.add(new SortedDrawable() {
					@Override
					public int getPriority() {
						return -1500000;
					}

					@Override
					public void draw(TickManager tickManager) {
						drawInspectionHud(level, camera, perspective);
					}
				});
			}
		};

		level.hudManager.addElement(element);
		addedLevels.add(level);
	}

	public static void applyServerData(
			Level level,
			int startX,
			int startY,
			int endX,
			int endY,
			Map<Long, Integer> reinforcement
	) {
		Map<Long, Integer> cache = reinforcementByLevel.computeIfAbsent(level, key -> new HashMap<>());

		cache.entrySet().removeIf(entry -> {
			int x = GameMath.getXFromUniqueLongKey(entry.getKey());
			int y = GameMath.getYFromUniqueLongKey(entry.getKey());
			return x >= startX && x <= endX && y >= startY && y <= endY;
		});

		cache.putAll(reinforcement);
	}

	private static void drawInspectionHud(
			Level level,
			GameCamera camera,
			PlayerMob player
	) {
		if (!activeByLevel.getOrDefault(level, false)) {
			return;
		}

		int centerX = player.getTileX();
		int centerY = player.getTileY();
		int radiusSquared = inspectionRadius * inspectionRadius;

		Map<Long, Integer> reinforcement =
				reinforcementByLevel.getOrDefault(
						level,
						Collections.emptyMap()
				);

		for (Map.Entry<Long, Integer> entry : reinforcement.entrySet()) {
			int x = GameMath.getXFromUniqueLongKey(entry.getKey());
			int y = GameMath.getYFromUniqueLongKey(entry.getKey());

			int dx = x - centerX;
			int dy = y - centerY;

			if (dx * dx + dy * dy > radiusSquared) {
				continue;
			}

			int drawX = camera.getTileDrawX(x);
			int drawY = camera.getTileDrawY(y);

			drawReinforcementIndicator(drawX, drawY, entry.getValue());
			drawHealthIndicator(drawX, drawY, getHealthRatio(level, x, y));
		}
	}

	private static boolean isInspectionGlassSelected(PlayerMob player) {
		InventoryItem selected = player.getSelectedHotbarItem();
		return selected != null && "inspectionglass".equals(selected.item.getStringID());
	}

	private static void requestServerData(Level level) {
		if (!level.isClient()) {
			return;
		}

		long currentTime = level.getTime();
		long nextRequestTime = nextRequestTimes.getOrDefault(level, 0L);

		if (currentTime < nextRequestTime) {
			return;
		}

		nextRequestTimes.put(level, currentTime + requestInterval);

		level.getClient().network.sendPacket(
				new PacketRequestInspectionGlassData()
		);
	}

	private static void drawReinforcementIndicator(int drawX, int drawY, int reinforcement) {
		int iconX = drawX + 1;
		int iconY = drawY + 13;

		drawQuad(iconX, iconY, 9, 18, 0, 0, 0);
		drawQuad(iconX + 1, iconY + 1, 7, 16, 209, 248, 255);

		int filled = Math.max(0, Math.min(4, reinforcement));

		for (int i = 0; i < 4; i++) {
			boolean reinforced = i < filled;
			int pipY = iconY + 14 - i * 4;

			if (reinforced) {
				drawQuad(iconX + 2, pipY, 5, 2, 0, 255, 0);
			} else {
				drawQuad(iconX + 2, pipY, 5, 2, 180, 180, 180);
			}
		}
	}

	private static float getHealthRatio(Level level, int tileX, int tileY) {
		float healthRatio = 1.0F;
		boolean found = false;

		GameTile tile = level.getTile(tileX, tileY);
		WeatheringMaterialTier tileTier = MaterialWeatheringClassifier.getTileTier(tile);

		if (tileTier != null && tileTier.isWeatherable() && level.tileLayer.isPlayerPlaced(tileX, tileY)) {
			DamagedObjectEntity damaged = level.entityManager.getDamagedObjectEntity(tileX, tileY);
			int damage = damaged == null ? 0 : damaged.tileDamage;
			healthRatio = getRemainingHealthRatio(tile.tileHealth, damage);
			found = true;
		}

		for (int layerID : necesse.engine.registries.ObjectLayerRegistry.getLayerIDs()) {
			GameObject object = level.getObject(layerID, tileX, tileY);
			WeatheringMaterialTier objectTier = MaterialWeatheringClassifier.getObjectTier(object);

			if (objectTier == null || !objectTier.isWeatherable()) {
				continue;
			}

			Point masterTile = DamageRepairLevelData.getObjectMasterTile(level, layerID, tileX, tileY);
			GameObject masterObject = level.getObject(layerID, masterTile.x, masterTile.y);

			if (masterObject.getID() == 0 || !level.objectLayer.isPlayerPlaced(layerID, masterTile.x, masterTile.y)) {
				continue;
			}

			DamagedObjectEntity damaged = level.entityManager.getDamagedObjectEntity(masterTile.x, masterTile.y);
			int damage = damaged == null ? 0 : damaged.getObjectDamage(layerID);
			float objectHealthRatio = getRemainingHealthRatio(masterObject.objectHealth, damage);

			healthRatio = found ? Math.min(healthRatio, objectHealthRatio) : objectHealthRatio;
			found = true;
		}

		return found ? healthRatio : 1.0F;
	}

	private static float getRemainingHealthRatio(int maxHealth, int damage) {
		if (maxHealth <= 0) {
			return 1.0F;
		}

		return Math.max(0.0F, Math.min(1.0F, (maxHealth - damage) / (float)maxHealth));
	}

	private static void drawHealthIndicator(int drawX, int drawY, float healthRatio) {
		int iconX = drawX + 22;
		int iconY = drawY + 13;

		drawQuad(iconX, iconY, 9, 18, 0, 0, 0);
		drawQuad(iconX + 1, iconY + 1, 7, 16, 209, 248, 255);

		int barX = iconX + 2;
		int barY = iconY + 2;
		int barWidth = 5;
		int barHeight = 14;
		int filledHeight = Math.max(0, Math.min(barHeight, Math.round(barHeight * healthRatio)));
		int emptyHeight = barHeight - filledHeight;

		if (emptyHeight > 0) {
			drawQuad(barX, barY, barWidth, emptyHeight, 180, 180, 180);
		}

		if (filledHeight > 0) {
			int red = Math.round(255.0F * (1.0F - healthRatio));
			int green = Math.round(255.0F * healthRatio);
			drawQuad(barX, barY + emptyHeight, barWidth, filledHeight, red, green, 0);
		}
	}

	private static void drawQuad(
			int x,
			int y,
			int width,
			int height,
			int red,
			int green,
			int blue
	) {
		Renderer.initQuadDraw(width, height)
				.color(red / 255.0F, green / 255.0F, blue / 255.0F, 1.0F)
				.draw(x, y);
	}
}
