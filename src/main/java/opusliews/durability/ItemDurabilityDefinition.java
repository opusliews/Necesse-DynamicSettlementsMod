package opusliews.durability;

import necesse.inventory.item.Item;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Predicate;

public class ItemDurabilityDefinition {
	public enum BreakBehavior {
		DESTROY,
		REPLACE
	}

	public boolean breakable;
	public int durability;
	private BreakBehavior breakBehavior = BreakBehavior.DESTROY;
	private String brokenItemStringID;
	private final EnumMap<DurabilityAction, List<DurabilityLossRule>> rules = new EnumMap<>(DurabilityAction.class);

	public ItemDurabilityDefinition(boolean breakable, int durability) {
		if (breakable && durability <= 0) {
			throw new IllegalArgumentException("Breakable items must have durability greater than zero");
		}

		this.breakable = breakable;
		this.durability = Math.max(0, durability);
	}

	public ItemDurabilityDefinition on(DurabilityAction action, int loss) {
		return on(action, loss, loss, null);
	}

	public ItemDurabilityDefinition on(DurabilityAction action, int minLoss, int maxLoss) {
		return on(action, minLoss, maxLoss, null);
	}

	public ItemDurabilityDefinition on(DurabilityAction action, int loss, Predicate<DurabilityContext> condition) {
		return on(action, loss, loss, condition);
	}

	public ItemDurabilityDefinition on(DurabilityAction action, int minLoss, int maxLoss, Predicate<DurabilityContext> condition) {
		rules.computeIfAbsent(action, ignored -> new ArrayList<>()).add(new DurabilityLossRule(minLoss, maxLoss, condition));
		return this;
	}

	public ItemDurabilityDefinition destroyOnBreak() {
		breakBehavior = BreakBehavior.DESTROY;
		brokenItemStringID = null;
		return this;
	}

	public ItemDurabilityDefinition replaceOnBreak(String itemStringID) {
		if (itemStringID == null || itemStringID.isEmpty()) {
			throw new IllegalArgumentException("Broken item string ID cannot be null or empty");
		}

		breakBehavior = BreakBehavior.REPLACE;
		brokenItemStringID = itemStringID;
		return this;
	}

	public ItemDurabilityDefinition replaceOnBreak(Item item) {
		if (item == null) throw new IllegalArgumentException("Broken item cannot be null");
		return replaceOnBreak(item.getStringID());
	}

	public BreakBehavior getBreakBehavior() {
		return breakBehavior;
	}

	public String getBrokenItemStringID() {
		return brokenItemStringID;
	}

	public List<DurabilityLossRule> getRules(DurabilityAction action) {
		return rules.get(action);
	}
}
