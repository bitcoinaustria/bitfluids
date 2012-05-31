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

/**
 * @author apetersson
 */
public enum FluidType {
    MATE(2.0) {
        @Override
        public String getDescription() {
            return "Mate oder Bier";
        }
    }, COLA(1.5) {
        @Override
        public String getDescription() {
            return "Cola";
        }
    };
    private final double euroPrice;

    private FluidType(double euroPrice) {
        this.euroPrice = euroPrice;
    }

    public double getEuroPrice() {
        return euroPrice;
    }

    public abstract String getDescription();
}
