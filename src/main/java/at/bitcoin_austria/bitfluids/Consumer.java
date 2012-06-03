package at.bitcoin_austria.bitfluids;

/**
 * @author apetersson
 */
public interface Consumer<E> {
    public void consume(E e);
}