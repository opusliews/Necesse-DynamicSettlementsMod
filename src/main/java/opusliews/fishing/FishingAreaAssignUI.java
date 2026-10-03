package opusliews.fishing;

import necesse.engine.network.client.Client;
import necesse.gfx.forms.components.FormContentBox;
import necesse.gfx.forms.components.FormFlow;
import necesse.gfx.forms.components.localComponents.FormLocalTextButton;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementAssignWorkForm;
import opusliews.forms.FishingAreasForm;
import opusliews.network.PacketFishingAreasRequest;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

public final class FishingAreaAssignUI {
	private static final Map<Integer, FishingAreaLevelData.Snapshot> snapshots = new HashMap<>();
	private static WeakReference<FishingAreasForm> activeForm = new WeakReference<>(null);

	private FishingAreaAssignUI() {
	}

	public static void addAssignButton(SettlementAssignWorkForm form, FormFlow flow, FormContentBox content) {
		int y = flow.next(40);
		FormLocalTextButton button = content.addComponent(new FormLocalTextButton(
				"ui", "settlementassignfishingareas", 16, y, content.getWidth() - 32));
		button.onClicked(event -> open(form));
		int requiredHeight = flow.next() + 8;
		if (requiredHeight > form.work.getHeight()) form.work.setHeight(requiredHeight);
		content.setHeight(requiredHeight);
		content.setContentBox(new java.awt.Rectangle(0, 0, content.getWidth(), requiredHeight));
	}

	public static void open(SettlementAssignWorkForm parent) {
		FishingAreasForm form = new FishingAreasForm(parent);
		parent.addComponent(form);
		activeForm = new WeakReference<>(form);
		parent.makeCurrent(form);
		parent.client.network.sendPacket(new PacketFishingAreasRequest(parent.container.getSettlementUniqueID()));
	}

	public static synchronized FishingAreaLevelData.Snapshot getSnapshot(int settlementUniqueID) {
		return snapshots.get(settlementUniqueID);
	}

	public static synchronized void applySync(Client client, int settlementUniqueID, FishingAreaLevelData.Snapshot snapshot) {
		snapshots.put(settlementUniqueID, snapshot);
		FishingAreasForm form = activeForm.get();
		if (form != null && form.getClient() == client && form.getSettlementUniqueID() == settlementUniqueID && form.isActive()) {
			form.applySnapshot(snapshot);
		}
	}

	public static synchronized void unregister(FishingAreasForm form) {
		if (form != null && activeForm.get() == form) activeForm = new WeakReference<>(null);
	}
}
