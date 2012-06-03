package at.bitcoin_austria.bitfluids.trafficSignal;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import at.bitcoin_austria.bitfluids.CasualListener;
import at.bitcoin_austria.bitfluids.PriceService;
import at.bitcoin_austria.bitfluids.RemoteSystemFail;
import com.google.common.base.Preconditions;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author apetersson
 */
//todo switch this to push-based to remove redundant checks...
public  class TrafficSignal {

    private final Context context;
    private final CasualListener bitcoinNet;
    private final PriceService priceService;

    public TrafficSignal(Context context, CasualListener bitcoinNet, PriceService priceService, TrafficSignReciever trafficSignReciever) {
        this.context = Preconditions.checkNotNull(context);
        this.bitcoinNet = Preconditions.checkNotNull(bitcoinNet);
        this.priceService = Preconditions.checkNotNull(priceService);
    }

    public enum SignalType {
        NETWORK, EXCHANGERATE, PEERS
    }

    public enum Status {
        RED(Color.RED), YELLOW(Color.YELLOW), GREEN(Color.GREEN);
        public final int androidColor;

        private Status(int androidColor) {
            this.androidColor = androidColor;
        }
    }


    public interface TrafficSignReciever{
        void onStatusChanged(SignalType signalType,Status status);
    }


    private Map<SignalType, Status> getSignal() {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        Map<SignalType, Status> ret = new LinkedHashMap<SignalType, Status>();
        ret.put(SignalType.NETWORK, activeNetworkInfo == null ? Status.RED : Status.GREEN);

        Integer connected = bitcoinNet.getNumberConnected();


        final Status p2pStatus;
        if (connected == null) {
            p2pStatus = Status.RED;
        } else if (connected < 6) {
            p2pStatus = Status.YELLOW;
        } else {
            p2pStatus = Status.GREEN;
        }
        ret.put(SignalType.PEERS, p2pStatus);

        Status priceStatus;
        try {
            Double eurQuote = priceService.getEurQuote();
            priceStatus = eurQuote > 0 ? Status.GREEN : Status.YELLOW;
        } catch (RemoteSystemFail remoteSystemFail) {
            priceStatus = Status.RED;
        }
        ret.put(SignalType.EXCHANGERATE, priceStatus);
        return Collections.unmodifiableMap(ret);
    }
}
