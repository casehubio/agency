package io.casehub.agency.speech.demo;

import io.casehub.connectors.voice.model.SynthesisOptions;
import io.casehub.connectors.voice.model.SynthesisResult;
import io.casehub.connectors.voice.spi.StreamingTextToSpeech;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

public class Qwen3TextToSpeech implements StreamingTextToSpeech {

    private static final System.Logger LOG = System.getLogger("speech-demo.qwen3");
    private static final int SAMPLE_RATE = 24000;
    private static final int CHUNK_SAMPLES = SAMPLE_RATE * 2;
    private static final int CHUNK_BYTES = CHUNK_SAMPLES * 2;

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String speaker;
    private final com.google.gson.Gson gson = new com.google.gson.Gson();

    public Qwen3TextToSpeech(String baseUrl, String speaker) {
        this.httpClient = HttpClient.newHttpClient();
        this.baseUrl = baseUrl;
        this.speaker = speaker;
    }

    public static Qwen3TextToSpeech withDefaults(String speaker) {
        return new Qwen3TextToSpeech("http://localhost:8790", speaker);
    }

    @Override
    public SynthesisResult synthesise(String text, SynthesisOptions options) {
        try {
            var body = new com.google.gson.JsonObject();
            body.addProperty("model", "qwen3-tts-0.6b");
            body.addProperty("input", text);
            body.addProperty("voice", speaker);
            body.addProperty("response_format", "wav");

            long start = System.currentTimeMillis();
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/audio/speech"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                    .timeout(Duration.ofSeconds(120))
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Qwen3-TTS server error " + response.statusCode()
                                           + ": " + new String(response.body()));
            }

            byte[] wavBytes = response.body();
            long elapsed = System.currentTimeMillis() - start;
            LOG.log(System.Logger.Level.INFO,
                    "Qwen3-TTS [{0}]: {1} bytes in {2}ms",
                    speaker, wavBytes.length, elapsed);
            return new SynthesisResult(wavBytes, "wav", List.of());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new RuntimeException("Qwen3-TTS synthesis failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void synthesiseStreaming(String text, SynthesisOptions options, Consumer<byte[]> onWavChunk) {
        try {
            var body = new com.google.gson.JsonObject();
            body.addProperty("text", text);
            body.addProperty("speaker", speaker);
            body.addProperty("language", "en");

            long start = System.currentTimeMillis();
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/tts/stream"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                    .timeout(Duration.ofSeconds(120))
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                byte[] err = response.body().readAllBytes();
                throw new RuntimeException("Qwen3-TTS stream error " + response.statusCode()
                                           + ": " + new String(err));
            }

            int chunkCount = 0;
            try (InputStream in = response.body()) {
                var pcmBuffer = new ByteArrayOutputStream();
                byte[] readBuf = new byte[8192];
                int read;
                while ((read = in.read(readBuf)) != -1) {
                    pcmBuffer.write(readBuf, 0, read);
                    while (pcmBuffer.size() >= CHUNK_BYTES) {
                        byte[] pcm = pcmBuffer.toByteArray();
                        byte[] chunk = new byte[CHUNK_BYTES];
                        System.arraycopy(pcm, 0, chunk, 0, CHUNK_BYTES);
                        byte[] remaining = new byte[pcm.length - CHUNK_BYTES];
                        System.arraycopy(pcm, CHUNK_BYTES, remaining, 0, remaining.length);
                        pcmBuffer.reset();
                        pcmBuffer.write(remaining);

                        onWavChunk.accept(wrapWav(chunk));
                        chunkCount++;
                    }
                }
                if (pcmBuffer.size() > 0) {
                    onWavChunk.accept(wrapWav(pcmBuffer.toByteArray()));
                    chunkCount++;
                }
            }

            long elapsed = System.currentTimeMillis() - start;
            LOG.log(System.Logger.Level.INFO,
                    "Qwen3-TTS streaming [{0}]: {1} chunks in {2}ms",
                    speaker, chunkCount, elapsed);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new RuntimeException("Qwen3-TTS streaming failed: " + e.getMessage(), e);
        }
    }

    private static byte[] wrapWav(byte[] pcmS16le) {
        int dataLen = pcmS16le.length;
        ByteBuffer header = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes());
        header.putInt(36 + dataLen);
        header.put("WAVE".getBytes());
        header.put("fmt ".getBytes());
        header.putInt(16);
        header.putShort((short) 1);
        header.putShort((short) 1);
        header.putInt(SAMPLE_RATE);
        header.putInt(SAMPLE_RATE * 2);
        header.putShort((short) 2);
        header.putShort((short) 16);
        header.put("data".getBytes());
        header.putInt(dataLen);
        header.put(pcmS16le);
        return header.array();
    }
}
