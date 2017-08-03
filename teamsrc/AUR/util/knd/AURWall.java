package AUR.util.knd;

import java.awt.Graphics2D;
import java.awt.Rectangle;

public class AURWall {

	public double x0 = 0;
	public double y0 = 0;
	public double x1 = 0;
	public double y1 = 0;

	public boolean vis = false;

	public AURWall(double x0, double y0, double x1, double y1) {
		this.x0 = x0;
		this.y0 = y0;
		this.x1 = x1;
		this.y1 = y1;
		vis = false;
	}

	public boolean inBoundOrIntersectWith(Rectangle bound) {
		if (bound.contains(x0, y0) || bound.contains(x1, y1)) {
			return true;
		}
		if (bound.intersectsLine(x0, y0, x1, y1)) {
			return true;
		}
		return false;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || obj instanceof AURWall == false) {
			return false;
		}
		AURWall w = (AURWall) obj;
		if (true && this.x0 == w.x0 && this.x1 == w.x1 && this.y0 == w.y0 && this.y1 == w.y1) {
			return true;
		}
		if (true && this.x0 == w.x1 && this.x1 == w.x0 && this.y0 == w.y1 && this.y1 == w.y0) {
			return true;
		}
		return false;
	}

	public void draw(Graphics2D g) {
		g.drawLine((int) x0, (int) y0, (int) x1, (int) y1);
	}
}
