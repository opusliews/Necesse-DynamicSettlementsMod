package opusliews.forms;

import java.awt.Color;
import java.awt.Rectangle;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.localization.Localization;
import necesse.engine.network.client.Client;
import necesse.engine.window.GameWindow;
import necesse.engine.window.WindowManager;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.Renderer;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.components.FormCheckBox;
import necesse.gfx.forms.components.FormLabel;
import necesse.gfx.forms.presets.containerComponent.ContainerFormSwitcher;
import necesse.gfx.gameFont.FontOptions;
import opusliews.container.JobRequestBulletinContainer;

public class JobRequestBulletinContainerForm extends ContainerFormSwitcher<JobRequestBulletinContainer> {
	private final WhiteForm form;
	private final FormCheckBox builderJobs;
	private final FormCheckBox carpenterJobs;

	public JobRequestBulletinContainerForm(Client client, JobRequestBulletinContainer container) {
		super(client, container);
		form = addComponent(new WhiteForm("jobRequestBulletin", 340, 105));

		FormLabel builderLabel = form.addComponent(new FormLabel(
				Localization.translate("ui", "builderjobsavailable"),
				new FontOptions(16),
				FormLabel.ALIGN_LEFT,
				20,
				22
		));
		builderLabel.setColor(Color.BLACK);

		builderJobs = form.addComponent(new BlackCheckBox(
				295,
				21,
				container.bulletin.isBuilderJobsAvailable()
		));
		builderJobs.onClicked(event -> container.setBuilderJobsAvailable.runAndSend(builderJobs.checked));

		FormLabel carpenterLabel = form.addComponent(new FormLabel(
				Localization.translate("ui", "carpenterjobsavailable"),
				new FontOptions(16),
				FormLabel.ALIGN_LEFT,
				20,
				59
		));
		carpenterLabel.setColor(Color.BLACK);

		carpenterJobs = form.addComponent(new BlackCheckBox(
				295,
				58,
				container.bulletin.isCarpenterJobsAvailable()
		));
		carpenterJobs.onClicked(event -> container.setCarpenterJobsAvailable.runAndSend(carpenterJobs.checked));

		centerForm();
		makeCurrent(form);
	}

	@Override
	public void draw(TickManager tickManager, PlayerMob perspective, Rectangle renderBox) {
		builderJobs.checked = container.bulletin.isBuilderJobsAvailable();
		carpenterJobs.checked = container.bulletin.isCarpenterJobsAvailable();
		super.draw(tickManager, perspective, renderBox);
	}

	@Override
	public void onWindowResized(GameWindow window) {
		super.onWindowResized(window);
		centerForm();
	}

	private void centerForm() {
		GameWindow window = WindowManager.getWindow();
		form.setPosMiddle(window.getHudWidth() / 2, window.getHudHeight() / 2);
	}

	@Override
	public boolean shouldOpenInventory() {
		return false;
	}

	private static final class WhiteForm extends Form {
		WhiteForm(String name, int width, int height) {
			super(name, width, height);
		}

		@Override
		public void drawBase(TickManager tickManager) {
			Renderer.initQuadDraw(getWidth(), getHeight()).color(Color.WHITE).draw(getX(), getY());
		}

		@Override
		public void drawEdge(TickManager tickManager) {
			Renderer.initQuadDraw(getWidth(), 1).color(Color.BLACK).draw(getX(), getY());
			Renderer.initQuadDraw(getWidth(), 1).color(Color.BLACK).draw(getX(), getY() + getHeight() - 1);
			Renderer.initQuadDraw(1, getHeight()).color(Color.BLACK).draw(getX(), getY());
			Renderer.initQuadDraw(1, getHeight()).color(Color.BLACK).draw(getX() + getWidth() - 1, getY());
		}
	}

	private static final class BlackCheckBox extends FormCheckBox {
		private static final int size = 16;

		BlackCheckBox(int x, int y, boolean checked) {
			super("", x, y, checked);
		}

		@Override
		public void draw(TickManager tickManager, PlayerMob perspective, Rectangle renderBox) {
			int x = getX();
			int y = getY();

			Renderer.initQuadDraw(size, size).color(Color.WHITE).draw(x, y);
			Renderer.initQuadDraw(size, 1).color(Color.BLACK).draw(x, y);
			Renderer.initQuadDraw(size, 1).color(Color.BLACK).draw(x, y + size - 1);
			Renderer.initQuadDraw(1, size).color(Color.BLACK).draw(x, y);
			Renderer.initQuadDraw(1, size).color(Color.BLACK).draw(x + size - 1, y);

			if (checked) {
				Renderer.initQuadDraw(8, 8).color(Color.BLACK).draw(x + 4, y + 4);
			}
		}

		@Override
		public Color getDrawColor() {
			return Color.BLACK;
		}
	}
}
