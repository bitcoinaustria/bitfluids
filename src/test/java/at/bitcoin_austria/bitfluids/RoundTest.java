/*
 * Copyright 2012 Bitcoin Austria
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package at.bitcoin_austria.bitfluids;

import com.google.bitcoin.core.Sha256Hash;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author apetersson
 */
public class RoundTest {

    private static final double EUR_PRICE = 4.1;

    @Test
    public void roundTest() {
        double expectedPrice = FluidType.MATE.getEuroPrice() / EUR_PRICE;
        Bitcoins bitcoins = Bitcoins.nearestValue(expectedPrice);
        testRounding(1.0, bitcoins);
        testRounding(1.03, Bitcoins.nearestValue(expectedPrice*1.03), 0.0000001);
        testRounding(1.0, Bitcoins.nearestValue(expectedPrice*1.01));
        testRounding(1.5, Bitcoins.nearestValue(expectedPrice*1.5), 0.0000001);
    }

    private void testRounding(final double expectedValue, Bitcoins satoshis) {
        testRounding(expectedValue, satoshis, 0);
    }

    private void testRounding(final double expectedValue, Bitcoins bitcoins, final double epsilon) {
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
            public void onFluidPaid(TransactionItem transactionItem) {
                assertEquals(expectedValue, transactionItem.count, epsilon);
                wasTested[0] = true;
            }

            @Override
            public void onError(String message, FluidType type, Bitcoins bitcoins) {

            }
        });

        trigger.onValue(bitcoins, Environment.TEST.getKey200(), Sha256Hash.ZERO_HASH);
        assertTrue(wasTested[0]);
    }
}
