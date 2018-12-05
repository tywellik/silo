
package de.tum.bgu.msm.models.accessibility;

import cern.colt.matrix.io.MatrixVectorWriter;
import cern.colt.matrix.tdouble.DoubleFactory1D;
import cern.colt.matrix.tdouble.DoubleFactory2D;
import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.algo.DoubleFormatter;
import de.tum.bgu.msm.Implementation;
import de.tum.bgu.msm.utils.SiloUtil;
import de.tum.bgu.msm.container.SiloDataContainer;
import de.tum.bgu.msm.data.GeoData;
import de.tum.bgu.msm.data.HouseholdDataManager;
import de.tum.bgu.msm.data.Region;
import de.tum.bgu.msm.data.Zone;
import de.tum.bgu.msm.data.geo.RegionImpl;
import de.tum.bgu.msm.data.geo.ZoneImpl;
import de.tum.bgu.msm.data.household.HouseholdUtil;
import de.tum.bgu.msm.data.person.PersonUtils;
import de.tum.bgu.msm.data.travelTimes.SkimTravelTimes;
import de.tum.bgu.msm.io.DefaultHouseholdReader;
import de.tum.bgu.msm.io.DefaultPersonReader;
import de.tum.bgu.msm.properties.Properties;
import de.tum.bgu.msm.util.matrices.Matrices;
import de.tum.bgu.msm.utils.TravelTimeUtil;
import junitx.framework.FileAssert;
import org.junit.Assert;
import org.junit.Test;
import org.matsim.api.core.v01.TransportMode;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class AccessibilityTest {

    @Test
    public void testZoneToZoneAccessiblities() {
        DoubleMatrix1D population = DoubleFactory1D.dense.ascending(10);
        DoubleMatrix2D travelTimes = DoubleFactory2D.dense.ascending(10, 10);
        DoubleMatrix2D accessibilities = SkimBasedAccessibility.calculateZoneToZoneAccessibilities(population, travelTimes, 1.2, -0.3);
        Assert.assertEquals(12.799312461169375, accessibilities.zSum(), 0.);
    }

    @Test
    public void testScaleAccessibilities() {
        DoubleMatrix1D accessibility = DoubleFactory1D.dense.ascending(10);
        SkimBasedAccessibility.scaleAccessibility(accessibility);
        Assert.assertEquals(100., accessibility.getQuick(9), 0.);
        Assert.assertEquals(550, accessibility.zSum(), 0.);
    }

    @Test
    public void testAggregateAccessibilities() {
        List<Integer> keys = Arrays.asList(0,1,2,3,4,5,6,7,8,9);
        DoubleMatrix2D accessibilitiesAuto = DoubleFactory2D.dense.ascending(10, 10);
        DoubleMatrix1D accessibilityAuto = DoubleFactory1D.dense.make(10);

        DoubleMatrix2D accessibilitiesTransit = DoubleFactory2D.dense.descending(10, 10);
        DoubleMatrix1D accessibilityTransit = DoubleFactory1D.dense.make(10);

        SkimBasedAccessibility.aggregateAccessibilities(accessibilitiesAuto, accessibilitiesTransit, accessibilityAuto, accessibilityTransit, keys);

        Assert.assertEquals(5050., accessibilityAuto.zSum(), 0);
        Assert.assertEquals(4950., accessibilityTransit.zSum(), 0);
    }

    @Test
    public void testRegionalAccessibilities() {
        Region region1 = new RegionImpl(1);
        Region region2 = new RegionImpl(2);
        List<Region> regions = Arrays.asList(region1, region2);
        Zone zone1 = new ZoneImpl(1, 1, 1, region1);
        Zone zone2 = new ZoneImpl(2, 1, 1, region1);
        Zone zone3 = new ZoneImpl(3, 1 ,1, region2);
        region1.addZone(zone1);
        region1.addZone(zone2);
        region2.addZone(zone3);
        DoubleMatrix1D accessibilityAuto = DoubleFactory1D.dense.make(4);
        accessibilityAuto.assign(10);

        DoubleMatrix1D regionalAccessibility = SkimBasedAccessibility.calculateRegionalAccessibility(regions, accessibilityAuto);
        Assert.assertEquals(3, regionalAccessibility.size());
        Assert.assertEquals(10, regionalAccessibility.getQuick(region1.getId()), 0.);
        Assert.assertEquals(10, regionalAccessibility.getQuick(region2.getId()), 0.);
    }

    @Test
    public void testIntegration()  {
        Properties properties = SiloUtil.siloInitialization(Implementation.MARYLAND, "test/scenarios/annapolis/javaFiles/siloMstm.properties");

        SiloDataContainer dataContainer = SiloDataContainer.loadSiloDataContainer(Properties.get());
        GeoData geoData = dataContainer.getGeoData();
        geoData.readData();

        HouseholdDataManager hhManager = new HouseholdDataManager(dataContainer, PersonUtils.getFactory(), HouseholdUtil.getFactory());
        String householdFile = properties.main.baseDirectory + properties.householdData.householdFileName;
        householdFile += "_" + properties.main.startYear + ".csv";
        new DefaultHouseholdReader(hhManager).readData(householdFile);
        String personFile = properties.main.baseDirectory + properties.householdData.personFileName;
        personFile += "_" + properties.main.startYear + ".csv";
        new DefaultPersonReader(hhManager).readData(personFile);

        TravelTimeUtil.updateCarSkim((SkimTravelTimes) dataContainer.getTravelTimes(), 2000, Properties.get());
        TravelTimeUtil.updateTransitSkim((SkimTravelTimes) dataContainer.getTravelTimes(), 2000, Properties.get());

        SkimBasedAccessibility accessibility = new SkimBasedAccessibility(dataContainer);
        accessibility.updateHansenAccessibilities(2000);

        DoubleMatrix2D minTravelTimes = Matrices.doubleMatrix2D(geoData.getZones().values(), geoData.getRegions().values());

        for(Zone zone: geoData.getZones().values()) {
            for(Region region: geoData.getRegions().values()) {
                minTravelTimes.setQuick(zone.getZoneId(), region.getId(),
                		dataContainer.getTravelTimes().getTravelTimeToRegion(zone, region, Properties.get().transportModel.peakHour_s, TransportMode.car));
            }
        }

        DoubleMatrix1D accCar = Matrices.doubleMatrix1D(geoData.getZones().values());
        DoubleMatrix1D accTransit = Matrices.doubleMatrix1D(geoData.getZones().values());
        DoubleMatrix1D accRegions = Matrices.doubleMatrix1D(geoData.getRegions().values());

        for(Zone zone: geoData.getZones().values()) {
            accCar.setQuick(zone.getId(), accessibility.getAutoAccessibilityForZone(zone));
            accTransit.setQuick(zone.getId(), accessibility.getTransitAccessibilityForZone(zone));
        }

        for(Region region: geoData.getRegions().values()) {
            accRegions.setQuick(region.getId(), accessibility.getRegionalAccessibility(region));
        }

        Locale.setDefault(Locale.ENGLISH);

        try {
            new File("test/output/").mkdirs();
            MatrixVectorWriter writerZone2Region = new MatrixVectorWriter(new FileWriter("test/output/zone2regionTravelTimes.txt"));
            writerZone2Region.print(new DoubleFormatter().toString(minTravelTimes));
            writerZone2Region.flush();
            writerZone2Region.close();

            MatrixVectorWriter writerCar = new MatrixVectorWriter(new FileWriter("test/output/accessibilitiesCar.txt"));
            writerCar.printArray(accCar.toArray());
            writerCar.flush();
            writerCar.close();

            MatrixVectorWriter writerTransit = new MatrixVectorWriter(new FileWriter("test/output/accessibilitiesTransit.txt"));
            writerTransit.printArray(accTransit.toArray());
            writerTransit.flush();
            writerTransit.close();

            MatrixVectorWriter writerRegion = new MatrixVectorWriter(new FileWriter("test/output/accessibilitiesRegion.txt"));
            writerRegion.printArray(accRegions.toArray());
            writerRegion.flush();
            writerRegion.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        FileAssert.assertEquals("car accessibilities are different.", new File("test/input/accessibilitiesCar.txt"), new File("test/output/accessibilitiesCar.txt"));
        FileAssert.assertEquals("transit accessibilities are different.", new File("test/input/accessibilitiesTransit.txt"), new File("test/output/accessibilitiesTransit.txt"));
        FileAssert.assertEquals("region accessibilities are different.", new File("test/input/accessibilitiesRegion.txt"), new File("test/output/accessibilitiesRegion.txt"));
        FileAssert.assertEquals("zone 2 region travel times  are different.", new File("test/input/zone2regionTravelTimes.txt"), new File("test/output/zone2regionTravelTimes.txt"));
    }
}