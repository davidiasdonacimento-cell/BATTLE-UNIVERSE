package com.battleuniverse.game;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Minimal crash recorder. It does not hide or swallow crashes: the original
 * Android handler is always invoked after a bounded diagnostic is persisted.
 */
public final class CrashDiagnostics {
    private static final String TAG = "BUCrashDiagnostics";
    private static final String FILE_NAME = "last_crash.txt";
    private static final int MAX_LOG_BYTES = 48 * 1024;
    private static volatile boolean installed;

    private CrashDiagnostics() { }

    public static synchronized void install(Context context) {
        if (installed || context == null) return;
        final File output = new File(context.getApplicationContext().getFilesDir(), FILE_NAME);
        final Thread.UncaughtExceptionHandler previous =
            Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                StringWriter trace = new StringWriter();
                error.printStackTrace(new PrintWriter(trace));
                String timestamp = new SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(new Date());
                String report = "BATTLE UNIVERSE crash diagnostic\n"
                    + "time=" + timestamp + "\n"
                    + "thread=" + thread.getName() + "\n"
                    + trace;
                byte[] bytes = report.getBytes(StandardCharsets.UTF_8);
                int start = Math.max(0, bytes.length - MAX_LOG_BYTES);
                try (FileOutputStream stream = new FileOutputStream(output, false)) {
                    stream.write(bytes, start, bytes.length - start);
                    stream.flush();
                }
                Log.e(TAG, "Crash diagnostic saved locally.");
            } catch (Throwable recordingFailure) {
                Log.e(TAG, "Could not persist crash diagnostic.", recordingFailure);
            } finally {
                if (previous != null) previous.uncaughtException(thread, error);
                else {
                    android.os.Process.killProcess(android.os.Process.myPid());
                    System.exit(10);
                }
            }
        });
        installed = true;
    }
}
