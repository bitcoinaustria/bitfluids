package at.bitcoin_austria.bitfluids;

import android.graphics.Bitmap;

/**
 * this <b>static</b> inner class stores the state, including the background
 * AsyncTask, for restoring when Activity restarts.
 *
 * @author schilly
 */
class BitFluidsActivityState {
    // TODO this will be bitcoin addresses, instead of just strings.
    String addr_eur_1_5;
    String addr_eur_2_0;
    Bitmap qr_nonalk_img;
    Bitmap qr_alk_img;
    String txt_view_state;
    Double btceur;
    DlBlockstoreThread dlBlockstore;

    @Override
    public String toString() {
        return String.format("State: %f btc/eur;%s;%s", btceur, addr_eur_1_5, addr_eur_2_0);
    }
}
