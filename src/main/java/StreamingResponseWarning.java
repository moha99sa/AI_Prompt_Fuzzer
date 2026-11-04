import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Reports Burp's streaming transport rejection without treating it as a negative result. */
final class StreamingResponseWarning extends JPanel {
    static final String GUIDANCE = "Burp rejected a streaming response; the affected request was not analysed. "
            + "In Settings > Network > HTTP > Streaming responses, review "
            + "'Treat responses with the text/event-stream MIME type as streaming' and any matching URL rules. "
            + "Disabling streaming classification may allow finite responses to complete, but affects the whole "
            + "Burp project; streams that never finish may time out. Alternatively, request non-streaming output "
            + "if the target supports it. Retry affected requests after adjusting the settings.";

    private final AtomicInteger rejectedRequests = new AtomicInteger();
    private final JLabel summary = new JLabel();
    private final Consumer<String> output;

    StreamingResponseWarning(Consumer<String> output, Runnable openSettings) {
        super(new BorderLayout(8, 4));
        this.output = output;
        setBorder(BorderFactory.createTitledBorder("Streaming response warning"));
        add(summary, BorderLayout.NORTH);
        JTextArea guidance = new JTextArea(GUIDANCE);
        guidance.setEditable(false);
        guidance.setLineWrap(true);
        guidance.setWrapStyleWord(true);
        guidance.setOpaque(false);
        guidance.setFont(summary.getFont());
        add(guidance, BorderLayout.CENTER);
        JButton settings = new JButton("Open Burp settings");
        settings.setToolTipText("Then select Network > HTTP > Streaming responses");
        settings.addActionListener(event -> openSettings.run());
        add(settings, BorderLayout.EAST);
        setVisible(false);
    }

    boolean reportIfStreaming(Throwable error) {
        if (!isStreamingRejection(error)) {
            return false;
        }
        int count = rejectedRequests.incrementAndGet();
        output.accept("[W]: " + (count == 1 ? GUIDANCE
                : "Streaming response rejected; request not analysed. See the streaming warning in AI Prompt Fuzzer."));
        SwingUtilities.invokeLater(() -> {
            summary.setText("Requests not analysed due to streaming since extension load: " + rejectedRequests.get());
            setVisible(true);
            revalidate();
            repaint();
        });
        return true;
    }

    static boolean isStreamingRejection(Throwable error) {
        // Montoya exposes this failure as a RuntimeException, not a dedicated exception type.
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        while (error != null && visited.add(error)) {
            if (error instanceof RuntimeException && "Streaming response received".equals(error.getMessage())) {
                return true;
            }
            error = error.getCause();
        }
        return false;
    }
}
