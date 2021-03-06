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
package run;

import de.tum.bgu.msm.Implementation;
import de.tum.bgu.msm.SiloModel;
import de.tum.bgu.msm.properties.Properties;
import de.tum.bgu.msm.utils.SiloUtil;
import org.apache.log4j.Logger;

/**
 * Implements SILO for Minneapolis/St. Paul (MetCouncil)
 *
 * @author Rolf Moeckel
 * Created on Jan 28, 2011 in Vienna, VA
 */

public class SiloMsp {
    static Logger logger = Logger.getLogger(SiloMsp.class);

    /**
     * @param args Arguments fed in from command line
     */

    public static void main(String[] args) {

        Properties properties = SiloUtil.siloInitialization(Implementation.MSP, args[0]);
        logger.info("Starting SILO for Minneapolis/St. Paul");
        SiloModel model = new SiloModel(properties);
        model.runModel();
        logger.info("Finished SILO.");

    }
}
