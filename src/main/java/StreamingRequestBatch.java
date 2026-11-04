import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Stops queued requests after Burp rejects streaming, while allowing in-flight requests to finish. */
final class StreamingRequestBatch {
    private final AtomicBoolean stopped = new AtomicBoolean();
    private final AtomicInteger attempted = new AtomicInteger();

    <T> Optional<T> send(Supplier<T> sender) {
        if (stopped.get()) {
            return Optional.empty();
        }
        // A concurrent rejection may arrive after this check; never interrupt an in-flight request.
        attempted.incrementAndGet();
        try {
            return Optional.of(sender.get());
        } catch (RuntimeException error) {
            if (StreamingResponseWarning.isStreamingRejection(error)) {
                stopped.set(true);
            }
            throw error;
        }
    }

    boolean isStopped() {
        return stopped.get();
    }

    int attemptedRequests() {
        return attempted.get();
    }
}
