package opusliews.patches;

import necesse.engine.Settings;
import necesse.engine.input.Input;
import necesse.engine.input.InputEvent;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.state.MainGame;
import necesse.engine.util.GameMath;
import necesse.engine.window.GameWindow;
import necesse.gfx.forms.components.FormTypingComponent;
import net.bytebuddy.asm.Advice;
import opusliews.DynamicSettlementsSettings;

public class InGameScaleControlsPatch {
	public static final int leftControlKey = 341;
	public static final int rightControlKey = 345;
	public static final int keypadSubtractKey = 333;
	public static final int keypadAddKey = 334;
	public static final float zoomStep = 0.05F;
	public static final float epsilon = 0.001F;

	public static boolean previousKeypadAddDown;
	public static boolean previousKeypadSubtractDown;

	@ModMethodPatch(
			target = MainGame.class,
			name = "frameTick",
			arguments = {necesse.engine.gameLoop.tickManager.TickManager.class, GameWindow.class}
	)
	public static class MainGameFrameTickPatch {
		@Advice.OnMethodEnter
		public static void onEnter(
				@Advice.This MainGame game,
				@Advice.Argument(1) GameWindow window
		) {
			if (game == null || window == null) return;

			if (!DynamicSettlementsSettings.enableInGameScaleShortcuts) {
				previousKeypadAddDown = false;
				previousKeypadSubtractDown = false;
				return;
			}

			Input input = window.getInput();
			if (input == null) return;

			boolean keypadAddDown = input.isKeyDown(keypadAddKey);
			boolean keypadSubtractDown = input.isKeyDown(keypadSubtractKey);
			boolean keypadAddPressed = keypadAddDown && !previousKeypadAddDown;
			boolean keypadSubtractPressed = keypadSubtractDown && !previousKeypadSubtractDown;
			previousKeypadAddDown = keypadAddDown;
			previousKeypadSubtractDown = keypadSubtractDown;

			if (!window.isFocused()) return;

			boolean controlDown = input.isKeyDown(leftControlKey) || input.isKeyDown(rightControlKey);
			if (!controlDown) return;

			boolean changed = false;

			if (!game.formManager.isMouseOver()) {
				for (Object rawEvent : input.getEvents()) {
					InputEvent event = (InputEvent)rawEvent;
					if (event.isUsed() || !event.isMouseWheelEvent()) continue;

					float wheelY = (float)event.getMouseWheelY();
					if (wheelY == 0.0F) continue;

					float oldSize = Settings.sceneSize;
					Settings.sceneSize = GameMath.limit(
							Settings.sceneSize + zoomStep * wheelY,
							GameWindow.minSceneSize,
							GameWindow.maxSceneSize
					);
					Settings.sceneSize = GameMath.toDecimals(Settings.sceneSize, 2);
					event.use();

					if (Math.abs(Settings.sceneSize - oldSize) > epsilon) {
						window.updateSceneSize();
						game.getClient().chat.addOrModifyMessage(
								"dynamicSettlementsZoomLevel",
								"Zoom: " + Math.round(Settings.sceneSize * 100.0F) + "%"
						);
						changed = true;
					}
				}
			}

			if (!FormTypingComponent.isCurrentlyTyping() && (keypadAddPressed || keypadSubtractPressed)) {
				// Consume the matching key event so vanilla's regular zoom controls do not also fire.
				for (Object rawEvent : input.getEvents()) {
					InputEvent event = (InputEvent)rawEvent;
					if (event.getID() == keypadAddKey || event.getID() == keypadSubtractKey) {
						event.use();
					}
				}

				int direction = keypadAddPressed ? 1 : -1;
				int currentStep = Math.round(Settings.interfaceSize * 10.0F);
				int newStep = Math.max(10, Math.min(20, currentStep + direction));
				float newSize = newStep / 10.0F;

				if (Math.abs(newSize - Settings.interfaceSize) > epsilon) {
					Settings.interfaceSize = newSize;
					window.updateHudSize();
					game.getClient().chat.addOrModifyMessage(
							"dynamicSettlementsInterfaceSize",
							"Interface size: " + Math.round(Settings.interfaceSize * 100.0F) + "%"
					);
					changed = true;
				}
			}

			if (changed) Settings.saveClientSettings();
		}
	}
}
