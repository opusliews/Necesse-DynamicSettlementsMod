package necesse.entity.mobs.ai.behaviourTree.trees;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.ai.behaviourTree.event.AIEvent;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opusliews.hunger.SettlerStarvationSystem;

/**
 * Targets vanilla HumanAI's low-health global-tick callback, not the general hide flag.
 * Prevents the vanilla callback from cancelling jobs when the low HP came from starvation.
 * This callback is compiled into HumanAI$2 in Necesse 1.3.3.
 */
@ModMethodPatch(target = HumanAI$2.class, name = "lambda$onRootSet$0", arguments = {HumanMob.class, AIEvent.class})
public class HumanAIStarvationLowHealthHidePatch {
    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static boolean onEnter(@Advice.Argument(0) HumanMob human) {
        return SettlerStarvationSystem.shouldIgnoreLowHealthHide(human);
    }
}
