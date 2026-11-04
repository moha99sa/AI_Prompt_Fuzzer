import javax.swing.*;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Dependency-free regression checks; run with the commands in src/test/README.md. */
public class StreamingResponseWarningTest {
    public static void main(String[] args) throws Exception {
        RuntimeException streaming = new RuntimeException("Streaming response received");
        check(StreamingResponseWarning.isStreamingRejection(streaming), "Direct rejection");
        check(StreamingResponseWarning.isStreamingRejection(new Exception(streaming)), "Wrapped rejection");
        check(!StreamingResponseWarning.isStreamingRejection(null), "Null exception");
        check(!StreamingResponseWarning.isStreamingRejection(new RuntimeException()), "Null message");
        check(!StreamingResponseWarning.isStreamingRejection(new RuntimeException("Connection timed out")),
                "Unrelated transport errors must retain their existing handling");
        check(!StreamingResponseWarning.isStreamingRejection(new Exception("Streaming response received")),
                "Only the known runtime failure is recognised");
        RuntimeException first = new RuntimeException("first");
        RuntimeException second = new RuntimeException("second", first);
        first.initCause(second);
        check(!StreamingResponseWarning.isStreamingRejection(first), "Cyclic causes must terminate");

        List<String> output = new ArrayList<>();
        AtomicInteger settingsOpened = new AtomicInteger();
        StreamingResponseWarning[] warning = new StreamingResponseWarning[1];
        SwingUtilities.invokeAndWait(() -> {
            warning[0] = new StreamingResponseWarning(output::add, settingsOpened::incrementAndGet);
            check(!warning[0].isVisible(), "No warning before a failure");
        });
        check(!warning[0].reportIfStreaming(new RuntimeException("Other error")), "Unrelated error not handled");
        check(output.isEmpty(), "Unrelated error produces no streaming warning");
        // Reports originate outside the event dispatch thread, as they do in request workers.
        check(warning[0].reportIfStreaming(streaming), "Streaming error handled");
        check(warning[0].reportIfStreaming(new Exception(streaming)), "Repeated wrapped error handled");
        SwingUtilities.invokeAndWait(() -> {
            check(warning[0].isVisible(), "Warning remains visible after requests finish");
            for (Component component : warning[0].getComponents()) {
                if (component instanceof JLabel && ((JLabel) component).getText().startsWith("Requests not analysed")) {
                    check(((JLabel) component).getText().endsWith(": 2"), "Accurate rejected-request count");
                }
                if (component instanceof JButton) {
                    ((JButton) component).doClick();
                }
            }
        });
        check(settingsOpened.get() == 1, "Settings button invokes navigation without changing settings");
        check(output.size() == 2, "Every rejected request is logged");
        check(output.get(0).contains(StreamingResponseWarning.GUIDANCE), "First warning includes recovery steps");
        check(output.get(1).contains("request not analysed"), "Repeated warning explains missing results");
        warning[0].reportStoppedBatch(12);
        SwingUtilities.invokeAndWait(() -> {
            boolean found = false;
            for (Component component : warning[0].getComponents()) {
                if (component instanceof JLabel && ((JLabel) component).getText().contains("Requests not sent: 12")) {
                    found = true;
                }
            }
            check(found, "Stopped batch and unsent count are visible");
        });
        check(output.get(2).contains("Requests not sent: 12"), "Stopped batch is logged");
        checkBatchStops(streaming);
        System.out.println("StreamingResponseWarning regression checks passed.");
    }

    private static void checkBatchStops(RuntimeException streaming) throws Exception {
        StreamingRequestBatch batch = new StreamingRequestBatch();
        AtomicInteger sends = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var inFlight = executor.submit(() -> batch.send(() -> {
                sends.incrementAndGet();
                started.countDown();
                try {
                    check(finish.await(5, TimeUnit.SECONDS), "In-flight request released");
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("In-flight request must not be interrupted", error);
                }
                return "response";
            }));
            check(started.await(5, TimeUnit.SECONDS), "Request started before rejection");
            try {
                batch.send(() -> { sends.incrementAndGet(); throw streaming; });
                throw new AssertionError("Rejection must reach the existing warning handler");
            } catch (RuntimeException error) {
                check(error == streaming, "Original rejection preserved");
            }
            for (int i = 0; i < 20; i++) {
                check(batch.send(() -> { sends.incrementAndGet(); return "unexpected"; }).isEmpty(),
                        "Pending request skipped");
            }
            finish.countDown();
            check(inFlight.get(5, TimeUnit.SECONDS).orElseThrow().equals("response"), "In-flight result preserved");
            check(sends.get() == 2 && batch.attemptedRequests() == 2, "Only started requests counted");
        } finally {
            finish.countDown();
            executor.shutdownNow();
        }
        StreamingRequestBatch retry = new StreamingRequestBatch();
        RuntimeException unrelated = new RuntimeException("Connection timed out");
        try {
            retry.send(() -> { throw unrelated; });
        } catch (RuntimeException error) {
            check(error == unrelated, "Unrelated error preserved");
        }
        check(!retry.isStopped(), "Unrelated errors do not stop a batch");
        check(retry.send(() -> "retry").orElseThrow().equals("retry"), "A new run can send requests");
    }

    private static void check(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
    }
}
