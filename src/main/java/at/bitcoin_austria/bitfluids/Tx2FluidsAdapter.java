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

import com.google.bitcoin.core.Address;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * this is an Adapter between a transaction notifier
 * which is easily providable from bitcoinj and a FluidsNotifier which is needed in the UI code.
 * does some custom rounding (within 2%) for clearer display for the user..
 * @author apetersson
 */
public class Tx2FluidsAdapter {

    public static final BigDecimal SATOSHIS_PER_BITCOIN = BigDecimal.valueOf(Math.pow(10, 8));
    private final PriceService priceService;

    private final Map<Address, FluidType> lookup;

    public Tx2FluidsAdapter(PriceService priceService, final Environment environment) {
        this.priceService = priceService;
        lookup = new HashMap<Address, FluidType>() {{
            put(environment.getKey200(), FluidType.MATE);
            put(environment.getKey150(), FluidType.COLA);
        }};
    }


    public TxNotifier convert(final FluidsNotifier fluidsNotifier) {
        return new TxNotifier() {
            @Override
            public void onValue(BigInteger satoshis, Address key) {
                FluidType type = lookup.get(key);
                BigDecimal bitcoins = new BigDecimal(satoshis).divide(SATOSHIS_PER_BITCOIN);
                try {
                    double price = priceService.getEurQuote();
                    BigDecimal eurPerBitcoin = BigDecimal.valueOf(price);
                    BigDecimal euros = bitcoins.multiply(eurPerBitcoin);
                    BigDecimal euroPrice = BigDecimal.valueOf(type.getEuroPrice());
                    BigDecimal anzahl = euros.divide(euroPrice, RoundingMode.HALF_UP);
                    BigDecimal rounded = BigDecimal.valueOf(anzahl.add(BigDecimal.valueOf(0.5)).longValue());
                    //is within 2% -> return rounded
                    double difference = anzahl.subtract(rounded).divide(rounded, RoundingMode.HALF_UP).abs().doubleValue();
                    if (difference < 0.02) {
                        anzahl = rounded;
                    }
                    fluidsNotifier.onFluidPaid(type, anzahl);
                } catch (RemoteSystemFail remoteSystemFail) {
                    fluidsNotifier.onError(remoteSystemFail.getMessage(), type, bitcoins);
                }
            }
        };
    }
}
