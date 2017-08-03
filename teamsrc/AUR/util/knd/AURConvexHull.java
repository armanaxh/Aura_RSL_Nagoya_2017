package AUR.util.knd;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Stack;

public class AURConvexHull {

	public ArrayList<AURValuePoint> points = new ArrayList<AURValuePoint>();
	public ArrayList<AURValuePoint> resultPoints = new ArrayList<AURValuePoint>();
	public Polygon convexPolygon = null;
	private AURValuePoint mblP = null;
	private Stack<AURValuePoint> stack = new Stack<AURValuePoint>();

	public AURValuePoint centerPoint = new AURValuePoint(0, 0, null);

	public void calc(ArrayList<AURValuePoint> points) {
		/*
		 * // Test 1 points.clear(); points.add(new Point(100, 100));
		 * points.add(new Point(250, 150)); points.add(new Point(200, 200));
		 * points.add(new Point(250, 250)); points.add(new Point(50, 110));
		 */
		/*
		 * // Test 2 points.clear(); Random rand = new Random(); for(int i = 0;
		 * i < 20; i++) { points.add(new Point(100 + rand.nextInt(400), 100 +
		 * rand.nextInt(400))); }
		 */
		/*
		 * // Test 3 points.clear(); points.add(new Point(100, 100));
		 * points.add(new Point(250, 100)); points.add(new Point(300, 100));
		 * points.add(new Point(400, 100)); points.add(new Point(200, 200));
		 */
		ArrayList<AURValuePoint> temp = new ArrayList<AURValuePoint>();
		this.points.clear();
		this.resultPoints.clear();
		this.points.addAll(points);
		if (points.size() <= 0) {
			calcCenter();
			return;
		}
		mblP = points.get(0);
		for (AURValuePoint p : this.points) {
			if (p.y < mblP.y || (Double.compare(p.y, mblP.y) == 0 && p.x < mblP.x)) {
				mblP = p;
			}
		}
		if (this.points.size() > 1) {
			this.points.remove(mblP);
			Collections.sort(this.points, new Comparator<AURValuePoint>() {
				@Override
				public int compare(AURValuePoint o1, AURValuePoint o2) {
					int orination = AURGeoUtil.getOrination(mblP.x, mblP.y, o1.x, o1.y, o2.x, o2.y);
					if (orination == 0) {
						return Double.compare(mblP.distPower2(o1), mblP.distPower2(o2));
					}
					return orination;
				}
			});

			this.points.add(0, mblP);
			temp.add(0, mblP);
			AURValuePoint curPoint;
			AURValuePoint nexPoint;
			int ori;
			for (int i = 1; i < this.points.size(); i++) {
				if (i == (this.points.size() - 1)) {
					temp.add(this.points.get(i));
					continue;
				}
				curPoint = this.points.get(i);
				nexPoint = this.points.get(i + 1);
				ori = AURGeoUtil.getOrination(mblP.x, mblP.y, curPoint.x, curPoint.y, nexPoint.x, nexPoint.y);
				if (ori != 0) {
					temp.add(this.points.get(i));
				}
			}

			if (temp.size() < 3) {
				this.resultPoints.addAll(temp);
				calcCenter();
				return;
			}

			stack.clear();
			stack.push(temp.get(0));
			stack.push(temp.get(1));
			stack.push(temp.get(2));
			AURValuePoint p1;
			AURValuePoint p2;
			AURValuePoint p3;
			for (int i = 3; i < temp.size(); i++) {
				p1 = stack.elementAt(stack.size() - 2);
				p2 = stack.peek();
				p3 = temp.get(i);
				while (AURGeoUtil.getOrination(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y) == 1) {
					stack.pop();
					p1 = stack.elementAt(stack.size() - 2);
					p2 = stack.peek();
				}
				stack.push(temp.get(i));
			}
			this.resultPoints.addAll(stack);
			calcCenter();
			return;
		} else {
			resultPoints.add(mblP);
			calcCenter();
			return;
		}
	}

	public boolean isOnEdge(Rectangle rect) {
		int size = resultPoints.size();
		if (size == 1) {
			return true;
		}
		int ni;
		AURValuePoint pi;
		AURValuePoint pni;
		for (int i = 0; i < size; i++) {
			ni = (i + 1) % size;
			pi = resultPoints.get(i);
			pni = resultPoints.get(ni);
			if (rect.intersectsLine(pi.x, pi.y, pni.x, pni.y)) {
				return true;
			}
		}
		return false;
	}

	private void calcCenter() {
		centerPoint.set(0, 0);
		int size = resultPoints.size();
		if (size <= 0) {
			return;
		}
		for (AURValuePoint p : resultPoints) {
			centerPoint.x += p.x;
			centerPoint.y += p.y;
		}
		centerPoint.x /= size;
		centerPoint.y /= size;
	}

	public void draw(Graphics2D g) {
		int a = 5;
		int ii = 0;
		for (AURValuePoint point : points) {
			g.setColor(Color.gray);
			g.fillRect((int) (point.x - a), (int) (point.y - a), 2 * a, 2 * a);
			g.setColor(Color.black);
			// g.drawString(ii++ + "", (int) (point.getX()), (int)
			// (point.getY()));
		}
		ii = 0;
		for (AURValuePoint point : resultPoints) {
			a = 2;
			g.setColor(Color.GREEN);
			g.fillRect((int) (point.x - a), (int) (point.y - a), 2 * a, 2 * a);
			g.setColor(Color.black);
			// g.drawString(ii++ + "", (int) (point.x), (int) (point.y));
		}
		g.setColor(Color.gray);
		int ni;
		for (int i = 0; i < resultPoints.size(); i++) {
			ni = (i + 1) % resultPoints.size();
			g.drawLine((int) (resultPoints.get(i).x), (int) (resultPoints.get(i).y), (int) (resultPoints.get(ni).x),
					(int) (resultPoints.get(ni).y));
		}

		a = 4;
		g.setColor(Color.blue);
		g.fillRect((int) (centerPoint.x - a), (int) (centerPoint.y - a), 2 * a, 2 * a);
	}
}