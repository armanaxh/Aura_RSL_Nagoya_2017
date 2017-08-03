package AUR.util.knd;

import java.awt.Polygon;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.LinkedList;

import AUR.util.FibonacciHeap.Entry;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.StandardEntityConstants.Fieryness;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

public class AURAreaGraph {

	public boolean vis = false;
	public boolean needUpdate = false;
	public Area area = null;
	public int areaCostFactor = 1;
	public ArrayList<AURBorder> borders = new ArrayList<AURBorder>();
	public ArrayList<AURAreaGraph> neighbours = new ArrayList<AURAreaGraph>();
	// public ArrayList<EntityID> addedBlockaeds = new ArrayList<>();
	public ArrayList<Polygon> areaBlockadePolygons = new ArrayList<Polygon>();
	public AURWorldGraph wsg = null;
	public AURAreaGrid instanceAreaGrid = null;
	public double cx = 0;
	public double cy = 0;
	public boolean fireChecked = false;
	public final static int AREA_TYPE_ROAD = 0;
	public final static int AREA_TYPE_BULDING = 1;
	public final static int AREA_TYPE_REFUGE = 2;
	public final static int AREA_TYPE_ROAD_HYDRANT = 3;
	public final static int AREA_TYPE_GAS_STATION = 4;
	public boolean onFireProbability = false;
	public int areaType = AREA_TYPE_ROAD;
	public int updateTime = -1;
	public int forgetTime = 30;
	public final static int policeForgetTime = 30;
	public final static int ambulanceForgetTime = 30;
	public final static int fireBirgadeForgetTime = 30;
	public double lastDijkstraCost = AURGeoUtil.INF;
	public AURNode lastDijkstraEntranceNode = null;
	public double lastNoBlockadeDijkstraCost = AURGeoUtil.INF;
	public AURNode lastNoBlockadeDijkstraEntranceNode = null;
	double realDistFromAgent = AURGeoUtil.INF;
	public final static int COLOR_RED = 0;
	public final static int COLOR_GREEN = 1;
	public final static int COLOR_BLUE = 2;
	public final static int COLOR_YELLOW = 3;
	public int color = 0;
	private boolean seen = false;
	private boolean burnt = false;

	public boolean isNeighbour(AURAreaGraph ag) {
		for (AURAreaGraph neiAg : neighbours) {
			if (neiAg.area.getID().equals(ag.area.getID())) {
				return true;
			}
		}
		return false;
	}

	public double distFromAgent() {
		return Math.hypot(cx - wsg.ai.getX(), cy - wsg.ai.getY());
	}

	public boolean isOnFire() {
		if (isBuilding() == false) {
			return false;
		}
		Building b = (Building) (this.area);
		if (b.isFierynessDefined() == false) {
			return false;
		}
		if (false || b.getFierynessEnum().equals(Fieryness.HEATING) || b.getFierynessEnum().equals(Fieryness.BURNING)
				|| b.getFierynessEnum().equals(Fieryness.INFERNO)) {
			return true;
		}
		return false;
	}

	public boolean seen() {
		return seen;
	}

	public void setSeen() {
		lastSeen = wsg.ai.getTime();
		seen = true;
	}

	public boolean burnt() {
		return burnt;
	}

	public boolean damage() {
		if (isBuilding()) {
			Building b = (Building) area;
			if (b.isFierynessDefined()) {
				if (false || b.getFierynessEnum().equals(Fieryness.WATER_DAMAGE)
						|| b.getFierynessEnum().equals(Fieryness.MINOR_DAMAGE)
						|| b.getFierynessEnum().equals(Fieryness.MODERATE_DAMAGE)
						|| b.getFierynessEnum().equals(Fieryness.SEVERE_DAMAGE)) {
					return true;
				}
			}
		}
		return false;
	}

	public void setBurnt() {
		burnt = true;
	}

	public double distFromPointToBorder(double fx, double fy, AURBorder border) {
		return AURGeoUtil.dist(fx, fy, border.CenterNode.x, border.CenterNode.y);
	}

	public double distFromBorderToBorder(AURBorder b1, AURBorder b2) {
		return AURGeoUtil.dist(b1.CenterNode.x, b1.CenterNode.y, b2.CenterNode.x, b2.CenterNode.y);
	}

	public AURAreaGraph(double cx, double cy) {
		this.cx = cx;
		this.cy = cy;
	}

	public boolean isSmall = false;
	
	public int countUnburntsInGrid() {
		int result = 0;

		int i = (int) ((this.cy - wsg.gridDy) / wsg.worldGridSize);
		int j = (int) ((this.cx - wsg.gridDx) / wsg.worldGridSize);
		if (wsg.areaGraphsGrid[i][j] != null) {

			for(AURAreaGraph ag : wsg.areaGraphsGrid[i][j]) {
				if(ag.isBuilding() && ag.burnt() == false && ag.isOnFire()) {
					result++;
				}
			}
		}
		
		return result;
	}
	
	
	public int getWaterNeeded() {
		if (isBuilding() == false) {
			return 0;
		}
		Building b = (Building) area;
		if (b.isTemperatureDefined()) {
			return AURFireSimulator.getWaterNeeded(this, b.getTemperature() * 1.0, 10);
		}
		return AURFireSimulator.getWaterNeeded(this, 1000, 0);

	}

	public boolean isBig = false;
	public int ownerAgent = -1;

	public AURAreaGraph(Area area, AURWorldGraph wsg, AURAreaGrid instanceAreaGrid) {
		if (area == null || wsg == null) {
			return;
		}
		Polygon poly = (Polygon) (area.getShape());
		double area_ = AURGeoUtil.getArea(poly);
		if (area_ < 1000 * 1000 * 25) {
			isSmall = true;
		}
		if (area_ > (wsg.worldGridSize * wsg.worldGridSize * 4) / 6) {
			isBig = true;
		}
		this.area = area;
		this.vis = false;
		this.wsg = wsg;
		this.cx = this.area.getX();
		this.cy = this.area.getY();
		this.instanceAreaGrid = instanceAreaGrid;
		StandardEntityURN areaURN = this.area.getStandardURN();

		this.areaType = AREA_TYPE_ROAD;
		switch (areaURN) { // #toDo
		case REFUGE: {
			areaType = AREA_TYPE_REFUGE;
			break;
		}
		case GAS_STATION: {
			areaType = AREA_TYPE_GAS_STATION;
			break;
		}
		case POLICE_OFFICE:
		case AMBULANCE_CENTRE:
		case FIRE_STATION:
		case BUILDING: {
			areaType = AREA_TYPE_BULDING;
			break;
		}
		case HYDRANT: {
			areaType = AREA_TYPE_ROAD_HYDRANT;
			break;
		}
		}
		switch (wsg.ai.me().getStandardURN()) {
		case POLICE_FORCE: {
			forgetTime = policeForgetTime;
			break;
		}
		case AMBULANCE_TEAM: {
			forgetTime = ambulanceForgetTime;
			break;
		}
		case FIRE_BRIGADE: {
			forgetTime = fireBirgadeForgetTime;
			break;
		}
		}
	}

	public boolean isBuilding() {
		return (areaType == AREA_TYPE_BULDING || areaType == AREA_TYPE_GAS_STATION || areaType == AREA_TYPE_REFUGE);
	}

	public ArrayList<AURNode> getReachabeEdgeNodes(double x, double y) {
		ArrayList<AURNode> result = new ArrayList<>();
		if (area.getShape().contains(x, y) == false) {
			if (area.getShape().intersects(x - 10, y - 10, 20, 20) == false) {
				result.clear();
				return result;
			}
		}

		if (areaBlockadePolygons.size() == 0) {
			for (AURBorder border : borders) {
				for (AURNode node : border.nodes) {
					node.cost = AURGeoUtil.dist(x, y, node.x, node.y);
					result.add(node);
				}

			}
			return result;
		}
		result.addAll(instanceAreaGrid.getReachableEdgeNodesFrom(this, x, y));
		return result;
	}

	public ArrayList<AURNode> getEdgeToAllBorderCenters(double x, double y) {
		ArrayList<AURNode> result = new ArrayList<>();
		for (AURBorder border : borders) {
			border.CenterNode.cost = distFromPointToBorder(x, y, border);
			result.add(border.CenterNode);
		}
		return result;
	}

	public Entry<AURAreaGraph> pQueEntry = null;

	public double lineDistToClosestGasStation() {
		double minDist = AURGeoUtil.INF;
		double dist = 0;
		for (AURAreaGraph ag : wsg.gasStations) {
			Building b = (Building) (ag.area);
			if (b.isFierynessDefined() == false || b.getFierynessEnum().equals(Fieryness.UNBURNT)) {
				dist = AURGeoUtil.dist(ag.cx, ag.cy, this.cx, this.cy);
				if (dist < minDist) {
					minDist = dist;
				}
			}
		}
		return minDist;
	}

	private int lastSeen = -1;

	public int noSeeTime() {
		return wsg.ai.getTime() - lastSeen;
	}

	public void update(AURWorldGraph wsg) {
		realDistFromAgent = AURGeoUtil.INF;
		lastDijkstraCost = AURGeoUtil.INF;
		lastDijkstraEntranceNode = null;
		lastNoBlockadeDijkstraCost = AURGeoUtil.INF;
		lastNoBlockadeDijkstraEntranceNode = null;
		pQueEntry = null;
		needUpdate = false;
		if (wsg.changes.contains(area.getID()) || updateTime < 0) {
			updateTime = wsg.ai.getTime();
			needUpdate = true;
		}
		if (needUpdate || longTimeNoSee()) {
			/*
			 * if(longTimeNoSee()) { addedBlockaeds.clear(); }
			 */
			areaCostFactor = 5;
			areaBlockadePolygons.clear();
			for (AURBorder border : borders) {
				border.reset();
			}
			if (needUpdate) {

				if (isOnFire()) {
					areaCostFactor = 10;
				}
				updateTime = wsg.ai.getTime();
				if (area.getBlockades() != null) {
					/*
					 * if(true &&
					 * wsg.ai.me().getStandardURN().equals(StandardEntityURN.
					 * FIRE_BRIGADE)) { // #toDo int a = (int)
					 * (wsg.si.getPerceptionLosMaxDistance() / 4.1); // #toDo
					 * Rectangle bvrb = new Rectangle( (int) (wsg.ai.getX() -
					 * a), (int) (wsg.ai.getY() - a), (int) (2 * a), (int) (2 *
					 * a) ); Polygon bPolygon; for(EntityID entId :
					 * area.getBlockades()) { Blockade b = (Blockade)
					 * wsg.wi.getEntity(entId); bPolygon = (Polygon)
					 * (b.getShape()); if(false||
					 * addedBlockaeds.contains(b.getID()) ||
					 * bPolygon.intersects(bvrb) ||
					 * bvrb.contains(bPolygon.getBounds())) {
					 * areaBlockadePolygons.add(bPolygon);
					 * addedBlockaeds.add(b.getID()); } } } else {
					 */
					for (EntityID entId : area.getBlockades()) {
						Blockade b = (Blockade) wsg.wi.getEntity(entId);
						areaBlockadePolygons.add((Polygon) (b.getShape()));
					}
					// }

				}
			}
			needUpdate = true;
		}
		
		if(isBuilding()) {
			int temp = 0;
			Building b = ((Building) (this.area));
			temp = 0;
			if(b.isTemperatureDefined()) {
				temp = b.getTemperature();
			}
			if(isOnFire()) {
				if(fireReportTime == -1 || temp != lastTemperature) {
					fireReportTime = this.wsg.ai.getTime();
				}
			} else {
				this.fireReportTime = -1;
			}
			lastTemperature = temp;
		}
		
	}

	
	public int fireReportTime = -1;
	public int lastTemperature = 0;
	
	public final static int FIRE_REPORT_FORGET_TIME = 6;
	
	public boolean isRecentlyReportedFire() {
		return (wsg.ai.getTime() - fireReportTime) <= FIRE_REPORT_FORGET_TIME;
	}
	
	public void initForReCalc() {
		needUpdate = true;
	}

	public void addBorderCenterEdges() {
		AURBorder iB;
		AURBorder jB;
		double cost;
		AUREdge edge = null;
		for (int i = 0; i < borders.size(); i++) {
			iB = borders.get(i);
			for (int j = i + 1; j < borders.size(); j++) {
				jB = borders.get(j);
				cost = distFromBorderToBorder(iB, jB);
				edge = new AUREdge(iB.CenterNode, jB.CenterNode, (int) cost, this);
				iB.CenterNode.edges.add(edge);
				jB.CenterNode.edges.add(edge);

			}
		}
	}

	public boolean longTimeNoSee() {
		if (needUpdate) {
			return false;
		}
		if (areaBlockadePolygons.size() == 0) {
			return false;
		}
		return (wsg.ai.getTime() - updateTime) > forgetTime;
	}
}
