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

import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.fail;

/**
 * @author apetersson
 */
public class BitcoinsTest {

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidDouble1() {
        double btceur = 4.37135;
        double colaprice = 1.5 / btceur;
        assertEquals("0.34314342", Bitcoins.nearestValue(colaprice).toString()); //should work
        Bitcoins.valueOf(colaprice); //crashes
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidDouble2() {
        Bitcoins.valueOf(0.100000005); //too much precision after comma
    }

    @Test
    public void testValidDouble1() {
        double one = 0.11111111;
        assertEquals("0.11111111", Bitcoins.valueOf(one).toString());
    }

    @Test
    public void testValidDouble2() {
        double btceur = 4.0;
        double colaprice = 1.5 / btceur;
        assertEquals("0.375", Bitcoins.valueOf(colaprice).toString());
    }

    @Test
    public void testToString() {
        assertEquals("0.123", Bitcoins.valueOf(0.123).toString());
        assertEquals("20999999", Bitcoins.valueOf(20999999).toString());
        assertEquals("0.00000001", Bitcoins.valueOf(0.00000001).toString());
        assertEquals("20999999.99999999", Bitcoins.valueOf(20999999.99999999).toString());
        try {
            assertEquals("20999999.99999999", Bitcoins.valueOf(20999999.999999998).toString());
            fail();
        } catch (IllegalArgumentException ignored) {
            assertEquals("the given double value 2.0999999999999996E7 was not convertable to a precise value. error: 0.25 satoshis", ignored.getMessage());
        }
    }
}
