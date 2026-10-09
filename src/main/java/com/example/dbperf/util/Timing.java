package com.example.dbperf.util;

import java.util.Arrays;

public final class Timing {

    private Timing() {
    }

    /** Runs the task a few times and returns the MEDIAN time in milliseconds (ignores one-off spikes). */
    public static double medianMs(int runs, Runnable task) {
        task.run(); // warm-up: JIT, caches, query plan
        double[] times = new double[runs];
        for (int i = 0; i < runs; i++) {
            long t0 = System.nanoTime();
            task.run();
            times[i] = (System.nanoTime() - t0) / 1_000_000.0;
        }
        Arrays.sort(times);
        return times[runs / 2];
    }

    /** Runs the task once and returns the time in milliseconds. */
    public static double onceMs(Runnable task) {
        long t0 = System.nanoTime();
        task.run();
        return (System.nanoTime() - t0) / 1_000_000.0;
    }

    public static String fmt(double ms) {
        return String.format("%,.2f ms", ms);
    }

    public static String speedup(double slow, double fast) {
        return fast <= 0 ? "n/a" : String.format("~%,.0fx faster", slow / fast);
    }
}
