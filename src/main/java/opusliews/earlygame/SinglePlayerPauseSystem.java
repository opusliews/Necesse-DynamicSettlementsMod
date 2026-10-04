package opusliews.earlygame;

import necesse.engine.input.InputEvent;
import necesse.engine.input.InputID;
import necesse.engine.network.client.Client;
import necesse.engine.state.MainGame;
import necesse.engine.window.GameWindow;
import necesse.engine.window.WindowManager;
import necesse.gfx.GameBackground;
import opusliews.logging.Logging;

public final class SinglePlayerPauseSystem {
	private static final int barWidth = 22;
	private static final int barHeight = 76;
	private static final int barGap = 16;

	private static Client pausedClient;
	private static boolean pauseActive;

	private SinglePlayerPauseSystem() {
	}

	public static void frameTick(MainGame mainGame, GameWindow window) {
		if (mainGame == null || window == null) return;

		Client client = mainGame.getClient();
		if (client == null) return;

		if (pauseActive && pausedClient != client) {
			pauseActive = false;
			pausedClient = null;
			Logging.logMessage("[Pause] Cleared stale single-player pause state after client change.");
		}

		if (!client.isSingleplayer()) {
			if (pauseActive && pausedClient == client) {
				pauseActive = false;
				pausedClient = null;
				Logging.logMessage("[Pause] Cleared pause state because client is no longer single-player.");
			}
			return;
		}

		if (pauseActive && pausedClient == client && !client.isPaused()) {
			client.pause();
			Logging.logMessage("[Pause] Re-applied single-player pause after another UI resumed the client.");
		}

		InputEvent pauseEvent = window.getInput().getEvent(InputID.KEY_PAUSE);
		if (pauseEvent == null || pauseEvent.isUsed() || !pauseEvent.state) return;

		if (pauseActive) {
			if (!canToggleFromCurrentUi(mainGame, client)) return;

			pauseEvent.use();
			pauseActive = false;
			pausedClient = null;
			client.resume();
			Logging.logMessage("[Pause] Resumed single-player game from Pause key.");
			return;
		}

		if (!canToggleFromCurrentUi(mainGame, client)) return;

		pauseEvent.use();
		client.pause();
		pauseActive = true;
		pausedClient = client;
		Logging.logMessage("[Pause] Paused single-player game from Pause key.");
	}

	private static boolean canToggleFromCurrentUi(MainGame mainGame, Client client) {
		if (!mainGame.isRunning()) return false;
		if (client.isDead) return false;
		if (mainGame.formManager == null) return false;
		if (mainGame.formManager.hasFocusForm()) return false;
		if (mainGame.formManager.hasContinueForms()) return false;
		if (mainGame.formManager.hasFloatMenu()) return false;
		if (mainGame.formManager.isControllerKeyboardOpen()) return false;
		if (!mainGame.formManager.travel.isHidden()) return false;
		if (!mainGame.formManager.pauseMenu.isHidden()) return false;
		return true;
	}

	public static void drawPauseSymbol(MainGame mainGame) {
		if (!pauseActive || mainGame == null) return;
		Client client = mainGame.getClient();
		if (client == null || client != pausedClient || !client.isSingleplayer()) return;
		if (!mainGame.isRunning()) return;

		GameWindow window = WindowManager.getWindow();
		if (window == null) return;

		int totalWidth = barWidth * 2 + barGap;
		int x = (window.getHudWidth() - totalWidth) / 2;
		int y = (window.getHudHeight() - barHeight) / 2;

		GameBackground.form.getDrawOptions(x, y, barWidth, barHeight).draw();
		GameBackground.form.getDrawOptions(x + barWidth + barGap, y, barWidth, barHeight).draw();
	}
}
