package AUR.util.knd;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import rescuecore2.standard.entities.Building;

public class AURFireValueSetter {

	public AURConvexHull convexHullInstance = new AURConvexHull();
	public ArrayList<AURValuePoint> points = new ArrayList<AURValuePoint>();
	public AURFireSimulator fireSimulatorInstance = new AURFireSimulator();

	public void calc(AURWorldGraph wsg, ArrayList<AURValuePoint> points) {

		wsg.updateInfo(null);
		wsg.dijkstra(wsg.ai.getPosition());

		this.points.clear();
		this.points.addAll(points);

		for (AURValuePoint p : this.points) {
			p.value = 0;
		}

		calc_ConvexHull(this.points, 1.5);

		calc_Capacity(this.points, 0.0);
		calc_Fieryness(this.points, 1.7);
		calc_GasStation(this.points, 1.6);
		calc_noName(this.points, 1.6);
		mul_Color(wsg, points, 1.3);
		Collections.sort(this.points, new Comparator<AURValuePoint>() {
			@Override
			public int compare(AURValuePoint o1, AURValuePoint o2) {
				return Double.compare(o2.value, o1.value);
			}
		});
	}

	public void mul_selfDistance(AURWorldGraph wsg, ArrayList<AURValuePoint> points, double coefficient) {
		double max_ = 0;
		for (AURValuePoint p : points) {
			p.temp_value = p.areaGraph.distFromAgent();
			if (p.temp_value > max_) {
				max_ = p.temp_value;
			}
		}
		if (max_ > 0) {
			for (AURValuePoint p : points) {
				p.temp_value /= max_;
				p.temp_value /= 10;
				p.temp_value = 0.1 - p.temp_value;
			}
		}
		for (AURValuePoint p : points) {
			p.value *= (1 + p.temp_value) * coefficient;
		}
	}



	public void mul_Color(AURWorldGraph wsg, ArrayList<AURValuePoint> points, double coefficient) {
		int agentColor = wsg.getAgentColor();
		for (AURValuePoint p : points) {
			if (p.areaGraph.color == agentColor) {
				p.value *= (wsg.colorCoe[p.areaGraph.color][agentColor]) * coefficient;
			}

		}
	}

	private void calc_Capacity(ArrayList<AURValuePoint> points, double coefficient) {
		convexHullInstance.calc(points);
		double max_ = 0;
		for (AURValuePoint p : points) {
			p.temp_value = fireSimulatorInstance.getBuildingCapacity(p.areaGraph);
			if (p.temp_value > max_) {
				max_ = p.temp_value;
			}
		}
		if (max_ > 0) {
			for (AURValuePoint p : points) {
				p.temp_value /= max_;
			}
		}
		for (AURValuePoint p : points) {
			p.value += p.temp_value * coefficient;
		}
	}

	private void calc_ConvexHull(ArrayList<AURValuePoint> points, double coefficient) {
		convexHullInstance.calc(points);
		double maxDist = 0;
		for (AURValuePoint p : points) {
			p.temp_value = convexHullInstance.centerPoint.dist(p);
		}
		for (AURValuePoint p : convexHullInstance.resultPoints) {
			p.temp_value *= 1.01;
		}
		for (AURValuePoint p : points) {
			Rectangle rect = AURGeoUtil.getOffsetRect(p.areaGraph.area.getShape().getBounds(), 10000);
			if (convexHullInstance.isOnEdge(rect)) {
				p.temp_value *= 1.01;
			}
			if (p.temp_value > maxDist) {
				maxDist = p.temp_value;
			}
		}

		if (maxDist > 0) {
			for (AURValuePoint p : points) {
				p.temp_value /= maxDist;
			}
		}
		for (AURValuePoint p : points) {
			p.value += p.temp_value * coefficient;
		}
	}

	private void calc_Fieryness(ArrayList<AURValuePoint> points, double coefficient) {
		for (AURValuePoint p : points) {
			Building b = (Building) (p.areaGraph.area);
			switch (b.getFierynessEnum()) {
			case HEATING: {
				p.value += 1 * coefficient;
				break;
			}
			case BURNING: {
				p.value += 0.5 * coefficient;
				break;
			}
			case INFERNO: {
				p.value += 0.001 * coefficient;
				break;
			}
			}

		}
	}

	private void calc_GasStation(ArrayList<AURValuePoint> points, double coefficient) {
		double maxDist = 0;
		for (AURValuePoint p : points) {
			p.temp_value = p.areaGraph.lineDistToClosestGasStation();
			if (p.temp_value > maxDist) {
				maxDist = p.temp_value;
			}
		}

		if (maxDist > 0) {
			for (AURValuePoint p : points) {
				p.value += (1 - (p.temp_value / maxDist)) * coefficient;
			}
		}
	}
	
	private void calc_noName(ArrayList<AURValuePoint> points, double coefficient) {
		double max = 0;
		for (AURValuePoint p : points) {
			p.temp_value = p.areaGraph.countUnburntsInGrid();
			if (p.temp_value > max) {
				max = p.temp_value;
			}
		}

		if (max > 0) {
			for (AURValuePoint p : points) {
				p.value += ((p.temp_value / max)) * coefficient;
			}
		}
	}

	public void draw(Graphics2D g) {
		convexHullInstance.draw(g);
		int a = 5;
		g.setFont(new Font("TimesRoman", Font.PLAIN, 1500));
		for (AURValuePoint point : this.points) {
			g.setColor(Color.gray);
			g.fillRect((int) (point.x - a), (int) (point.y - a), 2 * a, 2 * a);
			g.setColor(Color.black);
			g.drawString(point.value + "", (int) (point.x), (int) (point.y));
		}
	}
}