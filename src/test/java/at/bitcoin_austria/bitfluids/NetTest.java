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

import org.apache.http.client.HttpClient;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * @author apetersson
 */
public abstract class NetTest {
  public static final Logger LOGGER = LoggerFactory.getLogger(NetTest.class);

  // give an implementation that ignores SSL certs. in standard java the mtgox
  // SSL cert does not validate.
  public static HttpClient wrapClient(HttpClient base) {
    try {
      SSLContext ctx = SSLContext.getInstance("TLS");
      X509TrustManager tm = new X509TrustManager() {

        @Override
        public void checkClientTrusted(X509Certificate[] xcs, String string) throws CertificateException {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] xcs, String string) throws CertificateException {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
          return null;
        }
      };
      ctx.init(null, new TrustManager[] { tm }, null);
      SSLSocketFactory ssf = new SSLSocketFactory(ctx);
      ssf.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
      ClientConnectionManager ccm = base.getConnectionManager();
      SchemeRegistry sr = ccm.getSchemeRegistry();
      sr.register(new Scheme("https", ssf, 443));
      return new DefaultHttpClient(ccm, base.getParams());
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  protected final void runTest() {
    Environment thisEnv = getEnvironment();
    File tempBlockStore = new File(thisEnv.getBlockChainFilename());
    tempBlockStore.mkdirs();
    Tx2FluidsAdapter adapter = new Tx2FluidsAdapter(new PriceService(wrapClient(new DefaultHttpClient())), thisEnv);
    TxNotifier notifier = adapter.convert(new FluidsNotifier() {
      @Override
      public void onFluidPaid(TransactionItem transactionItem) {
          LOGGER.info("someone paid for " + transactionItem);
      }

      @Override
      public void onError(String message, FluidType type, Bitcoins bitcoins) {
        LOGGER.warn("someone paid for " + bitcoins + " " + type + " ");
      }
    });
      BitcoinTransactionListener listener = new BitcoinTransactionListener(thisEnv);
      listener.init(notifier);
      listener.setStatsNotifier(new Stats() {
          @Override
          public void update(double tpm) {
              System.out.println("tpm = " + tpm);
          }
      });
      while (true) {
      try {
          Thread.sleep(1000);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  protected abstract Environment getEnvironment();
}
