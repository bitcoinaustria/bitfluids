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

/**
 * this <b>static</b> inner class stores the state, including the background
 * AsyncTask, for restoring when Activity restarts.
 *
 * @author schilly
 */
class BitFluidsActivityState {
    Bitmap qr_nonalk_img;
    Bitmap qr_alk_img;
    String txt_view_state;
    Double btceur;
    DlBlockstoreThread dlBlockstore;

    @Override
    public String toString() {
        return String.format("State: %f btc/eur", btceur);
    }
}
