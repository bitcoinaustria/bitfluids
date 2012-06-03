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

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.http.AndroidHttpClient;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;
import at.bitcoin_austria.bitfluids.trafficSignal.SignalType;
import at.bitcoin_austria.bitfluids.trafficSignal.Status;
import at.bitcoin_austria.bitfluids.trafficSignal.TrafficSignal;
import at.bitcoin_austria.bitfluids.trafficSignal.TrafficSignalReciever;
import com.google.bitcoin.core.Address;

import java.math.BigDecimal;
import java.util.LinkedList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BitFluidsMainActivity extends Activity {
    Environment env = Environment.PROD;

    final private Handler uiHandler = new Handler();

    // wake lock for preventing the screen from sleeping
    private PowerManager.WakeLock wakeLock;

    // list of transactions, change it via the ArrayAdapter
    private ListView list_view_tx;
    private ArrayAdapter<String> list_view_array;

    private TextView first_line;
    private BitFluidsActivityState state;

    private ImageView qr_alk;
    private ImageView qr_nonalk;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private LinkedList<String> transactionList;

    //service dependencies, initialized in constructor, run/shutdown in onCreate onDestroy
    private final PriceService priceService;
    private final TrafficSignal trafficSignal;
    private final CasualListener bitcoinTransactionListener;
    BroadcastReceiver netStatusReciever;

    public BitFluidsMainActivity() {
        priceService = new PriceService(AndroidHttpClient.newInstance("Bitfluids 0.1"));
        bitcoinTransactionListener = new CasualListener(env);
        trafficSignal = new TrafficSignal(this, bitcoinTransactionListener, priceService);
    }

    BitFluidsActivityState getState() {
        return state;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (netStatusReciever != null) {
            unregisterReceiver(netStatusReciever);
            netStatusReciever = null;
        }
        bitcoinTransactionListener.shutdown();
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
        }
        bind();

        // what follows is a list of initializations, encapsulated into {} blocks

        { // prevent the display from dimming
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "DoNotDimScreen");
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

        {
            final TextView NetStatus = (TextView) findViewById(R.id.NetStatus);
            final TextView ExchStatus = (TextView) findViewById(R.id.ExchStatus);
            final TextView P2PStatus = (TextView) findViewById(R.id.P2PStatus);

            int unknown = Color.CYAN;
            NetStatus.setBackgroundColor(unknown);
            ExchStatus.setBackgroundColor(unknown);
            P2PStatus.setBackgroundColor(unknown);
            // start background task to singal Status
            trafficSignal.addNotifier(new TrafficSignalReciever() {
                @Override
                public void onStatusChanged(final SignalType signalType, final Status status) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            switch (signalType) { //switch is generally ugly, perhaps this should move into enum?
                                case NETWORK:
                                    NetStatus.setBackgroundColor(status.androidColor);
                                    break;
                                case EXCHANGERATE:
                                    ExchStatus.setBackgroundColor(status.androidColor);
                                    break;
                                case PEERS:
                                    P2PStatus.setBackgroundColor(status.androidColor);
                                    break;
                                default:
                                    throw new IllegalArgumentException("unexpected signal :" + signalType);

                            }
                        }
                    });
                }
            });
        }


        {
            // first time on UI thread, to see exceptions properly
            new QueryBtcEur(BitFluidsMainActivity.this, priceService).execute();
            // query mt gox, every 10 minutes
            final Runnable queryBtcEurTask = new Runnable() {
                @Override
                public void run() {
                    QueryBtcEur btcEur = new QueryBtcEur(BitFluidsMainActivity.this, priceService);
                    btcEur.execute();
                }
            };
            scheduler.scheduleAtFixedRate(queryBtcEurTask, 10, 10 * 60, TimeUnit.SECONDS);
        }

        {
            Tx2FluidsAdapter adapter = new Tx2FluidsAdapter(priceService, env);
            TxNotifier convert = adapter.convert(new FluidsNotifier() {
                @Override
                    public void onFluidPaid(final FluidType type, final BigDecimal amount) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                transactionList.add(type.getDescription() + " " + amount + " Stück");
                                if (transactionList.size() > 5) {
                                    transactionList.remove(0);
                                }
                                list_view_array.notifyDataSetChanged();
                            }
                        });
                    }

                    @Override
                    public void onError(String message, FluidType type, BigDecimal bitcoins) {

                    }
            });
            bitcoinTransactionListener.addNotifier(convert);
        }


        { // click on QR code copies public address (after wallet init!)
            class QrClickListener implements OnClickListener {
                final private Address addr;

                public QrClickListener(Address addr) {
                    this.addr = addr;
                }

                @Override
                public void onClick(View v) {
                    copyToClipboard(addr.toString());
                    String t = "Address " + addr + " copied to clipboard.";
                    Toast.makeText(getApplicationContext(), t, Toast.LENGTH_SHORT).show();
                }
            }

            qr_alk.setOnClickListener(new QrClickListener(env.getKey200()));
            qr_nonalk.setOnClickListener(new QrClickListener(env.getKey150()));
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

    private Bitmap drawOneQrCode(int id, int id_txt, double amountBtc, double amountEur, Address addr) {
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
        state.qr_alk_img = drawOneQrCode(R.id.qr_code_alk, R.id.qr_code_alk_txt, btc2_0, eur2_0, env.getKey200());
        state.qr_nonalk_img = drawOneQrCode(R.id.qr_code_nonalk, R.id.qr_code_nonalk_txt, btc1_5, eur1_5,
                env.getKey150());
    }

    final void copyToClipboard(final String txt) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setText(txt);
    }
}
