package opusliews.forms;

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;

import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.GameResources;
import necesse.gfx.drawOptions.DrawOptions;
import necesse.gfx.forms.components.FormComponent;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.FormTextButton;
import necesse.gfx.gameFont.FontManager;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.gameTexture.GameSprite;
import necesse.gfx.gameTexture.GameTexture;
import necesse.gfx.shader.FormShader;
import necesse.gfx.ui.ButtonColor;
import necesse.gfx.ui.ButtonState;
import necesse.gfx.ui.GameInterfaceStyle;

public class FormTwoLineTextButton extends FormTextButton {
	public static final int DEFAULT_HEIGHT = 48;
	public static final int DEFAULT_LINE_GAP = 1;

	private final String line1;
	private final String line2;
	private final int height;
	private final int lineGap;

	public FormTwoLineTextButton(String line1, String line2, int x, int y, int width) {
		this(line1, line2, null, x, y, width, DEFAULT_HEIGHT, DEFAULT_LINE_GAP, ButtonColor.BASE);
	}

	public FormTwoLineTextButton(String line1, String line2, int x, int y, int width, int height) {
		this(line1, line2, null, x, y, width, height, DEFAULT_LINE_GAP, ButtonColor.BASE);
	}

	public FormTwoLineTextButton(
			String line1,
			String line2,
			String tooltip,
			int x,
			int y,
			int width,
			int height,
			int lineGap,
			ButtonColor color
	) {
		super(line1 + " " + line2, tooltip, x, y, width, createSize(height), color);
		this.line1 = line1;
		this.line2 = line2;
		this.height = height;
		this.lineGap = lineGap;
	}

	@Override
	public void draw(TickManager tickManager, PlayerMob perspective, Rectangle renderBox) {
		Color drawColor = getDrawColor();
		ButtonState state = getButtonState();
		boolean useDownTexture = isDown() && isHovering();
		int textOffset = useDownTexture ? size.buttonDownContentDrawOffset : 0;

		if (useDownTexture) {
			size.getButtonDownDrawOptions(
					getInterfaceStyle(),
					color,
					state,
					getX(),
					getY(),
					getWidth(),
					drawColor
			).draw();
		} else {
			size.getButtonDrawOptions(
					getInterfaceStyle(),
					color,
					state,
					getX(),
					getY(),
					getWidth(),
					drawColor
			).draw();
		}

		Rectangle contentRect = size.getContentRectangle(getWidth());
		FormShader.FormShaderState textState = GameResources.formShader.startState(
				new Point(getX(), getY()),
				new Rectangle(
						contentRect.x,
						contentRect.y,
						contentRect.width,
						contentRect.height
				)
		);

		try {
			FontOptions fontOptions = size.getFontOptions().color(getTextColor());
			int lineHeight = fontOptions.getSize();
			int totalTextHeight = lineHeight * 2 + lineGap;
			int firstY = (height - totalTextHeight) / 2 + textOffset;

			drawCenteredLine(line1, firstY, fontOptions);
			drawCenteredLine(line2, firstY + lineHeight + lineGap, fontOptions);
		} finally {
			textState.end();
		}

		if (useDownTexture) {
			size.getButtonDownEdgeDrawOptions(
					getInterfaceStyle(),
					color,
					state,
					getX(),
					getY(),
					getWidth(),
					drawColor
			).draw();
		} else {
			size.getButtonEdgeDrawOptions(
					getInterfaceStyle(),
					color,
					state,
					getX(),
					getY(),
					getWidth(),
					drawColor
			).draw();
		}

		if (isHovering()) {
			addTooltips(perspective);
		}
	}

	private void drawCenteredLine(String text, int y, FontOptions fontOptions) {
		int x = getWidth() / 2 - FontManager.bit.getWidthCeil(text, fontOptions) / 2;
		FontManager.bit.drawString((float)x, (float)y, text, fontOptions);
	}

	@Override
	public List getHitboxes() {
		return singleBox(new Rectangle(getX(), getY(), getWidth(), height));
	}

	public int getButtonHeight() {
		return height;
	}

	private static FormInputSize createSize(final int height) {
		return new FormInputSize(height, 0, 0, 2) {
			@Override
			public DrawOptions getButtonDrawOptions(
					GameInterfaceStyle style,
					ButtonColor color,
					ButtonState state,
					int x,
					int y,
					int width,
					Color drawColor
			) {
				return () -> drawStretchedButton(
						style.button_32.getButtonTexture(color, state),
						x,
						y,
						width,
						height,
						drawColor
				);
			}

			@Override
			public DrawOptions getButtonDownDrawOptions(
					GameInterfaceStyle style,
					ButtonColor color,
					ButtonState state,
					int x,
					int y,
					int width,
					Color drawColor
			) {
				return () -> drawStretchedButton(
						style.button_32.getButtonDownTexture(color, state),
						x,
						y,
						width,
						height,
						drawColor
				);
			}

			@Override
			public DrawOptions getButtonEdgeDrawOptions(
					GameInterfaceStyle style,
					ButtonColor color,
					ButtonState state,
					int x,
					int y,
					int width,
					Color drawColor
			) {
				return () -> {};
			}

			@Override
			public DrawOptions getButtonDownEdgeDrawOptions(
					GameInterfaceStyle style,
					ButtonColor color,
					ButtonState state,
					int x,
					int y,
					int width,
					Color drawColor
			) {
				return () -> {};
			}

			@Override
			public DrawOptions getFormTabDrawOptions(
					GameInterfaceStyle style,
					ButtonColor color,
					ButtonState state,
					int x,
					int y,
					int width,
					Color drawColor
			) {
				return FormInputSize.SIZE_32_TO_40.getFormTabDrawOptions(
						style,
						color,
						state,
						x,
						y,
						width,
						drawColor
				);
			}

			@Override
			public DrawOptions getFormTabDownDrawOptions(
					GameInterfaceStyle style,
					ButtonColor color,
					ButtonState state,
					int x,
					int y,
					int width,
					Color drawColor
			) {
				return FormInputSize.SIZE_32_TO_40.getFormTabDownDrawOptions(
						style,
						color,
						state,
						x,
						y,
						width,
						drawColor
				);
			}

			@Override
			public DrawOptions getFormTabEdgeDrawOptions(
					GameInterfaceStyle style,
					ButtonColor color,
					ButtonState state,
					int x,
					int y,
					int width,
					Color drawColor
			) {
				return FormInputSize.SIZE_32_TO_40.getFormTabEdgeDrawOptions(
						style,
						color,
						state,
						x,
						y,
						width,
						drawColor
				);
			}

			@Override
			public DrawOptions getFormTabDownEdgeDrawOptions(
					GameInterfaceStyle style,
					ButtonColor color,
					ButtonState state,
					int x,
					int y,
					int width,
					Color drawColor
			) {
				return FormInputSize.SIZE_32_TO_40.getFormTabDownEdgeDrawOptions(
						style,
						color,
						state,
						x,
						y,
						width,
						drawColor
				);
			}

			@Override
			public Color getButtonColor(GameInterfaceStyle style, ButtonState state) {
				return FormInputSize.SIZE_32_TO_40.getButtonColor(style, state);
			}

			@Override
			public Color getTextColor(GameInterfaceStyle style, ButtonState state) {
				return FormInputSize.SIZE_32_TO_40.getTextColor(style, state);
			}

			@Override
			public DrawOptions getInputDrawOptions(
					GameInterfaceStyle style,
					int x,
					int y,
					int width
			) {
				return FormInputSize.SIZE_32_TO_40.getInputDrawOptions(
						style,
						x,
						y,
						width
				);
			}

			@Override
			public FontOptions getFontOptions() {
				return new FontOptions(16);
			}

			@Override
			public Rectangle getContentRectangle(int width) {
				return new Rectangle(3, 3, width - 6, height - 6);
			}
		};
	}

	private static void drawStretchedButton(
			GameTexture texture,
			int x,
			int y,
			int width,
			int height,
			Color drawColor
	) {
		int spriteSize = texture.getHeight();

		GameSprite end = new GameSprite(
				texture,
				0,
				0,
				spriteSize,
				spriteSize,
				spriteSize,
				height
		);

		GameSprite middle = new GameSprite(
				texture,
				1,
				0,
				spriteSize,
				spriteSize,
				spriteSize,
				height
		);

		FormComponent.drawWidthComponent(
				end,
				middle,
				x,
				y,
				width,
				drawColor
		);
	}
}