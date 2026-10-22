package io.casehub.agency.summarisation.yaml.builtin;

import java.util.List;
import java.util.Map;

public class BatchContextView {
    private final int size;
    private final Map<String, Map<String, Integer>> counts;
    private final Map<String, Double> sums;
    private final Map<String, Object> avgs;
    private final List<Map<String, Object>> batch;

    @SuppressWarnings("unchecked")
    public BatchContextView(Map<String, Object> raw) {
        this.size = (int) raw.getOrDefault("size", 0);
        this.counts = (Map<String, Map<String, Integer>>) raw.getOrDefault("counts", Map.of());
        this.sums = (Map<String, Double>) raw.getOrDefault("sums", Map.of());
        this.avgs = (Map<String, Object>) raw.getOrDefault("avgs", Map.of());
        this.batch = (List<Map<String, Object>>) raw.getOrDefault("batch", List.of());
    }

    public int getSize() { return size; }
    public List<Map<String, Object>> getBatch() { return batch; }

    public int countOf(String field, String value) {
        return counts.getOrDefault(field, Map.of()).getOrDefault(value, 0);
    }

    public double sumOf(String field) {
        return sums.getOrDefault(field, 0.0);
    }

    public double avgOf(String field) {
        var val = avgs.getOrDefault(field, null);
        return val instanceof Number n ? n.doubleValue() : 0.0;
    }
}
