package at.bitcoin_austria.bitfluids;

import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.Wallet;

import java.io.File;
import java.math.BigInteger;

/**
 * Created by IntelliJ IDEA.
 * User: Andreas
 * Date: 12.03.12
 * Time: 00:48
 */
public class ProdMain {

    public static void main(String[] args)  {
        File tempBlockStore = new File("tempBlockStore");
        tempBlockStore.mkdirs();
        Environment thisEnv = Environment.PROD;
        NetworkParameters testParams = thisEnv.getNetworkParams();
        Wallet testWallet = new Wallet(testParams);
        testWallet.addKey(thisEnv.getKey200());
        testWallet.addKey(thisEnv.getKey150());
        DlBlockstoreThread dlBlockstoreThread = new DlBlockstoreThread(thisEnv, tempBlockStore, testWallet,new TxNotifier() {
            @Override
            public void onValue(BigInteger satoshis, ECKey key) {

            }
        });
        dlBlockstoreThread.setDaemon(false);
        dlBlockstoreThread.start();
        while (true) {
            try {
                Thread.sleep(1000);
                System.out.println(dlBlockstoreThread.getStatus());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
