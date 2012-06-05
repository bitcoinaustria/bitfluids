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

import at.bitcoin_austria.bitfluids.trafficSignal.Status;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author apetersson
 */
public class PriceService {

    private Double lastResult;
    private long lastTimeChecked;

    private final HttpClient httpClient;
    private final List<Consumer<Status>> lastQuoteListeners;

    public PriceService(HttpClient httpClient) {
        lastQuoteListeners = new ArrayList<Consumer<Status>>();
        this.httpClient = httpClient;
    }

    public synchronized Double getEurQuote() throws RemoteSystemFail {
        if ((lastResult != null) && (new Date().getTime() - lastTimeChecked < Utils.TEN_MINUTES_IN_MILLIS)) {
            notifySuccess();
            return lastResult;
        }
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
                // TODO this just looks awkward still this is harmless compared to the rest
                String btc_eur = ((JSONObject) ((JSONObject) json.get("return")).get("avg")).get("value").toString();
                lastResult = Double.parseDouble(btc_eur);
                lastTimeChecked = new Date().getTime();
                notifySuccess();
                return lastResult;

            } else {
                httpResponse.getEntity().getContent().close();
                throw new RemoteSystemFail("ERROR: " + statusLine.getReasonPhrase());
            }

        } catch (ClientProtocolException e) {
            notifyFailed();
            throw new RemoteSystemFail(e);
        } catch (IOException e) {
            notifyFailed();
            throw new RemoteSystemFail(e);
        } catch (JSONException e) {
            notifyFailed();
            throw new RemoteSystemFail(e);
        }
    }

    private void notifySuccess() {
        final Status ret;
        if (lastResult == null || lastResult <= 0) {
            ret = Status.RED;
        } else {
            ret = Status.GREEN;
        }
        for (Consumer<Status> listener : lastQuoteListeners) {
            listener.consume(ret);
        }
    }

    private void notifyFailed() {
        for (Consumer<Status> listener : lastQuoteListeners) {
            listener.consume(Status.RED);
        }
    }

    public void addLastQuoteListener(Consumer<Status> consumer) {
        lastQuoteListeners.add(consumer);
    }
}
