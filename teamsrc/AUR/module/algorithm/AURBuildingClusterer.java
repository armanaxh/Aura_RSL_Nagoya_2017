package AUR.module.algorithm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import adf.agent.precompute.PrecomputeData;
import org.apache.commons.math3.ml.clustering.CentroidCluster;
import org.apache.commons.math3.ml.clustering.Clusterable;
import org.apache.commons.math3.ml.clustering.KMeansPlusPlusClusterer;
import AUR.util.HungarianAlgorithm;
import AUR.util.knd.AURAreaGraph;
import AUR.util.knd.AURGeoUtil;
import AUR.util.knd.AURWorldGraph;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.component.module.algorithm.Clustering;
import adf.component.module.algorithm.StaticClustering;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.EntityID;

public class AURBuildingClusterer extends StaticClustering {
	
	private int clusterNumber = 3;
	private AgentInfo ai;
	private WorldInfo wi;
	private AURWorldGraph wsg = null;
	private ArrayList<StandardEntity> sortedTeamAgents = new ArrayList<>();
	private boolean calced = false;
    private int lastClusterEntitiesQueryIndex = -1;
    private int lastClusterEntityIDsQueryIndex = -1;
    private Collection<StandardEntity> lastClusterEntitiesQueryResult = new ArrayList<>();
    private Collection<EntityID> lastClusterEntityIDsQueryResult = new ArrayList<>();
    private ArrayList<ClusterItem> items = new ArrayList<>();
    
	class ClusterItem implements Clusterable {
		
		public AURAreaGraph ag;
		public int clusterIndex = 0;
		double point[] = new double[2];
		
		@Override
		public double[] getPoint() {
			return point;
		}
		
		public ClusterItem(AURAreaGraph ag, int clusterIndex) {
			this.ag = ag;
			this.clusterIndex = clusterIndex;
			this.point[0] = this.ag.cx;
			this.point[1] = this.ag.cy;
		}
		
	}
	
	public AURBuildingClusterer(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
		super(ai, wi, si, moduleManager, developData);
		this.wi = wi;
		this.ai = ai;
		this.wsg = moduleManager.getModule("knd.AuraWorldGraph");
	}

    public int getClusterNumber() {
    	return clusterNumber;
    }

    private int getAgentInitialClusterIndex(StandardEntity agent) {
    	if(allocations == null) {
    		return -1;
    	}
    	return allocations[sortedTeamAgents.indexOf(agent)];
    }
    
    public int getClusterIndex(StandardEntity entity) {
    	if(sortedTeamAgents.contains(entity)) {
    		return getAgentInitialClusterIndex(entity);
    	}
    	return getClusterIndex(entity.getID());
    }

    public int getClusterIndex(EntityID id) {
    	for(ClusterItem item : items) {
    		if(item.ag.area.getID().equals(id)) {
    			return item.clusterIndex;
    		}
    	}
    	return -1;
    }
    
    public Collection<StandardEntity> getClusterEntities(int index) {
    	Collection<StandardEntity> result = new ArrayList<>();
    	if(lastClusterEntitiesQueryIndex != index) {
    		lastClusterEntitiesQueryResult.clear();
        	for(ClusterItem item : items) {
        		if(item.clusterIndex == index) {
        			lastClusterEntitiesQueryResult.add(item.ag.area);
        		}
        	}
    	}
    	lastClusterEntitiesQueryIndex = index;
    	result.addAll(lastClusterEntitiesQueryResult);
    	return result;
    }
    
    public Collection<EntityID> getClusterEntityIDs(int index) {
    	Collection<EntityID> result = new ArrayList<>();
    	if(lastClusterEntityIDsQueryIndex != index) {
    		lastClusterEntityIDsQueryResult.clear();
        	for(ClusterItem item : items) {
        		if(item.clusterIndex == index) {
        			lastClusterEntityIDsQueryResult.add(item.ag.area.getID());
        		}
        	}
    	}
    	lastClusterEntityIDsQueryIndex = index;
    	result.addAll(lastClusterEntityIDsQueryResult);
    	return result;
    }

    private int allocations[] = null;
    
    private void calcBestClusterAllocation() {
    	allocations = new int[sortedTeamAgents.size()];
    	double clusterCenters[][] = new double[clusterNumber][3];
    	for(int i = 0; i < clusterNumber; i++) {
    		clusterCenters[i][0] = 0;
    		clusterCenters[i][1] = 0;
    		clusterCenters[i][2] = 0;
    	}
    	for(ClusterItem item : items) {
    		clusterCenters[item.clusterIndex][0] += item.point[0];
    		clusterCenters[item.clusterIndex][1] += item.point[1];
    		clusterCenters[item.clusterIndex][2] += 1;
    	}
    	for(int i = 0; i < clusterNumber; i++) {
    		if(clusterCenters[i][2] != 0) {
        		clusterCenters[i][0] /= clusterCenters[i][2];
        		clusterCenters[i][1] /= clusterCenters[i][2];
    		}
    	}
    	
		double cm[][] = new double[sortedTeamAgents.size()][clusterNumber];
		for(int i = 0; i < cm.length; i++) {
			for(int j = 0; j < cm[0].length; j++) {
				StandardEntity agentStd = sortedTeamAgents.get(i);
				double ax = (int) (agentStd.getProperty("urn:rescuecore2.standard:property:x").getValue());
				double ay = (int) (agentStd.getProperty("urn:rescuecore2.standard:property:y").getValue());
				double dist = AURGeoUtil.dist(
					ax,
					ay,
					clusterCenters[j][0],
					clusterCenters[j][1]
				);
				cm[i][j] = dist;
			}
		}
		HungarianAlgorithm ha = new HungarianAlgorithm(cm);
		allocations = ha.execute(); // #toDo
    }
    
    @Override
    public Clustering calc() {
    	//long t = System.currentTimeMillis();
    	if(this.calced) {
    		return this;
    	}
    	items.clear();
    	sortedTeamAgents.clear();
    	sortedTeamAgents.addAll(wi.getEntitiesOfType(ai.me().getStandardURN()));
    	
    	Collections.sort(sortedTeamAgents, new Comparator<StandardEntity>() {
    		@Override
    		public int compare(StandardEntity o1, StandardEntity o2) {
    			return o1.getID().getValue() - o2.getID().getValue();
    		}
		});
    	this.clusterNumber = Math.max(1, sortedTeamAgents.size());
		for(AURAreaGraph ag : wsg.areas.values()) {
			if(ag.isBuilding()) {
				items.add(new ClusterItem(ag, 0));
			}
		}
		KMeansPlusPlusClusterer<ClusterItem> dbscan = new KMeansPlusPlusClusterer<ClusterItem>(
			clusterNumber, 
			30
		);
		dbscan.getRandomGenerator().setSeed(ai.me().getStandardURN().ordinal() + 5);
		List<CentroidCluster<ClusterItem>> lcd = dbscan.cluster(items);
		int ci = 0;
		for(CentroidCluster<ClusterItem> x : lcd) {
			for(ClusterItem item : x.getPoints()) {
				item.clusterIndex = ci;
			}
			ci++;
		}
		calcBestClusterAllocation();
//		System.out.println("clustering time: " + (System.currentTimeMillis() - t) + "ms");
		this.calced = true;
		return this;
    }

	@Override
	public Clustering resume(PrecomputeData precomputeData) {
		 super.resume(precomputeData);
		 this.preparate();
		 return this;
	}

	@Override
    public Clustering preparate() {
    	calc();
    	return super.preparate();
    }
}
