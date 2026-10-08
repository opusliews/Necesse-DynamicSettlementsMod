package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PathDoorOption;
import net.bytebuddy.asm.Advice;
import opusliews.path.PathDoorAdjacentRegionCacheFix;

@ModMethodPatch(
        target = PathDoorOption.class,
        name = "canMoveToTile",
        arguments = {int.class, int.class, int.class, int.class, boolean.class}
)
public class PathDoorAdjacentRegionCacheFixPatch {
    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static boolean onEnter(
            @Advice.This PathDoorOption option,
            @Advice.Argument(0) int fromTileX,
            @Advice.Argument(1) int fromTileY,
            @Advice.Argument(2) int toTileX,
            @Advice.Argument(3) int toTileY,
            @Advice.Argument(4) boolean acceptAdjacentTiles
    ) {
        return PathDoorAdjacentRegionCacheFix.canSkipAsUnreachable(
                option, fromTileX, fromTileY, toTileX, toTileY, acceptAdjacentTiles);
    }

    @Advice.OnMethodExit
    public static void onExit(
            @Advice.Enter boolean skippedAsUnreachable,
            @Advice.Return(readOnly = false) boolean result
    ) {
        if (skippedAsUnreachable) {
            result = false;
        }
    }
}
