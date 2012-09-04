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
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.http.AndroidHttpClient;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
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
import com.google.bitcoin.core.Sha256Hash;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import java.text.DecimalFormat;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BitFluidsMainActivity extends Activity {
    public static final String TAG = "BF";
    public static final int TTS_ACTION = 1224;
    private final Environment env = Environment.PROD;

    private final Handler uiHandler = new Handler();

    // wake lock for preventing the screen from sleeping
    private PowerManager.WakeLock wakeLock;

    // list of transactions, change it via the ArrayAdapter
    private ListView list_view_tx;
    private ArrayAdapter<TransactionItem> list_view_adapter;

    private BitFluidsActivityState state;

    private ImageView qr_alk;
    private ImageView qr_nonalk;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    //service dependencies, initialized in constructor, run/shutdown in onCreate onDestroy
    private BitcoinTransactionListener bitcoinTransactionListener;
    private BroadcastReceiver netStatusReciever;
    private TextView netStatus;
    private TextView exchStatus;
    private TextView p2pStatus;
    private TextToSpeech textToSpeech;

    public BitFluidsMainActivity() {
        Log.i(TAG, "starting up");
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable throwable) {
                Log.e(TAG, "excpetion!", throwable);
            }
        });
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (textToSpeech != null) {
            textToSpeech.shutdown();
        }
        if (netStatusReciever != null) {
            unregisterReceiver(netStatusReciever);
            netStatusReciever = null;
        }
        bitcoinTransactionListener.shutdown();
        bitcoinTransactionListener = null;
    }


    /**
     * usual binder for ui elements
     */
    private void bind() {
        list_view_tx = (ListView) findViewById(R.id.list_view_tx);
        qr_alk = (ImageView) findViewById(R.id.qr_code_alk);
        qr_nonalk = (ImageView) findViewById(R.id.qr_code_nonalk);

        netStatus = (TextView) findViewById(R.id.NetStatus);
        exchStatus = (TextView) findViewById(R.id.ExchStatus);
        p2pStatus = (TextView) findViewById(R.id.P2PStatus);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        wakeLock.release();
    }

    /**
     * Save the state, this will be passed-in again in {@link #onCreate(Bundle)}
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

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("state", state);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        restoreState(savedInstanceState);
    }

    private void restoreState(Bundle savedInstanceState) {
        Preconditions.checkNotNull(bitcoinTransactionListener);
        if (savedInstanceState != null) {
            state = (BitFluidsActivityState) savedInstanceState.getSerializable("state");
        }
        if (state == null) {
            state = new BitFluidsActivityState();
        }
        bitcoinTransactionListener.addHashes(state.getTransactionItems());
        list_view_adapter = new ArrayAdapter<TransactionItem>(this, R.layout.list_tx_item, Lists.reverse(state.getTransactionItems()));
        list_view_tx.setAdapter(list_view_adapter);
    }

    @Override
    protected void onStop() {
        super.onStop();

    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        bind();
        checkForTTS();

        bitcoinTransactionListener = new BitcoinTransactionListener(env);
        restoreState(savedInstanceState);
        final PriceService priceService = new PriceService(AndroidHttpClient.newInstance("Bitfluids 0.1"));

        // what follows is a list of initializations, encapsulated into {} blocks

        { // prevent the display from dimming
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "DoNotDimScreen");
        }

        { // size of QR codes should be a bit less than 1/3rd of (longest) screen
            // dimension - the layout default is ok, but this should work better.
            int qrSize = 1, displayWidth, displayHeight;
            double scale = 0.8;
            switch (getResources().getConfiguration().orientation) {
                case Configuration.ORIENTATION_LANDSCAPE:
                    displayHeight = getWindowManager().getDefaultDisplay().getHeight();
                    qrSize = (int) ((displayHeight / 2.0) * scale);
                    break;
                case Configuration.ORIENTATION_PORTRAIT:
                    displayWidth = getWindowManager().getDefaultDisplay().getWidth();
                    qrSize = (int) ((displayWidth / 3.0) * scale);
                    break;
                default:
                    displayWidth = getWindowManager().getDefaultDisplay().getWidth();
                    displayHeight = getWindowManager().getDefaultDisplay().getHeight();
                    qrSize = (int) (Math.max(displayWidth, displayHeight) / 3.0 * scale);
            }
            LinearLayout.LayoutParams qr_ll = new LinearLayout.LayoutParams(qrSize, qrSize);
            qr_alk.setLayoutParams(qr_ll);
            qr_nonalk.setLayoutParams(qr_ll);
        }

        { // init the tx list
            // this will be tx objects …
            // … and here we would need a custom array adapter

            list_view_tx.setOnItemClickListener(new OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    Toast.makeText(getApplicationContext(), ((TextView) view).getText(), Toast.LENGTH_SHORT).show();
                }
            });
        }

        {
            int unknown = Color.CYAN;
            netStatus.setBackgroundColor(unknown);
            exchStatus.setBackgroundColor(unknown);
            p2pStatus.setBackgroundColor(unknown);
            // start background task to singal Status
            TrafficSignal trafficSignal = new TrafficSignal(this, bitcoinTransactionListener, priceService);
            netStatusReciever = trafficSignal.addNotifier(new TrafficSignalReciever() {
                @Override
                public void onStatusChanged(final SignalType signalType, final Status status) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            switch (signalType) { //switch is generally ugly, perhaps this should move into enum?
                                case NETWORK:
                                    netStatus.setBackgroundColor(status.androidColor);
                                    break;
                                case EXCHANGERATE:
                                    exchStatus.setBackgroundColor(status.androidColor);
                                    break;
                                case PEERS:
                                    p2pStatus.setBackgroundColor(status.androidColor);
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
            final Runnable queryBtcEurTask = new Runnable() {
                @Override
                public void run() {
                    QueryBtcEur btcEur = new QueryBtcEur(BitFluidsMainActivity.this, priceService, env);
                    //noinspection unchecked
                    btcEur.execute();
                }
            };
            scheduler.scheduleAtFixedRate(queryBtcEurTask, 0, 10 * 60, TimeUnit.SECONDS);
            //todo soll nicht gleichzeitig laufen!
        }

        {
            Tx2FluidsAdapter adapter = new Tx2FluidsAdapter(priceService, env);
            TxNotifier convert = adapter.convert(new FluidsNotifier() {
                @Override
                public void onFluidPaid(final TransactionItem transactionItem) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            state.getTransactionItems().add(transactionItem);
                            list_view_adapter.notifyDataSetChanged();
                            String sentence = transactionItem.buildSentence();
                            if (textToSpeech != null) {
                                textToSpeech.speak(sentence, TextToSpeech.QUEUE_ADD, null);
                            }
                        }
                    });
                }

                @Override
                public void onError(String message, FluidType type, Bitcoins bitcoins) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            state.getTransactionItems().add(new TransactionItem(null, null, 0, Double.NaN, Sha256Hash.ZERO_HASH));
                            list_view_adapter.notifyDataSetChanged();

                        }
                    });
                }
            });
            bitcoinTransactionListener.init(convert);
            final TextView TpsText = (TextView) findViewById(R.id.TPS);
            bitcoinTransactionListener.setStatsNotifier(new Stats() {
                @Override
                public void update(final double tpm) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            TpsText.setText(new DecimalFormat("#.0").format(tpm));
                        }
                    });
                }
            });
        }


        { // click on QR code copies public address (after wallet init!)
            class QrClickListener implements OnClickListener {
                private final Address addr;

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

    private void checkForTTS() {
        Intent checkIntent = new Intent();
        checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        startActivityForResult(checkIntent, TTS_ACTION);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == TTS_ACTION) {
            if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                textToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
                    @Override
                    public void onInit(int i) {
                        Log.i(TAG, "init of text-to-speech finished: " + i);
                    }
                });
                textToSpeech.setLanguage(Locale.GERMAN);
            } else {
                Intent installTTSIntent = new Intent();
                installTTSIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                startActivity(installTTSIntent);
            }
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

    private void drawOneQrCode(int id, int id_txt, Bitcoins amountBtc, double amountEur, Bitmap qr_bitmap) {
        ImageView qr_image_view = (ImageView) findViewById(id);
        qr_image_view.setImageBitmap(qr_bitmap);
        TextView qr_txt = ((TextView) findViewById(id_txt));
        String txt = amountBtc.toCurrencyString() + "\n" + "(~" + Utils.eurDF.format(amountEur) + ")";
        qr_txt.setText(txt);
    }

    void drawQrCodes(Bitmap qrcode1_5, Bitmap qrcode2_0, Bitcoins btc_15, Bitcoins btc_20) {
        drawOneQrCode(R.id.qr_code_nonalk, R.id.qr_code_nonalk_txt, btc_15, FluidType.COLA.getEuroPrice(), qrcode1_5);
        drawOneQrCode(R.id.qr_code_alk, R.id.qr_code_alk_txt, btc_20, FluidType.MATE.getEuroPrice(), qrcode2_0);
    }

    final void copyToClipboard(final String txt) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setText(txt);
    }
}
