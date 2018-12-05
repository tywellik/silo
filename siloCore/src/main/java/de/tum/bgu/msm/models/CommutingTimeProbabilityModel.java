package de.tum.bgu.msm.models;

import org.apache.log4j.Logger;

import com.pb.common.datafile.TableDataSet;

import de.tum.bgu.msm.utils.SiloUtil;
import de.tum.bgu.msm.properties.Properties;

public class CommutingTimeProbabilityModel {
    private static final Logger LOGGER = Logger.getLogger(CommutingTimeProbabilityModel.class);

    private float[] workTLFD;
    
    /**
     * Initializes the object by reading trip length distributions
     * and zone to region travel times. Travel times should therefore
     * be read/updated _BEFORE_ this method is called.
     */
    public void initialize() {
        LOGGER.info("Initializing trip length frequency distributions");
        readWorkTripLengthFrequencyDistribution();
    }
	
	private void readWorkTripLengthFrequencyDistribution() {
        String fileName = Properties.get().main.baseDirectory + Properties.get().accessibility.htsWorkTLFD;
        TableDataSet tlfd = SiloUtil.readCSVfile(fileName);
        workTLFD = new float[tlfd.getRowCount() + 1];
        for (int row = 1; row <= tlfd.getRowCount(); row++) {
            int tt = (int) tlfd.getValueAt(row, "TravelTime");
            if (tt > workTLFD.length) {
                LOGGER.error("Inconsistent trip length frequency in " + Properties.get().main.baseDirectory +
                        Properties.get().accessibility.htsWorkTLFD + ": " + tt + ". Provide data in 1-min increments.");
            }
            workTLFD[tt] = tlfd.getValueAt(row, "utility");
        }
    }

    public float getCommutingTimeProbability(int minutes) {
        if (minutes < workTLFD.length) {
            return workTLFD[minutes];
        } else {
            return 0;
        }
    }
}