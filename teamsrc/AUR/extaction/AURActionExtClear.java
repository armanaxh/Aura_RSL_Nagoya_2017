package AUR.extaction;

import java.awt.Polygon;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;

import AUR.util.knd.AURWalkWatcher;
import AUR.util.knd.AURWorldGraph;
import adf.agent.action.Action;
import adf.agent.action.common.ActionMove;
import adf.agent.action.common.ActionRest;
import adf.agent.action.police.ActionClear;
import adf.agent.communication.MessageManager;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.extaction.ExtAction;
import rescuecore2.config.NoSuchConfigOptionException;
import rescuecore2.misc.geometry.GeometryTools2D;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.Edge;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.PoliceForce;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

public class AURActionExtClear extends ExtAction {

	private int clearDistance;
	private int distanceLimit;
	private int forcedMove;
	private int thresholdRest;
	private int kernelTime;

	private EntityID target;
	private int oldClearX;
	private int oldClearY;
	private int count;

	private double agentSize = 602;
	private double repairRate = 2;

	private AURWorldGraph wsg  = null;
	private AURWalkWatcher walkWatcher = null;

	public AURActionExtClear(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager,	DevelopData developData)
	{
		super(ai, wi, si, moduleManager, developData);
		this.clearDistance = si.getClearRepairDistance();
		this.distanceLimit = 9*this.clearDistance/10;
		this.forcedMove = developData.getInteger("ActionExtClear.forcedMove", 1);
		this.thresholdRest = developData.getInteger("ActionExtClear.rest", 100);

		this.target = null;
		this.oldClearX = 0;
		this.oldClearY = 0;
		this.count = 0;

		this.wsg 			= moduleManager.getModule("knd.AuraWorldGraph");
		this.walkWatcher 	= moduleManager.getModule("knd.AuraWalkWatcher");
	}

	@Override
	public ExtAction precompute(PrecomputeData precomputeData) {
		super.precompute(precomputeData);
		if (this.getCountPrecompute() >= 2) {
			return this;
		}
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
		if (this.getCountUpdateInfo() >= 2) {
			return this;
		}
		wsg.updateInfo(messageManager);
		return this;
	}

	@Override
	public ExtAction setTarget(EntityID target)
	{
		this.target = null;
		StandardEntity entity = this.worldInfo.getEntity(target);
		if (entity != null)
		{
			if (entity instanceof Road)
			{
				this.target = target;
			} else if (entity.getStandardURN().equals(StandardEntityURN.BLOCKADE))
			{
				this.target = ((Blockade) entity).getPosition();
			} else if (entity instanceof Building)
			{
				this.target = target;
			}
		}
		return this;
	}

	@Override
	public ExtAction calc()
	{
		this.result = null;
		PoliceForce policeForce = (PoliceForce) this.agentInfo.me();

		if (this.needRest(policeForce))
		{
			List<EntityID> list = new ArrayList<>();
			if (this.target != null)
				list.add(this.target);
			this.result = this.calcRest(policeForce, list);
		}

		if (this.target == null)
			return this;

		EntityID agentPosition = policeForce.getPosition();
		StandardEntity positionStandardEntity = Objects.requireNonNull(this.worldInfo.getEntity(agentPosition));
		StandardEntity targetStandardEntity = this.worldInfo.getEntity(this.target);

		if (targetStandardEntity == null || !(targetStandardEntity instanceof Area))
			return this;

		/*
		 * agent is standing on anywhere
		 * and there is another human object, trapped in a blockade
		 */
		if (positionStandardEntity instanceof Road)
		{
			this.result = this.getRescueAction(policeForce, (Road) positionStandardEntity,(Area) targetStandardEntity);

			if (this.result != null)
				return this;
		}
		/*
		 * if agent is standing on the target
		 */
		if (agentPosition.equals(this.target))
		{
			this.result = this.getAreaClearAction(policeForce, targetStandardEntity);

		}
		/*
		 * if agent is standing on a target's neighborhood
		 */
		else if (((Area) targetStandardEntity).getEdgeTo(agentPosition) != null)
		{
			this.result = this.selfClean(policeForce, (Area) targetStandardEntity);

		}
		/*
		 * if agent is standing on somewhere else
		 */
		else
		{
			List<EntityID> path = this.wsg.getNoBlockadePathToClosest(agentPosition, new ArrayList<EntityID>(Arrays.asList(this.target)));
			if (path != null && path.size() > 0)
			{
				int index = path.indexOf(agentPosition);
				if (index == -1)
				{
					Area area = (Area) positionStandardEntity;
					for (int i = 0; i < path.size(); i++)
					{
						if (area.getEdgeTo(path.get(i)) != null)
						{
							index = i;
							break;
						}
					}
				} else if (index >= 0)
				{
					index++;
				}
				if (index >= 0 && index < path.size())
				{
					StandardEntity entity = this.worldInfo.getEntity(path.get(index));
					this.result = this.getNeighbourPositionAction(policeForce, (Area) entity, path);

					if (this.result != null && this.result.getClass() == ActionMove.class)
						if (!((ActionMove) this.result).getUsePosition())
							this.result = null;
				}
				if (this.result == null)
				{
					//this.result = new ActionMove(path);
					this.result = walkWatcher.check(new ActionMove(path));

				}
			}
		}
		return this;
	}

	private Action getRescueAction(PoliceForce police, Road road, Area targetStandardEntity)
	{
		if (!road.isBlockadesDefined())
			return null;

		double policeX = police.getX();
		double policeY = police.getY();

		Collection<Blockade> blockades = this.worldInfo.getBlockades(road).stream().filter(Blockade::isApexesDefined)
				.collect(Collectors.toSet());
		Collection<StandardEntity> agents = this.worldInfo.getEntitiesOfType(StandardEntityURN.AMBULANCE_TEAM,
				StandardEntityURN.FIRE_BRIGADE/*, StandardEntityURN.CIVILIAN*/, StandardEntityURN.POLICE_FORCE);

		List<StandardEntity> listSE = new ArrayList<>(agents);
		if (listSE.contains((StandardEntity) police))
		{
			for (Blockade blockade : blockades)
			{
				if (!this.isInside(policeX, policeY, blockade.getApexes()))
					continue;
				return new ActionClear(blockade.getID());
			}
		}
		listSE.remove((StandardEntity) police);

		Action moveAction = null;
		for (StandardEntity entity : listSE)
		{
			Human human = (Human) entity;
			if (!human.isPositionDefined() || human.getPosition().getValue() != road.getID().getValue())
				continue;

			double humanX = human.getX();
			double humanY = human.getY();

			Point2D pointBestCollideWithBlockade = null;
			double distanceMin = Double.MAX_VALUE;
			for (Blockade blockade : blockades)
			{
				Point2D l2d = this.getPointIntersectLine2D(policeX, policeY, humanX, humanY, blockade);
				if (l2d != null)
				{
					double temp = getDistance(policeX, policeY, l2d.getX(), l2d.getY());
					if (temp < distanceMin)
					{
						distanceMin = temp;
						pointBestCollideWithBlockade = l2d;
					}
				}
			}

			if (pointBestCollideWithBlockade != null)
			{
				double midl2dx = pointBestCollideWithBlockade.getX();
				double midl2dy = pointBestCollideWithBlockade.getY();

				Vector2D vector = this.scaleClear(this.getVector(policeX, policeY, midl2dx, midl2dy));
				int clearX = (int) (policeX + vector.getX());
				int clearY = (int) (policeY + vector.getY());

				moveAction = new ActionMove(Lists.newArrayList(road.getID()),(int) midl2dx,(int) midl2dy);

				if (this.getDistance(policeX, policeY, midl2dx, midl2dy) < this.distanceLimit)
				{
					if (this.equalsPoint(this.oldClearX, this.oldClearY, clearX, clearY))
					{
						if (this.count >= this.forcedMove)
						{
							this.count = 0;
							return new ActionMove(Lists.newArrayList(road.getID()),(int) midl2dx,(int) midl2dy);
						}
						this.count++;
					}
					this.oldClearX = (int) clearX;
					this.oldClearY = (int) clearY;
					return new ActionClear(clearX, clearY);
				}
			}

			if (moveAction != null)
			{
				return moveAction;
			}

		}
		return moveAction;
	}

	private Point2D getPointClear(double startX, double startY, double endX, double endY)
	{
		Vector2D vector = this.scaleClear(this.getVector(startX, startY, endX, endY));
		int clearX = (int) (startX + vector.getX());
		int clearY = (int) (startY + vector.getY());

		return new Point2D(clearX, clearY);
	}

	private Action getAreaClearAction(PoliceForce police, StandardEntity targetEntity)
	{
		if (targetEntity instanceof Building)
		{
			return null;
		}
		Road road = (Road) targetEntity;
		if (!road.isBlockadesDefined() || road.getBlockades().isEmpty())
		{
			return null;
		}

		double agentX = police.getX();
		double agentY = police.getY();

		Area areaTargetEntity = (Area) targetEntity;
		Edge edge = null;

		Collection<Blockade> blockades = this.worldInfo.getBlockades(road).stream().filter(Blockade::isApexesDefined)
				.collect(Collectors.toSet());

		Point2D PointBestCollideWithBlocakde = null;
		double distanceMin = Double.MAX_VALUE;

		for (EntityID ied : areaTargetEntity.getNeighbours())
		{
			StandardEntity se = worldInfo.getEntity(ied);
			if (se instanceof Building)
			{
				Area building = (Area) se;
				edge = building.getEdgeTo(targetEntity.getID());

				double midX = (edge.getStartX() + edge.getEndX()) / 2;
				double midY = (edge.getStartY() + edge.getEndY()) / 2;


				for (Blockade blockade : blockades)
				{
					Point2D l2d = this.getPointIntersectLine2D(agentX, agentY, midX, midY, blockade);
					if (l2d != null)
					{
						double temp = getDistance(agentX, agentY, l2d.getX(), l2d.getY());
						if (temp < distanceMin)
						{
							distanceMin = temp;
							PointBestCollideWithBlocakde = l2d;
						}
					}
				}
			}

			if (PointBestCollideWithBlocakde != null) break;
		}

		Action actionClear = null;

		if (PointBestCollideWithBlocakde != null)
		{
			double midx = PointBestCollideWithBlocakde.getX();
			double midy = PointBestCollideWithBlocakde.getY();
			Point2D pointClear = getPointClear(agentX, agentY, midx, midy);
			int clearX = (int) (pointClear.getX());
			int clearY = (int) (pointClear.getY());
			if (this.getDistance(agentX, agentY, midx, midy) < this.distanceLimit)
			{
				actionClear = new ActionClear(clearX, clearY);
				if (this.equalsPoint(this.oldClearX, this.oldClearY, clearX, clearY))
				{
					if (this.count >= this.forcedMove)
					{
						this.count = 0;
						return new ActionMove(Lists.newArrayList(road.getID()), (int) midx, (int) midy);
					}
					this.count++;
				}
				this.oldClearX = clearX;
				this.oldClearY = clearY;
			} else
				return new ActionMove(Lists.newArrayList(road.getID()), (int) midx, (int) midy);

			return actionClear;
		}
		return null;
	}

	private Action selfClean(PoliceForce police, Area target)
	{
		double agentX = police.getX();
		double agentY = police.getY();
		StandardEntity position = Objects.requireNonNull(this.worldInfo.getPosition(police));
		Edge edge = target.getEdgeTo(position.getID());

		if (edge == null) return null;

		double midX = (edge.getStartX() + edge.getEndX()) / 2;
		double midY = (edge.getStartY() + edge.getEndY()) / 2;

		/*
		 * if agent standing on the neighbor position
		 * and there is a blockade ahead
		 */
		if (position instanceof Area)
		{
			Area road = (Area) position;
			if (road.isBlockadesDefined() && road.getBlockades().size() > 0)
			{
				Action actionClear = null;
				double distance = Double.MAX_VALUE;
				Point2D targetPoint2D = null;
				for (Blockade blockade : this.worldInfo.getBlockades(road))
				{

					if (blockade == null || !blockade.isApexesDefined()) continue;

					Point2D l2d = this.getPointIntersectLine2D(agentX, agentY, midX, midY, blockade);

					if (l2d != null)
					{
						double temp = GeometryTools2D.getDistance(l2d, new Point2D(agentX, agentY));
						if (temp < distance)
						{
							distance = temp;
							targetPoint2D = l2d;
						}
					}
				}
				if (targetPoint2D != null)
				{
					double midl2dx = targetPoint2D.getX();
					double midl2dy = targetPoint2D.getY();
					actionClear = new ActionMove(Lists.newArrayList(position.getID()),(int) midl2dx, (int) midl2dy );
					if (this.getDistance(agentX, agentY, midl2dx, midl2dy) < this.distanceLimit)
					{
						Vector2D vector = this.scaleClear(this.getVector(agentX, agentY, midl2dx, midl2dy));
						int clearX = (int) (agentX + vector.getX());
						int clearY = (int) (agentY + vector.getY());

						actionClear = new ActionClear(clearX, clearY);
						if (this.equalsPoint(this.oldClearX, this.oldClearY, clearX, clearY))
						{
							if (this.count >= this.forcedMove)
							{
								this.count = 0;
								return new ActionMove(Lists.newArrayList(road.getID()),(int) midl2dx, (int)midl2dy);
							}
							this.count++;
						}
						this.oldClearX = clearX;
						this.oldClearY = clearY;
					}
				}
				if (actionClear != null) return actionClear;
			}
		}
		if (target.isBlockadesDefined() && target.getBlockades().size() > 0)
		{
			Action actionMove = new ActionMove(Lists.newArrayList(position.getID(), target.getID()), target.getX(),target.getY());

			double midXEdge = (edge.getStartX()+edge.getEndX())/2;
			double midYEdge = (edge.getStartY()+edge.getEndY())/2;

			double tX = target.getX();
			double tY = target.getY();

			Vector2D vectorDirection = getVector(midXEdge, midYEdge, tX, tY).normalised().scale(this.agentSize);
			double targetX = midXEdge + vectorDirection.getX();
			double targetY = midYEdge + vectorDirection.getY();

			double distance = Double.MAX_VALUE;
			Point2D targetPoint2D = null;
			for (Blockade blockade : this.worldInfo.getBlockades(target))
			{
				Point2D l2d = this.getPointIntersectLine2D(midXEdge, midYEdge, targetX, targetY, blockade);

				if (l2d != null)
				{
					double temp = GeometryTools2D.getDistance(l2d, new Point2D(agentX, agentY));
					if (temp < distance)
					{
						distance = temp;
						targetPoint2D = l2d;
					}
				}
			}
			if (targetPoint2D != null)
			{
				double midl2dx = targetPoint2D.getX();
				double midl2dy = targetPoint2D.getY();

				Vector2D vector = this.scaleClear(this.getVector(agentX, agentY, midl2dx, midl2dy));
				int clearX = (int) (agentX + vector.getX());
				int clearY = (int) (agentY + vector.getY());

				actionMove = new ActionMove(Lists.newArrayList(position.getID(), target.getID()), (int)midl2dx, (int)midl2dy);

				if (this.getDistance(agentX, agentY, midl2dx, midl2dy) < this.distanceLimit)
				{
					if (this.equalsPoint(this.oldClearX, this.oldClearY, clearX, clearY)) {
						if (this.count >= this.forcedMove)
						{
							this.count = 0;
							return new ActionMove(Lists.newArrayList(position.getID(), target.getID()));
						}
						this.count++;
					}
					this.oldClearX = (int) clearX;
					this.oldClearY = (int) clearY;
					return new ActionClear(clearX, clearY);
				}
			}
			return actionMove;
		}
		return new ActionMove(Lists.newArrayList(position.getID(), target.getID()));
	}

	private Action getNeighbourPositionAction(PoliceForce police, Area target, List<EntityID> path) {
		double agentX = police.getX();
		double agentY = police.getY();

		StandardEntity position = Objects.requireNonNull(this.worldInfo.getPosition(police));

		Edge edge = target.getEdgeTo(position.getID());
		if (edge == null)
		{
			return null;
		}

		if (position instanceof Area)
		{
			Area road = (Area) position;
			if (road.isBlockadesDefined() && road.getBlockades().size() > 0)
			{
				double midX = (edge.getStartX() + edge.getEndX()) / 2;
				double midY = (edge.getStartY() + edge.getEndY()) / 2;
				Action actionClear = null;
				double distance = Double.MAX_VALUE;
				Point2D targetPoint2D = null;
				for (Blockade blockade : this.worldInfo.getBlockades(road))
				{
					if (blockade == null || !blockade.isApexesDefined())
					{
						continue;
					}
					Point2D l2d = this.getPointIntersectLine2D(agentX, agentY, midX, midY, blockade);

					if (l2d != null)
					{
						double temp = GeometryTools2D.getDistance(l2d, new Point2D(agentX, agentY));
						if (temp < distance)
						{
							distance = temp;
							targetPoint2D = l2d;
						}
					}
				}
				if (targetPoint2D != null)
				{
					double midl2dx = targetPoint2D.getX();
					double midl2dy = targetPoint2D.getY();
					actionClear = new ActionMove(Lists.newArrayList(position.getID()),(int) midl2dx, (int) midl2dy );
					if (this.getDistance(agentX, agentY, midl2dx, midl2dy) < this.distanceLimit)
					{
						Vector2D vector = this.scaleClear(this.getVector(agentX, agentY, midl2dx, midl2dy));
						int clearX = (int) (agentX + vector.getX());
						int clearY = (int) (agentY + vector.getY());

						actionClear = new ActionClear(clearX, clearY);
						if (this.equalsPoint(this.oldClearX, this.oldClearY, clearX, clearY))
						{
							if (this.count >= this.forcedMove)
							{
								this.count = 0;
								return new ActionMove(Lists.newArrayList(road.getID()),(int) midl2dx, (int)midl2dy);
							}
							this.count++;
						}
						this.oldClearX = clearX;
						this.oldClearY = clearY;
					}
				}
				if (actionClear != null)
				{
					return actionClear;
				}
			}
		}
		if (target instanceof Area)
		{
			Area road = (Area) target;
			if (!road.isBlockadesDefined() || road.getBlockades().isEmpty())
			{
				return new ActionMove(Lists.newArrayList(position.getID(), target.getID()));
			}
			Blockade clearBlockade = null;
			Double minPointDistance = Double.MAX_VALUE;
			int clearX = 0;
			int clearY = 0;

			Edge e = null;
			int indexNeighborPos = path.indexOf(target.getID());
			int indexNeighborsNeighbor = indexNeighborPos+1;

			StandardEntity standardEntityNeighborPos = worldInfo.getEntity(path.get(indexNeighborPos));

			e = ((Area) standardEntityNeighborPos).getEdgeTo(path.get(indexNeighborsNeighbor));
			/*
			for (int i = 0; i < path.size(); i++)
			{
				if (path.get(i).equals(target.getID()))
				{
					StandardEntity se = worldInfo.getEntity(path.get(i));
					e = ((Area) se).getEdgeTo(path.get(++i));
				}
			}
			 */
			 if (e == null)
			 {
				 EntityID leid = ((Area) standardEntityNeighborPos).getNeighbours().get(0);
				 if (position.getID().equals(leid))
				 {
 					leid = ((Area) standardEntityNeighborPos).getNeighbours().get(1);
				 }
				e = ((Area) standardEntityNeighborPos).getEdgeTo(leid);
			 }
			double midXx = (e.getStartX() + e.getEndX()) / 2;
			double midYy = (e.getStartY() + e.getEndY()) / 2;

			double midX = (edge.getStartX() + edge.getEndX()) / 2;
			double midY = (edge.getStartY() + edge.getEndY()) / 2;

			Vector2D vectorDirection = getVector(midX, midY, midXx, midYy).normalised().scale(this.agentSize);
			double targetX = midX + vectorDirection.getX();
			double targetY = midY + vectorDirection.getY();

			for (EntityID id : road.getBlockades())
			{
				Blockade blockade = (Blockade) this.worldInfo.getEntity(id);
				Point2D l2d = this.getPointIntersectLine2D(midX, midY, targetX, targetY, blockade);
				if (l2d != null)
				{

					double midl2dx = l2d.getX();
					double midl2dy = l2d.getY();

					double distance = this.getDistance(agentX, agentY, midl2dx, midl2dy);
					if (distance < minPointDistance)
					{
						clearBlockade = blockade;
						minPointDistance = distance;
						clearX = (int)midl2dx;
						clearY = (int)midl2dy;
					}
				}
			}

			if (clearBlockade != null && minPointDistance < this.distanceLimit)
			{
				Vector2D vector = this.scaleClear(this.getVector(agentX, agentY, clearX, clearY));
				clearX = (int) (agentX + vector.getX());
				clearY = (int) (agentY + vector.getY());
				if (this.equalsPoint(this.oldClearX, this.oldClearY, clearX, clearY))
				{
					if (this.count >= this.forcedMove)
					{
						this.count = 0;
						return new ActionMove(Lists.newArrayList(road.getID()), clearX, clearY);
					}
					this.count++;
				}
				this.oldClearX = clearX;
				this.oldClearY = clearY;
				return new ActionClear(clearX, clearY, clearBlockade);
			}
		}

		return new ActionMove(Lists.newArrayList(position.getID(), target.getID()));
	}

	private boolean equalsPoint(double p1X, double p1Y, double p2X, double p2Y) {
		return this.equalsPoint(p1X, p1Y, p2X, p2Y, 1000.0D);
	}

	private boolean equalsPoint(double p1X, double p1Y, double p2X, double p2Y, double range) {
		return (p2X - range < p1X && p1X < p2X + range) && (p2Y - range < p1Y && p1Y < p2Y + range);
	}

	private boolean isInside(double pX, double pY, int[] apex)
	{
		Point2D p = new Point2D(pX, pY);
		Vector2D v1 = (new Point2D(apex[apex.length - 2], apex[apex.length - 1])).minus(p);
		Vector2D v2 = (new Point2D(apex[0], apex[1])).minus(p);
		double theta = this.getAngle(v1, v2);

		for (int i = 0; i < apex.length - 2; i += 2) {
			v1 = (new Point2D(apex[i], apex[i + 1])).minus(p);
			v2 = (new Point2D(apex[i + 2], apex[i + 3])).minus(p);
			theta += this.getAngle(v1, v2);
		}
		return Math.round(Math.abs((theta / 2) / Math.PI)) >= 1;
	}

	private boolean intersect(double agentX, double agentY, double pointX, double pointY, Area area) {
		for (Edge edge : area.getEdges()) {
			double startX = edge.getStartX();
			double startY = edge.getStartY();
			double endX = edge.getEndX();
			double endY = edge.getEndY();
			if (java.awt.geom.Line2D.linesIntersect(agentX, agentY, pointX, pointY, startX, startY, endX, endY)) {
				double midX = (edge.getStartX() + edge.getEndX()) / 2;
				double midY = (edge.getStartY() + edge.getEndY()) / 2;
				if (!equalsPoint(pointX, pointY, midX, midY) && !equalsPoint(agentX, agentY, midX, midY)) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean intersect(Blockade blockade, Blockade another) {
		if (blockade.isApexesDefined() && another.isApexesDefined()) {
			int[] apexes0 = blockade.getApexes();
			int[] apexes1 = another.getApexes();
			for (int i = 0; i < (apexes0.length - 2); i += 2) {
				for (int j = 0; j < (apexes1.length - 2); j += 2) {
					if (java.awt.geom.Line2D.linesIntersect(apexes0[i], apexes0[i + 1], apexes0[i + 2], apexes0[i + 3],
							apexes1[j], apexes1[j + 1], apexes1[j + 2], apexes1[j + 3])) {
						return true;
					}
				}
			}
			for (int i = 0; i < (apexes0.length - 2); i += 2) {
				if (java.awt.geom.Line2D.linesIntersect(apexes0[i], apexes0[i + 1], apexes0[i + 2], apexes0[i + 3],
						apexes1[apexes1.length - 2], apexes1[apexes1.length - 1], apexes1[0], apexes1[1])) {
					return true;
				}
			}
			for (int j = 0; j < (apexes1.length - 2); j += 2) {
				if (java.awt.geom.Line2D.linesIntersect(apexes0[apexes0.length - 2], apexes0[apexes0.length - 1],
						apexes0[0], apexes0[1], apexes1[j], apexes1[j + 1], apexes1[j + 2], apexes1[j + 3])) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean intersect(double agentX, double agentY, double pointX, double pointY, Blockade blockade) {
		List<Line2D> lines = GeometryTools2D.pointsToLines(GeometryTools2D.vertexArrayToPoints(blockade.getApexes()),
				true);
		for (Line2D line : lines) {
			Point2D start = line.getOrigin();
			Point2D end = line.getEndPoint();
			double startX = start.getX();
			double startY = start.getY();
			double endX = end.getX();
			double endY = end.getY();
			if (java.awt.geom.Line2D.linesIntersect(agentX, agentY, pointX, pointY, startX, startY, endX, endY)) {
				return true;
			}
		}
		return false;
	}

	private Point2D getPointIntersectLine2D(double agentX, double agentY, double pointX, double pointY, Blockade blockade)
	{
		double minDistance = Double.MAX_VALUE;
		Point2D l2d = null;
		Polygon shape = (Polygon) blockade.getShape();
		int size = 500;
		Point2D pointAgent  = new Point2D(agentX, agentY);
		Point2D pointTarget = new Point2D(pointX, pointY);


		Line2D line = new Line2D(pointAgent, pointTarget);
		Vector2D mainLineVector = line.getDirection();
		Vector2D normalisedMainLineVector = mainLineVector.normalised();

		Vector2D normalMainLineVector1 = normalisedMainLineVector.getNormal();
		Vector2D normalMainLineVector2 = normalisedMainLineVector.getNormal().getNormal().getNormal();

		Vector2D MainLineVector1 = normalMainLineVector1.scale(size);
		Vector2D MainLineVector2 = normalMainLineVector2.scale(size);

		Line2D line1 = new Line2D(new Point2D(agentX+MainLineVector1.getX(), agentY+MainLineVector1.getY()), new Point2D(pointX+MainLineVector1.getX(), pointY+MainLineVector1.getY()));
		Line2D line2 = new Line2D(new Point2D(agentX+MainLineVector2.getX(), agentY+MainLineVector2.getY()), new Point2D(pointX+MainLineVector2.getX(), pointY+MainLineVector2.getY()));

		line.setOrigin(new Point2D(line.getOrigin().getX()-line.getDirection().getX()/4, line.getOrigin().getY()-line.getDirection().getY()/4));

		for (int i=0 ; i<shape.npoints; i++)
		{
			double[] pp = new double[2];
			if (findLineSegmentIntersection(line.getOrigin().getX(), line.getOrigin().getY(), pointX, pointY, shape.xpoints[i], shape.ypoints[i], shape.xpoints[(i+1)%shape.npoints],  shape.ypoints[(i+1)%shape.npoints], pp) != 1)
				continue;

			Point2D p2d = new Point2D(pp[0], pp[1]);

			if (p2d != null)
			{
				double temp = GeometryTools2D.getDistance(new Point2D(agentX, agentY), p2d);
				if (temp < minDistance)
				{
					minDistance = temp;
					l2d = p2d;
				}
			}
		}
		for (int i=0 ; i<shape.npoints; i++)
		{
			double[] pp1 = new double[2];
			if (findLineSegmentIntersection(line1.getOrigin().getX(), line1.getOrigin().getY(), line1.getEndPoint().getX(), line1.getEndPoint().getY(), shape.xpoints[i], shape.ypoints[i], shape.xpoints[(i+1)%shape.npoints],  shape.ypoints[(i+1)%shape.npoints], pp1) != 1)
				continue;


			Point2D p2d1 = new Point2D(pp1[0], pp1[1]);

			if (p2d1 != null)
			{
				double temp = GeometryTools2D.getDistance(new Point2D(agentX, agentY), p2d1);
				if (temp < minDistance)
				{
					minDistance = temp;
					l2d = p2d1;
				}
			}
		}

		for (int i=0 ; i<shape.npoints; i++)
		{
			double[] pp2 = new double[2];
			if (findLineSegmentIntersection(line2.getOrigin().getX(), line2.getOrigin().getY(), line2.getEndPoint().getX(), line2.getEndPoint().getY(), shape.xpoints[i], shape.ypoints[i], shape.xpoints[(i+1)%shape.npoints],  shape.ypoints[(i+1)%shape.npoints], pp2) != 1)
				continue;

			Point2D p2d2 = new Point2D(pp2[0], pp2[1]);

			if (p2d2 != null)
			{
				double temp = GeometryTools2D.getDistance(new Point2D(agentX, agentY), p2d2);
				if (temp < minDistance)
				{
					minDistance = temp;
					l2d = p2d2;
				}
			}
		}
		return l2d;
	}

	private Point2D getClosestPointtoBlockade(double agentX, double agentY, Blockade blockade)
	{
		double minDistance = Double.MAX_VALUE;
		Point2D l2d = null;
		Polygon shape = (Polygon) blockade.getShape();
		for (int i=0 ; i < shape.npoints; i++)
		{
			Point2D temp = GeometryTools2D.getClosestPoint(new Line2D(shape.xpoints[i], shape.ypoints[i], shape.xpoints[(i+1)%shape.npoints],  shape.ypoints[(i+1)%shape.npoints]), new Point2D(agentX, agentY));
			if (getDistance(agentX, agentY, temp.getX(), temp.getY()) < minDistance)
			{
				minDistance = getDistance(agentX, agentY, temp.getX(), temp.getY());
				l2d = temp;
			}
		}
		if (l2d == null) return null;
		return l2d;
	}

	private double max (double a, double b, double c, double d)
	{
		return Math.max (Math.max (a, b), Math.max (c, d));
	}

	private boolean equals (double a, double b, double limit)
	{
		return Math.abs (a - b) < limit;
	}

	private boolean equals (double a, double b)
	{
		return equals (a, b, 1.0e-5);
	}
	private double min (double a, double b, double c, double d)
	{
		return Math.min (Math.min (a, b), Math.min (c, d));
	}

	private int findLineSegmentIntersection (double x0, double y0, double x1, double y1, double x2, double y2, double x3, double y3, double[] intersection)
	{
		// Make limit depend on input domain
		final double LIMIT    = 1e-5;
		final double INFINITY = 1e10;

		double x, y;

		//
		// Convert the lines to the form y = ax + b
		//

		// Slope of the two lines
		double a0 = equals (x0, x1, LIMIT) ? INFINITY : (y0 - y1) / (x0 - x1);
		double a1 = equals (x2, x3, LIMIT) ? INFINITY : (y2 - y3) / (x2 - x3);

		double b0 = y0 - a0 * x0;
		double b1 = y2 - a1 * x2;

		// Check if lines are parallel
		if (equals (a0, a1)) {
			if (!equals (b0, b1))
				return -1; // Parallell non-overlapping

			else {
				if (equals (x0, x1)) {
					if (Math.min (y0, y1) < Math.max (y2, y3) ||
							Math.max (y0, y1) > Math.min (y2, y3)) {
						double twoMiddle = y0 + y1 + y2 + y3 -
								min (y0, y1, y2, y3) -
								max (y0, y1, y2, y3);
						y = (twoMiddle) / 2.0;
						x = (y - b0) / a0;
					}
					else return -1;  // Parallell non-overlapping
				}
				else {
					if (Math.min (x0, x1) < Math.max (x2, x3) ||
							Math.max (x0, x1) > Math.min (x2, x3)) {
						double twoMiddle = x0 + x1 + x2 + x3 -
								min (x0, x1, x2, x3) -
								max (x0, x1, x2, x3);
						x = (twoMiddle) / 2.0;
						y = a0 * x + b0;
					}
					else return -1;
				}

				intersection[0] = x;
				intersection[1] = y;
				return -2;
			}
		}

		// Find correct intersection point
		if (equals (a0, INFINITY)) {
			x = x0;
			y = a1 * x + b1;
		}
		else if (equals (a1, INFINITY)) {
			x = x2;
			y = a0 * x + b0;
		}
		else {
			x = - (b0 - b1) / (a0 - a1);
			y = a0 * x + b0;
		}

		intersection[0] = x;
		intersection[1] = y;

		// Then check if intersection is within line segments
		double distanceFrom1;
		if (equals (x0, x1)) {
			if (y0 < y1)
				distanceFrom1 = y < y0 ? length (x, y, x0, y0) :
						y > y1 ? length (x, y, x1, y1) : 0.0;
			else
				distanceFrom1 = y < y1 ? length (x, y, x1, y1) :
						y > y0 ? length (x, y, x0, y0) : 0.0;
		}
		else {
			if (x0 < x1)
				distanceFrom1 = x < x0 ? length (x, y, x0, y0) :
						x > x1 ? length (x, y, x1, y1) : 0.0;
			else
				distanceFrom1 = x < x1 ? length (x, y, x1, y1) :
						x > x0 ? length (x, y, x0, y0) : 0.0;
		}

		double distanceFrom2;
		if (equals (x2, x3)) {
			if (y2 < y3)
				distanceFrom2 = y < y2 ? length (x, y, x2, y2) :
						y > y3 ? length (x, y, x3, y3) : 0.0;
			else
				distanceFrom2 = y < y3 ? length (x, y, x3, y3) :
						y > y2 ? length (x, y, x2, y2) : 0.0;
		}
		else {
			if (x2 < x3)
				distanceFrom2 = x < x2 ? length (x, y, x2, y2) :
						x > x3 ? length (x, y, x3, y3) : 0.0;
			else
				distanceFrom2 = x < x3 ? length (x, y, x3, y3) :
						x > x2 ? length (x, y, x2, y2) : 0.0;
		}

		return equals (distanceFrom1, 0.0) &&
				equals (distanceFrom2, 0.0) ? 1 : 0;
	}

	private double length (double x0, double y0, double x1, double y1)
	{
		double dx = x1 - x0;
		double dy = y1 - y0;

		return Math.sqrt (dx*dx + dy*dy);
	}



	private double getDistance(double fromX, double fromY, double toX, double toY) {
		double dx = toX - fromX;
		double dy = toY - fromY;
		return Math.hypot(dx, dy);
	}

	private double getAngle(Vector2D v1, Vector2D v2) {
		double flag = (v1.getX() * v2.getY()) - (v1.getY() * v2.getX());
		double angle = Math
				.acos(((v1.getX() * v2.getX()) + (v1.getY() * v2.getY())) / (v1.getLength() * v2.getLength()));
		if (flag > 0) {
			return angle;
		}
		if (flag < 0) {
			return -1 * angle;
		}
		return 0.0D;
	}

	private Vector2D getVector(double fromX, double fromY, double toX, double toY) {
		return (new Point2D(toX, toY)).minus(new Point2D(fromX, fromY));
	}

	private Vector2D scaleClear(Vector2D vector) {
		return vector.normalised().scale(this.clearDistance);
	}

	private Vector2D scaleBackClear(Vector2D vector) {
		return vector.normalised().scale(-510);
	}

	private boolean needRest(Human agent) {
		int hp = agent.getHP();
		int damage = agent.getDamage();
		if (damage == 0 || hp == 0)
		{
			return false;
		}
		int activeTime = (hp / damage) + ((hp % damage) != 0 ? 1 : 0);
		if (this.kernelTime == -1)
		{
			try
			{
				this.kernelTime = this.scenarioInfo.getKernelTimesteps();
			} catch (NoSuchConfigOptionException e)
			{
				this.kernelTime = -1;
			}
		}
		return damage >= this.thresholdRest || (activeTime + this.agentInfo.getTime()) < this.kernelTime;
	}

	private Action calcRest(Human human, Collection<EntityID> targets)
	{
		EntityID position = human.getPosition();
		Collection<EntityID> refuges = this.worldInfo.getEntityIDsOfType(StandardEntityURN.REFUGE);
		int currentSize = refuges.size();
		if (refuges.contains(position))
		{
			return new ActionRest();
		}
		List<EntityID> firstResult = null;
		while (refuges.size() > 0) {
			List<EntityID> path = this.wsg.getNoBlockadePathToClosest(position, new ArrayList<EntityID>(refuges));
			if (path != null && path.size() > 0) {
				if (firstResult == null) {
					firstResult = new ArrayList<>(path);
					if (targets == null || targets.isEmpty()) {
						break;
					}
				}
				EntityID refugeID = path.get(path.size() - 1);
				this.setTarget(refugeID);
				List<EntityID> fromRefugeToTarget = this.wsg.getNoBlockadePathToClosest(position, new ArrayList<EntityID>(targets));
				if (fromRefugeToTarget != null && fromRefugeToTarget.size() > 0) {
					return new ActionMove(path);
				}
				refuges.remove(refugeID);
				// remove failed
				if (currentSize == refuges.size()) {
					break;
				}
				currentSize = refuges.size();
			} else {
				break;
			}
		}
		return firstResult != null ? new ActionMove(firstResult) : null;
	}

}
