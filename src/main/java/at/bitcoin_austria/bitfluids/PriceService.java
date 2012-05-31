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

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * @author apetersson
 */
//todo cache for 10 minutes
public class PriceService {

    private final HttpClient httpClient;

    public PriceService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public Double getEurQuote() throws RemoteSystemFail {
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
                throw new RemoteSystemFail("ERROR: " + statusLine.getReasonPhrase());
            }

        } catch (ClientProtocolException e) {
            throw new RemoteSystemFail(e);
        } catch (IOException e) {
            throw new RemoteSystemFail(e);
        } catch (JSONException e) {
            throw new RemoteSystemFail(e);
        }
    }
}
