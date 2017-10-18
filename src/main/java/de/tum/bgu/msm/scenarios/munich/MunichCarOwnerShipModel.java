package de.tum.bgu.msm.scenarios.munich;

import de.tum.bgu.msm.SiloUtil;
import de.tum.bgu.msm.autoOwnership.CarOwnershipModel;
import de.tum.bgu.msm.data.*;
import org.apache.log4j.Logger;

import javax.script.ScriptException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.*;

/**
 * Implements car ownership level change (subsequent years) for the Munich Metropolitan Area
 * @author Matthew Okrah
 * Created on 28/08/2017 in Munich, Germany.
 */
public class MunichCarOwnerShipModel implements CarOwnershipModel {

    static Logger logger = Logger.getLogger(MunichCarOwnerShipModel.class);
    private ResourceBundle rb;

    private double[][][][][][][][] carUpdateProb; // [previousCars][hhSize+][hhSize-][income+][income-][license+][changeRes][three probabilities]

    private Reader reader;
    private MunichCarOwnershipJSCalculator calculator;

    public MunichCarOwnerShipModel(ResourceBundle rb){
        logger.info(" Setting up probabilities for car update model");
        this.rb = rb;
    }

    public static void summarizeCarUpdate() {
        // This function summarizes household car ownership update and quits
        PrintWriter pwa = SiloUtil.openFileForSequentialWriting("microData/interimFiles/carUpdate.csv", false);
        pwa.println("id, dwelling, zone, license, income, size, autos");
        for (Household hh: Household.getHouseholdArray()) {
            pwa.println(hh.getId() + "," + hh.getDwellingId() + "," + hh.getHomeZone() + "," + hh.getHHLicenseHolders()+ "," +  hh.getHhIncome() + "," + hh.getHhSize() + "," + hh.getAutos());
        }
        pwa.close();

        logger.info("Summarized car update and quit.");
        System.exit(0);
    }

    @Override
    public void initialize() {
        // Setting up probabilities for car update model

        reader = new InputStreamReader(this.getClass().getResourceAsStream("UpdateCarOwnershipCalc"));
        calculator = new MunichCarOwnershipJSCalculator(reader, false);
        //set car update probabilities
        carUpdateProb = new double[4][2][2][2][2][2][2][3];
        for (int prevCar = 0; prevCar < 4; prevCar++){
            calculator.setPreviousCars(prevCar);
            for (int sizePlus = 0; sizePlus < 2; sizePlus++){
                calculator.setHHSizePlus(sizePlus);
                for (int sizeMinus = 0; sizeMinus < 2; sizeMinus++){
                    calculator.setHHSizeMinus(sizeMinus);
                    for (int incPlus = 0; incPlus < 2; incPlus++){
                        calculator.setHHIncomePlus(incPlus);
                        for (int incMinus = 0; incMinus < 2; incMinus++){
                            calculator.setHHIncomeMinus(incMinus);
                            for (int licPlus = 0; licPlus < 2; licPlus++){
                                calculator.setLicensePlus(licPlus);
                                for (int changeRes = 0; changeRes < 2; changeRes++){
                                    calculator.setChangeResidence(changeRes);
                                    try {
                                        carUpdateProb[prevCar][sizePlus][sizeMinus][incPlus][incMinus][licPlus][changeRes] = calculator.calculate();
                                    } catch (ScriptException e) {
                                        e.printStackTrace();
                                    }
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
        //

        int[] counter = new int[2];
        for (Map.Entry<Integer, int[]> pair : updatedHouseholds.entrySet()) {
            Household hh = Household.getHouseholdFromId(pair.getKey());
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
                if (hh.getHhIncome() > previousAttributes[1] + 6000) {
                    hhIncomePlus = 1;
                } else if (hh.getHhIncome() < previousAttributes[1] - 6000) {
                    hhIncomeMinus = 1;
                }
                if (hh.getHHLicenseHolders() > previousAttributes[2]){
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
                    }
                }
            }
        }
        return counter;
    }
}
