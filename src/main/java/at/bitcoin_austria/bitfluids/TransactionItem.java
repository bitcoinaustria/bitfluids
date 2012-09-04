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

import com.google.bitcoin.core.Sha256Hash;

import java.io.Serializable;

/**
 * @author apetersson
 */
public class TransactionItem implements Serializable {

    public final Bitcoins paid;
    public final int count;
    public final double euroPerBitcoin;
    public FluidType fluidType;
    public Sha256Hash hash;

    public TransactionItem(FluidType fluidType, Bitcoins paid, int count, double euroPerBitcoin, Sha256Hash hash) {
        this.fluidType = fluidType;
        this.paid = paid;
        this.count = count;
        this.euroPerBitcoin = euroPerBitcoin;
        this.hash = hash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TransactionItem that = (TransactionItem) o;

        if (hash != null ? !hash.equals(that.hash) : that.hash != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return hash != null ? hash.hashCode() : 0;
    }

    @Override
    public String toString() {
        return count + " Stück " + fluidType + " tx " + hash.toString().substring(0, 4);
    }

    public String buildSentence() {
        if (count == 0) {
            return "Sie Geizkragen, das waren nur " + paid.toString() + " Bitcoins " + fluidType.getDescription() + " kostet mehr!";
        }
        String anzahl = count == 1 ? "ein" : String.valueOf(count);
        return "Sie können nun "
                + anzahl
                + "  Stück "
                + fluidType.getDescription()
                + " aus dem Kühlschrank nehmen";
    }
}
