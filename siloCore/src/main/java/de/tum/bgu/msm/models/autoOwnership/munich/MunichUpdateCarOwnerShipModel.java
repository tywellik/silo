package de.tum.bgu.msm.models.autoOwnership.munich;

import de.tum.bgu.msm.utils.SiloUtil;
import de.tum.bgu.msm.container.SiloDataContainer;
import de.tum.bgu.msm.data.HouseholdDataManager;
import de.tum.bgu.msm.data.dwelling.Dwelling;
import de.tum.bgu.msm.data.household.Household;
import de.tum.bgu.msm.data.household.HouseholdUtil;
import de.tum.bgu.msm.models.AbstractModel;
import de.tum.bgu.msm.models.autoOwnership.UpdateCarOwnershipModel;
import org.apache.log4j.Logger;

import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.Map;

/**
 * Implements car ownership level change (subsequent years) for the Munich Metropolitan Area
 * @author Matthew Okrah
 * Created on 28/08/2017 in Munich, Germany.
 */
public class MunichUpdateCarOwnerShipModel extends AbstractModel implements UpdateCarOwnershipModel {

    private static Logger logger = Logger.getLogger(MunichUpdateCarOwnerShipModel.class);

    private double[][][][][][][][] carUpdateProb; // [previousCars][hhSize+][hhSize-][income+][income-][license+][changeRes][three probabilities]

    public MunichUpdateCarOwnerShipModel(SiloDataContainer dataContainer) {
        super(dataContainer);
    }

    public void summarizeCarUpdate() {
        // This function summarizes household car ownership update and quits
        PrintWriter pwa = SiloUtil.openFileForSequentialWriting("microData/interimFiles/carUpdate.csv", false);
        pwa.println("id, dwelling, zone, license, income, size, autos");
        HouseholdDataManager householdData = dataContainer.getHouseholdData();
        for (Household hh: householdData.getHouseholds()) {
            Dwelling dwelling = dataContainer.getRealEstateData().getDwelling(hh.getDwellingId());
            int homeZone = -1;
            if(dwelling != null) {
                homeZone = dwelling.getZoneId();
            }
            pwa.println(hh.getId() + "," + hh.getDwellingId() + "," + homeZone + "," +
                    HouseholdUtil.getHHLicenseHolders(hh)+ "," +  HouseholdUtil.getHhIncome(hh) + "," + hh.getHhSize() + "," + hh.getAutos());
        }
        pwa.close();

        logger.info("Summarized car update and quit.");
        System.exit(0);
    }

    @Override
    public void initialize() {
        // Setting up probabilities for car update model

        Reader reader = new InputStreamReader(this.getClass().getResourceAsStream("UpdateCarOwnershipCalc"));
        MunichCarOwnershipJSCalculator calculator = new MunichCarOwnershipJSCalculator(reader);
        //set car update probabilities
        carUpdateProb = new double[4][2][2][2][2][2][2][3];
        for (int prevCar = 0; prevCar < 4; prevCar++){
            for (int sizePlus = 0; sizePlus < 2; sizePlus++){
                for (int sizeMinus = 0; sizeMinus < 2; sizeMinus++){
                    for (int incPlus = 0; incPlus < 2; incPlus++){
                        for (int incMinus = 0; incMinus < 2; incMinus++){
                            for (int licPlus = 0; licPlus < 2; licPlus++){
                                for (int changeRes = 0; changeRes < 2; changeRes++){
                                    carUpdateProb[prevCar][sizePlus][sizeMinus][incPlus][incMinus][licPlus][changeRes] =
                                            calculator.calculateCarOwnerShipProbabilities(prevCar, sizePlus, sizeMinus, incPlus, incMinus, licPlus, changeRes);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public int[] updateCarOwnership(Map<Integer, int[]> updatedHouseholds) {

        int[] counter = new int[2];
        HouseholdDataManager householdData = dataContainer.getHouseholdData();
        for (Map.Entry<Integer, int[]> pair : updatedHouseholds.entrySet()) {
            Household hh = householdData.getHouseholdFromId(pair.getKey());
            if (hh != null) {
                int[] previousAttributes = pair.getValue();
                // update cars owned by household hh
                int previousCars = hh.getAutos();
                int hhSizePlus = 0;
                int hhSizeMinus = 0;
                int hhIncomePlus = 0;
                int hhIncomeMinus = 0;
                int licensePlus = 0;
                int changeResidence = previousAttributes[3];

                if (hh.getHhSize() > previousAttributes[0]){
                    hhSizePlus = 1;
                } else if (hh.getHhSize() < previousAttributes[0]){
                    hhSizeMinus = 1;
                }
                int hhIncome = HouseholdUtil.getHhIncome(hh);
                if (hhIncome > previousAttributes[1] + 6000) {
                    hhIncomePlus = 1;
                } else if (hhIncome < previousAttributes[1] - 6000) {
                    hhIncomeMinus = 1;
                }
                if (HouseholdUtil.getHHLicenseHolders(hh) > previousAttributes[2]){
                    licensePlus = 1;
                }

                double[] prob = carUpdateProb[previousCars][hhSizePlus][hhSizeMinus][hhIncomePlus][hhIncomeMinus][licensePlus][changeResidence];

                int action = SiloUtil.select(prob);

                if (action == 1){ //add one car
                    if (hh.getAutos() < 3) { //maximum number of cars is equal to 3
                        hh.setAutos(hh.getAutos() + 1);
                        counter[0]++;
                    }
                } else if (action == 2) { //remove one car
                    if (hh.getAutos() > 0){ //cannot have less than zero cars
                        hh.setAutos(hh.getAutos() - 1);
                        counter[1]++;
                        // update number of AVs if necessary after household relinquishes a car
                        if (hh.getAutonomous() > hh.getAutos()) { // no. of AVs cannot exceed total no. of autos
                            hh.setAutonomous(hh.getAutos());
                        }
                    }
                }
            }
        }
        return counter;
    }
}
