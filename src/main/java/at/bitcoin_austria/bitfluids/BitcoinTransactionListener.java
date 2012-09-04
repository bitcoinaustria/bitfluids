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

import android.util.Log;
import com.google.bitcoin.core.*;
import com.google.bitcoin.discovery.PeerDiscovery;
import com.google.bitcoin.store.BlockStore;
import com.google.bitcoin.store.BlockStoreException;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * a very lazy implementation of a bitcoin client.
 * this only checks for broadcasts of bitcoin transactions, not for blocks
 * and apparently also not for validity of the parent inputs and transactions.
 * the very basic idea is, that we listen to standard bitcoin-qt nodes,
 * which typically only broadcast valid transactions.
 * currently we depend on the first sight of a transaction.
 * todo 1:
 * in the future we will want to check with each and every
 * of our connected hosts if he considers the TX valid.
 * or
 * we will maintain the blockchain ourselves, which means increased startup time and bandwidth.
 * also less flexibility due to not knowing the deposit public keys.
 * todo 2: inform UI about changes in confidence (double-spend, more peers seen it, inclusion in block)
 *
 * @author apetersson
 */
public class BitcoinTransactionListener {
    private static final long TIMEFRAME_MILLIS = 120 * 1000; //look at the last 2 minutes
    private static final int MILLIS_PER_MINUTE = 1000 * 60;
    public static final int MAX_CONNECTIONS = 4;
    private final Environment env;
    private final List<Address> lookingFor;
    private final List<Consumer<Integer>> peerCountListeners;

    //    private BlockChain chain;
    private PeerGroup peerGroup;

    private static final Logger LOGGER = LoggerFactory.getLogger(BitcoinTransactionListener.class);
    //weakhashSet would be sufficient, but there is no such thing
    private final WeakHashMap<Sha256Hash, Boolean> isInteresting = new WeakHashMap<Sha256Hash, Boolean>();

    //we keep strong refs to these to surely not double-check
    private final Set<Sha256Hash> interestingHashes = Collections.synchronizedSet(new HashSet<Sha256Hash>());

    private final TreeSet<Long> transactionTimes = new TreeSet<Long>();
    private final long startupTime;
    private Stats stats;

    public BitcoinTransactionListener(Environment env) {
        peerCountListeners = new ArrayList<Consumer<Integer>>();
        this.env = env;
        lookingFor = Arrays.asList(env.getKey200(), env.getKey150());
        startupTime = new Date().getTime();
    }

    public void setStatsNotifier(Stats stats) {
        this.stats = stats;
    }

    /**
     * this starts off acitivity in the bitcoin network. (non-blocking)
     * to stop activity
     *
     * @param txNotifier callback when a new matching transaction occurs
     * @see #shutdown()
     */
    public void init(final TxNotifier txNotifier) {
        Preconditions.checkState(peerGroup == null);
        final PeerEventListener txProcessListener = new AbstractPeerEventListener() {
            @Override
            public void onTransaction(Peer peer, Transaction t) {
                processTransaction(t, txNotifier);
            }
        };
        try {
            peerGroup = new PeerGroup(env.getNetworkParams(), new BlockChain(env.getNetworkParams(), new DummyBlockStore(env)));
            final AtomicInteger counter = new AtomicInteger(0);
            peerGroup.addEventListener(new AbstractPeerEventListener() {

                /**
                 * currently we want to ignore all incoming blocks..
                 */
                @Override
                public Message onPreMessageReceived(Peer peer, Message m) {
                    if (m instanceof Block) {
                        for (Transaction transaction : ((Block) m).getTransactions()) {
                            processTransaction(transaction, txNotifier);
                        }
                        return null;
                    }
                    return m;
                }

                @Override
                public void onPeerConnected(Peer peer, int peerCount) {
                    counter.addAndGet(1);
                    peer.addEventListener(txProcessListener);
                    runListener();
                }

                @Override
                public void onPeerDisconnected(Peer peer, int peerCount) {
                    counter.addAndGet(-1);
                    peer.removeEventListener(txProcessListener);
                    runListener();
                }

                private void runListener() {
                    for (Consumer<Integer> peerCountListener : peerCountListeners) {
                        peerCountListener.consume(counter.get());
                    }
                }
            });

            List<PeerDiscovery> discoveries = env.getPeerDiscoveries();
            for (PeerDiscovery discovery : discoveries) {
                peerGroup.addPeerDiscovery(discovery);
            }
            //in our case we are only interested in future transactions.
            peerGroup.setFastCatchupTimeSecs(new Date().getTime() / 1000);
            peerGroup.setMaxConnections(MAX_CONNECTIONS);
            peerGroup.start();
        } catch (BlockStoreException e) {
            throw new RuntimeException(e);
        }
    }

    //synchronized to avoid double incoming TX
    private synchronized void processTransaction(Transaction t, TxNotifier txNotifier) {
        Sha256Hash transactionHash = t.getHash();
        //have seen it, but maybe already forgotten
        Boolean cached = isInteresting.get(transactionHash);
        if (cached != null) {
            Log.d("BF", " already seen:" + transactionHash);
            return;
        }
        //double-check maybe it was already collected and evicted from weakhashmap
        if (interestingHashes.contains(transactionHash)) {
            return;
        }
        boolean wasInteresting = analyzeTransaction(t, txNotifier);
        updateStats();
        if (wasInteresting) {
            interestingHashes.add(transactionHash);
        }
        isInteresting.put(transactionHash, wasInteresting);
    }


    private void updateStats() {
        long now = new Date().getTime();
        transactionTimes.add(now);
        Iterator<Long> iterator = transactionTimes.iterator();
        while (iterator.hasNext()) {
            Long time = iterator.next();
            if (now - time > TIMEFRAME_MILLIS) {
                iterator.remove();
            } else {
                break;
            }
        }
        long runtime = now - startupTime;
        double runtimeMinutes = Math.min(runtime, TIMEFRAME_MILLIS) / (double) MILLIS_PER_MINUTE;
        double tpm = transactionTimes.size() / runtimeMinutes;
        if (stats != null) {
            stats.update(tpm);
        }
    }

    private boolean analyzeTransaction(Transaction t, TxNotifier txNotifier) {
        try {
            List<TransactionOutput> outputs = t.getOutputs();
            boolean wasInteresting = false;
            for (TransactionOutput output : outputs) {
                final Address candidateAddress = extractAddress(t, output);
                if (candidateAddress != null) {
                    byte[] candidate = candidateAddress.getHash160();
                    for (Address address : lookingFor) {
                        if (Arrays.equals(address.getHash160(), candidate)) {
                            Bitcoins bitcoins = Bitcoins.valueOf(output);
                            LOGGER.debug("detected relevant transaction!" + bitcoins);
                            wasInteresting = true;
                            txNotifier.onValue(bitcoins, address, t.getHash());
                        }
                    }
                }
            }
            return wasInteresting;
        } catch (RuntimeException e) {
            LOGGER.error("there was an error while looking at a transaction", e);
            return false;
        }
    }

    @Nullable
    private static Address extractAddress(Transaction t, TransactionOutput output) {
        Address candidateAddress = null;
        try {
            Script scriptPubKey = output.getScriptPubKey();
            candidateAddress = scriptPubKey.getToAddress();
        } catch (ScriptException e) {
            LOGGER.info("invalid script in TX id " + t.getHashAsString());
        }
        return candidateAddress;
    }

    public void addPeerCountListener(Consumer<Integer> consumer) {
        peerCountListeners.add(consumer);
    }

    public void shutdown() {
        if (peerGroup != null) {
            peerGroup.stop();
        }
        peerGroup = null;
    }

    public void addHashes(List<TransactionItem> transactionItems) {
        for (TransactionItem item : transactionItems) {
            interestingHashes.add(item.hash);
        }
    }

    private static class DummyBlockStore implements BlockStore {
        final Environment env;

        private DummyBlockStore(Environment env) {
            this.env = env;
        }

        @Override
        public void put(StoredBlock block) throws BlockStoreException {
            throw new UnsupportedOperationException("i'm too dumb!");
        }

        @Override
        public StoredBlock get(Sha256Hash hash) throws BlockStoreException {
            throw new UnsupportedOperationException("i'm too dumb!");
        }

        @Override
        public StoredBlock getChainHead() throws BlockStoreException {
            //always reporting genesisBlock as Chain Head...
            Block genesisBlock = env.getNetworkParams().genesisBlock;
            try {
                return new StoredBlock(genesisBlock, genesisBlock.getWork(), 0);
            } catch (VerificationException e) {
                throw new BlockStoreException(e);
            }
        }

        @Override
        public void setChainHead(StoredBlock chainHead) throws BlockStoreException {
            throw new UnsupportedOperationException("i'm too dumb!");
        }

        @Override
        public void close() throws BlockStoreException {
            throw new UnsupportedOperationException("i'm too dumb!");
        }
    }
}
