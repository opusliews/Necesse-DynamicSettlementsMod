package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.gfx.forms.components.FormStoryObjectiveComponent;
import net.bytebuddy.asm.Advice;

@ModMethodPatch(target = FormStoryObjectiveComponent.class, name = "reset", arguments = {})
public class StoryObjectiveComponentWidthPatch {
	@Advice.OnMethodEnter
	public static void onEnter(
			@Advice.This FormStoryObjectiveComponent component
	) {
		if (component.isInForm || !StoryObjectiveSidebarWidthPatch.isExpandingSidebar()) return;
		component.setWidth(StoryObjectiveSidebarWidthPatch.getExpandedObjectiveWidth());
	}
}
