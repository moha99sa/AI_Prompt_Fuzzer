import javax.swing.*;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
                if (component instanceof JLabel) {
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
        System.out.println("StreamingResponseWarning regression checks passed.");
    }

    private static void check(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
    }
}
