package at.bitcoin_austria.bitfluids.trafficSignal;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import at.bitcoin_austria.bitfluids.*;

/**
 * @author apetersson
 */
public  class TrafficSignal {

    private final Context context;
    private final PriceService priceService;
    private final CasualListener bitcoinNet;

    public TrafficSignal(Context context, CasualListener bitcoinNet, PriceService priceService) {
        this.bitcoinNet = bitcoinNet;
        this.context = context;
        this.priceService = priceService;
    }

    /**
     * @param trafficSignReciever where you will be notified
     * @return the BroadcastReceiver to unregister
     */
    public BroadcastReceiver addNotifier(final TrafficSignalReciever trafficSignReciever){
        checkInternet(trafficSignReciever);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                checkInternet(trafficSignReciever);
            }
        };
        context.registerReceiver(receiver,intentFilter);
        bitcoinNet.addPeerCountListener(new Consumer<Integer>() {
            @Override
            public void consume(Integer count) {
                if (count < 1) {
                    trafficSignReciever.onStatusChanged(SignalType.PEERS, Status.RED);
                } else if (count < 4) {
                    trafficSignReciever.onStatusChanged(SignalType.PEERS, Status.YELLOW);
                } else {
                    trafficSignReciever.onStatusChanged(SignalType.PEERS, Status.GREEN);
                }
            }
        });
        priceService.addLastQuoteListener(new Consumer<Status>() {
            @Override
            public void consume(Status status) {
                trafficSignReciever.onStatusChanged(SignalType.EXCHANGERATE, status);
            }
        });
        return receiver;
    }

    private void checkInternet(TrafficSignalReciever trafficSignalReciever) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        Status status = activeNetworkInfo == null ? Status.RED : Status.GREEN;
        trafficSignalReciever.onStatusChanged(SignalType.NETWORK, status);
    }
}
