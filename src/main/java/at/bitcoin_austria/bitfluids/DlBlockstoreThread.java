package at.bitcoin_austria.bitfluids;

import com.google.bitcoin.core.*;
import com.google.bitcoin.discovery.PeerDiscovery;
import com.google.bitcoin.store.BlockStore;
import com.google.bitcoin.store.BlockStoreException;
import com.google.bitcoin.store.BoundedOverheadBlockStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * @author schilly
 */
class DlBlockstoreThread extends Thread {
    private final Environment env;
    private final File extFilesDir;
    private final TxNotifier txNotifier;
    private final Wallet wallet;
    private BlockChain chain;
    private PeerGroup peerGroup;

    final static Logger LOGGER = LoggerFactory.getLogger(DlBlockstoreThread.class);

    public DlBlockstoreThread(Environment env, File extFilesDir, Wallet wallet, TxNotifier txNotifier) {
        this.env = env;
        this.wallet = wallet;
        this.extFilesDir = extFilesDir;
        this.txNotifier = txNotifier;
    }

    @Override
    public void run() {
        BlockStore blockStore;
        try {
            // we store this on the SD card, in
            // /Android/data/at.bitcoin_austria..../
            // there will be an exception wheren there is no
            // "getExternalFilesDir()". also, never store sensitive info
            // there!!!
            blockStore = new BoundedOverheadBlockStore(env.getNetworkParams(), new File(extFilesDir, env.getBlockChainFilename()));
            chain = new BlockChain(env.getNetworkParams(), wallet, blockStore);
            peerGroup = new PeerGroup(env.getNetworkParams(), chain);
            peerGroup.addEventListener(new AbstractPeerEventListener() {
                @Override
                public void onTransaction(Peer peer, Transaction t) {
                    for (ECKey key : wallet.getKeys()) {
                        byte[] lookingFor = key.getPubKey();
                        List<TransactionOutput> outputs = t.getOutputs();
                        for (TransactionOutput output : outputs) {
                            Address candAdr = null;
                            try {
                                Script scriptPubKey = output.getScriptPubKey();
                                candAdr = scriptPubKey.getToAddress();
                            } catch (ScriptException e) {
                                LOGGER.info("invalid script in TX id " + t.getHashAsString());
                            }
                            if (candAdr != null) {
                                byte[] candidate = candAdr.getHash160();
                                if (Arrays.equals(lookingFor, candidate)) {
                                    LOGGER.debug("detected relevant transaction!"+output.getValue());
                                    txNotifier.onValue(output.getValue(), key);
                                }
                            }
                        }
                    }
                }
            });

            List<PeerDiscovery> discoveries = env.getPeerDiscoveries();
            for (PeerDiscovery discovery : discoveries) {
                peerGroup.addPeerDiscovery(discovery);
            }
            //in our case we are only interested in future transactions.
//            peerGroup.setFastCatchupTimeSecs(Utils.now().getTime() / 1000);
            peerGroup.addWallet(wallet);
            peerGroup.start();
            peerGroup.downloadBlockChain();
        } catch (BlockStoreException e) {
            throw new RuntimeException(e);
        }
    }

    public String getStatus() {
        if (chain != null && peerGroup != null) {
            return "block:" + chain.getBestChainHeight() + " peers:" + peerGroup.getConnectedPeers().size();
        } else {
            return "not yet initialized";
        }
    }
}
