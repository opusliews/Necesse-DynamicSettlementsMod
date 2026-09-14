package opusliews.guard;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

import necesse.entity.mobs.friendly.human.GuardHumanMob;
import necesse.gfx.forms.presets.containerComponent.mob.ShopContainerForm;
import necesse.inventory.container.mob.ShopContainer;

public final class GuardDutyDialogueRefresh {
	private static final Map<Integer, WeakReference<ShopContainerForm>> forms = new HashMap<>();

	private GuardDutyDialogueRefresh() {
	}

	public static void track(GuardHumanMob guard, ShopContainerForm form) {
		if (guard == null || form == null) {
			return;
		}

		synchronized (forms) {
			forms.put(guard.getUniqueID(), new WeakReference<>(form));
		}
	}

	public static void refresh(int guardUniqueID) {
		ShopContainerForm form;
		synchronized (forms) {
			WeakReference<ShopContainerForm> reference = forms.get(guardUniqueID);
			form = reference == null ? null : reference.get();
			if (form == null) {
				forms.remove(guardUniqueID);
				return;
			}
		}

		if (!(form.getContainer() instanceof ShopContainer)) {
			return;
		}

		ShopContainer container = (ShopContainer)form.getContainer();
		if (!(container.humanShop instanceof GuardHumanMob)
				|| container.humanShop.getUniqueID() != guardUniqueID) {
			return;
		}

		form.updateDialogue();
	}
}
