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

import android.os.AsyncTask;
import android.widget.TextView;

import java.util.Date;

/**
 * a good read is
 * http://dl.google.com/googleio/2010/android-developing-RESTful-android
 * -apps.pdf but this is much simpler …
 *
 * @author schilly
 */
final class QueryBtcEur extends AsyncTask<Void, String, Double> {
    private final TextView txt_view;
    private final BitFluidsMainActivity activity;
    private final PriceService priceService;

    QueryBtcEur(BitFluidsMainActivity activity, PriceService priceService) {
        this.activity = activity;
        this.priceService = priceService;
        this.txt_view = (TextView) activity.findViewById(R.id.recent_activity);
    }

    @Override
    protected Double doInBackground(Void... v) {
        publishProgress("connecting …");
        try {
            return priceService.getEurQuote();
        } catch (RemoteSystemFail remoteSystemFail) {
            publishProgress("ERROR: " + remoteSystemFail.getMessage());
            return null;
        }
    }

    /**
     * runs on ui thread and publishes info strings
     */
    @Override
    protected void onProgressUpdate(String... val) {
        txt_view.setText(val[0]);
    }

    /**
     * this one runs on the UI thread
     */
    @Override
    protected void onPostExecute(Double btceur) {
        StringBuilder text = new StringBuilder();
        if (btceur != null) {
            activity.getState().btceur = btceur;
            final double eur1_5 = FluidType.COLA.getEuroPrice() / btceur;
            final double eur2_0 = FluidType.MATE.getEuroPrice()/ btceur;
            text.append("1฿ = ").append(btceur).append("€");
            //todo draw QR codes in background, this is apparently slow and blocks main thread
            activity.drawQrCodes(eur1_5, FluidType.COLA.getEuroPrice(), eur2_0, FluidType.MATE.getEuroPrice());
        } else {
            text.append("<Error: NULL>");
        }
        text.append("\num ").append(Utils.timeFmt.format(new Date()));
        activity.getState().txt_view_state = text.toString();
        this.txt_view.setText(text.toString());
    }
}