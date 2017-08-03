package AUR.util.knd;

import rescuecore2.standard.entities.Building;

public class AURFireSimulator {

	public static double FLOOL_HEIGHT = 4.08569;

	public static double unitCapacity(Building b) {
		switch (b.getBuildingCodeEnum()) {
		case STEEL:
			return 1;
		case WOOD:
			return 1.1;
		case CONCRETE:
			return 1.5;
		}
		return 1.5;
	}

	public static double getBuildingCapacity(AURAreaGraph ag) {
		Building b = (Building) (ag.area);
		double result = b.getFloors() * b.getGroundArea() * FLOOL_HEIGHT;
		return result * unitCapacity(b);
	}

	// copied from MRL
	public static double getBuildingEnergy(AURAreaGraph ag, double t) {
		return getBuildingCapacity(ag) * t;
	}

	// copied from MRL
	public static double waterCooling(AURAreaGraph ag, double temperature, int water) {
		double effect = water * 20;
		return (getBuildingEnergy(ag, temperature) - effect) / getBuildingCapacity(ag);
	}

	// copied from MRL
	public static int getWaterNeeded(AURAreaGraph ag, double temperature, double finalTemperature) {
		int waterNeeded = 0;
		double currentTemperature = temperature;
		int step = 500;
		while (true) {
			currentTemperature = waterCooling(ag, currentTemperature, step);
			waterNeeded += step;
			if (currentTemperature <= finalTemperature) {
				break;
			}
		}
		return waterNeeded;
	}

}
