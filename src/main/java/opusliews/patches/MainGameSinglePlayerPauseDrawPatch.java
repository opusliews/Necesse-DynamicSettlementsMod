package opusliews.patches;

import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.state.MainGame;
import net.bytebuddy.asm.Advice;
import opusliews.earlygame.SinglePlayerPauseSystem;

@ModMethodPatch(target = MainGame.class, name = "drawHud", arguments = {TickManager.class})
public class MainGameSinglePlayerPauseDrawPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This MainGame mainGame) {
		SinglePlayerPauseSystem.drawPauseSymbol(mainGame);
	}
}
