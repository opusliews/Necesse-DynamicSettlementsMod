package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PlayerMob;
import necesse.level.gameObject.ProcessingForgeObject;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.progression.GuideProgressionSystem;

/** Reveals Auto Forging when the player opens a Forge's interface. */
@ModMethodPatch(target = ProcessingForgeObject.class, name = "interact",
        arguments = {Level.class, int.class, int.class, PlayerMob.class})
public class ProcessingForgeJournalOpenPatch {
    @Advice.OnMethodExit
    public static void onExit(@Advice.Argument(0) Level level,
                              @Advice.Argument(3) PlayerMob player) {
        if (level != null && level.isServer() && player != null && player.isServerClient()) {
            GuideProgressionSystem.onForgeOpened(player.getServerClient());
        }
    }
}
