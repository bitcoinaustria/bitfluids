package at.bitcoin_austria.bitfluids;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.BlockChain;
import com.google.bitcoin.core.DownloadListener;
import com.google.bitcoin.core.DumpedPrivateKey;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.PeerAddress;
import com.google.bitcoin.core.PeerGroup;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.WalletEventListener;
import com.google.bitcoin.discovery.DnsDiscovery;
import com.google.bitcoin.discovery.PeerDiscoveryException;
import com.google.bitcoin.store.BlockStore;
import com.google.bitcoin.store.BlockStoreException;
import com.google.bitcoin.store.BoundedOverheadBlockStore;

public class BitFluidsMainActivity extends Activity {
  final private Handler          uiHandler = new Handler();

  // wake lock for preventing the screen from sleeping
  private PowerManager.WakeLock  wakeLock;

  // list of transactions, change it via the ArrayAdapter
  private ListView               list_view_tx;
  private ArrayAdapter<String>   list_view_array;

  private Spinner                alk_dropdown;
  private Spinner                nonalk_dropdown;
  private TextView               first_line;
  private BitFluidsActivityState state;

  private ImageView              qr_alk;
  private ImageView              qr_nonalk;

  /**
   * this <b>static</b> inner class stores the state, including the background
   * AsyncTask, for restoring when Activity restarts.
   * 
   * @author schilly
   * 
   */
  static class BitFluidsActivityState {
    // TODO this will be bitcoin addresses, instead of just strings.
    private String addr_eur_1_5;
    private String addr_eur_2_0;
    Bitmap         qr_nonalk_img;
    Bitmap         qr_alk_img;
    String         txt_view_state;
    Double         btceur;
    Thread         dlBlockstore;

    @Override
    public String toString() {
      return String.format("State: %f btc/eur;%s;%s", btceur, addr_eur_1_5, addr_eur_2_0);
    }
  }

  BitFluidsActivityState getState() {
    return state;
  }

  /** usual binder for ui elements */
  private void bind() {
    list_view_tx = (ListView) findViewById(R.id.list_view_tx);
    qr_alk = (ImageView) findViewById(R.id.qr_code_alk);
    qr_nonalk = (ImageView) findViewById(R.id.qr_code_nonalk);
    if (state.qr_alk_img == null) {
      qr_alk.setImageBitmap(state.qr_alk_img);
    }
    if (state.qr_nonalk_img == null) {
      qr_nonalk.setImageBitmap(state.qr_nonalk_img);
    }
    alk_dropdown = (Spinner) findViewById(R.id.qr_alk_amount);
    nonalk_dropdown = (Spinner) findViewById(R.id.qr_nonalk_amount);
    first_line = (TextView) findViewById(R.id.first_line);
    if (state.txt_view_state != null) {
      first_line.setText(state.txt_view_state);
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    wakeLock.release();
  }

  /**
   * Save the state, this will be passed-in again in {@link #onCreate(Bundle)}
   * 
   * @return
   */
  @Override
  public Object onRetainNonConfigurationInstance() {
    return state;
  }

  @Override
  protected void onResume() {
    super.onResume();
    wakeLock.acquire();
  }

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);

    // if this is not null, we are restarted …
    state = (BitFluidsActivityState) getLastNonConfigurationInstance();
    if (state == null) {
      state = new BitFluidsActivityState();
    } else {
      Log.i("STATE", "resuming from known state: " + state);
      Log.i("STATE", "dlBlockchain: " + state.dlBlockstore.isAlive());
    }
    bind();

    // what follows is a list of initializations, encapsulated into {} blocks

    { // prevent the display from dimming
      PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
      wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "DoNotDimScreen");
    }

    { // fill dropdown spinners
      ArrayAdapter<String> spinner_adapter_alk = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item,
          new ArrayList<String>(5));
      ArrayAdapter<String> spinner_adapter_nonalk = new ArrayAdapter<String>(this,
          android.R.layout.simple_spinner_item, new ArrayList<String>(5));
      for (ArrayAdapter<String> adapter : new ArrayAdapter[] { spinner_adapter_alk, spinner_adapter_nonalk }) {
        for (int i = 1; i <= 5; i++) {
          adapter.add(i + "x");
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
      }
      alk_dropdown.setAdapter(spinner_adapter_alk);
      nonalk_dropdown.setAdapter(spinner_adapter_nonalk);
    }

    { // size of QR codes should be a bit less than 1/3rd of (longest) screen
      // dimension - the layout default is ok, but this should work better.
      int displayWidth = getWindowManager().getDefaultDisplay().getWidth();
      int displayHeight = getWindowManager().getDefaultDisplay().getHeight();
      int qrSize = (int) (Math.max(displayWidth, displayHeight) / 3.0 * 0.9);
      LinearLayout.LayoutParams qr_ll = new LinearLayout.LayoutParams(qrSize, qrSize);
      qr_alk.setLayoutParams(qr_ll);
      qr_nonalk.setLayoutParams(qr_ll);
    }

    { // init the tx list
      // this will be tx objects …
      ArrayList<String> test_items = new ArrayList<String>();
      // … and here we would need a custom array adapter
      list_view_array = new ArrayAdapter<String>(this, R.layout.list_tx_item, test_items);
      list_view_tx.setAdapter(list_view_array);

      list_view_tx.setOnItemClickListener(new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
          Toast.makeText(getApplicationContext(), ((TextView) view).getText(), Toast.LENGTH_SHORT).show();
        }
      });
    }

    // first time on UI thread, to see exceptions properly
    new QueryBtcEur(BitFluidsMainActivity.this).execute();

    { // refresh when clicking on the text or button
      OnClickListener refreshClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
          QueryBtcEur btcEur = new QueryBtcEur(BitFluidsMainActivity.this);
          btcEur.execute();
        }
      };
      findViewById(R.id.first_line).setOnClickListener(refreshClickListener);
      findViewById(R.id.btn_refresh).setOnClickListener(refreshClickListener);
    }

    { // query mt gox, every 10 minutes
      final Runnable queryBtcEurTask = new Runnable() {
        @Override
        public void run() {
          QueryBtcEur btcEur = new QueryBtcEur(BitFluidsMainActivity.this);
          btcEur.execute();
        }
      };
      ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
      scheduler.scheduleAtFixedRate(queryBtcEurTask, 10 * 60, 10 * 60, TimeUnit.SECONDS);
    }

    { // start background task to listen to incoming TX
      final NetworkParameters btcNetworkParams = NetworkParameters.testNet();
      final FileOutputStream wallet_fos;
      final FileInputStream wallet_fis;
      final String filename_wallet = "bitfluids.wallet";
      // final File walletFile = new File(getExternalFilesDir(null),
      // filename_wallet);

      // aim of this block is to get a finalized ref to a working wallet
      final Wallet wallet;
      {
        Wallet wallet_tmp = null;
        try {
          wallet_fis = openFileInput(filename_wallet);
          wallet_tmp = Wallet.loadFromFileStream(wallet_fis);
          // wallet = Wallet.loadFromFile(walletFile);
          Log.i("KEYS", "successfully restored wallet");
        } catch (FileNotFoundException fnfe) {
          Log.w("WALLET", "creating new wallet");
          wallet_tmp = new Wallet(btcNetworkParams);
          wallet_tmp.keychain.add(new ECKey());
          wallet_tmp.keychain.add(new ECKey());
          try {
            // tmp_wallet.saveToFile(walletFile);
            wallet_fos = openFileOutput(filename_wallet, Context.MODE_PRIVATE);
            wallet_tmp.saveToFileStream(wallet_fos);
          } catch (IOException e1) {
            e1.printStackTrace();
          }
        } catch (IOException e) {
          e.printStackTrace();
        }
        // better way to do this?
        wallet = wallet_tmp;
      }
      // log private keys
      for (ECKey key : wallet.keychain) {
        Log.i("KEYS", key.toAddress(btcNetworkParams) + " == " + key.getPrivateKeyEncoded(btcNetworkParams));
      }

      // NOTE: to hardcode the keys, one needs to decode these encoded keys like
      // this (testnet keys in this case)
      try {
        ECKey hardcoded = new DumpedPrivateKey(NetworkParameters.testNet(),
            "92YE7WpfwRqFbS3gbTjo5myKq7Z9JuFpoywDmPpT7TsHFL1Dxeg").getKey();
        if (!"mi7cuBr4s1tuH9A6Z7b7Wn3AW86Zhvoyzy".equals(hardcoded.toAddress(NetworkParameters.testNet()).toString())) {
          Log.w("KEYS", "test for restoring pub/private key failed");
        }
      } catch (AddressFormatException afe) {
        afe.printStackTrace();
      }

      // we should have two keys in our wallet now
      state.addr_eur_1_5 = wallet.keychain.get(0).toAddress(btcNetworkParams).toString();
      state.addr_eur_2_0 = wallet.keychain.get(1).toAddress(btcNetworkParams).toString();

      // show known transactions
      for (Transaction tx : wallet.getTransactionsByTime()) {
        BigInteger sentToMe = tx.getValueSentToMe(wallet);
        String out = Utils.uriDF.format(sentToMe.doubleValue()) + "฿ erhalten";
        list_view_array.insert(out, 0);
      }

      wallet.addEventListener(new WalletEventListener() {
        @Override
        public void onReorganize(Wallet arg0) {
        }

        @Override
        public void onDeadTransaction(Wallet arg0, Transaction arg1, Transaction arg2) {
        }

        @Override
        public void onCoinsReceived(Wallet wallet, Transaction tx, BigInteger prevBalange, BigInteger newBalance) {
          BigInteger sentToMe = tx.getValueSentToMe(wallet);
          final String out = Utils.uriDF.format(sentToMe.doubleValue()) + "฿ erhalten";
          // w/o the uiHandler, it would have been on the wrong thread …
          uiHandler.post(new Runnable() {
            @Override
            public void run() {
              list_view_array.insert(out, 0);
            }
          });

          // do we have to do this?
          try {
            FileOutputStream wallet_fos = openFileOutput(filename_wallet, Context.MODE_PRIVATE);
            wallet.saveToFileStream(wallet_fos);
          } catch (IOException e1) {
            e1.printStackTrace();
          }
        }
      });

      if (state.dlBlockstore == null) {
        // be careful to not leak this activity into this thread, hence use a
        // static inner class!
        state.dlBlockstore = new DlBlockstoreThread(btcNetworkParams, getExternalFilesDir(null), wallet);
        state.dlBlockstore.start();
      }
    }

    { // click on QR code copies public address (after wallet init!)
      class QrClickListener implements OnClickListener {
        final private String addr;

        public QrClickListener(String addr) {
          this.addr = addr;
        }

        @Override
        public void onClick(View v) {
          copyToClipboard(addr);
          String t = "Address " + addr + " copied to clipboard.";
          Toast.makeText(getApplicationContext(), t, Toast.LENGTH_SHORT).show();
        }
      }

      qr_alk.setOnClickListener(new QrClickListener(state.addr_eur_2_0));
      qr_nonalk.setOnClickListener(new QrClickListener(state.addr_eur_1_5));
    }
  }

  /**
   * this needs to be a static class to avoid leaking main activity (?)
   * 
   * http://www.vogella.de/articles/AndroidPerformance/article.html#
   * concurrency_handler
   * 
   * @author schilly
   * 
   */
  static class DlBlockstoreThread extends Thread {
    private final NetworkParameters btcNetworkParams;
    private final File              extFilesDir;
    private final Wallet            wallet;

    public DlBlockstoreThread(NetworkParameters btcNetworkParams, File extFilesDir, Wallet wallet) {
      this.btcNetworkParams = btcNetworkParams;
      this.wallet = wallet;
      this.extFilesDir = extFilesDir;
    }

    @Override
    public void run() {
      BlockStore blockStore;
      try {
        // we store this on the SD card, in
        // /Android/data/at.bitcoin_austria..../
        // there will be an exception wheren there is no
        // "getExternalFilesDir()". also, never store sensitive info
        // there!!!
        blockStore = new BoundedOverheadBlockStore(btcNetworkParams, new File(extFilesDir, "bitfluids.blockchain"));
        BlockChain chain = new BlockChain(btcNetworkParams, wallet, blockStore);

        final PeerGroup peerGroup = new PeerGroup(blockStore, btcNetworkParams, chain);
        DnsDiscovery dnsDiscovery = new DnsDiscovery(btcNetworkParams);
        // IrcDiscovery ircDiscovery = new IrcDiscovery("channel ???");

        for (InetSocketAddress ip : dnsDiscovery.getPeers()) {
          peerGroup.addAddress(new PeerAddress(ip));
        }
        peerGroup.start();

        DownloadListener listener = new DownloadListener();
        peerGroup.startBlockChainDownload(listener);

      } catch (PeerDiscoveryException e) {
        e.printStackTrace();
      } catch (BlockStoreException e) {
        e.printStackTrace();
      }
    }
  };

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);

    // this doesn't work.

    // the idea is to change the view layout when the device is rotated.
    // in the android manifest, the param
    // android:configChanges="orientation|keyboardHidden"
    // already prevents this activity to be restartet (and hence all it's
    // async tasks, but the view isn't updated. this should happen here …

    /*
     * switch (newConfig.orientation) { case Configuration.ORIENTATION_PORTRAIT:
     * setContentView(R.id.base_layout_portrait); break;
     * 
     * default: // Configuration.ORIENTATION_LANDSCAPE:
     * setContentView(R.id.base_layout_landscape); break; }
     */
  };

  private Bitmap drawOneQrCode(int id, int id_txt, double amountBtc, double amountEur, String addr) {
    String uri = Utils.makeBitcoinUri(addr, amountBtc);
    Bitmap qr_bitmap = Utils.getQRCodeBitmap(uri, 512);
    ImageView qr_image_view = (ImageView) findViewById(id);
    qr_image_view.setImageBitmap(qr_bitmap);
    TextView qr_txt = ((TextView) findViewById(id_txt));
    String txt = Utils.uriDF.format(amountBtc) + "฿\n" + "(~" + Utils.eurDF.format(amountEur) + ")";
    qr_txt.setText(txt);
    return qr_bitmap;
  }

  void drawQrCodes(double btc1_5, double eur1_5, double btc2_0, double eur2_0) {
    if (state.addr_eur_1_5 == null)
      return;
    state.qr_alk_img = drawOneQrCode(R.id.qr_code_alk, R.id.qr_code_alk_txt, btc2_0, eur2_0, state.addr_eur_2_0);
    state.qr_nonalk_img = drawOneQrCode(R.id.qr_code_nonalk, R.id.qr_code_nonalk_txt, btc1_5, eur1_5,
        state.addr_eur_1_5);
  }

  final void copyToClipboard(final String txt) {
    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
    clipboard.setText(txt);
  }
}
