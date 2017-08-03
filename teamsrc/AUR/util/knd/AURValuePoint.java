package AUR.util.knd;

public class AURValuePoint {

	double x;
	double y;
	public double value = 0;
	public double temp_value = 0;

	public AURAreaGraph areaGraph = null;

	public AURValuePoint(double x, double y, AURAreaGraph areaGraph) {
		this.x = x;
		this.y = y;
		this.areaGraph = areaGraph;
	}

	public double distPower2(AURValuePoint p) {
		return (this.x - p.x) * (this.x - p.x) + (this.y - p.y) * (this.y - p.y);
	}

	public double dist(AURValuePoint p) {
		return AURGeoUtil.dist(x, y, p.x, p.y);
	}

	public void set(double x, double y) {
		this.x = x;
		this.y = y;
	}
}