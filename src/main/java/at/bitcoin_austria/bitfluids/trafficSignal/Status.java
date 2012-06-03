package at.bitcoin_austria.bitfluids.trafficSignal;

import android.graphics.Color;

/**
* @author apetersson
*/
public enum Status {
    RED(Color.RED), YELLOW(Color.YELLOW), GREEN(Color.GREEN);
    public final int androidColor;

    Status(int androidColor) {
        this.androidColor = androidColor;
    }
}
