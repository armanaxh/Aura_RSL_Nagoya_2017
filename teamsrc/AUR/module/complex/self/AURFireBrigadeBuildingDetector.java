package AUR.module.complex.self;

import java.util.ArrayList;
import java.util.LinkedList;
import AUR.util.AURCommunication;
import AUR.util.knd.AURAreaGraph;
import AUR.util.knd.AURFireValueSetter;
import AUR.util.knd.AURValuePoint;
import adf.agent.communication.MessageManager;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.module.complex.BuildingDetector;
import rescuecore2.standard.entities.Building;
import rescuecore2.worldmodel.EntityID;

public class AURFireBrigadeBuildingDetector extends BuildingDetector {
	private EntityID result;

	public AUR.util.knd.AURWorldGraph wsg = null;
	AgentInfo ai = null;
	double maxExtDist = 0;

	private AURCommunication acm = null;

	public AURFireBrigadeBuildingDetector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager,
			DevelopData developData) {
		super(ai, wi, si, moduleManager, developData);

		this.ai = ai;
		this.wsg = moduleManager.getModule("knd.AuraWorldGraph");
		maxExtDist = si.getFireExtinguishMaxDistance();
		acm = new AURCommunication(ai, wi, scenarioInfo, developData);
	}

	@Override
	public BuildingDetector updateInfo(MessageManager messageManager) {
		super.updateInfo(messageManager);
		wsg.updateInfo(messageManager);
		if (this.getCountUpdateInfo() >= 2) {
			return this;
		}
		this.acm.updateInfo(messageManager);
		return this;
	}

	public LinkedList<AURAreaGraph> getCloseFires() {
		LinkedList<AURAreaGraph> result = new LinkedList<>();
		wsg.dijkstra(ai.getPosition());
		for (AURAreaGraph ag : wsg.areas.values()) {
			
			if (ag.isOnFire()) {

				if (ag.noSeeTime() <= 4 || ag.isRecentlyReportedFire()) {
					result.add(ag);
				}
			}/* else {
				if(ag.noSeeTime() == 0 && ag.isBuilding()) {
					Building b = (Building) (ag.area);
					if(b.isTemperatureDefined() && b.getTemperature() >= 40) {
						result.add(ag); // check refuge & ...
					}
				}
			}*/
		}
		return result;
	}

	public ArrayList<AURAreaGraph> getReachableUnburntBuildingIDs() {
		wsg.dijkstra(ai.getPosition());
		ArrayList<AURAreaGraph> result = new ArrayList<>();
		for (AURAreaGraph ag : wsg.areas.values()) {
			if (true && ag.isBuilding() && ag.noSeeTime() > 0 && ag.burnt() == false
					&& ag.lastDijkstraEntranceNode != null) {
				result.add(ag);
			}
		}
		return result;
	}



	
	
	@Override
	public BuildingDetector calc() {

		ArrayList<AURValuePoint> points = new ArrayList<>();
		LinkedList<AURAreaGraph> closeFires = getCloseFires();
		for (AURAreaGraph ag : closeFires) {
			points.add(new AURValuePoint(ag.cx, ag.cy, ag));
		}

		if (points.size() > 0) {
			AURFireValueSetter vs = new AURFireValueSetter();
			vs.calc(wsg, points);
			if (vs.points.size() <= 0) {
				this.result = null;
				return this;
			}

			for (int i = 0; i < vs.points.size(); i++) {
				if (vs.points.get(i).areaGraph.distFromAgent() < maxExtDist) {
					this.result = vs.points.get(i).areaGraph.area.getID();
					return this;
				}

			}

		}
		this.result = null;
		return this;
	}

	@Override
	public EntityID getTarget() {
		return this.result;
	}

	@Override
	public BuildingDetector precompute(PrecomputeData precomputeData) {
		super.precompute(precomputeData);
		if (this.getCountPrecompute() >= 2) {
			return this;
		}
		return this;
	}

	@Override
	public BuildingDetector resume(PrecomputeData precomputeData) {
		super.resume(precomputeData);
		if (this.getCountPrecompute() >= 2) {
			return this;
		}
		return this;
	}

	@Override
	public BuildingDetector preparate() {
		super.preparate();
		if (this.getCountPrecompute() >= 2) {
			return this;
		}
		return this;
	}
}
