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

import com.google.bitcoin.core.*;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class AddressParse {
    @Test
    public void correctPubHash() throws AddressFormatException {
        ECKey ecKey = Utils.watchKeyFromString(Environment.APETERSSON_2_EUR_PUBKEY, NetworkParameters.testNet());
        assertEquals(ecKey.getPubKey().length,20);
        byte[] expectedResult = {-3, 115, 126, -15, 47, -17, -81, 12, 8, 121, 28, 112, 124, 73, 117, 41, -36, 124, 122, 37};
        assertArrayEquals(expectedResult, ecKey.getPubKey());
    }

/*
     @Test
    public void isMine(){
         NetworkParameters params = NetworkParameters.unitTests();
         Wallet wallet = new Wallet(params);
         TransactionOutput fakeOutput = new TransactionOutput(params, null, null, null);

     }
*/

}
