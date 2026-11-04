# Streaming warning checks

These checks use only the JDK and do not require Burp or additional dependencies.
Run from the repository root with Java 17 or later:

```sh
mkdir -p target/streaming-warning-tests
javac --release 17 -d target/streaming-warning-tests src/main/java/StreamingResponseWarning.java src/test/java/StreamingResponseWarningTest.java
java -Djava.awt.headless=true -cp target/streaming-warning-tests StreamingResponseWarningTest
```

The standalone test main is not automatically executed by Maven Surefire.

For integration verification in Burp:

1. Enable streaming classification and send a payload to a controlled SSE endpoint.
2. Confirm the extension output contains an actionable warning and the main tab displays the warning and rejected-request count. Rejected requests must not appear as negative findings.
3. Send several payloads. Each rejected request should count towards completion; the UI should finish the run without a series of modal dialogs.
4. Click **Open Burp settings**. On Burp 2025.8 or later, Settings should open. On an older version, manual navigation instructions should appear. No setting should change automatically.
5. Repeat in AI vs AI mode, if available. The run should stop after the rejected response without feeding an empty response back to AI.
6. Check an ordinary finite response and an unrelated network failure. Successful responses should retain their existing analysis; unrelated failures should retain their error messages.

Burp may still write its own exception trace to the error log before propagating the exception to the extension.
