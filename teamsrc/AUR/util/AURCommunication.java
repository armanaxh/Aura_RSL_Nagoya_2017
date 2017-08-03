package AUR.util;


import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.MessageUtil;
import adf.agent.communication.standard.bundle.StandardMessage;
import adf.agent.communication.standard.bundle.information.*;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.component.communication.CommunicationMessage;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.EntityID;


import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class AURCommunication {

    private WorldInfo worldInfo ;
    private AgentInfo agentInfo ;
    private Map<EntityID, Integer> receivedTimeMap;
    private int sendingAvoidTimeReceived;

    public AURCommunication(AgentInfo ai , WorldInfo wi ,ScenarioInfo scenarioInfo , DevelopData developData){
        this.agentInfo = ai ;
        this.worldInfo = wi ;
        this.receivedTimeMap = new HashMap<>();
        this.sendingAvoidTimeReceived = developData.getInteger("sample.tactics.MessageTool.sendingAvoidTimeReceived", 3);

    }

    public void updateInfo(MessageManager messageManager){
        this.reflectMessage(messageManager);
        //TODO for ADF BUG
        this.reflectMessage2(messageManager);
    }
    private  void reflectMessage(MessageManager messageManager)
    {
        Set<EntityID> changedEntities = worldInfo.getChanged().getChangedEntities();
        changedEntities.add(agentInfo.getID());
        int time = agentInfo.getTime();
        for (CommunicationMessage message : messageManager.getReceivedMessageList())
        {
            StandardEntity entity = null;
            entity = this.reflectMessage(worldInfo, (StandardMessage) message);
            if (entity != null) { this.receivedTimeMap.put(entity.getID(), time); }
        }
    }

    private  void reflectMessage2(MessageManager messageManager)
    {
        Set<EntityID> changedEntities = worldInfo.getChanged().getChangedEntities();
        changedEntities.add(agentInfo.getID());
        int time = agentInfo.getTime();
        for (CommunicationMessage message : messageManager.getReceivedMessageList())
        {
            StandardEntity entity = null;
            entity = MessageUtil.reflectMessage(worldInfo, (StandardMessage) message);
            if (entity != null) { this.receivedTimeMap.put(entity.getID(), time); }
        }
    }

    public static StandardEntity reflectMessage(@Nonnull WorldInfo worldInfo, @Nonnull StandardMessage message)
    {
        StandardEntity entity = null;
        Set<EntityID> changedEntities = worldInfo.getChanged().getChangedEntities();
        Class<? extends StandardMessage> messageClass = message.getClass();

        if (messageClass == MessageCivilian.class)
        {
            MessageCivilian mc = (MessageCivilian) message;
            if (!changedEntities.contains(mc.getAgentID()))
            {
                entity = MessageUtil.reflectMessage(worldInfo, mc);
            }
        }
        else if (messageClass == MessageAmbulanceTeam.class)
        {
            MessageAmbulanceTeam mat = (MessageAmbulanceTeam) message;
            if (!changedEntities.contains(mat.getAgentID()))
            {
                entity = MessageUtil.reflectMessage(worldInfo, mat);
            }
        }
        else if (messageClass == MessageFireBrigade.class)
        {
            MessageFireBrigade mfb = (MessageFireBrigade) message;
            if (!changedEntities.contains(mfb.getAgentID()))
            {
                entity = MessageUtil.reflectMessage(worldInfo, mfb);
            }
        }
        else if (messageClass ==  MessagePoliceForce.class )
        {
            MessagePoliceForce mpf = (MessagePoliceForce) message;
            if (!changedEntities.contains(mpf.getAgentID()))
            {
                  entity = MessageUtil.reflectMessage(worldInfo, mpf);
            }
        }
        else if (messageClass == MessageBuilding.class)
        {
            MessageBuilding mb = (MessageBuilding) message;
            if (!changedEntities.contains(mb.getBuildingID()))
            {
                entity = MessageUtil.reflectMessage(worldInfo, mb);
            }
        }
        else if (messageClass == MessageRoad.class)
        {
            MessageRoad mr = (MessageRoad) message;
            if (!changedEntities.contains(mr.getRoadID()))
            {
                entity = MessageUtil.reflectMessage(worldInfo, mr);
            }
        }

        return entity;
    }


    private boolean isRecentlyReceived(AgentInfo agentInfo, EntityID id)
    {
        return (this.receivedTimeMap.containsKey(id)
                && ((agentInfo.getTime() - this.receivedTimeMap.get(id)) < this.sendingAvoidTimeReceived));
    }
}


