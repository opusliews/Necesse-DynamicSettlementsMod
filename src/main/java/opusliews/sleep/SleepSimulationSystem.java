package opusliews.sleep;

import necesse.engine.Settings;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.world.WorldEntity;
import necesse.inventory.container.BedContainer;
import necesse.inventory.container.Container;

public final class SleepSimulationSystem {
	private static final int startSpeed = 2;
	private static final int maxSpeed = 32;
	private static final long rampStepNanos = 1_000_000_000L;
	private static final long audioFadeNanos = 2_000_000_000L;

	private static boolean vanillaSleepAccelerationDisabled;

	private static Server activeServer;
	private static boolean serverActive;
	private static long serverStartNanos;
	private static int serverSpeed;
	private static float serverPreviousTimeMod;
	private static boolean serverPreviousSkipDraw;

	private static boolean clientActive;
	private static boolean clientSimulationActive;
	private static boolean clientControlsTimeMod;
	private static long clientStartNanos;
	private static int clientSpeed;
	private static float clientPreviousTimeMod;
	private static boolean clientPreviousSkipDraw;
	private static float clientPreviousEffectsVolume;
	private static float clientPreviousWeatherVolume;
	private static float clientPreviousUIVolume;
	private static float clientFadeStartEffectsVolume;
	private static float clientFadeStartWeatherVolume;
	private static float clientFadeStartUIVolume;
	private static long clientAudioFadeStartNanos;
	private static AudioFadeState clientAudioFadeState = AudioFadeState.IDLE;

	private SleepSimulationSystem() {
	}

	public static void serverTick(Server server) {
		disableVanillaSleepAcceleration();

		if (server == null || server.isStopped()) {
			stopServer(server);
			return;
		}

		if (!areAllPlayersSleeping(server)) {
			stopServer(server);
			return;
		}

		if (!serverActive || activeServer != server) {
			if (serverActive) {
				stopServer(activeServer);
			}
			startServer(server);
		}

		int targetSpeed = getRampSpeed(serverStartNanos);
		if (targetSpeed != serverSpeed) {
			serverSpeed = targetSpeed;
		}

		applyTimeMod(serverSpeed);
	}

	public static void clientFrameTick(Client client) {
		disableVanillaSleepAcceleration();

		boolean sleeping = client != null
				&& !client.isDisconnected()
				&& client.worldEntity != null
				&& client.worldEntity.isSleeping();

		if (sleeping) {
			if (!clientSimulationActive) {
				startClientSleep(client);
			}

			updateAudioFade();

			if (clientControlsTimeMod) {
				int targetSpeed = getRampSpeed(clientStartNanos);
				clientSpeed = targetSpeed;
				applyTimeMod(targetSpeed);
			}
		}
		else {
			if (clientSimulationActive) {
				endClientSleep();
			}

			if (clientActive) {
				updateAudioFade();
			}
		}
	}

	public static void prepareBedTick(BedContainer container) {
		if (container == null || container.getClient() == null || container.getClient().playerMob == null) {
			return;
		}

		WorldEntity worldEntity = container.getClient().playerMob.getWorldEntity();

		if (worldEntity.isNight() || worldEntity.isSleeping()) {
			return;
		}

		container.sleepTimer = 0;
		container.nextWakeUpTime = 0L;

		if (container.getClient().isClient()) {
			container.sleepingPlayers = 0;
		}
	}

	public static void stopServer(Server server) {
		if (!serverActive) {
			return;
		}

		if (server != null && activeServer != null && server != activeServer) {
			return;
		}

		TickManager.globalTimeMod = serverPreviousTimeMod;
		TickManager.skipDrawIfBehind = serverPreviousSkipDraw;

		serverActive = false;
		activeServer = null;
		serverSpeed = 0;
	}

	public static void stopClient() {
		if (!clientActive) {
			return;
		}

		Settings.effectsVolume = clientPreviousEffectsVolume;
		Settings.weatherVolume = clientPreviousWeatherVolume;
		Settings.UIVolume = clientPreviousUIVolume;

		if (clientControlsTimeMod) {
			TickManager.globalTimeMod = clientPreviousTimeMod;
			TickManager.skipDrawIfBehind = clientPreviousSkipDraw;
		}

		clientActive = false;
		clientSimulationActive = false;
		clientControlsTimeMod = false;
		clientSpeed = 0;
		clientAudioFadeState = AudioFadeState.IDLE;
	}

	private static void startServer(Server server) {
		activeServer = server;
		serverActive = true;
		serverStartNanos = System.nanoTime();
		serverSpeed = startSpeed;
		serverPreviousTimeMod = TickManager.globalTimeMod;
		serverPreviousSkipDraw = TickManager.skipDrawIfBehind;

		applyTimeMod(serverSpeed);
	}

	private static void startClientSleep(Client client) {
		if (!clientActive) {
			clientActive = true;
			clientPreviousEffectsVolume = Settings.effectsVolume;
			clientPreviousWeatherVolume = Settings.weatherVolume;
			clientPreviousUIVolume = Settings.UIVolume;
		}

		clientSimulationActive = true;
		clientStartNanos = System.nanoTime();
		clientSpeed = startSpeed;
		beginAudioFade(AudioFadeState.FADING_OUT);

		clientControlsTimeMod = client.getLocalServer() == null;
		if (clientControlsTimeMod) {
			clientPreviousTimeMod = TickManager.globalTimeMod;
			clientPreviousSkipDraw = TickManager.skipDrawIfBehind;
			applyTimeMod(clientSpeed);
		}
	}

	private static void endClientSleep() {
		if (!clientSimulationActive) {
			return;
		}

		if (clientControlsTimeMod) {
			TickManager.globalTimeMod = clientPreviousTimeMod;
			TickManager.skipDrawIfBehind = clientPreviousSkipDraw;
		}

		clientSimulationActive = false;
		clientControlsTimeMod = false;
		clientSpeed = 0;
		beginAudioFade(AudioFadeState.FADING_IN);
	}

	private static void beginAudioFade(AudioFadeState state) {
		clientFadeStartEffectsVolume = Settings.effectsVolume;
		clientFadeStartWeatherVolume = Settings.weatherVolume;
		clientFadeStartUIVolume = Settings.UIVolume;
		clientAudioFadeStartNanos = System.nanoTime();
		clientAudioFadeState = state;
	}

	private static void updateAudioFade() {
		if (clientAudioFadeState == AudioFadeState.IDLE) {
			return;
		}

		if (clientAudioFadeState == AudioFadeState.MUTED) {
			Settings.effectsVolume = 0.0F;
			Settings.weatherVolume = 0.0F;
			Settings.UIVolume = 0.0F;
			return;
		}

		float progress = Math.min(1.0F, (float)(System.nanoTime() - clientAudioFadeStartNanos) / (float)audioFadeNanos);
		if (clientAudioFadeState == AudioFadeState.FADING_OUT) {
			float remaining = 1.0F - progress;
			Settings.effectsVolume = clientFadeStartEffectsVolume * remaining;
			Settings.weatherVolume = clientFadeStartWeatherVolume * remaining;
			Settings.UIVolume = clientFadeStartUIVolume * remaining;

			if (progress >= 1.0F) {
				Settings.effectsVolume = 0.0F;
				Settings.weatherVolume = 0.0F;
				Settings.UIVolume = 0.0F;
				clientAudioFadeState = AudioFadeState.MUTED;
			}
		}
		else if (clientAudioFadeState == AudioFadeState.FADING_IN) {
			Settings.effectsVolume = lerp(clientFadeStartEffectsVolume, clientPreviousEffectsVolume, progress);
			Settings.weatherVolume = lerp(clientFadeStartWeatherVolume, clientPreviousWeatherVolume, progress);
			Settings.UIVolume = lerp(clientFadeStartUIVolume, clientPreviousUIVolume, progress);

			if (progress >= 1.0F) {
				Settings.effectsVolume = clientPreviousEffectsVolume;
				Settings.weatherVolume = clientPreviousWeatherVolume;
				Settings.UIVolume = clientPreviousUIVolume;
				clientAudioFadeState = AudioFadeState.IDLE;
				clientActive = false;
			}
		}
	}

	private static float lerp(float from, float to, float progress) {
		return from + (to - from) * progress;
	}

	private static boolean areAllPlayersSleeping(Server server) {
		if (server.getPlayersOnline() <= 0) {
			return false;
		}

		boolean continuingSleep = serverActive && activeServer == server;
		if (!continuingSleep && !server.world.worldEntity.isNight()) {
			return false;
		}

		return server.streamClients().allMatch(client -> {
			Container container = client.getContainer();
			if (!(container instanceof BedContainer)) {
				return false;
			}

			return ((BedContainer)container).sleepTimer > BedContainer.WAIT_TIME;
		});
	}

	private static int getRampSpeed(long startNanos) {
		long elapsed = Math.max(0L, System.nanoTime() - startNanos);
		int steps = (int)(elapsed / rampStepNanos);
		return Math.min(maxSpeed, startSpeed + steps);
	}

	private static void applyTimeMod(int speed) {
		TickManager.globalTimeMod = speed;
		TickManager.skipDrawIfBehind = speed > 1;
	}

	private static void disableVanillaSleepAcceleration() {
		if (!vanillaSleepAccelerationDisabled) {
			WorldEntity.sleepingModifier = 0.0F;
			vanillaSleepAccelerationDisabled = true;
		}
	}

	private enum AudioFadeState {
		IDLE,
		FADING_OUT,
		MUTED,
		FADING_IN
	}
}
