package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.gfx.forms.presets.HelpForms;
import net.bytebuddy.asm.Advice;
import opusliews.logging.Logging;

@ModMethodPatch(
        target = HelpForms.class,
        name = "openHelpForm",
        arguments = {String.class, Object[].class}
)
public class RecruitSettlerMajorObjectiveIntroPatch {
    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static boolean onEnter(@Advice.Argument(0) String key) {
        if ("majorobjectiveintro".equals(key)) {
            Logging.logMessage("[StoryObjectives] Suppressed obsolete vanilla majorobjectiveintro help form after recruitsettler");
            return true;
        }
        return false;
    }
}
