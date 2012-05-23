package at.bitcoin_austria.bitfluids;

import com.google.bitcoin.core.ECKey;

import java.math.BigInteger;

public interface TxNotifier {
    void onValue(BigInteger satoshis, ECKey key);
}
