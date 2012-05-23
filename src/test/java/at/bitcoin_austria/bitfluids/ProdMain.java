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
