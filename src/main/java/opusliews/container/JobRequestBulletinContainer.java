package opusliews.container;

import necesse.engine.network.NetworkClient;
import necesse.engine.network.server.ServerClient;
import necesse.inventory.container.Container;
import necesse.inventory.container.customAction.BooleanCustomAction;
import necesse.level.maps.Level;
import opusliews.object.JobRequestBulletinObjectEntity;

public class JobRequestBulletinContainer extends Container {
	public final JobRequestBulletinObjectEntity bulletin;
	public final BooleanCustomAction setBuilderJobsAvailable;
	public final BooleanCustomAction setCarpenterJobsAvailable;

	public JobRequestBulletinContainer(NetworkClient client, int uniqueSeed, JobRequestBulletinObjectEntity bulletin) {
		super(client, uniqueSeed);
		this.bulletin = bulletin;

		setBuilderJobsAvailable = (BooleanCustomAction)registerAction(new BooleanCustomAction() {
			@Override
			protected void run(boolean value) {
				bulletin.setBuilderJobsAvailable(value);
			}
		});

		setCarpenterJobsAvailable = (BooleanCustomAction)registerAction(new BooleanCustomAction() {
			@Override
			protected void run(boolean value) {
				bulletin.setCarpenterJobsAvailable(value);
			}
		});
	}

	@Override
	public boolean isValid(ServerClient client) {
		if (!super.isValid(client)) return false;
		Level level = client.getLevel();
		return !bulletin.removed()
				&& level.getObject(bulletin.tileX, bulletin.tileY).isInInteractRange(
						level,
						bulletin.tileX,
						bulletin.tileY,
						client.playerMob
				);
	}
}
