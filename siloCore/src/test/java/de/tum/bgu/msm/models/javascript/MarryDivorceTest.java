package de.tum.bgu.msm.models.javascript;

import de.tum.bgu.msm.Implementation;
import de.tum.bgu.msm.data.person.*;
import de.tum.bgu.msm.utils.SiloUtil;
import de.tum.bgu.msm.container.SiloDataContainer;
import de.tum.bgu.msm.models.demography.MarryDivorceJSCalculator;
import de.tum.bgu.msm.properties.Properties;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.InputStreamReader;
import java.io.Reader;

public class MarryDivorceTest {

    private MarryDivorceJSCalculator calculator;
    private final double SCALE = 1.1;
    private SiloDataContainer dataContainer;

    @BeforeClass
    public static void intitializeProperties() {
        SiloUtil.siloInitialization(Implementation.MARYLAND, "./test/scenarios/annapolis/javaFiles/siloMstm.properties");
    }

    @Before
    public void setup() {
        Reader reader = new InputStreamReader(this.getClass().getResourceAsStream("MarryDivorceCalcMstm"));
        calculator = new MarryDivorceJSCalculator(reader, SCALE);
        this.dataContainer = SiloDataContainer.loadSiloDataContainer(Properties.get());
    }

    @Test
    public void testModelOne() {
        Person person = PersonUtils.getFactory().createPerson(1, 20, Gender.MALE, Race.other, Occupation.EMPLOYED, PersonRole.MARRIED, -1, 0);
        Assert.assertEquals((0.05926 /2) * SCALE, calculator.calculateMarriageProbability(person), 0.);
    }

    @Test
    public void testModelTwo() {
        Person person = PersonUtils.getFactory().createPerson(1, 50, Gender.FEMALE, Race.other, Occupation.EMPLOYED, PersonRole.MARRIED, -1, 0);
        Assert.assertEquals((0.02514 /2) * SCALE, calculator.calculateMarriageProbability(person), 0.);    }

    @Test(expected = RuntimeException.class)
    public void testModelFailuresOne() {
        calculator.calculateMarriageProbability(null);
    }


    @Test
    public void testModelThree() {
        Assert.assertEquals(0.02156, calculator.calculateDivorceProbability(31), 0.);
    }

    @Test(expected = RuntimeException.class)
    public void testModelFailuresTwo() {
        calculator.calculateDivorceProbability(200);
    }

    @Test(expected = RuntimeException.class)
    public void testModelFailuresThree() {
        calculator.calculateDivorceProbability(-2);
    }

}
