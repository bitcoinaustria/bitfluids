package at.bitcoin_austria.bitfluids;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.AsyncTask;
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

import com.google.bitcoin.core.*;

public class BitFluidsMainActivity extends Activity {
    Environment env = Environment.PROD;

    final private Handler uiHandler = new Handler();

    // wake lock for preventing the screen from sleeping
    private PowerManager.WakeLock wakeLock;

    // list of transactions, change it via the ArrayAdapter
    private ListView list_view_tx;
    private ArrayAdapter<String> list_view_array;

    private Spinner alk_dropdown;
    private Spinner nonalk_dropdown;
    private TextView first_line;
    private BitFluidsActivityState state;

    private ImageView qr_alk;
    private ImageView qr_nonalk;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private LinkedList<String> transactionList;

    BitFluidsActivityState getState() {
        return state;
    }

    /**
     * usual binder for ui elements
     */
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

    /**
     * Called when the activity is first created.
     */
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
            for (ArrayAdapter<String> adapter : new ArrayAdapter[]{spinner_adapter_alk, spinner_adapter_nonalk}) {
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
            transactionList = new LinkedList<String>();
            transactionList.add("test123");
            // … and here we would need a custom array adapter
            list_view_array = new ArrayAdapter<String>(this, R.layout.list_tx_item, transactionList);
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
            scheduler.scheduleAtFixedRate(queryBtcEurTask, 10 * 60, 10 * 60, TimeUnit.SECONDS);
        }

        { // start background task to listen to incoming TX
            final NetworkParameters btcNetworkParams = NetworkParameters.testNet();
            final Wallet wallet = createWallet(btcNetworkParams);
            // log private keys
            /*    for (ECKey key : wallet.keychain) {
                Log.i("KEYS", key.toAddress(btcNetworkParams) + " == " + key.getPrivateKeyEncoded(btcNetworkParams));
            }*/

            /*  // NOTE: to hardcode the keys, one needs to decode these encoded keys like
            // this (testnet keys in this case)
            try {
              ECKey hardcoded = new DumpedPrivateKey(NetworkParameters.testNet(),
                  "92YE7WpfwRqFbS3gbTjo5myKq7Z9JuFpoywDmPpT7TsHFL1Dxeg").getKey();
              if (!"mi7cuBr4s1tuH9A6Z7b7Wn3AW86Zhvoyzy".equals(hardcoded.toAddress(NetworkParameters.testNet()).toString())) {
                Log.w("KEYS", "test for restoring pub/private key failed");
              }
            } catch (AddressFormatException afe) {
              afe.printStackTrace();
            }*/

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
                public void onCoinsReceived(Wallet wallet, Transaction tx, BigInteger prevBalange, BigInteger newBalance) {
                    //todo this is not yet fired, for now use a workaround with TxNotifier
                   /* BigInteger sentToMe = tx.getValueSentToMe(wallet);
                    final String out = Utils.uriDF.format(sentToMe.doubleValue()) + "฿ erhalten";
                    // w/o the uiHandler, it would have been on the wrong thread …
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            list_view_array.insert(out, 0);
                        }
                    });*/
                }

                @Override
                public void onCoinsSent(Wallet wallet, Transaction tx, BigInteger prevBalance, BigInteger newBalance) {
                }

                @Override
                public void onReorganize(Wallet wallet) {
                }

                @Override
                public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx) {
                }
            });

            scheduler.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    //noinspection unchecked
                    new AsyncTask<Void, Void, String>() {
                        @Override
                        protected void onPostExecute(String s) {
                            transactionList.add(s);
                            if (transactionList.size() > 5) {
                                transactionList.remove(0);
                            }
                            list_view_array.notifyDataSetChanged();
                        }

                        @Override
                        protected String doInBackground(Void... voids) {
                            String s = state.dlBlockstore.getStatus();
                            return s;

                        }
                    }.execute();
                }
            }, 5, 60, TimeUnit.SECONDS);
            if (state.dlBlockstore == null) {
                state.dlBlockstore = new DlBlockstoreThread(env, getExternalFilesDir(null), wallet,new TxNotifier() {
                    @Override
                    public void onValue(final BigInteger satoshis, final ECKey key) {

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                transactionList.add("recieved: "+ satoshis.toString());
                                if (transactionList.size() > 5) {
                                    transactionList.remove(0);
                                }
                                list_view_array.notifyDataSetChanged();
                            }
                        });
                    }
                });
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
     * @param btcNetworkParams test or prod
     * @return get a working wallet
     */
    private Wallet createWallet(NetworkParameters btcNetworkParams) {
        try {
            File walletFile = new File(env.getWalletFilename());
            if (walletFile.exists()) {
                Log.i("KEYS", "restoring wallet from file");
                FileInputStream wallet_fis = openFileInput(env.getWalletFilename());
                return Wallet.loadFromFileStream(wallet_fis);
            } else {
                Log.w("WALLET", "creating new wallet");
                final Wallet wallet = new Wallet(btcNetworkParams);
                wallet.addKey(env.getKey200());
                wallet.addKey(env.getKey150());
                FileOutputStream wallet_fos = openFileOutput(env.getWalletFilename(), Context.MODE_PRIVATE);
                wallet.saveToFileStream(wallet_fos);
                return wallet;
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

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
    }

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
