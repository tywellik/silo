package de.tum.bgu.msm.models.transportModel.matsim;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.vividsolutions.jts.geom.Coordinate;
import de.tum.bgu.msm.data.Location;
import de.tum.bgu.msm.data.MicroLocation;
import de.tum.bgu.msm.data.Region;
import de.tum.bgu.msm.data.Zone;
import de.tum.bgu.msm.data.travelTimes.SkimTravelTimes;
import de.tum.bgu.msm.data.travelTimes.TravelTimes;
import de.tum.bgu.msm.properties.Properties;
import de.tum.bgu.msm.utils.TravelTimeUtil;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.TripRouter;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.facilities.Facility;
import org.matsim.utils.leastcostpathtree.LeastCostPathTree;

import java.util.*;

public final class MatsimTravelTimes implements TravelTimes {
	private final static Logger LOG = Logger.getLogger(MatsimTravelTimes.class);

	private SkimTravelTimes delegate = new SkimTravelTimes() ;
	private LeastCostPathTree leastCoastPathTree;
	private Network network;
	private TripRouter tripRouter;
	private final Map<Zone, List<Node>> zoneCalculationNodesMap = new HashMap<>();
	private final static int NUMBER_OF_CALC_POINTS = 1;
	private final Map<Id<Node>, Map<Double, Map<Id<Node>, LeastCostPathTree.NodeData>>> treesForNodesByTimes = new HashMap<>();
	
	//
	Map<Integer, Zone> zones;
	//
	
	enum TimeSlices{night, earlyMorning, morningPeak, lateMorning, midday, afternoonPeak, earlyEvening, lateEvening} // 0-5, 5-8, 8-10, 10-12, 12-16, 16-19, 19-22, 22-0
	private final Map<Id<Node>, Map<TimeSlices, Map<Id<Node>, LeastCostPathTree.NodeData>>> treesForNodesByTimeSlices = new HashMap<>();

	private final Table<Zone, Region, Double> travelTimeToRegion = HashBasedTable.create();

	public MatsimTravelTimes() {
	}

	void update(TripRouter tripRouter, LeastCostPathTree leastCoastPathTree) {
		this.tripRouter = tripRouter;
		this.leastCoastPathTree = leastCoastPathTree;
		this.treesForNodesByTimes.clear();
//		TravelTimeUtil.updateTransitSkim(delegate,
//				Properties.get().main.startYear, Properties.get());
	}

	public void initialize(Map<Integer, Zone> zones, Network network) {
		this.network = network;
		//
		this.zones = zones;
		//
		for (Zone zone : zones.values()) {
            for (int i = 0; i < NUMBER_OF_CALC_POINTS; i++) { // Several points in a given origin zone
            	Coordinate coordinate = zone.getRandomCoordinate();
				Coord originCoord = new Coord(coordinate.x, coordinate.y);
                Node originNode = NetworkUtils.getNearestLink(network, originCoord).getToNode();

				if (!zoneCalculationNodesMap.containsKey(zone)) {
					zoneCalculationNodesMap.put(zone, new LinkedList<>());
				}
				zoneCalculationNodesMap.get(zone).add(originNode);
			}
		}
        LOG.trace("There are " + zoneCalculationNodesMap.keySet().size() + " origin zones.");
    }

	private double getZoneToZoneTravelTime(Zone origin, Zone destination, double timeOfDay_s, String mode) {
		switch (mode) {
			case TransportMode.car:
				double sumTravelTime_min = 0.;
				for (Node originNode : zoneCalculationNodesMap.get(origin)) { // Several points in a given origin zone
					Map<Id<Node>, LeastCostPathTree.NodeData> tree;
					if (treesForNodesByTimes.containsKey(originNode.getId())) {  // Node already checked
						Map<Double, Map<Id<Node>, LeastCostPathTree.NodeData>> treesForCurrentNodeByTimes = treesForNodesByTimes.get(originNode.getId());
						if (treesForCurrentNodeByTimes.containsKey(timeOfDay_s)) { // Time already checked
							tree = treesForCurrentNodeByTimes.get(timeOfDay_s);
						} else {
							leastCoastPathTree.calculate(network, originNode, timeOfDay_s);
							tree = leastCoastPathTree.getTree();
							treesForCurrentNodeByTimes.put(timeOfDay_s, tree);
						}
					} else {
						Map<Double, Map<Id<Node>, LeastCostPathTree.NodeData>> treesForOneNodeByTimes = new HashMap<>();
						leastCoastPathTree.calculate(network, originNode, timeOfDay_s);
						tree = leastCoastPathTree.getTree();
						treesForOneNodeByTimes.put(timeOfDay_s, tree);
						treesForNodesByTimes.put(originNode.getId(), treesForOneNodeByTimes);
					}
	
					for (Node destinationNode : zoneCalculationNodesMap.get(destination)) { // Several points in a given destination zone
						double arrivalTime_s = tree.get(destinationNode.getId()).getTime();
						sumTravelTime_min += ((arrivalTime_s - timeOfDay_s) / 60.);
					}
				}
				return sumTravelTime_min / NUMBER_OF_CALC_POINTS;
			case TransportMode.pt:
				//TODO: reconsider matsim pt travel times. nk apr'18
	            return delegate.getTravelTime(origin, destination, timeOfDay_s, mode);
			default:
	        	throw new IllegalArgumentException("Other modes not implemented yet..");
		}
	}
	
	public double getTravelTime(Location origin, Location destination, double timeOfDay_s, String mode) {
		TimeSlices timeSlice = getTimeSlice(timeOfDay_s);
		
		if (origin instanceof MicroLocation && destination instanceof MicroLocation) { // Microlocations case
			switch (mode) {
				case TransportMode.car:
					double sumTravelTime_min = 0.;
					//
					Coordinate coordinate = null;
					if (Properties.get().main.useMicrolocation) {
						coordinate = ((MicroLocation) origin).getCoordinate();
					} else{
						LOG.warn("Use random coordinate."); // TODO
						coordinate = zones.get(origin.getZoneId()).getRandomCoordinate();
					}
					Coord originCoord = CoordUtils.createCoord(coordinate);
			        //
//					Coord originCoord = CoordUtils.createCoord(((MicroLocation) origin).getCoordinate());
					Coord destinationCoord = CoordUtils.createCoord(((MicroLocation) destination).getCoordinate());
		
					// TODO take care of network access
					// sumTravelTime_min += ...;
					
					Node originNode = NetworkUtils.getNearestLink(network, originCoord).getToNode();
					Map<Id<Node>, LeastCostPathTree.NodeData> tree;
					
					if (treesForNodesByTimeSlices.containsKey(originNode.getId())) { // Node already checked
						Map<TimeSlices, Map<Id<Node>, LeastCostPathTree.NodeData>> treesForCurrentNodeByTimeSlice = treesForNodesByTimeSlices.get(originNode.getId());
						if (treesForCurrentNodeByTimeSlice.containsKey(timeSlice)) { // Time slice already checked
							tree = treesForCurrentNodeByTimeSlice.get(timeSlice);
						} else {
							leastCoastPathTree.calculate(network, originNode, getRepresentativeTime(timeSlice));
							tree = leastCoastPathTree.getTree();
							treesForCurrentNodeByTimeSlice.put(timeSlice, tree);
						}
					} else {
						Map<TimeSlices, Map<Id<Node>, LeastCostPathTree.NodeData>> treesForCurrentNodeByTimeSlice = new HashMap<>();
						leastCoastPathTree.calculate(network, originNode, getRepresentativeTime(timeSlice));
						tree = leastCoastPathTree.getTree();
						treesForCurrentNodeByTimeSlice.put(timeSlice, tree);
						treesForNodesByTimeSlices.put(originNode.getId(), treesForCurrentNodeByTimeSlice);
					}
					
					Node destinationNode = NetworkUtils.getNearestLink(network, destinationCoord).getToNode();
					
					double arrivalTime_s = tree.get(destinationNode.getId()).getTime();
					sumTravelTime_min += ((arrivalTime_s - getRepresentativeTime(timeSlice)) / 60.);
					
					// TODO take care of network egress
					// sumTravelTime_min += ...;
					
					return sumTravelTime_min;
				case TransportMode.pt:
					//TODO: reconsider matsim pt travel times. nk apr'18
//		            return delegate.getTravelTime(origin, destination, timeOfDay_s, mode);
		            return 100;
		        default:
		        	throw new IllegalArgumentException("Other modes not implemented yet..");
			}
		
		}
		else if (origin instanceof Zone) { // Non-microlocations case
			Zone originZone = (Zone) origin;
			if (destination instanceof Zone) {
				return getZoneToZoneTravelTime(originZone, (Zone) destination, timeOfDay_s, mode);
			} else if (destination instanceof Region) {
				Region destinationRegion = (Region) destination;
				if (travelTimeToRegion.contains(originZone, destinationRegion)) {
					return travelTimeToRegion.get(originZone, destinationRegion);
				}
				double min = Double.MAX_VALUE;
        		for (Zone zoneInRegion : destinationRegion.getZones()) {
        			double travelTime = getZoneToZoneTravelTime(originZone, zoneInRegion, timeOfDay_s, mode);
        			if (travelTime < min) {
        				min = travelTime;
        			}
        		}
        		travelTimeToRegion.put(originZone, destinationRegion, min);
			}
		}
		
		throw new IllegalArgumentException("The combination with origin of type " + origin.getClass().getName() 
				+ " and destination of type " + destination.getClass().getName() + " is not valid.");
	}

	public double getTravelTimeTripRouter(Location origin, Location destination, double timeOfDay_s, String mode) {
		if (origin instanceof MicroLocation && destination instanceof MicroLocation) { // Microlocations case
			Coordinate originCoord = ((MicroLocation) origin).getCoordinate();
			Coordinate destinationCoord = ((MicroLocation) destination).getCoordinate();
			Facility fromFacility = new DummyFacility(new Coord(originCoord.x, originCoord.y));
			Facility toFacility = new DummyFacility(new Coord(destinationCoord.x, destinationCoord.y));
			org.matsim.api.core.v01.population.Person person = null;
			List<? extends PlanElement> trip = tripRouter.calcRoute(mode, fromFacility, toFacility, timeOfDay_s, person);
			double ttime = 0. ;
			for ( PlanElement pe : trip ) {
				if ( pe instanceof Leg) {
					ttime += ((Leg) pe).getTravelTime() ;
				}
			}
			// TODO take care of relevant interaction activities
			return ttime;
		}
		else if (origin instanceof Zone) { // Non-microlocations case
			Zone originZone = (Zone) origin;
			if (destination instanceof Zone) {
				return getZoneToZoneTravelTime(originZone, (Zone) destination, timeOfDay_s, mode);
			} else if (destination instanceof Region) {
				Region destinationRegion = (Region) destination;
				if (travelTimeToRegion.contains(originZone, destinationRegion)) {
					return travelTimeToRegion.get(originZone, destinationRegion);
				}
				double min = Double.MAX_VALUE;
        		for (Zone zoneInRegion : destinationRegion.getZones()) {
        			double travelTime = getZoneToZoneTravelTime(originZone, zoneInRegion, timeOfDay_s, mode);
        			if (travelTime < min) {
        				min = travelTime;
        			}
        		}
        		travelTimeToRegion.put(originZone, destinationRegion, min);
			}
		}
		throw new IllegalArgumentException("The combination with origin of type " + origin.getClass().getName() 
					+ " and destination of type " + destination.getClass().getName() + " is not valid.");
	}

	@Override
	public double getTravelTime(int origin, int destination, double timeOfDay_s, String mode) {
		throw new IllegalArgumentException("Not implemented in MATSim case.");
	}

	@Override
	public double getTravelTimeToRegion(Location origin, Region destination, double timeOfDay_s, String mode) {
		// TODO Auto-generated method stub
		return 0;
	}

	private static class DummyFacility implements Facility {

		private final Coord coord;

		private DummyFacility(Coord coord) {
			this.coord = coord;
		}

		@Override
		public Id<Link> getLinkId() {
			return null;
		}

		@Override
		public Coord getCoord() {
			return this.coord;
		}

		@Override
		public Map<String, Object> getCustomAttributes() {
			return null;
		}
	}
	
	private TimeSlices getTimeSlice(double timeOfDay_s) {
		if (timeOfDay_s <= 5 * 60. * 60.) {
			return TimeSlices.night;
		} else if (timeOfDay_s <= 8 * 60. * 60.) {
			return TimeSlices.earlyEvening;
		} else if (timeOfDay_s <= 10 * 60. * 60.) {
			return TimeSlices.morningPeak;
		} else if (timeOfDay_s <= 12 * 60. * 60.) {
			return TimeSlices.lateEvening;
		} else if (timeOfDay_s <= 16 * 60. * 60.) {
			return TimeSlices.midday;
		} else if (timeOfDay_s <= 19 * 60. * 60.) {
			return TimeSlices.afternoonPeak;
		} else if (timeOfDay_s <= 22 * 60. * 60.) {
			return TimeSlices.earlyEvening;
		} else {
			return TimeSlices.lateEvening;
		}
	}
	

	private double getRepresentativeTime(TimeSlices timeSlice) {
		double representativeTime = 0.;
		switch (timeSlice) {
		case night:
			representativeTime = 2.5 * 60. * 60.;
			break;
		case earlyMorning:
			representativeTime = 6.5 * 60. * 60.;
			break;
		case morningPeak:
			representativeTime = 9 * 60. * 60.;
			break;
		case lateMorning:
			representativeTime = 11 * 60. * 60.;
			break;
		case midday:
			representativeTime = 14 * 60. * 60.;
			break;
		case afternoonPeak:
			representativeTime = 17.5 * 60. * 60.;
			break;
		case earlyEvening:
			representativeTime = 20.5 * 60. * 60.;
			break;
		case lateEvening:
			representativeTime = 23 * 60. * 60.;
			break;
		default:
			LOG.warn("Can't be time slice = " + timeSlice);
		}
		return representativeTime;
	}
}