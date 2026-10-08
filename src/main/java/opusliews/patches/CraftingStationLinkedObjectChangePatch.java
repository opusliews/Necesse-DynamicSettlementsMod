package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.crafting.CraftingStationLinkCache;

@ModMethodPatch(target = Level.class, name = "replaceObjectEntity", arguments = {int.class, int.class})
public class CraftingStationLinkedObjectChangePatch {
    @Advice.OnMethodExit
    public static void onExit(
            @Advice.This Level level,
            @Advice.Argument(0) int tileX,
            @Advice.Argument(1) int tileY
    ) {
        CraftingStationLinkCache.onObjectChanged(level, tileX, tileY);
    }
}
