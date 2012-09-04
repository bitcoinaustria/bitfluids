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

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.widget.TextView;
import com.google.bitcoin.uri.BitcoinURI;

import java.util.Date;

/**
 * a good read is
 * http://dl.google.com/googleio/2010/android-developing-RESTful-android
 * -apps.pdf but this is much simpler …
 *
 * @author schilly
 */
final class QueryBtcEur extends AsyncTask<Void, String, QueryBtcEur.Data> {
    public static final int SHOW_NUM_DIGITS = 3;
    private final TextView txt_view;
    private final BitFluidsMainActivity activity;
    private final PriceService priceService;
    private final Environment env;

    QueryBtcEur(BitFluidsMainActivity activity, PriceService priceService, Environment env) {
        this.activity = activity;
        this.priceService = priceService;
        this.env = env;
        this.txt_view = (TextView) activity.findViewById(R.id.recent_activity);
    }

    protected static class Data {
        final Double price;
        final Bitmap qrcode2_0;
        final Bitmap qrcode1_5;
        final Bitcoins price15;
        final Bitcoins price20;


        private Data(Double price, Bitmap qrcode2_0, Bitmap qrcode1_5, Bitcoins price15, Bitcoins price20) {
            this.price = price;
            this.qrcode2_0 = qrcode2_0;
            this.qrcode1_5 = qrcode1_5;
            this.price15 = price15;
            this.price20 = price20;
        }
    }

    @Override
    protected QueryBtcEur.Data doInBackground(Void... v) {
        publishProgress("connecting …");
        try {
            Double btceur = priceService.getEurQuote();
            Bitcoins price150 = roundedBitcoins(FluidType.COLA.getEuroPrice() / btceur);
            Bitcoins price200 = roundedBitcoins(FluidType.MATE.getEuroPrice() / btceur);
            String uri150 = BitcoinURI.convertToBitcoinURI(env.getKey150(), price150.toBigInteger(), FluidType.COLA.getDescription(),null);
            String uri200 = BitcoinURI.convertToBitcoinURI(env.getKey200(), price200.toBigInteger(), FluidType.MATE.getDescription(), null);
            Bitmap bitmap150 = Utils.getQRCodeBitmap(uri150, 512);
            Bitmap bitmap200 = Utils.getQRCodeBitmap(uri200, 512);
            return new Data(btceur,bitmap200,bitmap150, price150, price200);
        } catch (RemoteSystemFail remoteSystemFail) {
            publishProgress("ERROR: " + remoteSystemFail.getMessage());
            return null;
        }
    }

    private Bitcoins roundedBitcoins(double colaprice) {
        return Bitcoins.nearestValue(colaprice).roundToSignificantFigures(SHOW_NUM_DIGITS);
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
    protected void onPostExecute(Data data) {
        StringBuilder text = new StringBuilder();
        if (data != null) {
            text.append("1฿ = ").append(data.price).append("€");
            //todo draw QR codes in background, this is apparently slow and blocks main thread
            activity.drawQrCodes(data.qrcode1_5,data.qrcode2_0, data.price15,data.price20);
        } else {
            text.append("<Error: NULL>");
        }
        text.append("\num ").append(Utils.timeFmt.format(new Date()));
        this.txt_view.setText(text.toString());
    }
}