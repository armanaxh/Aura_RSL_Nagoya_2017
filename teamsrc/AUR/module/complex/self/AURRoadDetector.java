package AUR.module.complex.self;

import java.awt.Polygon;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import AUR.util.knd.AURWorldGraph;
import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.information.MessagePoliceForce;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.communication.CommunicationMessage;
import adf.component.module.algorithm.Clustering;
import adf.component.module.complex.RoadDetector;
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
import rescuecore2.standard.entities.Refuge;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

public class AURRoadDetector extends RoadDetector {

	private List<EntityID> targetAreas;
	private List<EntityID> clusterRefuges;
	private List<EntityID> clusterAreas;
	private List<EntityID> civilha;
	private List<EntityID> done;
	private List<EntityID> refugesChecked;
	
	private Clustering 	   clustering;

	private EntityID       result;
	private int 	       fireRate;

	private int 		   state = -1;
	private int 		   currentIndex = 0;


	private List<StandardEntity>  	   listAgents;
	private AURWorldGraph wsg  = null;

	public AURRoadDetector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData)
	{
		super(ai, wi, si, moduleManager, developData);

		this.clustering = moduleManager.getModule("SampleRoadDetector.Clustering", "adf.sample.module.algorithm.SampleKMeans");
		this.wsg 		= moduleManager.getModule("knd.AuraWorldGraph");

		this.result 	 	= null;
		this.targetAreas 	= new ArrayList<EntityID>();
		this.clusterRefuges = new ArrayList<EntityID>();
		this.clusterAreas 	= new ArrayList<EntityID>();
		this.civilha		= new ArrayList<EntityID>();
		this.done 			= new ArrayList<EntityID>();
		this.refugesChecked	= new ArrayList<EntityID>();
		
		this.listAgents		= new ArrayList<>();
		this.fireRate 		= 2;

		/*clusterRefugeList = new ArrayList<>();
		refugeRoundList = new HashMap<>();
		refugeRoundRadius = developData.getInteger("AuraRoadDetector.refugeRoundRoadRadius", 10);*/


	}

	@Override
	public RoadDetector calc()
	{
		if (this.result == null)
		{
			state = -1;
			EntityID positionID = this.agentInfo.getPosition();
			/*
			 * clear refuges in cluster
			 */
			if (this.clusterRefuges.size() > 0)
			{
				List<EntityID> path = this.wsg.getNoBlockadePathToClosest(positionID, this.clusterRefuges);
				if (path != null && path.size() > 0)
				{
					this.result = path.get(path.size() - 1);
					refugesChecked.add(this.result);
					this.clusterRefuges.remove(this.result);

					state =2;
					return this;
				}
				clusterRefuges.clear();
			}
			if (this.civilha.size() > 0)
			{
				List<EntityID> path = this.wsg.getNoBlockadePathToClosest(positionID, this.civilha);
				if (path != null && path.size() > 0)
				{
					this.result = path.get(path.size() - 1);

					this.civilha.remove(this.result);

					state =3;
					return this;
				}
				civilha.clear();
			}
			/*
			 * clear roads that make round
			 */
			if (this.clusterAreas.size() > 0)
			{
				List<EntityID> path = this.wsg.getNoBlockadePathToClosest(positionID, this.clusterAreas);
				if (path != null && path.size() > 0)
				{
					this.result = path.get(path.size() - 1);

					this.clusterAreas.remove(this.result);

					state =4;
					return this;
				}
				clusterAreas.clear();
			}
			/*
			 */
			if (this.targetAreas.size() > 0)
			{
				List<EntityID> path = this.wsg.getNoBlockadePathToClosest(positionID, this.targetAreas);
				if (path != null && path.size() > 0)
				{
					this.result = path.get(path.size() - 1);
				
					this.targetAreas.remove(this.result);

					state =5;
					return this;
				}
				targetAreas.clear();
			}
		}
		return this;
	}

	@Override
	public EntityID getTarget()
	{
		return this.result;
	}

	@Override
	public RoadDetector resume(PrecomputeData precomputeData)
	{
		super.resume(precomputeData);
		if (this.getCountResume() >= 2) {
			return this;
		}
		this.preparate();
		return this;
	}

	@Override
	public RoadDetector preparate() {
		super.preparate();
		if (this.getCountPreparate() >= 2) {
			return this;
		}
		this.clustering.preparate();

		this.currentIndex = this.clustering.getClusterIndex(agentInfo.me());

		for (StandardEntity e : this.clustering.getClusterEntities(this.clustering.getClusterIndex(agentInfo.me())))
		{

			if (e instanceof Refuge)
			{
				for (EntityID eid : ((Area) e).getNeighbours())
					if (this.worldInfo.getEntity(eid) instanceof Road)
						this.clusterRefuges.add(eid);
			}
			else if (e instanceof Building)
			{
				for (EntityID eid : ((Area) e).getNeighbours())
					if (this.worldInfo.getEntity(eid) instanceof Road)
						this.targetAreas.add(eid);
			}
			else if (e instanceof Road)
			{

				this.clusterAreas.add(e.getID());
			}
		}

		return this;
	}

	@Override
	public RoadDetector updateInfo(MessageManager messageManager) {
		super.updateInfo(messageManager);
		if (this.getCountUpdateInfo() >= 2) {
			return this;
		}
		wsg.updateInfo(messageManager);
		this.clustering.updateInfo(messageManager);
		
		if (state == 3) messageManager.addMessage(new MessagePoliceForce(true, (PoliceForce) this.agentInfo.me(), MessagePoliceForce.ACTION_MOVE, this.result));
		int minEntityIdAgents = Integer.MAX_VALUE;
		boolean flag1 = false;
		for (CommunicationMessage message : messageManager.getReceivedMessageList())
		{
			if (message instanceof MessagePoliceForce)
			{
				MessagePoliceForce mpf = (MessagePoliceForce) message;
				if (mpf.getTargetID().equals(this.result))
				{
					flag1 = true;
					if (mpf.getSenderID().getValue() < minEntityIdAgents)
					{
						minEntityIdAgents = mpf.getSenderID().getValue();
					}
				}
			}
		}

		if (this.agentInfo.getID().getValue() != minEntityIdAgents && state == 3 && flag1)
		{
			if (!done.contains(result))
				this.done.add(result);
			this.result = null;
		}

		Collection<StandardEntity> civils = this.worldInfo.getEntitiesOfType(StandardEntityURN.CIVILIAN, StandardEntityURN.AMBULANCE_TEAM, StandardEntityURN.FIRE_BRIGADE);

		civils.removeAll(listAgents);
		listAgents.addAll(civils);

		for (StandardEntity standardEntity : civils)
		{
			if (standardEntity.getID().equals(this.agentInfo.getID())) continue;
			if (standardEntity instanceof Human)
			{
				if (!((Human) standardEntity).isPositionDefined()) continue;
				
				EntityID ee = ((Human) standardEntity).getPosition();

				if (this.worldInfo.getEntity(ee) instanceof Building)
					if (!(this.worldInfo.getEntity(ee) instanceof Refuge))
						if (!this.civilha.contains(ee))
							//TODO revise use equals
							if (ee != result)
								if (!done.contains(ee))
								{
									StandardEntity seeeee = this.worldInfo.getEntity(ee);
									List<EntityID> leeeeid = ((Area) seeeee).getNeighbours();

									for(int i=0; i< leeeeid.size() ; i++)
									{
										StandardEntity s = this.worldInfo.getEntity(leeeeid.get(i));
										if (!(s instanceof Road))
										{
											//TODO revise
											leeeeid.remove(i--);
										}
									}

									for(int i=0; i< leeeeid.size() ; i++)
									{
											if (!targetAreas.contains(leeeeid.get(i))) leeeeid.remove(i--);
									}


									this.civilha.addAll(leeeeid);

									if (this.result != null && state != 3)
									{
//										clusterAreas.add(this.result);
										targetAreas.add(this.result);
										this.result = null;
									}
								}
			}
		}
		for (EntityID e : this.worldInfo.getChanged().getChangedEntities())
		{
			StandardEntity standardEntity = this.worldInfo.getEntity(e);
			if (standardEntity instanceof Building)
			{
				if (((Building) standardEntity).getFieryness() > fireRate)
				{
					if (result != null)
					{
						List<EntityID> leeeeid = ((Area) standardEntity).getNeighbours();
						for(int i=0; i< leeeeid.size() ; i++)
						{
							StandardEntity s = this.worldInfo.getEntity(leeeeid.get(i));
							if (s instanceof Road)
								if (result != null)
									if (result.equals(leeeeid.get(i)))
									{
										if (!done.contains(result)) done.add(result);
										result = null;
									}
						}
					}

					this.targetAreas.removeAll(((Area) standardEntity).getNeighbours());
					/*if (this.civilha.contains(standardEntity.getID()))
					{
						this.civilha.remove(standardEntity.getID());
					  //this.impo.add(standardEntity.getID());
					}*/
				}
			}

			if (standardEntity instanceof Road)
			{
				Road road = (Road) standardEntity;
				if (road.isBlockadesDefined())
				{
					Collection<StandardEntity> agents = this.worldInfo.getEntitiesOfType(StandardEntityURN.AMBULANCE_TEAM, StandardEntityURN.FIRE_BRIGADE);
					Collection<Blockade> blockades = this.worldInfo.getBlockades(road).stream().filter(Blockade::isApexesDefined).collect(Collectors.toSet());
					for (StandardEntity se : agents)
					{
						for (Blockade blockade : blockades)
						{
							if (!this.isInside(((Human) se).getX(), ((Human) se).getY(), blockade.getApexes()))
								continue;
							if (!civilha.contains(((Human) se).getPosition()))
								if (((Human) se).getPosition() != result)
								{
									civilha.add(((Human) se).getPosition());
									if (this.result != null && state != 3)
									{
//										clusterAreas.add(this.result);
										targetAreas.add(this.result);
										this.result = null;
									}
								}
						}
					}
				}
			}
		}
		EntityID agentPosID = this.agentInfo.getPosition();
		if (this.result != null && (result.equals(agentPosID) || ((Area) this.worldInfo.getEntity(result)).getNeighbours().contains(agentPosID)))
		{
			StandardEntity se = this.worldInfo.getEntity(result);
			Area areaPos = (Area) se;
			boolean flag2 = false;
			for (EntityID eid : areaPos.getNeighbours())
			{
				StandardEntity see = this.worldInfo.getEntity(eid);
				if (see instanceof Building)
				{
					Edge edge = ((Area) see).getEdgeTo(result);
					double x = this.agentInfo.getX();
					double y = this.agentInfo.getY();

					double midx = (edge.getEndX()+edge.getStartX())/2;
					double midy = (edge.getEndY()+edge.getStartY())/2;
					boolean flag = false;
					for (Blockade blockade : this.worldInfo.getBlockades(result))
						if (intersect(x, y, midx, midy, blockade))
							flag = true;
					if (!flag)
					{
						if (!done.contains(eid))
							done.add(eid);
//						result = null;
//						break;
					}
					else
						flag2 = true;
				}
			}

			if (!flag2) result = null;

			if (result != null && (state != 5 && state != 3 && state != 2))
				if (result.equals(agentPosID))
				{

					if (!done.contains(result))
						done.add(result);
					result = null;
				}
			
		}
		
		Collection<StandardEntity> refuges = this.worldInfo.getEntitiesOfType(StandardEntityURN.REFUGE);
		EntityID agentPosition = this.agentInfo.getPosition();
		List<EntityID> listEid = new ArrayList<>();
		for (StandardEntity se : refuges)
		{
			List<EntityID> listNe = ((Area) se).getNeighbours();
			for (EntityID eid : listNe)
				if (this.worldInfo.getEntity(eid) instanceof Road)
					listEid.add(eid);
		}
		boolean flag22 = false;
		for (EntityID se : listEid)
		{
			List<EntityID> ei = new ArrayList<>();
			ei.add(se);
			List<EntityID> listPath = this.wsg.getNoBlockadePathToClosest(agentPosition, ei);
			if (listPath != null && listPath.size() <= 5)
			{
				if (!clusterRefuges.contains(se) && !refugesChecked.contains(se))
				{
					if (result != null && !this.result.equals(se))
					{
						this.clusterRefuges.add(se);
						this.refugesChecked.add(se);
							flag22 = true;
						/*clusterAreas.add(this.result);
						this.result = null;*/
					}
				}
			}
		}
		if (flag22 && state != 2 && state != 3) 
		{
//			clusterAreas.add(this.result);
			targetAreas.add(this.result);
			this.result = null;
		}

		if (this.targetAreas.size() <= 0)
		{

			for (StandardEntity e : this.clustering.getClusterEntities((++currentIndex%this.clustering.getClusterNumber())))
			{
				if (e instanceof Refuge)
				{
					for (EntityID eid : ((Area) e).getNeighbours())
						if (this.worldInfo.getEntity(eid) instanceof Road)
							this.clusterRefuges.add(eid);
				}
				else if (e instanceof Building)
				{
					for (EntityID eid : ((Area) e).getNeighbours())
						if (this.worldInfo.getEntity(eid) instanceof Road)
							this.targetAreas.add(eid);
				}
			}

		}
		return this;
	}

	private boolean intersect(double agentX, double agentY, double pointX, double pointY, Blockade blockade)
	{
		double minDistance = Double.MAX_VALUE;
		Point2D l2d = null;
		Polygon shape = (Polygon) blockade.getShape();
		for (int i=0 ; i<shape.npoints; i++)
		{
			Line2D line = new Line2D(new Point2D(agentX, agentY), new Point2D(pointX, pointY));
			line.setOrigin(new Point2D(line.getOrigin().getX()-line.getDirection().getX()/4, line.getOrigin().getY()-line.getDirection().getY()/4));
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
		if (l2d == null) return false;
		return true;
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

	private double getAngle(Vector2D v1, Vector2D v2)
	{
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
}
