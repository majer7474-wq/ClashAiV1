package dev.clashvision.agent;

import java.nio.ByteBuffer;

/** Estimates filled segments only after detecting the elixir drop and its two-tone track. */
public final class ElixirGauge {
    private ElixirGauge() {}

    public static int estimate(ByteBuffer rgba, int width, int height, int rowStride, int pixelStride) {
        if (width < 120 || height <= width || pixelStride < 3) return -1;
        int hudWidth = Math.min(width, (int)Math.round(height * 9.0 / 16));
        int inset = (width - hudWidth) / 2;
        int[] votes = new int[11];
        for (int row = 0; row < 12; row++) {
            int y = (int)(height * (0.962 + row * 0.0025));
            if (color(rgba, hudWidth, inset, y, 0.24, rowStride, pixelStride) != 1) continue;
            int filled = 0;
            boolean emptySeen = false;
            boolean valid = true;
            for (int slot = 0; slot < 10; slot++) {
                // Sample near the right side of each segment to avoid the elixir number.
                double fraction = 0.268 + (slot + 0.76) * 0.0704;
                int kind = color(rgba, hudWidth, inset, y, fraction, rowStride, pixelStride);
                if (kind == 0 || (kind == 1 && emptySeen)) {
                    valid = false;
                    break;
                }
                if (kind == 1) filled++;
                else emptySeen = true;
            }
            if (valid) votes[filled]++;
        }
        int best = 0;
        for (int i = 1; i < votes.length; i++) if (votes[i] > votes[best]) best = i;
        return votes[best] >= 3 ? best : -1;
    }

    private static int color(ByteBuffer image, int w, int inset, int y, double fx, int stride, int pixel) {
        int i = image.position() + y * stride + (inset + (int)(w * fx)) * pixel;
        if (i < 0 || i + 2 >= image.limit()) return 0;
        int r = image.get(i) & 255, g = image.get(i + 1) & 255, b = image.get(i + 2) & 255;
        if (r >= 150 && b >= 145 && Math.min(r, b) - g >= 65) return 1;
        if (r < 30 && g >= 18 && g <= 85 && b >= 86 && b - g >= 48) return 2;
        return 0;
    }
}
