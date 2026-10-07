package opusliews.patches;

import necesse.engine.localization.message.GameMessage;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import net.bytebuddy.asm.Advice;
import opusliews.hunger.SettlerStarvationSystem;

@ModMethodPatch(
        target = ServerClient.class,
        name = "sendChatMessage",
        arguments = {GameMessage.class}
)
public class ServerClientStarvationDeathMessagePatch {
    @Advice.OnMethodEnter
    public static void onEnter(@Advice.Argument(value = 0, readOnly = false) GameMessage message) {
        message = SettlerStarvationSystem.replaceStarvationDeathMessage(message);
    }
}
