package de.tum.bgu.msm.data;

import cern.colt.matrix.tdouble.DoubleMatrix1D;
import com.pb.common.datafile.TableDataSet;
import com.vividsolutions.jts.geom.Coordinate;
import de.tum.bgu.msm.Implementation;
import de.tum.bgu.msm.data.dwelling.DwellingType;
import de.tum.bgu.msm.utils.SiloUtil;
import de.tum.bgu.msm.container.SiloDataContainer;
import de.tum.bgu.msm.container.SiloModelContainer;
import de.tum.bgu.msm.data.dwelling.Dwelling;
import de.tum.bgu.msm.data.dwelling.DwellingImpl;
import de.tum.bgu.msm.data.household.Household;
import de.tum.bgu.msm.data.household.HouseholdUtil;
import de.tum.bgu.msm.data.job.Job;
import de.tum.bgu.msm.data.maryland.MstmZone;
import de.tum.bgu.msm.data.person.Person;
import de.tum.bgu.msm.data.school.School;
import de.tum.bgu.msm.properties.Properties;
import de.tum.bgu.msm.util.matrices.Matrices;
import org.apache.log4j.Logger;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Methods to summarize model results
 * Author: Rolf Moeckel, PB Albuquerque
 * Created on 23 February 2012 in Albuquerque
 **/


public class SummarizeData {
    private static final String DEVELOPMENT_FILE = "development"; ;
    static Logger logger = Logger.getLogger(SummarizeData.class);


    private static PrintWriter resultWriter;
    private static PrintWriter spatialResultWriter;

    private static PrintWriter resultWriterFinal;
    private static PrintWriter spatialResultWriterFinal;

    public static Boolean resultWriterReplicate = false;

    private static TableDataSet scalingControlTotals;
    private static int[] prestoRegionByTaz;
    private static final String RESULT_FILE_SPATIAL = "resultFileSpatial";
    private static final String RESULT_FILE = "resultFile";

    public static void openResultFile(Properties properties) {
        // open summary file

        String directory = properties.main.baseDirectory + "scenOutput/" + properties.main.scenarioName;
        resultWriter = SiloUtil.openFileForSequentialWriting(directory + "/" + RESULT_FILE +
                ".csv", properties.main.startYear != properties.main.implementation.BASE_YEAR);
        resultWriterFinal = SiloUtil.openFileForSequentialWriting(directory + "/" + RESULT_FILE + "_" + properties.main.endYear + ".csv", false);
    }


    public static void readScalingYearControlTotals() {
        // read file with control totals to scale synthetic population to exogenous assumptions for selected output years

        String fileName = Properties.get().main.baseDirectory + Properties.get().main.scalingControlTotals;
        scalingControlTotals = SiloUtil.readCSVfile(fileName);
        scalingControlTotals.buildIndex(scalingControlTotals.getColumnPosition("Zone"));
    }


    public static void resultFile(String action) {
        // handle summary file
        resultFile(action, true);
    }

    public static void resultFile(String action, Boolean writeFinal) {
        // handle summary file
        switch (action) {
            case "close":
                resultWriter.close();
                resultWriterFinal.close();
                break;
            default:
                resultWriter.println(action);
                if (resultWriterReplicate && writeFinal) resultWriterFinal.println(action);
                break;
        }
    }

    public static void resultFileSpatial(String action) {
        resultFileSpatial(action, true);
    }

    public static void resultFileSpatial(String action, Boolean writeFinal) {
        // handle summary file
        switch (action) {
            case "open":
                String directory = Properties.get().main.baseDirectory + "scenOutput/" + Properties.get().main.scenarioName;
                spatialResultWriter = SiloUtil.openFileForSequentialWriting(directory + "/" + RESULT_FILE_SPATIAL +
                        ".csv", Properties.get().main.startYear != Properties.get().main.implementation.BASE_YEAR);
                spatialResultWriterFinal = SiloUtil.openFileForSequentialWriting(directory + "/" + RESULT_FILE_SPATIAL + "_" + Properties.get().main.endYear + ".csv", false);
                break;
            case "close":
                spatialResultWriter.close();
                spatialResultWriterFinal.close();
                break;
            default:
                spatialResultWriter.println(action);
                if (resultWriterReplicate && writeFinal) spatialResultWriterFinal.println(action);
                break;
        }
    }

    public static void summarizeSpatially(int year, SiloModelContainer modelContainer, SiloDataContainer dataContainer) {
        // write out results by zone

        List<DwellingType> dwellingTypes = dataContainer.getRealEstateData().getDwellingTypes();
        String hd = "Year" + year + ",autoAccessibility,transitAccessibility,population,households,hhInc_<" + Properties.get().main.incomeBrackets[0];
        for (int inc = 0; inc < Properties.get().main.incomeBrackets.length; inc++) {
            hd = hd.concat(",hhInc_>" + Properties.get().main.incomeBrackets[inc]);
        }
        for (DwellingType dwellingType : dwellingTypes){
            hd = hd.concat("dd_" + dwellingType.toString());
        }

        hd = hd.concat(",availLand,avePrice,jobs,shWhite,shBlack,shHispanic,shOther");
        resultFileSpatial(hd);

        final int highestZonalId = dataContainer.getGeoData().getZones().keySet()
                .stream().mapToInt(Integer::intValue).max().getAsInt();
        int[][] dds = new int[dwellingTypes.size()][highestZonalId + 1];
        int[] prices = new int[highestZonalId + 1];
        int[] jobs = new int[highestZonalId + 1];
        int[] hhs = new int[highestZonalId + 1];
        int[][] hhInc = new int[Properties.get().main.incomeBrackets.length + 1][highestZonalId + 1];
        DoubleMatrix1D pop = getPopulationByZone(dataContainer);
        for (Household hh : dataContainer.getHouseholdData().getHouseholds()) {
            int zone = dataContainer.getRealEstateData().getDwelling(hh.getDwellingId()).getZoneId();
            int incGroup = hh.getHouseholdType().getIncomeCategory().ordinal();
            hhInc[incGroup][zone]++;
            hhs[zone]++;
        }
        for (Dwelling dd : dataContainer.getRealEstateData().getDwellings()) {
            dds[dataContainer.getRealEstateData().getDwellingTypes().indexOf(dd.getType())][dd.getZoneId()]++;
            prices[dd.getZoneId()] += dd.getPrice();
        }
        for (Job jj : dataContainer.getJobData().getJobs()) {
            jobs[jj.getZoneId()]++;
        }


        for (int taz : dataContainer.getGeoData().getZones().keySet()) {
            float avePrice = -1;
            int ddThisZone = 0;
            for (DwellingType dt : dwellingTypes) {
                ddThisZone += dds[dwellingTypes.indexOf(dt)][taz];
            }
            if (ddThisZone > 0) {
                avePrice = prices[taz] / ddThisZone;
            }
            double autoAcc = modelContainer.getAcc().getAutoAccessibilityForZone(taz);
            double transitAcc = modelContainer.getAcc().getTransitAccessibilityForZone(taz);
            double availLand = dataContainer.getRealEstateData().getAvailableCapacityForConstruction(taz);
//            Formatter f = new Formatter();
//            f.format("%d,%f,%f,%d,%d,%d,%f,%f,%d", taz, autoAcc, transitAcc, pop[taz], hhs[taz], dds[taz], availLand, avePrice, jobs[taz]);
            String txt = taz + "," + autoAcc + "," + transitAcc + "," + pop.getQuick(taz) + "," + hhs[taz];
            for (int inc = 0; inc <= Properties.get().main.incomeBrackets.length; inc++)
                txt = txt.concat("," + hhInc[inc][taz]);
            for (DwellingType dt : dwellingTypes){
                txt = txt.concat("," + dds[dwellingTypes.indexOf(dt)][taz]);
            }
            txt = txt.concat("," + availLand + "," + avePrice + "," + jobs[taz] + "," +
                    // todo: make the summary application specific, Munich does not work with these race categories
                    "0,0,0,0");
//                    modelContainer.getMove().getZonalRacialShare(taz, Race.white) + "," +
//                    modelContainer.getMove().getZonalRacialShare(taz, Race.black) + "," +
//                    modelContainer.getMove().getZonalRacialShare(taz, Race.hispanic) + "," +
//                    modelContainer.getMove().getZonalRacialShare(taz, Race.other));
//            String txt = f.toString();
            resultFileSpatial(txt);
        }
    }

    public static DoubleMatrix1D getPopulationByZone(SiloDataContainer dataContainer) {
        DoubleMatrix1D popByZone = Matrices.doubleMatrix1D(dataContainer.getGeoData().getZones().values());
        for (Household hh : dataContainer.getHouseholdData().getHouseholds()) {
            final int zone = dataContainer.getRealEstateData().getDwelling(hh.getDwellingId()).getZoneId();
            popByZone.setQuick(zone, popByZone.getQuick(zone) + hh.getHhSize());
        }
        return popByZone;
    }


    public static void scaleMicroDataToExogenousForecast(int year, SiloDataContainer dataContainer) {
        //TODO Will fail for new zones with 0 households and a projected growth. Could be an issue when modeling for Zones with transient existence.
        // scale synthetic population to exogenous forecast (for output only, scaled synthetic population is not used internally)

        if (!scalingControlTotals.containsColumn(("HH" + year))) {
            logger.warn("Could not find scaling targets to scale micro data to year " + year + ". No scaling completed.");
            return;
        }
        logger.info("Scaling synthetic population to exogenous forecast for year " + year + " (for output only, " +
                "scaled population is not used internally).");

        int artificialHhId = dataContainer.getHouseholdData().getHighestHouseholdIdInUse() + 1;
        int artificialPpId = dataContainer.getHouseholdData().getHighestPersonIdInUse() + 1;

        // calculate how many households need to be created or deleted in every zone
        final int highestId = dataContainer.getGeoData().getZones().keySet()
                .stream().mapToInt(Integer::intValue).max().getAsInt();
        int[] changeOfHh = new int[highestId + 1];
        HashMap<Integer, int[]> hhByZone = dataContainer.getHouseholdData().getHouseholdsByZone();
        for (int zone : dataContainer.getGeoData().getZones().keySet()) {
            int hhs = 0;
            if (hhByZone.containsKey(zone)) hhs = hhByZone.get(zone).length;
            changeOfHh[zone] =
                    (int) scalingControlTotals.getIndexedValueAt(zone, ("HH" + year)) - hhs;
        }

        PrintWriter pwh = SiloUtil.openFileForSequentialWriting(Properties.get().main.scaledMicroDataHh + year + ".csv", false);
        pwh.println("id,dwelling,zone,hhSize,autos");
        PrintWriter pwp = SiloUtil.openFileForSequentialWriting(Properties.get().main.scaledMicroDataPp + year + ".csv", false);
        pwp.println("id,hhID,age,gender,race,occupation,driversLicense,workplace,income");

        HouseholdDataManager householdData = dataContainer.getHouseholdData();
        for (int zone : dataContainer.getGeoData().getZones().keySet()) {
            if (hhByZone.containsKey(zone)) {
                int[] hhInThisZone = hhByZone.get(zone);
                int[] selectedHH = new int[hhInThisZone.length];
                if (changeOfHh[zone] > 0) {          // select households to duplicate (draw with replacement)
                    for (int i = 0; i < changeOfHh[zone]; i++) {
                        int selected = SiloUtil.select(hhInThisZone.length) - 1;
                        selectedHH[selected]++;
                    }
                } else if (changeOfHh[zone] < 0) {   // select households to delete (draw without replacement)
                    float[] prob = new float[hhInThisZone.length];
                    SiloUtil.setArrayToValue(prob, 1);
                    for (int i = 0; i < Math.abs(changeOfHh[zone]); i++) {
                        int selected = SiloUtil.select(prob);
                        selectedHH[selected] = 1;
                        prob[selected] = 0;
                    }
                }

                // write out households and duplicate (if changeOfHh > 0) or delete (if changeOfHh < 0) selected households
                for (int i = 0; i < hhInThisZone.length; i++) {
                    Household hh = householdData.getHouseholdFromId(hhInThisZone[i]);
                    if (changeOfHh[zone] > 0) {
                        // write out original household
                        pwh.print(hh.getId());
                        pwh.print(",");
                        pwh.print(hh.getDwellingId());
                        pwh.print(",");
                        pwh.print(zone);
                        pwh.print(",");
                        pwh.print(hh.getHhSize());
                        pwh.print(",");
                        pwh.println(hh.getAutos());
                        for (Person pp : hh.getPersons().values()) {
                            pwp.print(pp.getId());
                            pwp.print(",");
                            pwp.print(pp.getHousehold().getId());
                            pwp.print(",");
                            pwp.print(pp.getAge());
                            pwp.print(",");
                            pwp.print(pp.getGender().getCode());
                            pwp.print(",");
                            pwp.print(pp.getRace());
                            pwp.print(",");
                            pwp.print(pp.getOccupation());
                            pwp.print(",0,");
                            pwp.print(pp.getJobId());
                            pwp.print(",");
                            pwp.println(pp.getIncome());
                        }
                        // duplicate household if selected
                        if (selectedHH[i] > 0) {    // household to be repeated for this output file
                            for (int repeat = 0; repeat < selectedHH[i]; repeat++) {
                                pwh.print(artificialHhId);
                                pwh.print(",");
                                pwh.print(hh.getDwellingId());
                                pwh.print(",");
                                pwh.print(zone);
                                pwh.print(",");
                                pwh.print(hh.getHhSize());
                                pwh.print(",");
                                pwh.println(hh.getAutos());
                                for (Person pp : hh.getPersons().values()) {
                                    pwp.print(artificialPpId);
                                    pwp.print(",");
                                    pwp.print(artificialHhId);
                                    pwp.print(",");
                                    pwp.print(pp.getAge());
                                    pwp.print(",");
                                    pwp.print(pp.getGender().getCode());
                                    pwp.print(",");
                                    pwp.print(pp.getRace());
                                    pwp.print(",");
                                    pwp.print(pp.getOccupation());
                                    pwp.print(",0,");
                                    pwp.print(pp.getJobId());
                                    pwp.print(",");
                                    pwp.println(pp.getIncome());
                                    artificialPpId++;
                                }
                                artificialHhId++;
                            }
                        }
                    } else if (changeOfHh[zone] < 0) {
                        if (selectedHH[i] == 0) {    // household to be kept (selectedHH[i] == 1 for households to be deleted)
                            pwh.print(hh.getId());
                            pwh.print(",");
                            pwh.print(hh.getDwellingId());
                            pwh.print(",");
                            pwh.print(zone);
                            pwh.print(",");
                            pwh.print(hh.getHhSize());
                            pwh.print(",");
                            pwh.println(hh.getAutos());
                            for (Person pp : hh.getPersons().values()) {
                                pwp.print(pp.getId());
                                pwp.print(",");
                                pwp.print(pp.getHousehold().getId());
                                pwp.print(",");
                                pwp.print(pp.getAge());
                                pwp.print(",");
                                pwp.print(pp.getGender().getCode());
                                pwp.print(",");
                                pwp.print(pp.getRace());
                                pwp.print(",");
                                pwp.print(pp.getOccupation());
                                pwp.print(",0,");
                                pwp.print(pp.getJobId());
                                pwp.print(",");
                                pwp.println(pp.getIncome());
                            }
                        }
                    }
                }
            } else {
                if (scalingControlTotals.getIndexedValueAt(zone, ("HH" + year)) > 0)
                    logger.warn("SILO has no households in zone " +
                            zone + " that could be duplicated to match control total of " +
                            scalingControlTotals.getIndexedValueAt(zone, ("HH" + year)) + ".");
            }
        }
        pwh.close();
        pwp.close();
    }


    public static void summarizeHousing(int year, SiloDataContainer dataContainer) {
        // summarize housing data for housing environmental impact calculations

        if (!SiloUtil.containsElement(Properties.get().main.bemModelYears, year)) return;
        String directory = Properties.get().main.baseDirectory + "scenOutput/" + Properties.get().main.scenarioName + "/bem/";
        SiloUtil.createDirectoryIfNotExistingYet(directory);

        String fileName = (directory + Properties.get().main.housingEnvironmentImpactFile + "_" + year + ".csv");

        PrintWriter pw = SiloUtil.openFileForSequentialWriting(fileName, false);
        pw.println("id,zone,type,size,yearBuilt,occupied");
        for (Dwelling dd : dataContainer.getRealEstateData().getDwellings()) {
            pw.print(dd.getId());
            pw.print(",");
            pw.print(dd.getZoneId());
            pw.print(",");
            pw.print(dd.getType());
            pw.print(",");
            pw.print(dd.getBedrooms());
            pw.print(",");
            pw.print(dd.getYearBuilt());
            pw.print(",");
            pw.println((dd.getResidentId() == -1));
        }
        pw.close();
    }


    public static void writeOutSyntheticPopulation(int year, SiloDataContainer dataContainer) {
        String relativePathToHhFile;
        String relativePathToPpFile;
        String relativePathToDdFile;
        String relativePathToJjFile;
        String relativePathToSsFile;
        if (year == Properties.get().main.implementation.BASE_YEAR) {
            //printing out files for the synthetic population at the base year - store as input files
            relativePathToHhFile = Properties.get().householdData.householdFileName;
            relativePathToPpFile = Properties.get().householdData.personFileName;
            relativePathToDdFile = Properties.get().realEstate.dwellingsFileName;
            relativePathToJjFile = Properties.get().jobData.jobsFileName;
            relativePathToSsFile = Properties.get().schoolData.schoolsFileName;
        } else {
            //printing out files for the synthetic population at any other year - store as output files
            relativePathToHhFile = Properties.get().householdData.householdFinalFileName;
            relativePathToPpFile = Properties.get().householdData.personFinalFileName;
            relativePathToDdFile = Properties.get().realEstate.dwellingsFinalFileName;
            relativePathToJjFile = Properties.get().jobData.jobsFinalFileName;
            relativePathToSsFile = Properties.get().schoolData.schoolsFinalFileName;
        }

        String filehh = Properties.get().main.baseDirectory + relativePathToHhFile + "_" + year + ".csv";
        writeHouseholds(filehh, dataContainer);

        String filepp = Properties.get().main.baseDirectory + relativePathToPpFile + "_" + year + ".csv";
        writePersons(filepp, dataContainer);

        String filedd = Properties.get().main.baseDirectory + relativePathToDdFile + "_" + year + ".csv";
        writeDwellings(filedd, dataContainer);

        String filejj = Properties.get().main.baseDirectory + relativePathToJjFile + "_" + year + ".csv";
        writeJobs(filejj, dataContainer);

        //todo do not print schools if implementation is not Munich (carlos)
        if(Properties.get().main.implementation.equals(Implementation.MUNICH)) {
            String filess = Properties.get().main.baseDirectory + relativePathToSsFile + "_" + year + ".csv";
            writeSchools(filess, dataContainer);
        }
    }

    private static void writeSchools(String filess, SiloDataContainer dataContainer) {
        PrintWriter pws = SiloUtil.openFileForSequentialWriting(filess, false);
        pws.print("id,zone,type,capacity,occupancy");
        if (Properties.get().main.implementation.equals(Implementation.MUNICH)) {
            pws.print(",");
            pws.print("coordX");
            pws.print(",");
            pws.print("coordY");
            pws.print(",");
            pws.print("startTime");
            pws.print(",");
            pws.print("duration");
        }
        pws.println();
        for (School ss : dataContainer.getSchoolData().getSchools()) {
            pws.print(ss.getId());
            pws.print(",");
            pws.print(ss.getZoneId());
            pws.print(",");
            pws.print(ss.getType());
            pws.print(",");
            pws.print(ss.getCapacity());
            pws.print(",");
            pws.print(ss.getOccupancy());
            if (Properties.get().main.implementation.equals(Implementation.MUNICH)) {
                try {
                    Coordinate coordinate = ((MicroLocation) ss).getCoordinate();
                    pws.print(",");
                    pws.print(coordinate.x);
                    pws.print(",");
                    pws.print(coordinate.y);
                } catch (Exception e) {
                    pws.print(",");
                    pws.print(0);
                    pws.print(",");
                    pws.print(0);
                }
                pws.print(",");
                pws.print(ss.getStartTimeInSeconds());
                pws.print(",");
                pws.print(ss.getStudyTimeInSeconds());
            }
            pws.println();
//            if (ss.getId() == SiloUtil.trackSs) {
//                SiloUtil.trackingFile("Writing ss " + ss.getId() + " to micro data file.");
//                SiloUtil.trackWriter.println(ss.toString());
//            }
        }
        pws.close();

    }

    private static void writeHouseholds(String filehh, SiloDataContainer dataContainer) {

        logger.info("  Writing household file to " + filehh);
        PrintWriter pwh = SiloUtil.openFileForSequentialWriting(filehh, false);
        pwh.println("id,dwelling,zone,hhSize,autos");
        for (Household hh : dataContainer.getHouseholdData().getHouseholds()) {
            if (hh.getId() == SiloUtil.trackHh) {
                SiloUtil.trackingFile("Writing hh " + hh.getId() + " to micro data file.");
                SiloUtil.trackWriter.println(hh.toString());
            }
            pwh.print(hh.getId());
            pwh.print(",");
            pwh.print(hh.getDwellingId());
            pwh.print(",");
            int zone = -1;
            Dwelling dwelling = dataContainer.getRealEstateData().getDwelling(hh.getDwellingId());
            if (dwelling != null) {
                zone = dwelling.getZoneId();
            }
            pwh.print(zone);
            pwh.print(",");
            pwh.print(hh.getHhSize());
            pwh.print(",");
            pwh.println(hh.getAutos());
        }
        pwh.close();


    }

    private static void writePersons(String filepp, SiloDataContainer dataContainer) {

        logger.info("  Writing person file to " + filepp);
        PrintWriter pwp = SiloUtil.openFileForSequentialWriting(filepp, false);
        pwp.print("id,hhid,age,gender,relationShip,race,occupation,driversLicense,workplace,income");
        if (Properties.get().main.implementation.equals(Implementation.MUNICH)) {
            pwp.print(",");
            pwp.print("nationality");
            pwp.print(",");
            pwp.print("education");
            pwp.print(",");
            pwp.print("homeZone");
            pwp.print(",");
            pwp.print("workZone");
            pwp.print(",");
            pwp.print("schoolDE");
            pwp.print(",");
            pwp.print("schoolTAZ");
            pwp.print(",");
            pwp.print("disability");
            pwp.print(",");
            pwp.print("schoolId");
            pwp.print(",");
            pwp.print("schoolCoordX");
            pwp.print(",");
            pwp.print("schoolCoordY");
        }
        pwp.println();
        for (Person pp : dataContainer.getHouseholdData().getPersons()) {
            pwp.print(pp.getId());
            pwp.print(",");
            pwp.print(pp.getHousehold().getId());
            pwp.print(",");
            pwp.print(pp.getAge());
            pwp.print(",");
            pwp.print(pp.getGender().getCode());
            pwp.print(",\"");
            String role = pp.getRole().toString();
            pwp.print(role);
            pwp.print("\",\"");
            pwp.print(pp.getRace());
            pwp.print("\",");
            pwp.print(pp.getOccupation().getCode());
            pwp.print(",");
            if (Properties.get().main.implementation.equals(Implementation.MUNICH)) {
                pwp.print(pp.hasDriverLicense());
            } else {
                pwp.print(0);
            }
            pwp.print(",");
            pwp.print(pp.getJobId());
            pwp.print(",");
            pwp.print(pp.getIncome());
            if (Properties.get().main.implementation.equals(Implementation.MUNICH)) {
                pwp.print(",");
                pwp.print(pp.getNationality().toString());
                pwp.print(",");
                pwp.print(pp.getEducationLevel());
                pwp.print(",");
                Dwelling dd = dataContainer.getRealEstateData().getDwelling(pp.getHousehold().getDwellingId());
                pwp.print(dd.getZoneId());
                pwp.print(",");
                int jobTaz = dataContainer.getJobData().getJobFromId(pp.getJobId()).getZoneId();
                pwp.print(jobTaz);
                pwp.print(",");
                Coordinate schoolCoord = pp.getSchoolLocation();
                pwp.print(pp.getSchoolType());
                pwp.print(",");
                try {
                    pwp.print(pp.getSchoolPlace());
                } catch (NullPointerException e){
                    pwp.print(0);
                }
                pwp.print(",");
                pwp.print(0);
                pwp.print(",");
                pwp.print(pp.getSchoolId());
                pwp.print(",");
                try {
                    pwp.print(schoolCoord.x);
                    pwp.print(",");
                    pwp.print(schoolCoord.y);
                } catch (NullPointerException e) {
                    pwp.print(0);
                    pwp.print(",");
                    pwp.print(0);
                }
            }
            pwp.println();


            if (pp.getId() == SiloUtil.trackPp) {
                SiloUtil.trackingFile("Writing pp " + pp.getId() + " to micro data file.");
                SiloUtil.trackWriter.println(pp.toString());
            }
        }
        pwp.close();

    }

    private static void writeDwellings(String filedd, SiloDataContainer dataContainer) {
        logger.info("  Writing dwelling file to " + filedd);

        PrintWriter pwd = SiloUtil.openFileForSequentialWriting(filedd, false);
        pwd.print("id,zone,type,hhID,bedrooms,quality,monthlyCost,restriction,yearBuilt");
        if (Properties.get().main.implementation.equals(Implementation.MUNICH)) {
            pwd.print(",");
            pwd.print("floor");
            pwd.print(",");
            pwd.print("building");
            pwd.print(",");
            pwd.print("year");
            pwd.print(",");
            pwd.print("usage");
            pwd.print(",");
            pwd.print("coordX");
            pwd.print(",");
            pwd.print("coordY");
        }
        pwd.println();

        for (Dwelling dd : dataContainer.getRealEstateData().getDwellings()) {
            pwd.print(dd.getId());
            pwd.print(",");
            pwd.print(dd.getZoneId());
            pwd.print(",\"");
            pwd.print(dd.getType());
            pwd.print("\",");
            pwd.print(dd.getResidentId());
            pwd.print(",");
            pwd.print(dd.getBedrooms());
            pwd.print(",");
            pwd.print(dd.getQuality());
            pwd.print(",");
            pwd.print(dd.getPrice());
            pwd.print(",");
            pwd.print(dd.getRestriction());
            pwd.print(",");
            pwd.print(dd.getYearBuilt());
            if (Properties.get().main.implementation.equals(Implementation.MUNICH)) {
                pwd.print(",");
                pwd.print(dd.getFloorSpace());
                pwd.print(",");
                pwd.print(dd.getBuildingSize());
                pwd.print(",");
                pwd.print(dd.getYearConstructionDE());
                pwd.print(",");
                pwd.print(dd.getUsage());
                pwd.print(",");
                try {
                    pwd.print(((DwellingImpl) dd).getCoordinate().x);
                    pwd.print(",");
                    pwd.print(((DwellingImpl) dd).getCoordinate().y);
                } catch (NullPointerException e) {
                    pwd.print(0);
                    pwd.print(",");
                    pwd.print(0);
                }
            }
            pwd.println();
            if (dd.getId() == SiloUtil.trackDd) {
                SiloUtil.trackingFile("Writing dd " + dd.getId() + " to micro data file.");
                SiloUtil.trackWriter.println(dd.toString());
            }
        }
        pwd.close();
    }

    private static void writeJobs(String filejj, SiloDataContainer dataContainer) {
        PrintWriter pwj = SiloUtil.openFileForSequentialWriting(filejj, false);
        pwj.print("id,zone,personId,type");
        if (Properties.get().main.implementation.equals(Implementation.MUNICH)) {
            pwj.print(",");
            pwj.print("coordX");
            pwj.print(",");
            pwj.print("coordY");
            pwj.print(",");
            pwj.print("startTime");
            pwj.print(",");
            pwj.print("duration");
        }
        pwj.println();
        for (Job jj : dataContainer.getJobData().getJobs()) {
            pwj.print(jj.getId());
            pwj.print(",");
            pwj.print(jj.getZoneId());
            pwj.print(",");
            pwj.print(jj.getWorkerId());
            pwj.print(",\"");
            pwj.print(jj.getType());
            pwj.print("\"");
            if (Properties.get().main.implementation.equals(Implementation.MUNICH)) {
                try {
                    Coordinate coordinate = ((MicroLocation) jj).getCoordinate();
                    pwj.print(",");
                    pwj.print(coordinate.x);
                    pwj.print(",");
                    pwj.print(coordinate.y);
                } catch (Exception e) {
                    pwj.print(",");
                    pwj.print(0);
                    pwj.print(",");
                    pwj.print(0);
                }
                pwj.print(",");
                pwj.print(jj.getStartTimeInSeconds());
                pwj.print(",");
                pwj.print(jj.getWorkingTimeInSeconds());
            }
            pwj.println();
            if (jj.getId() == SiloUtil.trackJj) {
                SiloUtil.trackingFile("Writing jj " + jj.getId() + " to micro data file.");
                SiloUtil.trackWriter.println(jj.toString());
            }
        }
        pwj.close();


    }

    public static void summarizeAutoOwnershipByCounty(Accessibility accessibility, SiloDataContainer dataContainer) {
        // This calibration function summarized households by auto-ownership and quits

        PrintWriter pwa = SiloUtil.openFileForSequentialWriting("autoOwnershipA.csv", false);
        pwa.println("hhSize,workers,income,transit,density,autos");
        int[][] autos = new int[4][60000];
        final RealEstateDataManager realEstate = dataContainer.getRealEstateData();
        final GeoData geoData = dataContainer.getGeoData();
        final JobDataManager jobData = dataContainer.getJobData();
        final HouseholdDataManager householdData = dataContainer.getHouseholdData();
        for (Household hh : householdData.getHouseholds()) {
            int autoOwnership = hh.getAutos();
            int zone = -1;
            Dwelling dwelling = realEstate.getDwelling(hh.getDwellingId());
            if (dwelling != null) {
                zone = dwelling.getZoneId();
            }
            int county = ((MstmZone) geoData.getZones().get(zone)).getCounty().getId();
            autos[autoOwnership][county]++;
            pwa.println(hh.getHhSize() + "," + HouseholdUtil.getNumberOfWorkers(hh) + "," + HouseholdUtil.getHhIncome(hh) + "," +
                    accessibility.getTransitAccessibilityForZone(zone) + "," + jobData.getJobDensityInZone(zone) + "," + hh.getAutos());
        }
        pwa.close();

        PrintWriter pw = SiloUtil.openFileForSequentialWriting("autoOwnershipB.csv", false);
        pw.println("County,0autos,1auto,2autos,3+autos");
        for (int county = 0; county < 60000; county++) {
            int sm = 0;
            for (int a = 0; a < 4; a++) sm += autos[a][county];
            if (sm > 0)
                pw.println(county + "," + autos[0][county] + "," + autos[1][county] + "," + autos[2][county] + "," + autos[3][county]);
        }
        pw.close();
        logger.info("Summarized auto ownership and quit.");
        System.exit(0);
    }

    public static void preparePrestoSummary(GeoData geoData) {

        String prestoZoneFile = Properties.get().main.baseDirectory + Properties.get().main.prestoZoneFile;
        TableDataSet regionDefinition = SiloUtil.readCSVfile(prestoZoneFile);
        regionDefinition.buildIndex(regionDefinition.getColumnPosition("aggFips"));

        final int highestId = geoData.getZones().keySet().stream().mapToInt(Integer::intValue).max().getAsInt();
        prestoRegionByTaz = new int[highestId + 1];
        Arrays.fill(prestoRegionByTaz, -1);
        for (Zone zone : geoData.getZones().values()) {
            try {
                prestoRegionByTaz[zone.getZoneId()] =
                        (int) regionDefinition.getIndexedValueAt(((MstmZone) zone).getCounty().getId(), "presto");
            } catch (Exception e) {
                prestoRegionByTaz[zone.getZoneId()] = -1;
            }
        }
    }

    public static void summarizePrestoRegion(int year, SiloDataContainer dataContainer) {
        // summarize housing costs by income group in SILO region

        String fileName = (Properties.get().main.baseDirectory + "scenOutput/" + Properties.get().main.scenarioName + "/" +
                Properties.get().main.prestoSummaryFile + ".csv");
        PrintWriter pw = SiloUtil.openFileForSequentialWriting(fileName, year != Properties.get().main.implementation.BASE_YEAR);
        pw.println(year + ",Housing costs by income group");
        pw.print("Income");
        for (int i = 0; i < 10; i++) pw.print(",rent_" + ((i + 1) * 250));
        pw.println(",averageRent");
        int[][] rentByIncome = new int[10][10];
        int[] rents = new int[10];
        RealEstateDataManager realEstate = dataContainer.getRealEstateData();
        for (Household hh : dataContainer.getHouseholdData().getHouseholds()) {
            int zone = -1;
            Dwelling dwelling = realEstate.getDwelling(hh.getDwellingId());
            if (dwelling != null) {
                zone = dwelling.getZoneId();
            }
            if (prestoRegionByTaz[zone] > 0) {
                int hhInc = HouseholdUtil.getHhIncome(hh);
                int rent = realEstate.getDwelling(hh.getDwellingId()).getPrice();
                int incCat = Math.min((hhInc / 10000), 9);
                int rentCat = Math.min((rent / 250), 9);
                rentByIncome[incCat][rentCat]++;
                rents[incCat] += rent;
            }
        }
        for (int i = 0; i < 10; i++) {
            pw.print(String.valueOf((i + 1) * 10000));
            int countThisIncome = 0;
            for (int r = 0; r < 10; r++) {
                pw.print("," + rentByIncome[i][r]);
                countThisIncome += rentByIncome[i][r];
            }
            pw.println("," + rents[i] / countThisIncome);
        }
    }

    public static void summarizeCarOwnershipByMunicipality(TableDataSet zonalData, SiloDataContainer dataContainer) {
        // This calibration function summarizes household auto-ownership by municipality and quits

        SiloUtil.createDirectoryIfNotExistingYet("microData/interimFiles/");
        PrintWriter pwa = SiloUtil.openFileForSequentialWriting("microData/interimFiles/carOwnershipByHh.csv", false);
        pwa.println("license,workers,income,logDistanceToTransit,areaType,autos");
        int[][] autos = new int[4][10000000];
        RealEstateDataManager realEstate = dataContainer.getRealEstateData();
        for (Household hh : dataContainer.getHouseholdData().getHouseholds()) {
            int autoOwnership = hh.getAutos();
            int zone = -1;
            Dwelling dwelling = realEstate.getDwelling(hh.getDwellingId());
            if (dwelling != null) {
                zone = dwelling.getZoneId();
            }
            int municipality = (int) zonalData.getIndexedValueAt(zone, "ID_city");
            int distance = (int) Math.log(zonalData.getIndexedValueAt(zone, "distanceToTransit"));
            int area = (int) zonalData.getIndexedValueAt(zone, "BBSR");
            autos[autoOwnership][municipality]++;
            pwa.println(HouseholdUtil.getHHLicenseHolders(hh) + "," + HouseholdUtil.getNumberOfWorkers(hh) + "," + HouseholdUtil.getHhIncome(hh) + "," +
                    distance + "," + area + "," + hh.getAutos());
        }
        pwa.close();

        PrintWriter pw = SiloUtil.openFileForSequentialWriting("microData/interimFiles/carOwnershipByMunicipality.csv", false);
        pw.println("Municipality,0autos,1auto,2autos,3+autos");
        for (int municipality = 0; municipality < 10000000; municipality++) {
            int sm = 0;
            for (int a = 0; a < 4; a++) sm += autos[a][municipality];
            if (sm > 0)
                pw.println(municipality + "," + autos[0][municipality] + "," + autos[1][municipality] + "," + autos[2][municipality] + "," + autos[3][municipality]);
        }
        pw.close();

        logger.info("Summarized initial auto ownership");
    }

    public static void writeOutDevelopmentFile(SiloDataContainer dataContainer) {
        // write out development capacity file to allow model run to be continued from this point later


        String baseDirectory = Properties.get().main.baseDirectory;
        String scenarioName = Properties.get().main.scenarioName;
        int endYear = Properties.get().main.endYear;

        String capacityFileName = baseDirectory + "scenOutput/" + scenarioName + "/" +
                DEVELOPMENT_FILE + "_" + endYear + ".csv";

        PrintWriter pw = SiloUtil.openFileForSequentialWriting(capacityFileName, false);
        StringBuilder builder = new StringBuilder();
        builder.append("Zone,");
        List<DwellingType> dwellingTypes = dataContainer.getRealEstateData().getDwellingTypes();
        for (DwellingType dwellingType : dwellingTypes) {
            builder.append(dwellingType.toString()).append(",");
        }
        builder.append("DevCapacity,DevLandUse");
        pw.println(builder);

        for (Zone zone : dataContainer.getGeoData().getZones().values()) {
            builder = new StringBuilder();
            builder.append(zone.getId()).append(",");
            Development development = zone.getDevelopment();
            for (DwellingType dwellingType : dwellingTypes) {
                builder.append(development.isThisDwellingTypeAllowed(dwellingType)?1:0).append(",");
            }
            builder.append(development.getDwellingCapacity()).append(",").append(development.getDevelopableArea());
            pw.println(builder);
        }
        pw.close();

    }
}
