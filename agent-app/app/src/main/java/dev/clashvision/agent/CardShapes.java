package dev.clashvision.agent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

/** Greyscale portrait matching for known cards when the general classifier is uncertain.
 * Reference art and coordinates from clash-jev (MIT). */
public final class CardShapes {
    private final Map<String, double[]> bank = new LinkedHashMap<>();

    public CardShapes(Reader source) throws IOException {
        try (BufferedReader reader = new BufferedReader(source)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split(" ");
                if (fields.length != 225) throw new IOException("Invalid card shape");
                double[] shape = new double[224];
                for (int i = 0; i < shape.length; i++) shape[i] = Integer.parseInt(fields[i + 1]) / 10000.0;
                bank.put(fields[0], shape);
            }
        }
    }

    public String identify(ByteBuffer rgba, int w, int h, int stride, int pixel, int slot) {
        // The reference bank has measured tablet layouts for these two aspect ratios.
        double ratio = (double) w / h;
        if (ratio < .61 || ratio > .68) return null;
        boolean narrow = Math.abs(ratio - .625) < Math.abs(ratio - .662);
        double scale = w / 419.0 / (narrow ? .94 : 1.0);
        boolean next = slot == 4;
        String winner = null;
        double top = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -2; dy <= 2; dy += 2) {
                double bw = next ? 25 : 54, bh = next ? 31 : 66;
                double x = w / 2.0 + ((next ? 52 : 115 + slot * 67) + dx - 209.5) * scale;
                double y = h + ((next ? 591 : 529) + dy - 633 + (narrow ? 1 : 0)) * scale;
                double[] a = feature(rgba, stride, pixel, x, y, bw * scale, bh * scale);
                double best = -1, second = -1;
                String name = null;
                for (Map.Entry<String, double[]> entry : bank.entrySet()) {
                    double score = 0;
                    for (int i = 0; i < a.length; i++) score += a[i] * entry.getValue()[i];
                    if (score > best) { second = best; best = score; name = entry.getKey(); }
                    else if (score > second) second = score;
                }
                if (best > top && best - second >= .18 && best >= (next ? .72 : .67)) {
                    top = best;
                    winner = name;
                }
            }
        }
        return winner;
    }

    private static double[] feature(ByteBuffer img, int stride, int pixel,
                                     double x, double y, double w, double h) {
        double[] a = new double[224];
        double sum = 0;
        for (int j = 0; j < 16; j++) {
            for (int i = 0; i < 14; i++) {
                double gray = 0;
                for (int v = 0; v < 2; v++) for (int u = 0; u < 2; u++) {
                    int sx = (int)(x + w * (.15 + .70 * (i + .25 + .5 * u) / 14));
                    int sy = (int)(y + h * (.09 + .64 * (j + .25 + .5 * v) / 16));
                    int base = img.position() + sy * stride + sx * pixel;
                    gray += .299 * (img.get(base) & 255) + .587 * (img.get(base + 1) & 255)
                            + .114 * (img.get(base + 2) & 255);
                }
                a[j * 14 + i] = gray;
                sum += gray;
            }
        }
        double norm = 0;
        for (int i = 0; i < a.length; i++) { a[i] -= sum / a.length; norm += a[i] * a[i]; }
        norm = Math.sqrt(norm);
        if (norm > 0) for (int i = 0; i < a.length; i++) a[i] /= norm;
        return a;
    }
}
