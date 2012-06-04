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
