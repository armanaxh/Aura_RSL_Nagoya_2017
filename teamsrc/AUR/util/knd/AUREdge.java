package AUR.util.knd;

public class AUREdge {

	public AURNode A;
	public AURNode B;
	public double weight = 0;
	public AURAreaGraph areaGraph;

	public AUREdge(AURNode A, AURNode B, double weight, AURAreaGraph areaGraph) {
		this.A = A;
		this.B = B;
		this.weight = weight;
		this.areaGraph = areaGraph;
	}

	public AURNode nextNode(AURNode from) {
		if (from == A) {
			return B;
		} else if (from == B) {
			return A;
		}
		return null;
	}

	public AURAreaGraph getNextAreaGraph(AURNode fromNode) {
		AURNode toNode = nextNode(fromNode);
		if (toNode.ownerArea1 == areaGraph) {
			return toNode.ownerArea2;
		}
		return toNode.ownerArea1;
	}
}
