package opusliews.charcoal;

import java.util.List;
import java.util.stream.Collectors;
import necesse.engine.window.WindowManager;
import necesse.gfx.forms.components.FormComponentList;
import necesse.gfx.forms.components.FormLabel;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementWorkPrioritiesForm;

public final class CharcoalProductionPriorityUI {
	private CharcoalProductionPriorityUI() {
	}

	public static void expandCharcoalColumn(SettlementWorkPrioritiesForm form) {
		FormComponentList jobTitles = form.jobTitles;
		List labels = (List)jobTitles.getComponentList().stream()
				.filter(component -> component instanceof FormLabel)
				.collect(Collectors.toList());
		if (labels.isEmpty()) {
			return;
		}

		FormLabel charcoalLabel = (FormLabel)labels.get(labels.size() - 1);
		charcoalLabel.setText("Firing");

		int extraWidth = 48;
		int maxWidth = Math.max(200, WindowManager.getWindow().getHudWidth() - 200);
		int newWidth = Math.min(form.getWidth() + extraWidth, maxWidth);
		int addedWidth = newWidth - form.getWidth();
		if (addedWidth <= 0) {
			return;
		}

		form.contentWidth += addedWidth;
		form.setWidth(newWidth);
		form.content.setWidth(newWidth);
		form.updateSize();
	}
}
