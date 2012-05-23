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

import com.google.bitcoin.core.ECKey;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: Andreas
 * Date: 23.05.12
 * Time: 18:51
 */
public class Tx2FluidsAdapter {

    public static final BigDecimal SATOSHIS_PER_BITCOIN = BigDecimal.valueOf(Math.pow(10, 8));
    private final PriceService priceService;

    private final Map<ECKey, FluidType> lookup;

    public Tx2FluidsAdapter(PriceService priceService, final Environment environment) {
        this.priceService = priceService;
        lookup = new HashMap<ECKey, FluidType>() {{
            put(environment.getKey200(), FluidType.MATE);
            put(environment.getKey150(), FluidType.COLA);
        }};
    }


    public TxNotifier convert(final FluidsNotifier fluidsNotifier) {
        return new TxNotifier() {
            @Override
            public void onValue(BigInteger satoshis, ECKey key) {
                FluidType type = lookup.get(key);
                BigDecimal bitcoins = new BigDecimal(satoshis).divide(SATOSHIS_PER_BITCOIN);
                try {
                    double price = priceService.getEurQuote();
                    BigDecimal eurPerBitcoin = BigDecimal.valueOf(price);
                    BigDecimal euros = bitcoins.multiply(eurPerBitcoin);
                    BigDecimal circaAnzahl = euros.divide(eurPerBitcoin);
                    fluidsNotifier.onFluidPaid(type, circaAnzahl);
                } catch (RemoteSystemFail remoteSystemFail) {
                    fluidsNotifier.onError(remoteSystemFail.getMessage(), type, bitcoins);
                }

            }
        };
    }
}
