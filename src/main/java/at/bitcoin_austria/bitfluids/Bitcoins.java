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

import com.google.bitcoin.core.TransactionOutput;
import com.google.common.base.Preconditions;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * a core Bitcoin Value representation, caputuring many domain specific aspects of it.
 * introduced to reduce the ambiguity when dealing with double, BigInteger, long, or even worse, integer representations
 *
 * @author apetersson
 */
public final class Bitcoins implements Serializable {
    /**
     * 100 000 000   must be long, else MAX_VALUE will overflow
     */
    private static final long SATOSHIS_PER_BITCOIN = (long) Math.pow(10, 8);
    private static final BigDecimal SATOSHIS_PER_BITCOIN_BD = BigDecimal.valueOf(SATOSHIS_PER_BITCOIN);
    private static final long MAX_VALUE = 21000000 * SATOSHIS_PER_BITCOIN;
    public static final String BITCOIN_SYMBOL = "฿";

    private final long satoshis;

    /**
     * if used properly, also valueOf(input) should be provided
     * ideally, BitcoinJ would already output Bitcoins instead of BigInteger
     *
     * @param output Object from BitcoiJ transaction
     * @return a value prepresentation of a Bitcoin domain object
     */
    public static Bitcoins valueOf(TransactionOutput output) {
        return Bitcoins.valueOf(output.getValue().longValue());
    }

    /**
     *
     * @param btc double Value in full bitcoins. must be an exact represenatation
     * @return bitcoin value representation
     * @throws IllegalArgumentException if the given double value loses precision when converted to long
     */
    public static Bitcoins valueOf(double btc) {
        return valueOf(toLongExact(btc));
    }

    public static Bitcoins nearestValue(double v) {
        return new Bitcoins(Math.round(v * SATOSHIS_PER_BITCOIN));
    }

    public static Bitcoins valueOf(long satoshis) {
        return new Bitcoins(satoshis);
    }

    private static long toLongExact(double origValue) {
        double satoshis = origValue * SATOSHIS_PER_BITCOIN;  //possible loss of precision here?
        long longSatoshis = Math.round(satoshis);
        if (satoshis != (double) longSatoshis) {
            double error = longSatoshis - satoshis;
            throw new IllegalArgumentException("the given double value " + origValue +
                    " was not convertable to a precise value." +
                    " error: " + error + " satoshis");
        }
        return longSatoshis;
    }

    private Bitcoins(long satoshis) {
        Preconditions.checkArgument(satoshis >= 0,
                "Bitcoin values must be debt-free and positive, but was %s", satoshis);
        Preconditions.checkArgument(satoshis < MAX_VALUE,
                "Bitcoin values must be smaller than 21 Million BTC, but was %s", satoshis);
        this.satoshis = satoshis;
    }

    public BigDecimal multiply(BigDecimal pricePerBtc) {
        return pricePerBtc.divide(SATOSHIS_PER_BITCOIN_BD).multiply(BigDecimal.valueOf(satoshis));
    }


    @Override
    public String toString() {
        //this could surely be implented faster without using BigDecimal. but it is good enough for now.
        //this could be cached
        return BigDecimal.valueOf(satoshis).divide(SATOSHIS_PER_BITCOIN_BD).toPlainString();
    }

    @Override
    public int hashCode() {
        return (int) (satoshis ^ (satoshis >>> 32));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Bitcoins bitcoins = (Bitcoins) o;

        if (satoshis != bitcoins.satoshis) return false;

        return true;
    }

    public BigInteger toBigInteger() {
        return BigInteger.valueOf(satoshis);
    }

    public String toCurrencyString() {
        return toString() + " " + BITCOIN_SYMBOL;
    }

    public Bitcoins roundToSignificantFigures(int n) {
        return Bitcoins.valueOf(roundToSignificantFigures(satoshis,n));
    }

    private static long roundToSignificantFigures(long num, int n) {     //todo optimize for long
        if(num == 0) {
            return 0;
        }
        final double d = Math.ceil(Math.log10(num < 0 ? -num: num));
        final int power = n - (int) d;

        final double magnitude = Math.pow(10, power);
        final long shifted = Math.round(num*magnitude);
        long ret = (long) (shifted / magnitude);
        return ret;
    }

}
