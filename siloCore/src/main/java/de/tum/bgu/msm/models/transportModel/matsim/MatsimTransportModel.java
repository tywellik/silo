/* *********************************************************************** *
 * project: org.matsim.*												   *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2008 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */
package de.tum.bgu.msm.models.transportModel.matsim;

import java.io.File;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.contrib.accessibility.AccessibilityAttributes;
import org.matsim.contrib.accessibility.AccessibilityConfigGroup;
import org.matsim.contrib.accessibility.AccessibilityConfigGroup.AccessibilityMeasureType;
import org.matsim.contrib.accessibility.AccessibilityConfigGroup.AreaOfAccesssibilityComputation;
import org.matsim.contrib.accessibility.AccessibilityModule;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.api.internal.MatsimWriter;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.FacilitiesConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.router.TripRouter;
import org.matsim.core.router.TripRouterFactoryBuilderWithDefaults;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutilityFactory;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.MutableScenario;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacility;
import org.matsim.utils.leastcostpathtree.LeastCostPathTree;

import de.tum.bgu.msm.container.SiloDataContainer;
import de.tum.bgu.msm.data.SummarizeData;
import de.tum.bgu.msm.models.accessibility.MatsimAccessibility;
import de.tum.bgu.msm.models.transportModel.TransportModelI;
import de.tum.bgu.msm.properties.Properties;

/**
 * @author dziemke
 */
public final class MatsimTransportModel implements TransportModelI  {
	private static final Logger LOG = Logger.getLogger( MatsimTransportModel.class );
	
	private final Config initialMatsimConfig;
	private final MatsimTravelTimes travelTimes;
	private Properties properties;
//	private TripRouter tripRouter = null;
	private final SiloDataContainer dataContainer;
	private ActivityFacilities zoneCentroids;
	private MatsimAccessibility accessibility;
	private final Network network;
	
	
	public MatsimTransportModel(SiloDataContainer dataContainer, Config matsimConfig, ActivityFacilities zoneCentroids,
	MatsimAccessibility accessibility, Properties properties) {
		this.dataContainer = Objects.requireNonNull(dataContainer);
		this.initialMatsimConfig = Objects.requireNonNull(matsimConfig);
		this.travelTimes = (MatsimTravelTimes) Objects.requireNonNull(dataContainer.getTravelTimes());
		this.properties = properties;
		this.zoneCentroids = zoneCentroids;
		this.accessibility = accessibility;
		network = NetworkUtils.createNetwork();
		new MatsimNetworkReader(network).readFile(matsimConfig.network().getInputFileURL(matsimConfig.getContext()).getFile());
		travelTimes.initialize(dataContainer.getGeoData().getZones().values(), network);
	}

	@Override
	public void runTransportModel(int year) {
		LOG.warn("Running MATSim transport model for year " + year + ".");

		String scenarioName = properties.main.scenarioName;

		boolean writePopulation = true;
		double populationScalingFactor = properties.transportModel.matsimScaleFactor;
		String matsimRunId = scenarioName + "_" + year;

		Config config = SiloMatsimUtils.createMatsimConfig(initialMatsimConfig, matsimRunId, populationScalingFactor, zoneCentroids);
		
		Population population = SiloMatsimUtils.createMatsimPopulation(config, dataContainer, populationScalingFactor);
		
		if (writePopulation) {
    		new File("./test/scenarios/annapolis_reduced/matsim_output/").mkdirs();
    		MatsimWriter populationWriter = new PopulationWriter(population);
    		populationWriter.write("./test/scenarios/annapolis_reduced/matsim_output/population_" + year + ".xml");
    	}

		// Get travel Times from MATSim
		LOG.warn("Using MATSim to compute travel times from zone to zone.");
		
		MutableScenario scenario = (MutableScenario) ScenarioUtils.loadScenario(config);
		scenario.setPopulation(population);
		
		// Opportunities
		Map<Integer, Integer> populationMap = SummarizeData.getPopulationByZoneAsMap(dataContainer);
		Map<Id<ActivityFacility>, Integer> zonePopulationMap = new TreeMap<>();
		for (int zoneId : populationMap.keySet()) {
			zonePopulationMap.put(Id.create(zoneId, ActivityFacility.class), populationMap.get(zoneId));
		}
		final ActivityFacilities opportunities = scenario.getActivityFacilities();
		int i = 0;
		for (ActivityFacility activityFacility : zoneCentroids.getFacilities().values()) {
			activityFacility.getAttributes().putAttribute(AccessibilityAttributes.WEIGHT, zonePopulationMap.get(activityFacility.getId()));
			opportunities.addActivityFacility(activityFacility);
			i++;
		}
		LOG.warn(i + " facilities added as opportunities.");
		
		SiloMatsimUtils.determineExtentOfFacilities(zoneCentroids);
		
		scenario.getConfig().facilities().setFacilitiesSource(FacilitiesConfigGroup.FacilitiesSource.setInScenario);
		// End opportunities
		
		// Accessibility settings
		AccessibilityConfigGroup acg = ConfigUtils.addOrGetModule(config, AccessibilityConfigGroup.class);
		acg.setMeasuringPointsFacilities(zoneCentroids);
		acg.setAreaOfAccessibilityComputation(AreaOfAccesssibilityComputation.fromFacilitiesObject);
		acg.setUseOpportunityWeights(true);
		acg.setWeightExponent(Properties.get().accessibility.alphaAuto); // TODO Need differentiation for different modes
		LOG.warn("Properties.get().accessibility.alphaAuto = " + Properties.get().accessibility.alphaAuto);
		acg.setAccessibilityMeasureType(AccessibilityMeasureType.rawSum);
		// End accessibility settings
		
		ConfigUtils.setVspDefaults(config);
		
		Controler controler = new Controler(scenario);
		
		// Accessibility module
		AccessibilityModule module = new AccessibilityModule();
		module.addFacilityDataExchangeListener(accessibility);
		controler.addOverridingModule(module);
		// End accessibility module
		
		controler.run();
		LOG.warn("Running MATSim transport model for year " + year + " finished.");
		
		TravelTime travelTime = controler.getLinkTravelTimes();
		TravelDisutility travelDisutility = controler.getTravelDisutilityFactory().createTravelDisutility(travelTime);
        updateTravelTimes(controler.getTripRouterProvider().get(), travelTime, travelDisutility);
	}

	
    /**
     * @param eventsFile
     */
	public void replayFromEvents(String eventsFile) {
        MutableScenario scenario = (MutableScenario) ScenarioUtils.loadScenario(initialMatsimConfig);
	    TravelTimeCalculator ttCalculator = TravelTimeCalculator.create(scenario.getNetwork(), scenario.getConfig().travelTimeCalculator());
        EventsManager events = EventsUtils.createEventsManager();
        events.addHandler(ttCalculator);
        (new MatsimEventsReader(events)).readFile(eventsFile);
        TripRouter tripRouter = TripRouterFactoryBuilderWithDefaults.createDefaultTripRouterFactoryImpl(scenario).get();
        TravelTime travelTime = ttCalculator.getLinkTravelTimes();
        TravelDisutility travelDisutility = new OnlyTimeDependentTravelDisutilityFactory().createTravelDisutility(travelTime);
        updateTravelTimes(tripRouter, travelTime, travelDisutility);
	}

	private void updateTravelTimes(TripRouter tripRouter, TravelTime travelTime, TravelDisutility disutility) {
		LeastCostPathTree leastCoastPathTree = new LeastCostPathTree(travelTime, disutility);
//
////		travelTimes.update(leastCoastPathTree, zoneFeatureMap, scenario.getNetwork(), controler.getTripRouterProvider().get() );
//		// for now, pt inforamtion from MATSim not required as there are no changes in PT supply (schedule) expected currently;
//		// potentially revise this later; nk/dz, nov'17
//		//TODO: Optimize pt travel time query
////		MatsimPtTravelTimes matsimPtTravelTimes = new MatsimPtTravelTimes(controler.getTripRouterProvider().get(), zoneFeatureMap, scenario.getNetwork());
////		acc.addTravelTimeForMode(TransportMode.pt, matsimTravelTimes); // use car times for now also, as pt travel times are too slow to compute, Nico Oct 17
//		
//		if (config.transit().isUseTransit() && Properties.get().main.implementation == Implementation.MUNICH) {
//			MatsimPTDistances matsimPTDistances = new MatsimPTDistances(config, scenario, (GeoDataMuc) dataContainer.getGeoData());
//		}
		travelTimes.update(tripRouter, leastCoastPathTree);
		
//		tripRouter = controler.getTripRouterProvider().get();
	}
	
	// Other idea; provide TripRouter more directly; requires more fundamental change, however
//	public final TripRouter getTripRouter() {
//		if(tripRouter == null) {
//			throw new RuntimeException("MATSim Transport Model needs to run at least once before trips can be queried!");
//		}
//		return tripRouter;
//	}
}