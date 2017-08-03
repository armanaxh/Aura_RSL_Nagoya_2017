package AUR.util.knd;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;

import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.EntityID;

public class AURFireSearchValueSetter {

	public AURConvexHull convexHullInstance = new AURConvexHull();
	public ArrayList<AURValuePoint> points = new ArrayList<AURValuePoint>();
	public AURFireSimulator fireSimulatorInstance = new AURFireSimulator();

	public void calc(AURWorldGraph wsg, ArrayList<AURValuePoint> points, Collection<EntityID> initialCluster, EntityID lastTarget) {

		// long t = System.currentTimeMillis();

		wsg.updateInfo(null);
		wsg.dijkstra(wsg.ai.getPosition());

		this.points.clear();
		this.points.addAll(points);
		for (AURValuePoint p : this.points) {
			p.value = 0;
		}

		// calc_Capacity(this.points, 0.69);

		add_Fieryness(this.points, 1.5);
		add_GasStation(this.points, 0.55);
		// add_CloseFire(this.points, 1.3);

		// add_NoSeeTime(this.points, 1.1);
		add_TravelCost(this.points, 1.5);
		add_InitialCluster(this.points, initialCluster, 1.6);
		mul_Color(wsg, this.points, 1.1);
		add_FireProbability(this.points, 1.9);
		// calc_ConvexHull(this.points, 0.5);
		calc_noName(this.points, 1.0);
		mul_Color(wsg, this.points, 1.1);
		add_NoSeeTime(this.points, 1.08);
		mul_lastTarget(lastTarget, this.points, 1);
		Collections.sort(this.points, new Comparator<AURValuePoint>() {
			@Override
			public int compare(AURValuePoint o1, AURValuePoint o2) {
				return Double.compare(o2.value, o1.value);
			}
		});
	}
	
	
	public void mul_lastTarget(EntityID lastTarget, ArrayList<AURValuePoint> points, double coefficient) {
		for (AURValuePoint p : points) {
			if(p.areaGraph.area.getID().equals(lastTarget)) {
				p.value *= 1.08 * coefficient;
			}
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

	public void calcNoBlockade(AURWorldGraph wsg, ArrayList<AURValuePoint> points, Collection<EntityID> initialCluster) {
		wsg.updateInfo(null);
		wsg.NoBlockadeDijkstra(wsg.ai.getPosition());

		this.points.clear();
		this.points.addAll(points);
		for (AURValuePoint p : this.points) {
			p.value = 0;
		}

		// calc_Capacity(this.points, 0.69);

		add_Fieryness(this.points, 1.5);
		add_GasStation(this.points, 0.55);
		// add_CloseFire(this.points, 1.3);

		// add_NoSeeTime(this.points, 1.1);
		add_NoBlockadeTravelCost(this.points, 1.5);
		add_InitialCluster(this.points, initialCluster, 1.6);
		mul_Color(wsg, this.points, 1.1);
		add_FireProbability(this.points, 1.9);
		// calc_ConvexHull(this.points, 0.5);
		calc_noName(this.points, 1.0);
		mul_Color(wsg, this.points, 1.1);
		add_NoSeeTime(this.points, 1.08);
		Collections.sort(this.points, new Comparator<AURValuePoint>() {
			@Override
			public int compare(AURValuePoint o1, AURValuePoint o2) {
				return Double.compare(o2.value, o1.value);
			}
		});
	}

	private void add_NoSeeTime(ArrayList<AURValuePoint> points, double coefficient) {
		double max_ = 0;
		for (AURValuePoint p : points) {
			p.temp_value = p.areaGraph.noSeeTime();
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

	private void add_Fieryness(ArrayList<AURValuePoint> points, double coefficient) {
		for (AURValuePoint p : points) {
			Building b = (Building) (p.areaGraph.area);
			if (b.isFierynessDefined() == false) {
				// p.value += 0.5 * coefficient;
				continue;
			}
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
				p.value += 0.1 * coefficient;
				break;
			}
			}

		}
	}

	private void add_FireProbability(ArrayList<AURValuePoint> points, double coefficient) {
		for (AURValuePoint p : points) {
			if (p.areaGraph.onFireProbability) {
				p.value += (1 + 0) * coefficient;
			}
		}
	}

	private void add_GasStation(ArrayList<AURValuePoint> points, double coefficient) {
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

	private void add_TravelCost(ArrayList<AURValuePoint> points, double coefficient) {
		double maxDist = 0;
		for (AURValuePoint p : points) {
			p.temp_value = p.areaGraph.lastDijkstraCost;
			if (p.temp_value > maxDist) {
				maxDist = p.temp_value;
			}
		}

		for (AURValuePoint p : points) {
			p.value += (1 - (p.temp_value / maxDist)) * coefficient;
		}
	}

	private void add_NoBlockadeTravelCost(ArrayList<AURValuePoint> points, double coefficient) {
		double maxDist = 0;
		for (AURValuePoint p : points) {
			p.temp_value = p.areaGraph.lastNoBlockadeDijkstraCost;
			if (p.temp_value > maxDist) {
				maxDist = p.temp_value;
			}
		}

		for (AURValuePoint p : points) {
			p.value += (1 - (p.temp_value / maxDist)) * coefficient;
		}
	}

	private void add_InitialCluster(ArrayList<AURValuePoint> points, Collection<EntityID> initialCluster,
			double coefficient) {
		for (AURValuePoint p : points) {
			if (true && p.areaGraph.seen() == false && p.areaGraph.burnt() == false
					&& initialCluster.contains(p.areaGraph.area.getID())) {
				p.value += (1 * coefficient);
			}

		}
	}

	/*
	 * public void draw(Graphics2D g) { convexHullInstance.draw(g); int a = 5;
	 * g.setFont(new Font("TimesRoman", Font.PLAIN, 1500)); for(K_ValuePoint
	 * point : this.points) { g.setColor(Color.gray); g.fillRect((int) (point.x
	 * - a), (int) (point.y - a), 2 * a, 2 * a); g.setColor(Color.black);
	 * g.drawString(point.value + "", (int) (point.x), (int) (point.y)); } }
	 */
}
