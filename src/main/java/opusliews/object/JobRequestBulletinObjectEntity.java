package opusliews.object;

import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.packet.PacketObjectEntity;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.level.maps.Level;

public class JobRequestBulletinObjectEntity extends ObjectEntity {
	public static final String type = "dynamicjobrequestbulletin";

	private boolean builderJobsAvailable;
	private boolean carpenterJobsAvailable;

	public JobRequestBulletinObjectEntity(Level level, int tileX, int tileY) {
		super(level, type, tileX, tileY);
	}

	@Override
	public void addSaveData(SaveData save) {
		super.addSaveData(save);
		save.addBoolean("builderJobsAvailable", builderJobsAvailable);
		save.addBoolean("carpenterJobsAvailable", carpenterJobsAvailable);
	}

	@Override
	public void applyLoadData(LoadData save) {
		super.applyLoadData(save);
		builderJobsAvailable = save.getBoolean("builderJobsAvailable", false, false);
		carpenterJobsAvailable = save.getBoolean("carpenterJobsAvailable", false, false);
	}

	@Override
	public void setupContentPacket(PacketWriter writer) {
		super.setupContentPacket(writer);
		writer.putNextBoolean(builderJobsAvailable);
		writer.putNextBoolean(carpenterJobsAvailable);
	}

	@Override
	public void applyContentPacket(PacketReader reader) {
		super.applyContentPacket(reader);
		builderJobsAvailable = reader.getNextBoolean();
		carpenterJobsAvailable = reader.getNextBoolean();
	}

	public boolean isBuilderJobsAvailable() {
		return builderJobsAvailable;
	}

	public boolean isCarpenterJobsAvailable() {
		return carpenterJobsAvailable;
	}

	public void setBuilderJobsAvailable(boolean value) {
		if (builderJobsAvailable == value) return;
		builderJobsAvailable = value;
		sync();
	}

	public void setCarpenterJobsAvailable(boolean value) {
		if (carpenterJobsAvailable == value) return;
		carpenterJobsAvailable = value;
		sync();
	}

	private void sync() {
		markDirty();
		if (getLevel().isServer() && getLevel().getServer() != null) {
			getLevel().getServer().network.sendToClientsWithEntity(new PacketObjectEntity(this), this);
		}
	}
}
