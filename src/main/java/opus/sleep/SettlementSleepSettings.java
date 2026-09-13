package opus.sleep;

public final class SettlementSleepSettings {
	public static final SettlementSleepSettings defaults = new SettlementSleepSettings(true, true, true);

	public final boolean wakeOnRaid;
	public final boolean wakeOnBarrierAttack;
	public final boolean wakeOnBarrierBreach;

	public SettlementSleepSettings(
			boolean wakeOnRaid,
			boolean wakeOnBarrierAttack,
			boolean wakeOnBarrierBreach
	) {
		this.wakeOnRaid = wakeOnRaid;
		this.wakeOnBarrierAttack = wakeOnBarrierAttack;
		this.wakeOnBarrierBreach = wakeOnBarrierBreach;
	}
}
