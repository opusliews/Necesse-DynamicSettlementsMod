package opusliews.earlygame;

import java.awt.Rectangle;
import java.lang.reflect.Field;

import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.window.GameWindow;
import necesse.engine.window.WindowManager;
import necesse.engine.world.WorldEntity;
import necesse.entity.mobs.Attacker;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.GameResources;
import necesse.gfx.Renderer;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.MainGameFormManager;
import opusliews.item.FirestarterItem;
import opusliews.logging.Logging;
import opusliews.progression.GuideProgressionSystem;

public final class FirstNightDeathSkipSystem {
	private static final long fadeDurationMs = 1200L;

	private static volatile boolean pendingFirstNightRespawnSkip;
	private static volatile boolean deathBlackout;
	private static volatile long fadeStartTime;
	private static volatile boolean deathFormReflectionFailed;
	private static Field deathFormField;

	private FirstNightDeathSkipSystem() {
	}

	public static void onPlayerDeath(PlayerMob player, Attacker attacker) {
		if (player == null || !player.isServer() || !player.isServerClient()) return;

		Server server = player.getServerClient().getServer();
		if (server == null || !server.isSingleplayer()) return;

		WorldEntity worldEntity = player.getWorldEntity();
		if (worldEntity == null || worldEntity.getDay() != 0 || !worldEntity.isNight()) return;
		if (GuideProgressionSystem.hasEverCrafted(player, FirestarterItem.stringID)) return;
		if (!isEligibleDeath(attacker)) return;

		pendingFirstNightRespawnSkip = true;
		deathBlackout = true;
		fadeStartTime = 0L;

		if (Logging.logEnabled) {
			Logging.logMessage("[FirstNightDeathSkip] Armed first-night morning skip for respawn player="
					+ player.getDisplayName() + " cause=" + getCauseName(attacker)
					+ " day=" + worldEntity.getDay() + " time=" + worldEntity.getDayTimeReadable());
		}
	}

	public static void onServerRespawn(ServerClient client) {
		if (!pendingFirstNightRespawnSkip || client == null || !client.isDead() || client.getRespawnTimeRemaining() > 200) return;

		Server server = client.getServer();
		if (server == null || !server.isSingleplayer()) return;

		setSixAM(server);
		pendingFirstNightRespawnSkip = false;
		deathBlackout = false;
		fadeStartTime = System.currentTimeMillis();

		if (Logging.logEnabled) {
			Logging.logMessage("[FirstNightDeathSkip] Respawn requested; advanced world to 06:00 and started fade player="
					+ client.getName() + " day=" + server.world.worldEntity.getDay()
					+ " time=" + server.world.worldEntity.getDayTimeReadable());
		}
	}

	public static boolean drawDeathBlackout(MainGameFormManager manager, TickManager tickManager, PlayerMob perspective) {
		if (!deathBlackout) return false;

		GameWindow window = WindowManager.getWindow();
		if (window == null) return false;

		GameResources.formShader.use();
		try {
			Renderer.initQuadDraw(window.getHudWidth(), window.getHudHeight())
					.color(0.0F, 0.0F, 0.0F, 1.0F)
					.draw(0, 0);

			Form deathForm = getDeathForm(manager);
			if (deathForm != null && !deathForm.isHidden()) {
				deathForm.draw(tickManager, perspective, new Rectangle(window.getHudWidth(), window.getHudHeight()));
			}
		} finally {
			GameResources.formShader.stop();
		}

		return true;
	}

	public static void drawRespawnFade() {
		long start = fadeStartTime;
		if (start == 0L) return;

		long elapsed = System.currentTimeMillis() - start;
		if (elapsed >= fadeDurationMs) {
			fadeStartTime = 0L;
			return;
		}

		float alpha = 1.0F - (float)elapsed / (float)fadeDurationMs;
		if (alpha <= 0.0F) {
			fadeStartTime = 0L;
			return;
		}

		GameWindow window = WindowManager.getWindow();
		if (window == null) return;

		GameResources.formShader.use();
		try {
			Renderer.initQuadDraw(window.getHudWidth(), window.getHudHeight())
					.color(0.0F, 0.0F, 0.0F, alpha)
					.draw(0, 0);
		} finally {
			GameResources.formShader.stop();
		}
	}

	private static void setSixAM(Server server) {
		WorldEntity worldEntity = server.world.worldEntity;
		int sixAMDayTime = Math.round(worldEntity.hourToDayTime(6.0F));
		long change = server.world.getTimeToNextTimeOfDay(sixAMDayTime);
		server.world.addWorldTime(change);
	}

	private static Form getDeathForm(MainGameFormManager manager) {
		if (manager == null || deathFormReflectionFailed) return null;

		try {
			if (deathFormField == null) {
				deathFormField = MainGameFormManager.class.getDeclaredField("death");
				deathFormField.setAccessible(true);
			}
			return (Form)deathFormField.get(manager);
		} catch (ReflectiveOperationException | ClassCastException exception) {
			deathFormReflectionFailed = true;
			Logging.logMessage("[FirstNightDeathSkip] Failed to access death form for blackout rendering: " + exception);
			return null;
		}
	}

	private static boolean isEligibleDeath(Attacker attacker) {
		if (attacker == PlayerMob.STARVING_ATTACKER) return true;
		if (attacker == null) return false;

		Mob owner = attacker.getFirstAttackOwner();
		return owner != null && owner.isHostile;
	}

	private static String getCauseName(Attacker attacker) {
		if (attacker == PlayerMob.STARVING_ATTACKER) return "starvation";
		if (attacker == null) return "none";
		Mob owner = attacker.getFirstAttackOwner();
		return owner == null ? "unknown" : owner.getStringID();
	}
}
