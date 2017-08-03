package AUR.util.knd;

import java.util.ArrayList;
import java.util.List;
import adf.agent.action.common.*;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.component.module.AbstractModule;
import rescuecore2.worldmodel.EntityID;

public class AURWalkWatcher extends AbstractModule {

	AgentInfo ai = null;
	AURRandomDirectSelector randomDirectSelector = null;
	private int ignoreUntil = 0;

	public AURWalkWatcher(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager,
			DevelopData developData) {
		super(ai, wi, si, moduleManager, developData);
		this.ai = ai;
		randomDirectSelector = new AURRandomDirectSelector(ai, wi);
		this.ignoreUntil = si.getKernelAgentsIgnoreuntil();
	}

	@Override
	public AbstractModule calc() {
		return null;
	}

	class Step {

		int fromID;
		int toID;
		double fromX = 0;
		double fromY = 0;

		public Step(int fromID, int toID) {
			this.fromID = fromID;
			this.toID = toID;
			this.fromX = ai.getX();
			this.fromY = ai.getY();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == null || ((obj instanceof Step) == false)) {
				return false;
			}
			Step step = (Step) obj;
			/* return this.fromID == step.fromID && this.toID == step.toID; */
			double dist2 = (step.fromX - this.fromX) * (step.fromX - this.fromX);
			dist2 += (step.fromY - this.fromY) * (step.fromY - this.fromY);
			return dist2 < 750 * 750;
		}
	}

	public ArrayList<Step> recentSteps = new ArrayList<>();

	private void add(List<EntityID> path) {
		recentSteps.add(new Step(ai.getPosition().getValue(), path.get(0).getValue()));
		if (recentSteps.size() > 4) {
			recentSteps.remove(0);
		}
	}

	private boolean anyProblem() {
		int size = recentSteps.size();
		if (size < 2) {
			return false;
		}
		Step last = recentSteps.get(size - 1);
		Step beforeLast = recentSteps.get(size - 2);
		if (last.equals(beforeLast)) {
			return true;
		}
		return false;
	}

	public ActionMove check(ActionMove moveAction) {
		if (moveAction == null || moveAction.getPath() == null || moveAction.getPath().size() == 0) {
			return null;
		}

		/*System.out.print(moveAction.getPath().get(0).getValue() + ": ");
		for(int i = 0; i < moveAction.getPath().size(); i++) {
			System.out.print(moveAction.getPath().get(i).getValue() + " > ");
		}
		System.out.print(moveAction.getPosX() + ", " + moveAction.getPosY());
		System.out.println();*/
		 
		if (ai.getTime() < ignoreUntil) {
			return moveAction;
		}
		randomDirectSelector.update();
		add(moveAction.getPath());
		if (anyProblem()) {
			//System.out.println("problem");
			randomDirectSelector.generate();
			List<EntityID> path = new ArrayList<>();
			path.add(ai.getPosition());
			return new ActionMove(path, (int) (randomDirectSelector.generatedPoint.getX()),
					(int) (randomDirectSelector.generatedPoint.getY()));

		} else {
			return moveAction;
		}
	}
}
