package opusliews.charcoal;

import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Collectors;
import necesse.gfx.forms.components.FormComponentList;
import necesse.gfx.forms.components.FormLabel;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementWorkPrioritiesForm;

public final class CharcoalProductionPriorityUI {
	private static final Field jobTitlesField;

	static {
		try {
			jobTitlesField = SettlementWorkPrioritiesForm.class.getDeclaredField("jobTitles");
			jobTitlesField.setAccessible(true);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	private CharcoalProductionPriorityUI() {
	}

	public static void applyMultilineTitle(SettlementWorkPrioritiesForm form) {
		try {
			FormComponentList jobTitles = (FormComponentList)jobTitlesField.get(form);
			List labels = (List)jobTitles.getComponentList().stream()
					.filter(component -> component instanceof FormLabel)
					.collect(Collectors.toList());
			if (!labels.isEmpty()) {
				((FormLabel)labels.get(labels.size() - 1)).setText("Charcoal\nProduction");
			}
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}
}
