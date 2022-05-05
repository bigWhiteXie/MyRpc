package protocol;

import java.util.concurrent.atomic.AtomicInteger;

public class SequenceGenerator {

    private static final AtomicInteger id = new AtomicInteger();

    public static int nextId() {
            return id.incrementAndGet();
        }

}
