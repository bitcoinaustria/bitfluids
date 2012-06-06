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

/**
 * @author apetersson
 */
public class FormatTest {
    @Test
    public void testEuroFormat(){
        //hmm this does not fail. in my emulator this does _not always_ work correctly.
        // must be something with the compiler missing UTF-8 encoded .java files.
        assertEquals("2,00 €",Utils.eurDF.format(2.0));
    }
}
