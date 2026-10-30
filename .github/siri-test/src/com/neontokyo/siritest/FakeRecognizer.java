package com.neontokyo.siritest;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

/**
 * Stands in for the TV's speech recognizer. It reads the sound Neon Tokyo passes through
 * EXTRA_AUDIO_SOURCE, measures it, and "hears" the voice command only when it is the 440 Hz tone
 * the pretend remote sent, at the 16 kHz the real recognizer expects. Anything else comes back
 * as what was measured, so the test says what went wrong.
 */
public class FakeRecognizer extends RecognitionService {
    private static final String TAG = "NeonSiriTest";
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onStartListening(Intent intent, Callback callback) {
        ParcelFileDescriptor source = intent.getParcelableExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE);
        int rate = intent.getIntExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 0);
        int channels = intent.getIntExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 0);
        int encoding = intent.getIntExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, 0);
        Log.i(TAG, "fake recognizer: listening, source " + (source != null) + ", " + rate + " Hz, "
                + channels + " ch, encoding " + encoding + ", language "
                + intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE));
        try {
            callback.readyForSpeech(new Bundle());
        } catch (Exception ignored) {
        }
        if (source == null) {
            main.post(() -> { try { callback.error(SpeechRecognizer.ERROR_CLIENT); } catch (Exception ignored) { } });
            return;
        }
        new Thread(() -> {
            ByteArrayOutputStream all = new ByteArrayOutputStream();
            try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(source)) {
                byte[] buf = new byte[4096];
                long deadline = System.currentTimeMillis() + 20_000;
                int n;
                while ((n = in.read(buf)) > 0 && System.currentTimeMillis() < deadline) all.write(buf, 0, n);
            } catch (Exception e) {
                Log.w(TAG, "fake recognizer: read failed", e);
            }
            byte[] b = all.toByteArray();
            int samples = b.length / 2;
            double energy = 0;
            int crossings = 0;
            short prev = 0;
            // skip the first 60 ms: the decoder's warm-up
            for (int i = Math.min(samples, 960); i < samples; i++) {
                short v = (short) ((b[2 * i] & 0xFF) | (b[2 * i + 1] << 8));
                energy += (double) v * v;
                if ((prev < 0) != (v < 0)) crossings++;
                prev = v;
            }
            int counted = Math.max(1, samples - Math.min(samples, 960));
            int rms = (int) Math.sqrt(energy / counted);
            int hz = rate > 0 ? (int) Math.round(crossings / 2.0 * rate / counted) : 0;
            Log.i(TAG, "fake recognizer: " + samples + " samples, rms " + rms + ", about " + hz + " Hz");
            boolean tone = rate == 16000 && channels == 1 && Math.abs(hz - 440) <= 25 && rms > 1500;
            ArrayList<String> heard = new ArrayList<>();
            heard.add(tone ? "öppna Hikari" : "tone " + hz + " Hz rms " + rms + " at " + rate);
            Bundle results = new Bundle();
            results.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, heard);
            main.post(() -> {
                try {
                    callback.endOfSpeech();
                    callback.results(results);
                } catch (Exception e) {
                    Log.w(TAG, "fake recognizer: could not answer", e);
                }
            });
        }).start();
    }

    @Override
    protected void onStopListening(Callback callback) {
    }

    @Override
    protected void onCancel(Callback callback) {
    }
}
