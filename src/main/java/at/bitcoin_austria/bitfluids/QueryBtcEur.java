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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Date;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import android.os.AsyncTask;
import android.widget.TextView;

import javax.net.ssl.*;
import javax.security.cert.CertificateException;
import javax.security.cert.X509Certificate;

/**
 * a good read is
 * http://dl.google.com/googleio/2010/android-developing-RESTful-android
 * -apps.pdf but this is much simpler …
 * 
 * @author schilly
 * 
 */
final class QueryBtcEur extends AsyncTask<Void, String, Double> {
  private final TextView              txt_view;
  private final BitFluidsMainActivity activity;

  QueryBtcEur(BitFluidsMainActivity activity) {
    this.activity = activity;
    this.txt_view = (TextView) activity.findViewById(R.id.first_line);
  }

    //todo this crashes on Android due to SSL cert not verifying. SSL was only recently mandatory on mtgox.
  @Override
  protected Double doInBackground(Void... v) {
    publishProgress("connecting …");
    // TODO can this be optimized? is one singleton client enough?
    HttpClient httpClient = new DefaultHttpClient();
    HttpResponse httpResponse;
    HttpGet httpGet = new HttpGet(Utils.MTGOX_BTCEUR);
    try {
      httpResponse = httpClient.execute(httpGet);
      StatusLine statusLine = httpResponse.getStatusLine();
      if (statusLine.getStatusCode() == HttpStatus.SC_OK) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        httpResponse.getEntity().writeTo(out);
        out.close();

        // parse json
        JSONObject json = new JSONObject(out.toString());
        // TODO this just looks awkward
        String btc_eur = ((JSONObject) ((JSONObject) json.get("return")).get("avg")).get("value").toString();

        return Double.parseDouble(btc_eur);

      } else {
        httpResponse.getEntity().getContent().close();
        publishProgress("ERROR: " + statusLine.getReasonPhrase());
      }

    } catch (ClientProtocolException cpe) {
      cpe.printStackTrace();
    } catch (IOException ioex) {
      ioex.printStackTrace();
    } catch (JSONException e) {
      e.printStackTrace();
    }

    return null;
  }

  /** runs on ui thread and publishes info strings */
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
      final double eur1_5 = 1.5 / btceur;
      final double eur2_0 = 2.0 / btceur;
      text.append("1฿ = ").append(btceur).append("€");
      activity.drawQrCodes(eur1_5, 1.5, eur2_0, 2.0);
    } else {
      text.append("<Error: NULL>");
    }
    text.append("\num ").append(Utils.timeFmt.format(new Date()));
    final String t = text.toString();
    activity.getState().txt_view_state = t;
    this.txt_view.setText(text.toString());
  }
}