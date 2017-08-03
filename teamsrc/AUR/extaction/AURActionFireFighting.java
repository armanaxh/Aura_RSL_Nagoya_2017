package AUR.extaction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import AUR.util.knd.AURAreaGraph;
import AUR.util.knd.AURValuePoint;
import AUR.util.knd.AURWalkWatcher;
import AUR.util.knd.AURWorldGraph;
import adf.agent.action.Action;
import adf.agent.action.common.ActionMove;
import adf.agent.action.common.ActionRest;
import adf.agent.action.fire.ActionExtinguish;
import adf.agent.action.fire.ActionRefill;
import adf.agent.communication.MessageManager;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.extaction.ExtAction;
import adf.component.module.algorithm.PathPlanning;
import rescuecore2.config.NoSuchConfigOptionException;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

public class AURActionFireFighting extends ExtAction {
	private PathPlanning pathPlanning;

	private int maxExtinguishPower;
	private int thresholdRest;
	private int kernelTime;

	private AgentInfo ai = null;
	private EntityID target = null;
	AURWalkWatcher walkWatcher = null;

	public AURWorldGraph wsg = null;

	int hydRate = 0;
	int refRate = 0;
	int maxCapacity = 0;

	public AURActionFireFighting(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo,
			ModuleManager moduleManager, DevelopData developData) {
		super(agentInfo, worldInfo, scenarioInfo, moduleManager, developData);
		this.maxExtinguishPower = scenarioInfo.getFireExtinguishMaxSum();
		this.thresholdRest = developData.getInteger("ActionFireFighting.rest", 100);
		this.walkWatcher = moduleManager.getModule("knd.AuraWalkWatcher");

		this.ai = agentInfo;
		this.wsg = moduleManager.getModule("knd.AuraWorldGraph");
		this.target = null;

		hydRate = scenarioInfo.getFireTankRefillHydrantRate();

		refRate = scenarioInfo.getRawConfig().getIntValue("fire.tank.refill_rate",
				scenarioInfo.getFireTankRefillRate());
		maxCapacity = scenarioInfo.getFireTankMaximum();

		switch (scenarioInfo.getMode()) {
		case PRECOMPUTATION_PHASE:
			this.pathPlanning = moduleManager.getModule("ActionFireFighting.PathPlanning",
					"adf.sample.module.algorithm.SamplePathPlanning");
			break;
		case PRECOMPUTED:
			this.pathPlanning = moduleManager.getModule("ActionFireFighting.PathPlanning",
					"adf.sample.module.algorithm.SamplePathPlanning");
			break;
		case NON_PRECOMPUTE:
			this.pathPlanning = moduleManager.getModule("ActionFireFighting.PathPlanning",
					"adf.sample.module.algorithm.SamplePathPlanning");
			break;
		}
	}

	public final int avgTravelDistance = 35000;

	@Override
	public ExtAction precompute(PrecomputeData precomputeData) {
		super.precompute(precomputeData);
		if (this.getCountPrecompute() >= 2) {
			return this;
		}
		this.pathPlanning.precompute(precomputeData);
		try {
			this.kernelTime = this.scenarioInfo.getKernelTimesteps();
		} catch (NoSuchConfigOptionException e) {
			this.kernelTime = -1;
		}
		return this;
	}

	@Override
	public ExtAction resume(PrecomputeData precomputeData) {
		super.resume(precomputeData);
		if (this.getCountResume() >= 2) {
			return this;
		}
		this.pathPlanning.resume(precomputeData);
		try {
			this.kernelTime = this.scenarioInfo.getKernelTimesteps();
		} catch (NoSuchConfigOptionException e) {
			this.kernelTime = -1;
		}
		return this;
	}

	@Override
	public ExtAction preparate() {
		super.preparate();
		if (this.getCountPreparate() >= 2) {
			return this;
		}
		this.pathPlanning.preparate();
		try {
			this.kernelTime = this.scenarioInfo.getKernelTimesteps();
		} catch (NoSuchConfigOptionException e) {
			this.kernelTime = -1;
		}
		return this;
	}

	@Override
	public ExtAction updateInfo(MessageManager messageManager) {
		super.updateInfo(messageManager);
		wsg.updateInfo(messageManager);
		if (this.getCountUpdateInfo() >= 2) {
			return this;
		}
		this.pathPlanning.updateInfo(messageManager);
		return this;
	}

	@Override
	public ExtAction setTarget(EntityID target) {
		this.target = null;
		if (target != null) {
			StandardEntity entity = this.worldInfo.getEntity(target);
			if (entity instanceof Building) {
				this.target = target;
			}
		}
		return this;
	}

	public ActionMove moveToRefiller() {

		LinkedList<AURAreaGraph> list = wsg.getAllRefillers();
		wsg.dijkstra(ai.getPositionArea().getID());
		LinkedList<AURValuePoint> vps = new LinkedList<AURValuePoint>();
		for (AURAreaGraph ag : list) {
			vps.add(new AURValuePoint(ag.cx, ag.cy, ag));
		}

		for (AURValuePoint vp : vps) {
			vp.value = (vp.areaGraph.lastDijkstraCost) + 1000;
			if (vp.areaGraph.areaType == AURAreaGraph.AREA_TYPE_REFUGE) {
				vp.value *= 1 + ((1 - wsg.colorCoe[wsg.getAgentColor()][vp.areaGraph.color])) / 2;
			} else {
				int coe = (Math.abs((vp.areaGraph.ownerAgent - wsg.agentOrder)));
				
				
				
				if(vp.areaGraph.ownerAgent == wsg.agentOrder) {
					vp.value *= 1e8;
				} else {
					vp.value *= (1e20 + coe * 1e20);
					//System.out.println(vp.value);
				}
				
				if(vp.areaGraph.isSmall) {
					vp.value *= (1e6);
				}
				
			}

		}

		vps.sort(new Comparator<AURValuePoint>() {
			@Override
			public int compare(AURValuePoint o1, AURValuePoint o2) {
				return Double.compare(o1.value, o2.value);
			}
		});
		
		if(vps == null || vps.size() <= 0) {
			return null;
		}

		Collection<EntityID> targets = new ArrayList<>();
		targets.add(vps.get(0).areaGraph.area.getID());

		this.pathPlanning.setFrom(ai.getPositionArea().getID());
		this.pathPlanning.setDestination(targets);
		this.pathPlanning.calc();
		List<EntityID> path = this.pathPlanning.getResult();
		if (path != null && path.size() > 1) {
			return walkWatcher.check(new ActionMove(path));
		}
		return null;
	}

	public Action moveToRefuge() {
		this.pathPlanning.setFrom(ai.getPositionArea().getID());
		this.pathPlanning.setDestination(wsg.getAllRefuges());
		this.pathPlanning.calc();
		List<EntityID> path = this.pathPlanning.getResult();
		if (path != null && path.size() > 0) {
			return walkWatcher.check(new ActionMove(path));
		}
		return null;
	}
	

	private EntityID refillerTarget = null;
	
	public boolean waitForRefill(FireBrigade agent) {
		if(refillerTarget == null) {
			return false;
		}
		if (agent.getWater() < maxCapacity && refillerTarget.equals(agent.getPosition())) {
			return true;
		}
		
		return false;
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
	
	public EntityID beforeLastBadSituationPos = null;
	public EntityID lastBadSituationPos = null;

	public boolean badSituation() {
		
		int fcount = 0;
		int bcount = 0;
		int dcount = 0;
		int allBuildings = 0;
		
		ArrayList<AURAreaGraph> list = wsg.getAreaGraph(wsg.changes);
		ArrayList<AURAreaGraph> rubbs = getReachableUnburntBuildingIDs();
		rubbs.removeAll(list);
		if (list.size() < 5) {
			return false;
		}
		for (AURAreaGraph ag : list) {
			if (ag.isOnFire()) {
				fcount++;
			}
			if (ag.burnt()) {
				bcount++;
			}
			if (ag.damage()) {
				dcount++;
			}
			if (ag.isBuilding()) {
				allBuildings++;
			}
		}
		/*
		 * if(fcount <= 2) { return false; }
		 */
		if (allBuildings == 0) {
			return false;
		}
		// System.out.println("\t" + ((double) (fcount + bcount + dcount) /
		// allBuildings));
		return ((double) (fcount + bcount + 0) / allBuildings) >= 0.75;
	}
	
	public int badCount = 0;
	@Override
	public ExtAction calc() {
		this.result = null;
		FireBrigade agent = (FireBrigade) this.agentInfo.me();

		if (waitForRefill(agent)) {
			//System.out.println("wait");
			exCount = 0;
			this.result = new ActionRefill();
			return this;
		}
		
		this.refillerTarget = null;
		
		if (needRefill()) {
			ActionMove ma = moveToRefiller();
			this.result = ma;
			if(ma != null && ma.getPath() != null && ma.getPath().size() > 1) {
				this.refillerTarget = ma.getPath().get(ma.getPath().size() - 1);
			} else {
				//System.out.println("null");
				this.result = null;
			}
			//System.out.println(refillerTarget.getValue() + "\t #################");
			
			exCount = 0;
			return this;
		}

		if (needRest(agent)) {
			if (ai.getPositionArea().getStandardURN().equals(StandardEntityURN.REFUGE)) {
				this.result = new ActionRest();
			} else {
				this.result = moveToRefuge();
			}

			exCount = 0;
			return this;
		}

		if (exCount >= 7) {
			exCount = 0;
			return this;
		}

		if (target == null) {
			exCount = 0;
			return this;
		}
		
		if (badCount < 5 && ai.getPosition().equals(beforeLastBadSituationPos) == false && badSituation()) {
			beforeLastBadSituationPos = lastBadSituationPos;
			lastBadSituationPos = ai.getPosition();
			badCount++;
			return this;
		}
		badCount = 0;
		
		this.result = this.calcExtinguish(agent, this.pathPlanning, this.target);
		return this;
	}

	public boolean readyExtinguishing() {
		int max_ = scenarioInfo.getFireExtinguishMaxDistance();

		if (this.worldInfo.getDistance(agentInfo.me().getID(), target) < max_) {
			if (agentInfo.getWater() > 1) {
				return true;
			}
		}
		return false;
	}

	public boolean needRefill() {
		return ai.getWater() < 800;
	}

	int exCount = 0;

	private Action calcExtinguish(FireBrigade agent, PathPlanning pathPlanning, EntityID target) {
		EntityID agentPosition = agent.getPosition();
		if (readyExtinguishing()) {
			Building b = (Building) ((Area) (worldInfo.getEntity(target)));
			exCount++;
			AURAreaGraph ag = wsg.getAreaGraph(target);
			int w = (int) ((double) ag.getWaterNeeded() * 1.0);
			w = Math.min(w, this.maxExtinguishPower);
			return new ActionExtinguish(target, Math.min(w, agent.getWater() - 1));
		} else {
			exCount = 0;
			return this.getMoveAction(pathPlanning, agentPosition, target);
		}
	}

	private Action getMoveAction(PathPlanning pathPlanning, EntityID from, EntityID target) {
		pathPlanning.setFrom(from);
		pathPlanning.setDestination(target);
		double aiX = agentInfo.getX();
		double aiY = agentInfo.getY();
		Area area = (Area) (worldInfo.getEntity(target));
		double rcX = area.getX();
		double rcY = area.getY();

		double range = scenarioInfo.getFireExtinguishMaxDistance();
		List<EntityID> path = pathPlanning.calc().getResult();
		if (path != null && path.size() > 0) {

			if (path.size() == 1 && path.get(0).equals(target)) {
				path.clear();
				path.add(from);

				AURAreaGraph ag = wsg.getAreaGraph(agentInfo.getPositionArea().getID());
				Point2D dest = wsg.instanceAreaGrid.getPointInRange(ag, rcX, rcY, range, aiX, aiY);
				if (dest != null) {
					return walkWatcher.check(new ActionMove(path, (int) (dest.getX()), (int) (dest.getY())));
				}

			} else {
				return walkWatcher.check(new ActionMove(path));
			}

		}
		return null;
	}

	private boolean needRest(Human agent) {
		int hp = agent.getHP();
		int damage = agent.getDamage();
		if (hp == 0 || damage == 0) {
			return false;
		}
		int activeTime = (hp / damage) + ((hp % damage) != 0 ? 1 : 0);
		if (this.kernelTime == -1) {
			try {
				this.kernelTime = this.scenarioInfo.getKernelTimesteps();
			} catch (NoSuchConfigOptionException e) {
				this.kernelTime = -1;
			}
		}
		return damage >= this.thresholdRest || (activeTime + this.agentInfo.getTime()) < this.kernelTime;
	}

}
