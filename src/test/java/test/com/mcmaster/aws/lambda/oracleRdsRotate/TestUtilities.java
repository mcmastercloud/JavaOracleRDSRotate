package test.com.mcmaster.aws.lambda.oracleRdsRotate;

import com.mcmaster.aws.lambda.oracleRdsRotate.Utilities;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestUtilities {

    @Test
    @Order(1)
    public void randomStringTest() {
        String sRandom = Utilities.randomString(10);
        assertEquals(sRandom.length(), 10);
        System.out.println(sRandom);
    }


}
