package opusliews.patches;

import java.awt.Rectangle;
import java.util.LinkedList;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.window.WindowManager;
import necesse.gfx.forms.MainGameFormManager;
import necesse.gfx.forms.components.FormComponent;
import necesse.gfx.forms.components.FormContentBox;
import necesse.gfx.forms.presets.sidebar.SidebarComponent;
import net.bytebuddy.asm.Advice;

@ModMethodPatch(target = MainGameFormManager.class, name = "fixSidebar", arguments = {})
public class StoryObjectiveSidebarWidthPatch {
	public static final float expandedSidebarWidthFraction = 0.40F;
	public static final int minExpandedSidebarWidth = 500;
	public static final int maxExpandedSidebarWidth = 700;
	public static final int expandedObjectivePadding = 20;

	public static boolean expandingSidebar;

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.FieldValue("sidebarBox") FormContentBox sidebarBox,
			@Advice.FieldValue("sidebar") LinkedList sidebar
	) {
		if (expandingSidebar || !sidebarBox.hasScrollbarY()) return;

		int expandedWidth = getExpandedSidebarWidth();
		if (expandedWidth <= sidebarBox.getWidth()) return;

		expandingSidebar = true;
		try {
			sidebarBox.setWidth(expandedWidth);

			int y = 0;
			for (Object value : sidebar) {
				SidebarComponent component = (SidebarComponent)value;
				component.onSidebarUpdate(5, y);
				y += ((FormComponent)component).getBoundingBox().height + 10;
			}

			Rectangle fitBox = sidebarBox.getContentBoxToFitComponents();
			sidebarBox.setContentBox(new Rectangle(0, -5, fitBox.width, fitBox.height + 15));
			sidebarBox.setHeight(Math.max(
					100,
					Math.min(
							sidebarBox.getContentBox().height,
							WindowManager.getWindow().getHudHeight() / 2
					)
			));
		} finally {
			expandingSidebar = false;
		}
	}

	public static boolean isExpandingSidebar() {
		return expandingSidebar;
	}

	public static int getExpandedSidebarWidth() {
		int hudWidth = WindowManager.getWindow().getHudWidth();
		return Math.min(
				maxExpandedSidebarWidth,
				Math.max(minExpandedSidebarWidth, (int)(hudWidth * expandedSidebarWidthFraction))
		);
	}

	public static int getExpandedObjectiveWidth() {
		return Math.max(400, getExpandedSidebarWidth() - expandedObjectivePadding);
	}
}
