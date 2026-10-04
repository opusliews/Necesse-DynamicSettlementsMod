package opusliews.patches;

import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.state.MainGame;
import necesse.engine.window.GameWindow;
import net.bytebuddy.asm.Advice;
import opusliews.earlygame.SinglePlayerPauseSystem;

@ModMethodPatch(target = MainGame.class, name = "frameTick", arguments = {TickManager.class, GameWindow.class})
public class MainGameSinglePlayerPauseInputPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This MainGame mainGame,
			@Advice.Argument(1) GameWindow window
	) {
		SinglePlayerPauseSystem.frameTick(mainGame, window);
	}
}
