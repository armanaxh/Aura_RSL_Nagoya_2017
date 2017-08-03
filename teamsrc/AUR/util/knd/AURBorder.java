package AUR.util.knd;

import java.util.ArrayList;

public class AURBorder {

	public AURAreaGraph area1;
	public AURAreaGraph area2;

	public boolean calced = false;
	public boolean ready = false;

	public double Ax = 0;
	public double Ay = 0;
	public double Bx = 0;
	public double By = 0;

	public AURNode CenterNode;

	public ArrayList<AURNode> nodes = new ArrayList<AURNode>();

	public void reset() {
		nodes.clear();
		calced = false;
		ready = false;
	}

	public AURBorder(AURAreaGraph area1, AURAreaGraph area2, double Ax, double Ay, double Bx, double By) {
		this.area1 = area1;
		this.area2 = area2;
		this.Ax = Ax;
		this.Ay = Ay;
		this.Bx = Bx;
		this.By = By;
		this.CenterNode = new AURNode((Ax + Bx) / 2, (Ay + By) / 2, area1, area2);
		this.nodes.add(new AURNode(CenterNode.x, CenterNode.y, area1, area2));
	}

}
