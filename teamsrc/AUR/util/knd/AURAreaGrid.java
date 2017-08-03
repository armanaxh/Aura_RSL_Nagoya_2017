package AUR.util.knd;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.standard.entities.Area;

public class AURAreaGrid {

	public final double gridSize = 450;
	private final int defaultSizeM = 351;
	private final int defaultSizeN = 503;
	private int currentSizeM = defaultSizeM;
	private int currentSizeN = defaultSizeN;
	public Area area = null;
	public final int CELL_FREE = 0;
	public final int CELL_BLOCK = 1;
	public final int CELL_AREA_EDGE = 2;
	public final int CELL_NODE = 4;
	public final int CELL_OUT = 8;
	private Polygon areaPolygon;
	public double gridPoints[][][] = new double[defaultSizeM][defaultSizeN][2];
	public int gridIntInfo[][][] = new int[defaultSizeM][defaultSizeN][3];
	public int graph[][] = new int[100][100];
	public int edgePoint[][] = new int[100][3];
	public AURNode edgePointObject[] = new AURNode[100];
	private int gridM = 0;
	private int gridN = 0;
	double boundsWidth = 0;
	double boundsheight = 0;
	double boundsX0 = 0;
	double boundsY0 = 0;
	double boundsX1 = 0;
	double boundsY1 = 0;
	private int setCenter_temp[] = { 0, 0 };
	private int getCellResult[] = new int[2];
	public int edgePointsSize = 0;
	public static final int TYPE = 0;
	public static final int COST = 1;
	public static final int EDGE_POINT_ID = 2;
	public static long _word_size = 1000000;
	public final int INF = 1000 * 1000 * 1000;
	public final double lineStepSize = gridSize / 4;
	private Queue<Long> que = new LinkedList<Long>();
	private ArrayList<Polygon> blockaePolygons = new ArrayList<Polygon>();
	public AURAreaGraph areaGraph = null;

	int dij_5[][] = { { +0, +0 }, { +1, +0 }, { -1, +0 }, { +0, +1 }, { +0, -1 } };
	int dij_4[][] = { { -1, +0 }, { +1, +0 }, { +0, +1 }, { +0, -1 } };
	int dij[][] = { { -1, +1 }, { +0, +1 }, { +1, +1 }, { -1, +0 }, { +0, +0 }, { +1, +0 }, { -1, -1 }, { +0, -1 },
			{ +1, -1 } };

	int dij_8[][] = { { -1, +1 }, { +0, +1 }, { +1, +1 }, { -1, +0 }, /*******/
			{ +1, +0 }, { -1, -1 }, { +0, -1 }, { +1, -1 } };

	private void checkGridArraySize(int m, int n) {
		if (currentSizeM >= m && currentSizeN >= n) {
			return;
		}
		currentSizeM = Math.max(currentSizeM, m + 10);
		currentSizeN = Math.max(currentSizeN, n + 10);
		gridPoints = new double[currentSizeM][currentSizeN][2];
		gridIntInfo = new int[currentSizeM][currentSizeN][4];
		System.out.println("grid array size changed: " + currentSizeM + ", " + currentSizeN);
	}

	public Point2D getPointHasSight(AURAreaGraph areaGraph, AURAreaInSightChecker checker, double fromX, double fromY) {
		this.areaGraph = areaGraph;
		this.areaPolygon = (Polygon) (areaGraph.area.getShape());
		edgePointsSize = 0;
		blockaePolygons.clear();

		this.area = areaGraph.area;

		initGrid();
		addAreaBlockades(this.areaGraph);

		int ij[] = getCell(fromX, fromY);
		Point2D result = null;
		if (ij[0] < 0) {
			return result;
		}
		int i, j;
		int ip, jp;
		i = ij[0];
		j = ij[1];
		que.clear();

		for (int ii = 0; ii < gridM; ii++) {
			for (int jj = 0; jj < gridN; jj++) {
				gridIntInfo[ii][jj][COST] = 0;
			}
		}
		for (int d = 0; d < 9; d++) {
			ip = i + dij[d][0];
			jp = j + dij[d][1];

			if (inside(ip, jp) && gridIntInfo[ip][jp][TYPE] != CELL_NODE) {
				gridIntInfo[ip][jp][TYPE] = CELL_FREE;
			}

		}

		gridIntInfo[i][j][COST] = 1;

		que.add(ijToInt(i, j));
		long heap_top = 0;

		while (que.isEmpty() == false) {
			heap_top = que.poll();
			intToIj(heap_top, ij);
			i = ij[0];
			j = ij[1];

			if (checker.query(gridPoints[i][j][0], gridPoints[i][j][1]) == true) {
				result = new Point2D(gridPoints[i][j][0], gridPoints[i][j][1]);
				return result;
			}

			for (int d = 0; d < 8; d++) {
				ip = i + dij_8[d][0];
				jp = j + dij_8[d][1];
				if (false || (inside(ip, jp) == false) || gridIntInfo[ip][jp][COST] > 0
						|| gridIntInfo[ip][jp][TYPE] == CELL_BLOCK || gridIntInfo[ip][jp][TYPE] == CELL_OUT) {
					continue;
				}
				gridIntInfo[ip][jp][COST] = gridIntInfo[i][j][COST] + 1;
				que.add(ijToInt(ip, jp));
			}
		}

		return result;
	}

	public Point2D getPointInRange(AURAreaGraph areaGraph, double rcx, double rcy, double r, double fromX,
			double fromY) {
		this.areaGraph = areaGraph;
		this.areaPolygon = (Polygon) (area.getShape());
		edgePointsSize = 0;
		blockaePolygons.clear();
		this.area = areaGraph.area;

		Point2D result = null;
		AURRange range = new AURRange(rcx, rcy, r);

		initGrid();
		addAreaBlockades(this.areaGraph);

		for (int i = 0; i < gridM; i++) {
			for (int j = 0; j < gridN; j++) {
				if (gridIntInfo[i][j][TYPE] == CELL_FREE) {
					if (range.contains(gridPoints[i][j][0], gridPoints[i][j][1])) {
						gridIntInfo[i][j][TYPE] = CELL_NODE;
					}
				}
			}
		}

		int ij[] = getCell(fromX, fromY);

		if (ij[0] < 0) {
			return result;
		}
		int i, j;
		int ip, jp;
		i = ij[0];
		j = ij[1];
		que.clear();

		for (int ii = 0; ii < gridM; ii++) {
			for (int jj = 0; jj < gridN; jj++) {
				gridIntInfo[ii][jj][COST] = 0;
			}
		}
		for (int d = 0; d < 9; d++) {
			ip = i + dij[d][0];
			jp = j + dij[d][1];

			if (inside(ip, jp) && gridIntInfo[ip][jp][TYPE] != CELL_NODE) {
				gridIntInfo[ip][jp][TYPE] = CELL_FREE;
			}

		}

		gridIntInfo[i][j][COST] = 1;

		que.add(ijToInt(i, j));
		long heap_top = 0;

		while (que.isEmpty() == false) {
			heap_top = que.poll();
			intToIj(heap_top, ij);
			i = ij[0];
			j = ij[1];

			if (gridIntInfo[i][j][0] == CELL_NODE) {
				result = new Point2D(gridPoints[i][j][0], gridPoints[i][j][1]);
				return result;
			}

			for (int d = 0; d < 8; d++) {
				ip = i + dij_8[d][0];
				jp = j + dij_8[d][1];
				if (false || (inside(ip, jp) == false) || gridIntInfo[ip][jp][COST] > 0
						|| gridIntInfo[ip][jp][TYPE] == CELL_BLOCK || gridIntInfo[ip][jp][TYPE] == CELL_OUT) {
					continue;
				}
				gridIntInfo[ip][jp][COST] = gridIntInfo[i][j][COST] + 1;
				que.add(ijToInt(ip, jp));
			}
		}

		return result;
	}

	public int[] getCell(double x, double y) {
		int i = (int) (Math.floor((y - gridPoints[0][0][1] + gridSize / 2) / gridSize));
		int j = (int) (Math.floor((x - gridPoints[0][0][0] + gridSize / 2) / gridSize));
		getCellResult[0] = -1;
		if ((i < 0 || i >= gridM || j < 0 || j >= gridN) == false) {
			getCellResult[0] = i;
			getCellResult[1] = j;
		}
		return getCellResult;
	}

	class AuraBound {

		double minX;
		double minY;
		double maxX;
		double maxY;

		public AuraBound(Polygon polygon) {
			minX = Double.MAX_VALUE;
			minY = Double.MAX_VALUE;
			maxX = Double.MIN_VALUE;
			maxY = Double.MIN_VALUE;
			for (int i = 0; i < polygon.npoints; i++) {
				minX = Math.min(minX, polygon.xpoints[i]);
				minY = Math.min(minY, polygon.ypoints[i]);
				maxX = Math.max(maxX, polygon.xpoints[i]);
				maxY = Math.max(maxY, polygon.ypoints[i]);
			}
		}

	}

	public void initGrid() {
		AuraBound bounds = new AuraBound(areaPolygon);

		boundsX0 = bounds.minX;
		boundsY0 = bounds.minY;
		boundsX1 = bounds.maxX;
		boundsY1 = bounds.maxY;

		int boundJ0 = (int) (Math.floor((boundsX0 - 0 + gridSize / 2) / gridSize)) - 3;
		int boundI0 = (int) (Math.floor((boundsY0 - 0 + gridSize / 2) / gridSize)) - 3;
		int boundJ1 = (int) (Math.ceil((boundsX1 - 0 + gridSize / 2) / gridSize)) + 3;
		int boundI1 = (int) (Math.ceil((boundsY1 - 0 + gridSize / 2) / gridSize)) + 3;

		gridM = boundI1 - boundI0 + 1;
		gridN = boundJ1 - boundJ0 + 1;
		checkGridArraySize(gridM, gridN);

		double oX = boundJ0 * gridSize;
		double oY = boundI0 * gridSize;

		double cx, cy;
		for (int i = 0; i < gridM; i++) {
			for (int j = 0; j < gridN; j++) {
				cx = j * gridSize + oX;
				cy = i * gridSize + oY;
				gridPoints[i][j][0] = cx;
				gridPoints[i][j][1] = cy;
				gridIntInfo[i][j][TYPE] = CELL_FREE;
				gridIntInfo[i][j][EDGE_POINT_ID] = -1;

			}
		}
		for (int i = 0; i < gridM; i++) {
			for (int j = 0; j < gridN; j++) {
				if (gridIntInfo[i][j][TYPE] == CELL_FREE) {
					if (areaPolygon.contains(gridPoints[i][j][0], gridPoints[i][j][1]) == false) {
						gridIntInfo[i][j][TYPE] = CELL_OUT; // #toDo
					}
				}
			}
		}
	}

	public void addAreaBlockades(AURAreaGraph ag) {
		addBlockades(ag.areaBlockadePolygons);
	}

	public void init(AURAreaGraph areaGraph) {
		this.areaGraph = areaGraph;
		this.areaPolygon = (Polygon) (areaGraph.area.getShape());
		edgePointsSize = 0;
		blockaePolygons.clear();
		this.area = areaGraph.area;

		initGrid();

		for (AURAreaGraph ag : areaGraph.neighbours) {
			for (AURBorder border : ag.borders) {
				markLine(border.Ax, border.Ay, border.Bx, border.By, CELL_AREA_EDGE);
			}
		}

		for (AURBorder border : areaGraph.borders) {
			markLine(border.Ax, border.Ay, border.Bx, border.By, CELL_AREA_EDGE);
		}

		addBlockades(areaGraph.areaBlockadePolygons);

		for (AURAreaGraph ag : areaGraph.neighbours) {
			addBlockades(ag.areaBlockadePolygons);
		}
	}

	public void addBlockades(ArrayList<Polygon> blockades) {
		blockaePolygons.addAll(blockades);
		double delta = 250;
		for (int i = 0; i < gridM; i++) {
			for (int j = 0; j < gridN; j++) {
				if (gridIntInfo[i][j][TYPE] != CELL_BLOCK) {
					for (Polygon p : blockaePolygons) {
						if (p.contains(gridPoints[i][j][0], gridPoints[i][j][1]) || p.intersects(
								gridPoints[i][j][0] - delta, gridPoints[i][j][1] - delta, delta * 2, delta * 2)) {
							gridIntInfo[i][j][TYPE] = CELL_BLOCK;
							break;
						}
					}
				}

			}
		}
	}

	public ArrayList<AURNode> getReachableEdgeNodesFrom(AURAreaGraph ag, double x, double y) {
		ArrayList<AURNode> result = new ArrayList<>();
		init(ag);
		edgePointsSize = 0;
		for (int i = 0; i < edgePoint.length; i++) {
			edgePoint[i][0] = -1;
			edgePointObject[i] = null;
		}
		for (AURBorder border : areaGraph.borders) {
			for (AURNode node : border.nodes) {
				markEdgeCenters(node, node.x, node.y, CELL_NODE);
			}
		}
		int ij[] = getCell(x, y);
		if (ij[0] < 0) {
			return result;
		}
		int i, j;
		int ip, jp;
		i = ij[0];
		j = ij[1];
		for (int d = 0; d < 9; d++) {
			ip = i + dij[d][0];
			jp = j + dij[d][1];
			if (inside(ip, jp) && gridIntInfo[ip][jp][TYPE] != CELL_NODE) {
				gridIntInfo[ip][jp][TYPE] = CELL_FREE;
			}
		}
		ij = new int[2];
		que.clear();
		for (int ii = 0; ii < gridM; ii++) {
			for (int jj = 0; jj < gridN; jj++) {
				gridIntInfo[ii][jj][COST] = 0;
			}
		}
		gridIntInfo[i][j][COST] = 1;
		que.add(ijToInt(i, j));
		long heap_top = 0;

		while (que.isEmpty() == false) {
			heap_top = que.poll();
			intToIj(heap_top, ij);
			i = ij[0];
			j = ij[1];

			if (gridIntInfo[i][j][0] == CELL_NODE) {
				int cell_id = gridIntInfo[i][j][EDGE_POINT_ID];
				AURNode node = edgePointObject[cell_id];
				node.cost = (int) (gridIntInfo[i][j][COST] * gridSize);
				result.add(node);
			}

			for (int d = 0; d < 9; d++) {
				ip = i + dij[d][0];
				jp = j + dij[d][1];
				if (false || (inside(ip, jp) == false) || gridIntInfo[ip][jp][COST] > 0
						|| gridIntInfo[ip][jp][TYPE] == CELL_BLOCK || gridIntInfo[ip][jp][TYPE] == CELL_OUT) {
					continue;
				}
				gridIntInfo[ip][jp][COST] = gridIntInfo[i][j][COST] + 1;
				que.add(ijToInt(ip, jp));
			}
		}
		return result;
	}

	public void setEdgePointsAndCreateGraph() {

		edgePointsSize = 0;
		for (int i = 0; i < edgePoint.length; i++) {
			edgePoint[i][0] = -1;
			edgePointObject[i] = null;
		}
		for (AURBorder border : areaGraph.borders) {
			if (border.calced == false) {
				markEdgeOpenCenters(border, border.Ax, border.Ay, border.Bx, border.By);
				border.calced = true;
			} else {
				for (AURNode node : border.nodes) {
					markEdgeCenters(node, node.x, node.y, CELL_NODE);
				}
				border.ready = true;
			}
		}
		for (int i = 0; i < edgePointsSize; i++) {
			for (int j = 0; j < edgePointsSize; j++) {
				graph[i][j] = INF;
				graph[j][i] = INF;
			}
		}
		ArrayList<AUREdge> delEdges = new ArrayList<>();
		for (AURBorder border : areaGraph.borders) {
			for (AURNode node : border.nodes) {
				for (AUREdge edge : node.edges) {
					if (edge.areaGraph.area.getID().equals(area.getID())) {
						delEdges.add(edge);
					}
				}
				node.edges.removeAll(delEdges);
			}
		}
		if (blockaePolygons.size() > 0) {
			for (int i = 0; i < edgePointsSize - 1; i++) {
				bfs(i);
			}
		} else {
			double cost = 0;
			AURNode iNode;
			AURNode jNode;
			AUREdge edge = null;
			for (int i = 0; i < edgePointsSize; i++) {
				for (int j = i + 1; j < edgePointsSize; j++) {
					iNode = edgePointObject[i];
					jNode = edgePointObject[j];
					cost = Math.abs(edgePoint[i][0] - edgePoint[j][0]) + Math.abs(edgePoint[i][1] - edgePoint[j][1]);
					cost = cost * gridSize;
					graph[i][j] = (int) cost;
					graph[j][i] = (int) cost;
					edge = new AUREdge(iNode, jNode, cost, areaGraph);
					iNode.edges.add(edge);
					jNode.edges.add(edge);
				}
			}
		}
	}

	public void bfs(int from) {
		que.clear();
		AURNode fromNode;
		AURNode toNode;
		fromNode = edgePointObject[from];
		int i, j;
		int ip, jp;
		int ij[] = new int[2];
		int count = 0;
		for (int ii = 0; ii < gridM; ii++) {
			for (int jj = 0; jj < gridN; jj++) {
				gridIntInfo[ii][jj][COST] = 0;
			}
		}
		for (int k = 0; k < edgePointsSize; k++) {
			if (graph[from][k] < INF) {
				count++;
			}
		}
		i = edgePoint[from][0];
		j = edgePoint[from][1];
		gridIntInfo[i][j][COST] = 1;
		que.add(ijToInt(i, j));
		long heap_top = 0;
		AUREdge edge = null;
		while (que.isEmpty() == false) {
			heap_top = que.poll();
			intToIj(heap_top, ij);
			i = ij[0];
			j = ij[1];
			if (gridIntInfo[i][j][0] == CELL_NODE && (gridIntInfo[i][j][EDGE_POINT_ID] != from)) {
				int cell_id = gridIntInfo[i][j][EDGE_POINT_ID];
				if ((graph[from][cell_id] >= INF)) {
					graph[from][cell_id] = gridIntInfo[i][j][COST];
					graph[cell_id][from] = gridIntInfo[i][j][COST];
					toNode = edgePointObject[cell_id];
					double cost = gridIntInfo[i][j][COST] * gridSize;
					edge = new AUREdge(fromNode, toNode, cost, areaGraph);
					fromNode.edges.add(edge);
					toNode.edges.add(edge);
					count++;
					if (count >= edgePointsSize) {
						break;
					}
				}
			}

			for (int d = 0; d < 9; d++) {
				ip = i + dij[d][0];
				jp = j + dij[d][1];
				if (false || (inside(ip, jp) == false) || gridIntInfo[ip][jp][COST] > 0
						|| gridIntInfo[ip][jp][TYPE] == CELL_BLOCK || gridIntInfo[ip][jp][TYPE] == CELL_OUT) {
					continue;
				}
				gridIntInfo[ip][jp][COST] = gridIntInfo[i][j][COST] + 1;
				que.add(ijToInt(ip, jp));
			}
		}
	}

	public static long ijToInt(int i, int j) {
		return i * _word_size + j;
	}

	public static void intToIj(long int_, int result[]) {
		result[0] = (int) (int_ / _word_size);
		result[1] = (int) (int_ % _word_size);
	}

	public void markLine(double x0, double y0, double x1, double y1, int type) {
		double dx = x1 - x0;
		double dy = y1 - y0;
		double m;
		double t;
		if (dx * dx + dy * dy < 1) {
			return;
		}
		int[] res;
		res = getCell(x1, y1);
		if (res[0] >= 0) {
			x1 = res[1] * gridSize + gridPoints[0][0][0];
			y1 = res[0] * gridSize + gridPoints[0][0][1];
		}
		res = getCell(x0, y0);
		if (res[0] >= 0) {
			x0 = res[1] * gridSize + gridPoints[0][0][0];
			y0 = res[0] * gridSize + gridPoints[0][0][1];
		}
		dx = x1 - x0;
		dy = y1 - y0;
		if (Math.abs(dx) >= Math.abs(dy)) {
			if (dx < 0) {
				t = x0;
				x0 = x1;
				x1 = t;
				t = y0;
				y0 = y1;
				y1 = t;
			}
			m = dy / dx;
			t = x0;
			double g = 0;
			double finish = x1;
			while (g + x0 < finish) {
				res = getCell(g + x0, y0 + g * m);
				if (res[0] != -1 && gridIntInfo[res[0]][res[1]][TYPE] != CELL_BLOCK) {
					markCell(res, type);
				}
				g += lineStepSize;
			}
		} else {
			if (dy < 0) {
				t = x0;
				x0 = x1;
				x1 = t;
				t = y0;
				y0 = y1;
				y1 = t;
			}
			m = dx / dy;
			t = y0;
			double g = 0;
			double finish = y1;
			while (g + y0 < finish) {
				res = getCell(x0 + g * m, y0 + g);
				if (res[0] != -1 && gridIntInfo[res[0]][res[1]][TYPE] != CELL_BLOCK) {
					markCell(res, type);
				}
				g += lineStepSize;
			}
		}
	}

	public void markEdgeCenters(AURBorder border, double x0, double y0, double x1, double y1, int type) {
		double dist = AURGeoUtil.dist(x0, y0, x1, y1);
		if (dist <= 0.5 * gridSize) {
			return;
		}
		int res[] = getCell((x0 + x1) / 2, (y0 + y1) / 2);
		if (res[0] != -1 && (gridIntInfo[res[0]][res[1]][TYPE] == CELL_AREA_EDGE)) {
			edgePoint[edgePointsSize][0] = res[0];
			edgePoint[edgePointsSize][1] = res[1];
			gridIntInfo[res[0]][res[1]][EDGE_POINT_ID] = edgePointsSize;
			markCell(res, type);
			double cx = res[1] * gridSize + gridPoints[0][0][0];
			double cy = res[0] * gridSize + gridPoints[0][0][1];
			edgePointObject[edgePointsSize] = new AURNode((int) cx, (int) cy, border.area1, border.area2);
			border.nodes.add(edgePointObject[edgePointsSize]);
			edgePointsSize++;
		}
	}

	public void markEdgeCenters(AURNode node, double x, double y, int type) {
		int res[] = getCell(x, y);
		if (res[0] != -1) {
			edgePoint[edgePointsSize][0] = res[0];
			edgePoint[edgePointsSize][1] = res[1];
			gridIntInfo[res[0]][res[1]][EDGE_POINT_ID] = edgePointsSize;
			markCell(res, type);
			edgePointObject[edgePointsSize] = node;
			edgePointsSize++;
		}
	}

	public void setCenter(int Ai, int Aj, int Bi, int Bj, byte type) {
		setCenter_temp[0] = (Ai + Bi) / 2;
		setCenter_temp[1] = (Aj + Bj) / 2;
		markCell(setCenter_temp, type);
	}

	public void markEdgeOpenCenters(AURBorder border, double x0, double y0, double x1, double y1) {
		double dx = x1 - x0;
		double dy = y1 - y0;
		double m;
		double t;
		double last_x = -1;
		double last_y = -1;
		double start_y = -1;
		double start_x = -1;
		if (dx * dx + dy * dy < 1) {
			return;
		}
		int[] res;
		res = getCell(x1, y1);
		if (res[0] >= 0) {
			x1 = res[1] * gridSize + gridPoints[0][0][0];
			y1 = res[0] * gridSize + gridPoints[0][0][1];
		}
		res = getCell(x0, y0);
		if (res[0] >= 0) {
			x0 = res[1] * gridSize + gridPoints[0][0][0];
			y0 = res[0] * gridSize + gridPoints[0][0][1];
		}
		dx = x1 - x0;
		dy = y1 - y0;
		boolean last_valid = false;
		boolean cur_valid = false;
		if (Math.abs(dx) >= Math.abs(dy)) {
			if (dx < 0) {
				t = x0;
				x0 = x1;
				x1 = t;
				t = y0;
				y0 = y1;
				y1 = t;
			}
			m = dy / dx;
			t = x0;
			double g = 0;
			start_x = -1;
			start_y = -1;
			double finish = x1;
			while (g + x0 < finish) {
				res = getCell(g + x0, y0 + g * m);
				int i, j;
				i = res[0];
				j = res[1];
				cur_valid = true;
				if (i == -1 || gridIntInfo[i][j][TYPE] != CELL_AREA_EDGE) {
					cur_valid = false;
				}
				if (!last_valid && cur_valid) {
					start_x = g + x0;
					start_y = y0 + g * m;
				}
				if (last_valid && !cur_valid) {
					markEdgeCenters(border, start_x, start_y, last_x, last_y, CELL_NODE);
					start_x = -1;
					start_y = -1;
				}
				last_valid = cur_valid;
				last_x = g + x0;
				last_y = y0 + g * m;
				if (i == -1) {
					break;
				}
				g += lineStepSize;
			}
			if (start_x >= 0) {
				markEdgeCenters(border, start_x, start_y, last_x, last_y, CELL_NODE);
			}

		} else {
			if (dy < 0) {
				t = x0;
				x0 = x1;
				x1 = t;
				t = y0;
				y0 = y1;
				y1 = t;
			}
			m = dx / dy;
			t = y0;
			double g = 0;
			start_x = -1;
			start_y = -1;
			double finish = y1;
			while (g + y0 < finish) {
				res = getCell(x0 + g * m, y0 + g);
				int i, j;
				i = res[0];
				j = res[1];

				cur_valid = true;

				if (i == -1 || gridIntInfo[i][j][TYPE] != CELL_AREA_EDGE) {
					cur_valid = false;
				}

				if (!last_valid && cur_valid) {
					start_x = x0 + g * m;
					start_y = y0 + g;
				}

				if (last_valid && !cur_valid) {
					markEdgeCenters(border, start_x, start_y, last_x, last_y, CELL_NODE);
					start_x = -1;
					start_y = -1;
				}
				last_valid = cur_valid;
				last_x = x0 + g * m;
				;
				last_y = y0 + g;
				;
				if (i == -1) {
					break;
				}
				g += lineStepSize;
			}
			if (start_x >= 0) {
				markEdgeCenters(border, start_x, start_y, last_x, last_y, CELL_NODE);
			}
		}
	}

	public void markCell(int[] ij, int type) {
		gridIntInfo[ij[0]][ij[1]][TYPE] = type;
	}

	public boolean inside(int i, int j) {
		if (i < 0 || j < 0 || i >= gridM || j >= gridN) {
			return false;
		}
		return true;
	}

	public void draw(Graphics2D g) {

		// g.drawLine(0, 0, mx, my);
		g.setColor(new Color(100, 100, 100, 100));
		// g.fill(areaPolygon.getBounds());

		g.fillPolygon(areaPolygon);
		for (Polygon p : blockaePolygons) {
			g.setColor(new Color(250, 100, 100, 100));
			g.fillPolygon(p);
		}
		int r = (int) gridSize / 2;

		for (int i = 0; i < gridM; i++) {
			for (int j = 0; j < gridN; j++) {
				r = (int) gridSize / 2;
				if (gridIntInfo[i][j][TYPE] == CELL_BLOCK) {
					g.setColor(new Color(40, 40, 40, 150));
					g.fillOval((int) (gridPoints[i][j][0] - r), (int) (gridPoints[i][j][1] - r), r * 2, r * 2);
				} else if (gridIntInfo[i][j][TYPE] == CELL_AREA_EDGE) {
					g.setColor(new Color(0, 0, 255, 50));
					g.fillOval((int) (gridPoints[i][j][0] - r), (int) (gridPoints[i][j][1] - r), r * 2, r * 2);
				} else if (gridIntInfo[i][j][TYPE] == CELL_NODE) {
					g.setColor(new Color(0, 255, 0, 200));
					// r = 7;
					g.fillOval((int) (gridPoints[i][j][0] - r), (int) (gridPoints[i][j][1] - r), r * 2, r * 2);
				} else if (gridIntInfo[i][j][TYPE] == CELL_OUT) {
					// g.setColor(new Color(40, 40, 40, 50));
					// r = 7;
					// g.fillOval((int) (gridPoints[i][j][0] - r), (int)
					// (gridPoints[i][j][1] - r), r * 2, r * 2);
				} else {
					// g.setColor(new Color(250, 250, 250, 150));
					// g.drawOval((int) (gridPoints[i][j][0] - r), (int)
					// (gridPoints[i][j][1] - r), r * 2, r * 2);
				}

			}
		}

		g.setColor(new Color(5, 150, 5, 255));
		g.setStroke(new BasicStroke(40));
		double x0;
		double y0;
		double x1;
		double y1;
		for (int i = 0; i < edgePointsSize; i++) {
			for (int j = i; j < edgePointsSize; j++) {
				if (graph[i][j] < INF) {
					x0 = gridPoints[edgePoint[i][0]][edgePoint[i][1]][0];
					y0 = gridPoints[edgePoint[i][0]][edgePoint[i][1]][1];
					x1 = gridPoints[edgePoint[j][0]][edgePoint[j][1]][0];
					y1 = gridPoints[edgePoint[j][0]][edgePoint[j][1]][1];

					// g.drawLine((int) (x0 + Math.random() * 0), (int) (y0 +
					// Math.random() * 0), (int) x1, (int) y1);
				}
			}
		}
	}
}
