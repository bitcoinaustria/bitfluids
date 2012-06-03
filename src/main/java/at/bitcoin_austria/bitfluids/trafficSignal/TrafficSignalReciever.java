package at.bitcoin_austria.bitfluids.trafficSignal;

/**
* @author apetersson
*/
public interface TrafficSignalReciever {
    void onStatusChanged(SignalType signalType, Status status);
}
