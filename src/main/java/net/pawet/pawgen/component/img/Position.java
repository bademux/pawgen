package net.pawet.pawgen.component.img;

import java.awt.*;
import java.util.Random;


enum Position {

	TOP_LEFT() {
		public Point calculate(int enclosingWidth, int enclosingHeight, int width, int height) {
			return new Point(0, 0);
		}
	},

	TOP_CENTER() {
		public Point calculate(int enclosingWidth, int enclosingHeight, int width, int height) {
			return new Point((enclosingWidth / 2) - (width / 2), 0);
		}
	},

	TOP_RIGHT() {
		public Point calculate(int enclosingWidth, int enclosingHeight, int width, int height) {
			return new Point(enclosingWidth - width, 0);
		}
	},

	CENTER_LEFT() {
		public Point calculate(int enclosingWidth, int enclosingHeight, int width, int height) {
			return new Point(0, (enclosingHeight / 2) - (height / 2));
		}
	},


	CENTER_RIGHT() {
		public Point calculate(int enclosingWidth, int enclosingHeight, int width, int height) {
			return new Point(enclosingWidth - width, (enclosingHeight / 2) - (height / 2));
		}
	},

	BOTTOM_LEFT() {
		public Point calculate(int enclosingWidth, int enclosingHeight, int width, int height) {
			return new Point(0, enclosingHeight - height);
		}
	},

	BOTTOM_CENTER() {
		public Point calculate(int enclosingWidth, int enclosingHeight, int width, int height) {
			return new Point((enclosingWidth / 2) - (width / 2), enclosingHeight - height);
		}
	},

	BOTTOM_RIGHT() {
		public Point calculate(int enclosingWidth, int enclosingHeight, int width, int height) {
			return new Point(enclosingWidth - width, enclosingHeight - height);
		}
	};

	/**
	 * Calculates the position of an object enclosed by an enclosing object.
	 *
	 * @param enclosingWidth  The width of the enclosing object that is
	 *                        to contain the enclosed object.
	 * @param enclosingHeight The height of the enclosing object that is
	 *                        to contain the enclosed object.
	 * @param width           The width of the object that is to be
	 *                        placed inside an enclosing object.
	 * @param height          The height of the object that is to be
	 *                        placed inside an enclosing object.
	 * @return The position to place the object.
	 */
	public abstract Point calculate(int enclosingWidth, int enclosingHeight, int width, int height);


	private final static Position[] values = Position.values();
	private final static Random rand = new Random();

	public static Position getRandom() {
		return values[rand.nextInt(values.length)];
	}
}

