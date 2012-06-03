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

import com.google.bitcoin.core.*;
import com.google.bitcoin.discovery.PeerDiscovery;
import com.google.bitcoin.store.BlockStore;
import com.google.bitcoin.store.BlockStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * a very lazy implementation of a bitcoin client.
 * this only checks for broadcasts of bitcoin transactions, not for blocks
 * and apparently also not for validity of the parent inputs and transactions.
 * the very basic idea is, that we listen to standard bitcoin-qt nodes,
 * which typically only broadcast valid transactions.
 * currently we depend on the first sight of a transaction.
 * todo:
 * in the future we will want to check with each and every
 * of our connected hosts if he considers the TX valid.
 * or
 * we will maintain the blockchain ourselves, which means increased startup time and bandwidth.
 * also less flexibility due to not knowing the deposit public keys.
 * @author apetersson
 */
public class CasualListener {
    private final Environment env;
    private final List<Address> lookingFor;
    final List<Consumer<Integer>> peerCountListeners;

    //    private BlockChain chain;
    private PeerGroup peerGroup;

    final static Logger LOGGER = LoggerFactory.getLogger(CasualListener.class);
    //weakhashSet would be sufficient, but there is no such thing
    final WeakHashMap<Sha256Hash, Boolean> isInteresting = new WeakHashMap<Sha256Hash, Boolean>();

    //we keep strong refs to these to surely not double-check
    final Set<Sha256Hash> interestingHashes = new HashSet<Sha256Hash>();


    public CasualListener(Environment env) {
        peerCountListeners = new ArrayList<Consumer<Integer>>();
        this.env = env;
        lookingFor = Arrays.asList(env.getKey200(), env.getKey150());
    }

    public void addNotifier(final TxNotifier txNotifier) {
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
                    if (m instanceof Block){
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
            peerGroup.start();
        } catch (BlockStoreException e) {
            throw new RuntimeException(e);
        }
    }

    //synchronized to avoid double incoming TX
    private synchronized void processTransaction(Transaction t, TxNotifier txNotifier) {
        Sha256Hash transactionHash = t.getHash();
        //have seen it, but maybe already forgotten
        if (isInteresting.get(transactionHash) != null) {
            return;
        }
        //double-check maybe it was already collected and evicted from weakhashmap
        if (interestingHashes.contains(transactionHash)) {
            return;
        }
        boolean wasInteresting = analyzeTransaction(t, txNotifier);
        if (wasInteresting) {
            interestingHashes.add(transactionHash);
        }
        isInteresting.put(transactionHash, wasInteresting);
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
                            BigInteger satoshis = output.getValue();
                            LOGGER.debug("detected relevant transaction!" + satoshis);
                            wasInteresting = true;
                            txNotifier.onValue(satoshis, address);
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
        peerGroup.stop();
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
 /*           if (hash.equals(env.getNetworkParams().genesisBlock.getHash())){
                 return new StoredBlock(env.getNetworkParams().genesisBlock,env.getNetworkParams().genesisBlock.getWork(),env.getNetworkParams());
            }
            return null;*/
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
