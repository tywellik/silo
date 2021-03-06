package sdg;

import de.tum.bgu.msm.Implementation;
import de.tum.bgu.msm.utils.SiloUtil;
import de.tum.bgu.msm.container.SiloDataContainer;
import de.tum.bgu.msm.properties.Properties;

public class Main {

    public static void main(String[] args) {

        Properties properties = SiloUtil.siloInitialization(Implementation.MUNICH, args[0]);

        SiloDataContainer siloDataContainer = SiloDataContainer.loadSiloDataContainer(properties);

        SDGCalculator.calculateSdgIndicators(siloDataContainer, Properties.get().main.baseDirectory + "/scenOutput/" + Properties.get().main.scenarioName,
                Properties.get().main.startYear);

    }

}
