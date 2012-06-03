package at.bitcoin_austria.bitfluids;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author apetersson
 */
public class RoundTest {

    private static final double EUR_PRICE = 4.1;

    @Test
    public void roundTest() {
        BigDecimal satoshis = BigDecimal.valueOf(FluidType.MATE.getEuroPrice() / EUR_PRICE).multiply(BigDecimal.valueOf(Math.pow(10, 8)));
        testRounding(1.0, satoshis);
        testRounding(1.03, satoshis.multiply(BigDecimal.valueOf(1.03)), 0.0000001);
        testRounding(1.0, satoshis.multiply(BigDecimal.valueOf(1.01)));
        testRounding(1.5, satoshis.multiply(BigDecimal.valueOf(1.5)), 0.0000001);
    }

    private void testRounding(final double expectedValue, BigDecimal satoshis) {
        testRounding(expectedValue, satoshis, 0);
    }

    private void testRounding(final double expectedValue, BigDecimal satoshis, final double epsilon) {
        PriceService priceService = new PriceService(null) {
            @Override
            public synchronized Double getEurQuote() throws RemoteSystemFail {
                return 4.1;
            }
        };
        Tx2FluidsAdapter toTest = new Tx2FluidsAdapter(priceService, Environment.TEST);
        final boolean[] wasTested = {false};
        TxNotifier trigger = toTest.convert(new FluidsNotifier() {
            @Override
            public void onFluidPaid(FluidType type, BigDecimal amount) {
                assertEquals(expectedValue, amount.doubleValue(), epsilon);
                wasTested[0] = true;
            }

            @Override
            public void onError(String message, FluidType type, BigDecimal bitcoins) {

            }
        });

        trigger.onValue(satoshis.toBigInteger(), Environment.TEST.getKey200());
        assertTrue(wasTested[0]);
    }
}
