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

package at.bitcoin_austria.bitfluids.trafficSignal;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import at.bitcoin_austria.bitfluids.BitcoinTransactionListener;
import at.bitcoin_austria.bitfluids.Consumer;
import at.bitcoin_austria.bitfluids.PriceService;

/**
 * @author apetersson
 */
public class TrafficSignal {

    private final Context context;
    private final PriceService priceService;
    private final BitcoinTransactionListener bitcoinNet;

    public TrafficSignal(Context context, BitcoinTransactionListener bitcoinNet, PriceService priceService) {
        this.bitcoinNet = bitcoinNet;
        this.context = context;
        this.priceService = priceService;
    }

    /**
     * @param trafficSignReciever where you will be notified
     * @return the BroadcastReceiver to unregister by the UI
     */
    public BroadcastReceiver addNotifier(final TrafficSignalReciever trafficSignReciever) {
        checkInternet(trafficSignReciever);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                checkInternet(trafficSignReciever);
            }
        };
        context.registerReceiver(receiver, intentFilter);
        bitcoinNet.addPeerCountListener(new Consumer<Integer>() {
            @Override
            public void consume(Integer count) {
                if (count < 1) {
                    trafficSignReciever.onStatusChanged(SignalType.PEERS, Status.RED);
                } else if (count < BitcoinTransactionListener.MAX_CONNECTIONS) {
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
