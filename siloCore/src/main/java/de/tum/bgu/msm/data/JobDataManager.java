/*
 * Copyright  2005 PB Consult Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package de.tum.bgu.msm.data;

import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Multiset;
import com.pb.common.datafile.TableDataSet;
import de.tum.bgu.msm.container.SiloDataContainer;
import de.tum.bgu.msm.properties.Properties;
import de.tum.bgu.msm.Implementation;
import de.tum.bgu.msm.SiloUtil;
import de.tum.bgu.msm.data.jobTypes.munich.MunichJobType;
import de.tum.bgu.msm.events.IssueCounter;
import de.tum.bgu.msm.models.accessibility.Accessibility;
import de.tum.bgu.msm.models.accessibility.SkimBasedAccessibility;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.TransportMode;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Keeps data of dwellings and non-residential floorspace
 * Author: Rolf Moeckel, PB Albuquerque
 * Created on 22 February 2013 in Rhede
 **/

public class JobDataManager {
    static Logger logger = Logger.getLogger(JobDataManager.class);

    private final GeoData geoData;
    private final Map<Integer, Job> jobs = new ConcurrentHashMap<>();
    private final SiloDataContainer data;

    private int highestJobIdInUse;
    private int[][] vacantJobsByRegion;
    private int[] vacantJobsByRegionPos;
    private int numberOfStoredVacantJobs;
    private final Map<Integer, Double> zonalJobDensity;


    public JobDataManager(SiloDataContainer data) {
        this.data = data;
        this.geoData = data.getGeoData();
        this.zonalJobDensity = new HashMap<>();
    }

    public Job createJob(int id, Location location, int workerId, String type) {
        Job job = new Job(id, location, workerId, type);
        this.jobs.put(id, job);
        return job;
    }

    public Job getJobFromId(int jobId) {
        return jobs.get(jobId);
    }

    public void saveJobs(Job[] jjs) {
        for (Job jj : jjs) jobs.put(jj.getId(), jj);
    }

    public int getJobCount() {
        return jobs.size();
    }

    public Collection<Job> getJobs() {
        return jobs.values();
    }

    public Set<Integer> getJobMapIDs() {
        return jobs.keySet();
    }

    public void removeJob(int id) {
        jobs.remove(id);
    }

    public void fillMitoZoneEmployees(Map<Integer, MitoZone> zones) {

        for (Job jj : jobs.values()) {
            final MitoZone zone = zones.get(jj.determineZoneId());
            final String type = jj.getType().toUpperCase();
            try {
                de.tum.bgu.msm.data.jobTypes.JobType mitoJobType = null;
                switch (Properties.get().main.implementation) {
                    case MUNICH:
                        mitoJobType = MunichJobType.valueOf(type);
                        break;
                    default:
                        logger.error("Implementation " + Properties.get().main.implementation + " is not yet supported by MITO", new IllegalArgumentException());
                }
                zone.addEmployeeForType(mitoJobType);
            } catch (IllegalArgumentException e) {
                logger.warn("Job type " + type + " not defined for MITO implementation: " + Properties.get().main.implementation);
            }
        }
    }


    public void readJobs(de.tum.bgu.msm.properties.Properties properties) {
        new JobType(properties.jobData.jobTypes);
        boolean readBin = properties.jobData.readBinaryJobFile;
        if (readBin) {
            readBinaryJobDataObjects();
        } else {
            readJobData(properties);
        }
        setHighestJobId();
    }


    private void readJobData(de.tum.bgu.msm.properties.Properties properties) {
        logger.info("Reading job micro data from ascii file");

        int year = Properties.get().main.startYear;
        String fileName = properties.main.baseDirectory + properties.jobData.jobsFileName;
        fileName += "_" + year + ".csv";

        String recString = "";
        int recCount = 0;
        try {
            BufferedReader in = new BufferedReader(new FileReader(fileName));
            recString = in.readLine();

            // read header
            String[] header = recString.split(",");
            int posId = SiloUtil.findPositionInArray("id", header);
            int posZone = SiloUtil.findPositionInArray("zone", header);
            int posWorker = SiloUtil.findPositionInArray("personId", header);
            int posType = SiloUtil.findPositionInArray("type", header);

            int posCoordX = -1;
            int posCoordY = -1;
            if(Properties.get().main.implementation == Implementation.MUNICH) {
                posCoordX = SiloUtil.findPositionInArray("CoordX", header);
                posCoordY = SiloUtil.findPositionInArray("CoordY", header);
            }


            // read line
            while ((recString = in.readLine()) != null) {
                recCount++;
                String[] lineElements = recString.split(",");
                int id = Integer.parseInt(lineElements[posId]);
                int zoneId = Integer.parseInt(lineElements[posZone]);
                Location location;
                Zone zone = geoData.getZones().get(zoneId);
                int worker = Integer.parseInt(lineElements[posWorker]);
                String type = lineElements[posType].replace("\"", "");

                //TODO: remove it when we implement interface
                if (Properties.get().main.implementation == Implementation.MUNICH) {
                	location = new MicroLocation(Double.parseDouble(lineElements[posCoordX]), Double.parseDouble(lineElements[posCoordY]), zone);
                } else {
                	location = zone;
                }
                Job jj = createJob(id, location, worker, type);

                if (id == SiloUtil.trackJj) {
                    SiloUtil.trackWriter.println("Read job with following attributes from " + fileName);
                    SiloUtil.trackWriter.println(jobs.get(id).toString());
                }
            }
        } catch (IOException e) {
            logger.fatal("IO Exception caught reading synpop job file: " + fileName);
            logger.fatal("recCount = " + recCount + ", recString = <" + recString + ">");
        }
        logger.info("Finished reading " + recCount + " jobs.");
    }


    public void writeBinaryJobDataObjects() {
        // Store job object data in binary file

        String fileName = Properties.get().main.baseDirectory + Properties.get().jobData.binaryJobsFileName;
        logger.info("  Writing job data to binary file.");
        Object[] data = jobs.values().toArray(new Job[]{});
        try {
            File fl = new File(fileName);
            ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(fl));
            out.writeObject(data);
            out.close();
        } catch (Exception e) {
            logger.error("Error saving to binary file " + fileName + ". Object not saved.\n" + e);
        }
    }


    private void readBinaryJobDataObjects() {
        // read jobs from binary file
        String fileName = Properties.get().main.baseDirectory + Properties.get().jobData.binaryJobsFileName;
        logger.info("Reading job data from binary file.");
        try {
            ObjectInputStream in = new ObjectInputStream(new FileInputStream(new File(fileName)));
            Object[] data = (Object[]) in.readObject();
            saveJobs((Job[]) data[0]);
        } catch (Exception e) {
            logger.error("Error reading from binary file " + fileName + ". Object not read.\n" + e);
        }
        logger.info("Finished reading " + jobs.size() + " jobs.");
    }


    public void setHighestJobId() {
        // identify highest job ID in use
        highestJobIdInUse = 0;
        for (int id : jobs.keySet()) {
            highestJobIdInUse = Math.max(highestJobIdInUse, id);
        }
    }


    public int getNextJobId() {
        // increase highestJobIdInUse by 1 and return value
        return ++highestJobIdInUse;
    }

    public List<Integer> getNextJobIds(int amount) {
        // increase highestJobIdInUse by 1 and return value
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < amount; i++) {
            ids.add(++highestJobIdInUse);
        }
        return ids;
    }


    public void calculateEmploymentForecast() {

        TableDataSet jobs;
        try {
            final String filename = Properties.get().main.baseDirectory + "/" + Properties.get().jobData.jobControlTotalsFileName;
            jobs = SiloUtil.readCSVfile(filename);
        } catch (Exception ee) {
            throw new RuntimeException(ee);
        }
        jobs.buildIndex(jobs.getColumnPosition("SMZ"));
        new JobType(Properties.get().jobData.jobTypes);

        //read the headers
        String[] labels = jobs.getColumnLabels();
        String[] jobTypes = JobType.getJobTypes();
        List<String> years = new ArrayList<>();

        //find the years that are defined in the job forecast
        String jobTypeName = jobTypes[0];
        for (String label : labels) {
            if (label.contains(jobTypeName)) {
                String year = (label.substring(jobTypeName.length(), label.length()));
                if (!years.contains(year)) {
                    years.add(year);
                }
            }

        }
        //proof the rest of job types are in the file
        for (int i = 1; i < jobTypes.length; i++) {
            for (String year : years) {
                boolean found = false;
                for (String label : labels) {
                    if (label.equals(jobTypes[i] + year)) {
                        found = true;
                    }
                }
                if (!found) {
                    throw new RuntimeException("Not defined all job types for year " + year);
                }
            }
        }

        String[] yearsGiven = years.toArray(new String[0]);

        String dir = Properties.get().main.baseDirectory + "scenOutput/" + Properties.get().main.scenarioName + "/employmentForecast/";
        SiloUtil.createDirectoryIfNotExistingYet(dir);

        int previousFixedYear = Integer.parseInt(yearsGiven[0]);
        int nextFixedYear;
        int interpolatedYear = previousFixedYear;
        for (int i = 0; i < yearsGiven.length - 1; i++) {
            nextFixedYear = Integer.parseInt(yearsGiven[i + 1]);
            while (interpolatedYear <= nextFixedYear) {
                final String forecastFileName = dir + Properties.get().jobData.employmentForeCastFile + (2000 + interpolatedYear) + ".csv";
                final PrintWriter pw = SiloUtil.openFileForSequentialWriting(forecastFileName, false);
                final StringBuilder builder = new StringBuilder("zone");
                for (String jobType : JobType.getJobTypes()) {
                    builder.append(",").append(jobType);
                }
                builder.append("\n");
                for (int zone : geoData.getZones().keySet()) {
                    builder.append(zone);
                    for (int jobTp = 0; jobTp < JobType.getNumberOfJobTypes(); jobTp++) {
                        final int index = jobs.getIndexedRowNumber(zone);
                        float currentValue;
                        if (interpolatedYear == previousFixedYear) {
                            currentValue = jobs.getValueAt(index, JobType.getJobType(jobTp) + yearsGiven[i]);
                        } else if (interpolatedYear == nextFixedYear) {
                            currentValue = jobs.getValueAt(index, JobType.getJobType(jobTp) + yearsGiven[i + 1]);
                        } else {
                            final float previousFixedValue = jobs.getValueAt(index, JobType.getJobType(jobTp) + yearsGiven[i]);
                            final float nextFixedValue = jobs.getValueAt(index, JobType.getJobType(jobTp) + yearsGiven[i + 1]);
                            currentValue = previousFixedValue + (nextFixedValue - previousFixedValue) * (interpolatedYear - previousFixedYear) /
                                    (nextFixedYear - previousFixedYear);
                        }
                        builder.append(",").append(currentValue);
                    }
                    builder.append("\n");
                }
                pw.print(builder.toString());
                pw.close();
                interpolatedYear++;
            }
            previousFixedYear = nextFixedYear;
        }
    }


    public void identifyVacantJobs() {
        // identify vacant jobs by region (one-time task at beginning of model run only)
        numberOfStoredVacantJobs = Properties.get().jobData.maxStorageOfvacantJobs;
        int highestRegionID = geoData.getRegions().keySet().stream().mapToInt(Integer::intValue).max().getAsInt();
        vacantJobsByRegion = new int[highestRegionID + 1][numberOfStoredVacantJobs + 1];
        vacantJobsByRegion = SiloUtil.setArrayToValue(vacantJobsByRegion, 0);
        vacantJobsByRegionPos = new int[highestRegionID + 1];
        vacantJobsByRegionPos = SiloUtil.setArrayToValue(vacantJobsByRegionPos, 0);

        logger.info("  Identifying vacant jobs");
        for (Job jj : jobs.values()) {
            if (jj.getWorkerId() == -1) {
                int jobId = jj.getId();

                int region = geoData.getZones().get(jj.determineZoneId()).getRegion().getId();
                if (vacantJobsByRegionPos[region] < numberOfStoredVacantJobs) {
                    vacantJobsByRegion[region][vacantJobsByRegionPos[region]] = jobId;
                    vacantJobsByRegionPos[region]++;
                } else {
                    IssueCounter.countExcessOfVacantJobs(region);
                }
                if (jobId == SiloUtil.trackJj) {
                    SiloUtil.trackWriter.println("Added job " + jobId + " to list of vacant jobs.");
                }
            }
        }
    }

    public void quitJob(boolean makeJobAvailableToOthers, Person person) {
        // Person quits job and the job is added to the vacantJobList
        // <makeJobAvailableToOthers> is false if this job disappears from the job market
        if (person == null) {
            return;
        }
        final int workplace = person.getWorkplace();
        Job jb = jobs.get(workplace);
        if (makeJobAvailableToOthers) {
            addJobToVacancyList(jb.determineZoneId(), workplace);
        }
        jb.setWorkerID(-1);
        person.setWorkplace(-1);
        person.setOccupation(Occupation.UNEMPLOYED);
        person.setIncome((int) (person.getIncome() * 0.6 + 0.5));
        //todo: think about smarter retirement/social welfare algorithm to adjust income after employee leaves work.
    }


    public int getNumberOfVacantJobsByRegion(int region) {
        return vacantJobsByRegionPos[region];
    }


//    public static void removeJobFromVacancyList(int jobId, int region, boolean logError) {
//        // remove job jobId in zone from vacancy list
//
//        boolean notFound = true;
//
//        if (vacantJobsByRegionPos[region] == 0) {
//            if (logError) logger.error("No vacant jobs in region " + region + " stored. Could not remove job " + jobId + ".");
//            return;
//        }
//        for (int pos = 0; pos < vacantJobsByRegionPos[region]; pos++) {
//            if (vacantJobsByRegion[region][pos] == jobId) {
//                vacantJobsByRegion[region][pos] = vacantJobsByRegion[region][vacantJobsByRegionPos[region] - 1];
//                vacantJobsByRegion[region][vacantJobsByRegionPos[region] - 1] = 0;
//                vacantJobsByRegionPos[region] -= 1;
//                if (jobId == SiloUtil.trackJj)
//                    SiloUtil.trackWriter.println("Removed job " + jobId + " from list of vacant jobs.");
//                notFound = false;
//                break;
//            }
//        }
//
//        if (notFound && logError) logger.warn("Could not find job " + jobId + " in list of vacant jobs. See method " +
//                "<removeJobFromVacancyList>.");
//    }


    public int findVacantJob(Zone homeZone, Collection<Region> regions, Accessibility accessibility) {
        // select vacant job for person living in homeZone

        Map<Region, Double> regionProb = new HashMap<>();

        if (homeZone != null) {
            // person has home location (i.e., is not inmigrating right now)
            for (Region reg : regions) {
                if (vacantJobsByRegionPos[reg.getId()] > 0) {
                    int distance = (int) (data.getTravelTimes().getTravelTimeToRegion(homeZone, reg,
                    		Properties.get().main.peakHour, TransportMode.car) + 0.5);
                    regionProb.put(reg, ((SkimBasedAccessibility)accessibility).getCommutingTimeProbability(distance) * (double) getNumberOfVacantJobsByRegion(reg.getId()));
                }
            }
            if (SiloUtil.getSum(regionProb.values()) == 0) {
                // could not find job in reasonable distance. Person will have to commute far and is likely to relocate in the future
                for (Region reg : regions) {
                    if (vacantJobsByRegionPos[reg.getId()] > 0) {
                    	int distance = (int) (data.getTravelTimes().getTravelTime(homeZone, reg,
                        		Properties.get().main.peakHour, TransportMode.car) + 0.5);
                    	regionProb.put(reg, 1. / distance);
                    }
                }
            }
        } else {
            // person has no home location because (s)he is inmigrating right now and a dwelling has not been chosen yet
            for (Region reg : regions) {
                if (vacantJobsByRegionPos[reg.getId()] > 0) {
                	regionProb.put(reg, (double) getNumberOfVacantJobsByRegion(reg.getId()));
                }
            }
        }

        if (SiloUtil.getSum(regionProb.values()) == 0) {
            logger.warn("No jobs remaining. Could not find new job.");
            return -1;
        }
        int selectedRegion = SiloUtil.select(regionProb).getId();
        if (getNumberOfVacantJobsByRegion(selectedRegion) == 0) {
            logger.warn("Selected region " + selectedRegion + " but could not find any jobs there.");
            return -1;
        }
        float[] jobProbability = new float[getNumberOfVacantJobsByRegion(selectedRegion)];
        jobProbability = SiloUtil.setArrayToValue(jobProbability, 1);
        int selectedJob = SiloUtil.select(jobProbability);

        int jobId = vacantJobsByRegion[selectedRegion][selectedJob];
        vacantJobsByRegion[selectedRegion][selectedJob] = vacantJobsByRegion[selectedRegion][vacantJobsByRegionPos[selectedRegion] - 1];
        vacantJobsByRegion[selectedRegion][vacantJobsByRegionPos[selectedRegion] - 1] = 0;
        vacantJobsByRegionPos[selectedRegion] -= 1;
        if (jobId == SiloUtil.trackJj)
            SiloUtil.trackWriter.println("Removed job " + jobId + " from list of vacant jobs.");
        return jobId;
    }


    public void addJobToVacancyList(int zone, int jobId) {
        // add job jobId to vacancy list

        int region = geoData.getZones().get(zone).getRegion().getId();
        vacantJobsByRegion[region][vacantJobsByRegionPos[region]] = jobId;
        if (vacantJobsByRegionPos[region] < numberOfStoredVacantJobs) {
            vacantJobsByRegionPos[region]++;
        }
        if (vacantJobsByRegionPos[region] >= numberOfStoredVacantJobs) {
            IssueCounter.countExcessOfVacantJobs(region);
        }
        if (jobId == SiloUtil.trackJj) {
            SiloUtil.trackWriter.println("Added job " + jobId + " to list of vacant jobs.");
        }
    }


    public void summarizeJobs(Map<Integer, Region> regions) {
        // summarize jobs for summary file

        String txt = "jobByRegion";
        for (String empType : JobType.getJobTypes()) txt += "," + empType;
        SummarizeData.resultFile(txt + ",total");

        final int highestId = regions.keySet().stream().mapToInt(Integer::intValue).max().getAsInt();
        int[][] jobsByTypeAndRegion = new int[JobType.getNumberOfJobTypes()][highestId + 1];
        for (Job job : jobs.values()) {
            jobsByTypeAndRegion[JobType.getOrdinal(job.getType())][geoData.getZones().get(job.determineZoneId()).getRegion().getId()]++;
        }

        for (int region : regions.keySet()) {
            StringBuilder line = new StringBuilder(String.valueOf(region));
            int regionSum = 0;
            for (String empType : JobType.getJobTypes()) {
                line.append(",").append(jobsByTypeAndRegion[JobType.getOrdinal(empType)][region]);
                regionSum += jobsByTypeAndRegion[JobType.getOrdinal(empType)][region];
            }
            SummarizeData.resultFile(line + "," + regionSum);
        }
    }


    public void calculateJobDensityByZone() {
        Multiset<Integer> counter = ConcurrentHashMultiset.create();
        jobs.values().parallelStream().forEach(j -> counter.add(j.determineZoneId()));
        geoData.getZones().forEach((id, zone) -> zonalJobDensity.put(id, (double) (counter.count(id) / zone.getArea())));
    }


    public double getJobDensityInZone(int zone) {
        return zonalJobDensity.get(zone);
    }

    public int getJobDensityCategoryOfZone(int zone) {
        // return job density category 1 to 10 of zone
        //TODO: magic numbers
        float[] densityCategories = {0.f, 0.143f, 0.437f, 0.865f, 1.324f, 1.8778f, 2.664f, 3.99105f, 6.f, 12.7f};
        for (int i = 0; i < densityCategories.length; i++) {
            if (zonalJobDensity.get(zone) < densityCategories[i]) {
                return i;
            }
        }
        return densityCategories.length;
    }
}
