package de.tum.bgu.msm.models.accessibility;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.contrib.accessibility.interfaces.FacilityDataExchangeInterface;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.facilities.ActivityFacility;

import cern.colt.matrix.tdouble.DoubleMatrix1D;
import de.tum.bgu.msm.container.SiloDataContainer;
import de.tum.bgu.msm.data.Region;
import de.tum.bgu.msm.data.Zone;
import de.tum.bgu.msm.util.matrices.Matrices;

/**
 * @author dziemke
 **/
public class MatsimAccessibility implements Accessibility, FacilityDataExchangeInterface {
	private static final Logger LOG = Logger.getLogger(MatsimAccessibility.class);

	private Map<Tuple<ActivityFacility, Double>, Map<String,Double>> accessibilitiesMap = new HashMap<>();
	private SiloDataContainer dataContainer;

	private Map<Id<ActivityFacility>, Double> autoAccessibilities = new HashMap<>();
	private Map<Id<ActivityFacility>, Double> transitAccessibilities = new HashMap<>();
	private DoubleMatrix1D regionalAccessibilities;

	public MatsimAccessibility(SiloDataContainer dataContainer) {
		this.dataContainer = dataContainer;
		this.regionalAccessibilities = Matrices.doubleMatrix1D(dataContainer.getGeoData().getRegions().values());
	}
	
	// FacilityDataExchangeInterface methods
	@Override
	public void setFacilityAccessibilities(ActivityFacility measurePoint, Double timeOfDay, Map<String, Double> accessibilities){
		if (timeOfDay == 8 * 60. * 60.) { // TODO Find better way for this check
			accessibilitiesMap.put(new Tuple<ActivityFacility, Double>(measurePoint, timeOfDay), accessibilities);
		}
	}
		
	@Override
	public void finish() {
		// Do nothing
	}

	// Accessibility interface methods
	@Override
    public void updateHansenAccessibilities(int year) {
		LOG.info("Prepare accessibility data structure for SILO.");
		for (Tuple<ActivityFacility, Double> tuple : accessibilitiesMap.keySet()) {
			if (tuple.getSecond() == 8 * 60. * 60.) { // TODO Need to make this more flexible
				ActivityFacility activityFacility = tuple.getFirst();
				double freeSpeedAccessibility = accessibilitiesMap.get(tuple).get("freespeed");
				autoAccessibilities.put(activityFacility.getId(), freeSpeedAccessibility);
			}
		}
        LOG.info("Scaling zone accessibilities");
        scaleAccessibility(autoAccessibilities);
        scaleAccessibility(transitAccessibilities);

        LOG.info("Calculating regional accessibilities");
        regionalAccessibilities.assign(calculateRegionalAccessibility(dataContainer.getGeoData().getRegions().values(), autoAccessibilities));
    }
	
    @Override
    public double getAutoAccessibilityForZone(Zone zone) {
    	return autoAccessibilities.get(Id.create(zone.getId(), ActivityFacility.class));
    }
    
    @Override
    public double getTransitAccessibilityForZone(Zone zone) {
    	LOG.warn("Transit accessibilities not yet properly implemented.");
    	return autoAccessibilities.get(Id.create(zone.getId(), ActivityFacility.class)); // TODO Put transit accessibilities here!
    }

    @Override
    public double getRegionalAccessibility(Region region) {
    	return regionalAccessibilities.get(region.getId());
    }
    
    // Other methods
    private static void scaleAccessibility(Map<Id<ActivityFacility>, Double> accessibility) {
		double highestAccessibility = Double.MIN_VALUE; // TODO Rather use minus infinity
		for (double value : accessibility.values()) {
			if (value > highestAccessibility) {
				highestAccessibility = value;
			}
		}
        final double scaleFactor = 100.0 / highestAccessibility;
        for (Id<ActivityFacility> measurePointId : accessibility.keySet()) {
        	accessibility.put(measurePointId, accessibility.get(measurePointId) * scaleFactor);
        }
    }
	
	private static DoubleMatrix1D calculateRegionalAccessibility(Collection<Region> regions, Map<Id<ActivityFacility>, Double> autoAccessibilities) {
        final DoubleMatrix1D matrix = Matrices.doubleMatrix1D(regions);
        for (Region region : regions) {
        	double regionalAccessibilitySum = 0.;
        	for (Zone zone : region.getZones()) {
        		Id<ActivityFacility> measurePointId = Id.create(zone.getId(), ActivityFacility.class);
        		regionalAccessibilitySum = regionalAccessibilitySum + autoAccessibilities.get(measurePointId);
        	}
        	matrix.set(region.getId(), regionalAccessibilitySum / region.getZones().size());
        }
        return matrix;
    }
}