package io.casehub.agency.summarisation.yaml.builtin;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BatchContext {

    private BatchContext() {}

    private static class DefaultZeroMap extends HashMap<String, Integer> {
        @Override
        public Integer get(Object key) {
            return getOrDefault(key, 0);
        }
    }

    public static Map<String, Object> compute(List<Map<String, Object>> events,
                                               List<String> aggregateFields) {
        var ctx = new LinkedHashMap<String, Object>();
        ctx.put("size", events.size());
        ctx.put("batch", events);

        if (!aggregateFields.isEmpty()) {
            var counts = new LinkedHashMap<String, Map<String, Integer>>();
            var sums = new LinkedHashMap<String, Double>();
            var numericCounts = new LinkedHashMap<String, Integer>();

            for (var field : aggregateFields) {
                counts.put(field, new DefaultZeroMap());
                sums.put(field, 0.0);
                numericCounts.put(field, 0);
            }

            for (var event : events) {
                for (var field : aggregateFields) {
                    var value = event.get(field);
                    if (value != null) {
                        var fieldCounts = counts.get(field);
                        var key = value.toString();
                        fieldCounts.merge(key, 1, Integer::sum);

                        if (value instanceof Number n) {
                            sums.merge(field, n.doubleValue(), Double::sum);
                            numericCounts.merge(field, 1, Integer::sum);
                        }
                    }
                }
            }

            var avgs = new LinkedHashMap<String, Object>();
            for (var field : aggregateFields) {
                int nc = numericCounts.getOrDefault(field, 0);
                avgs.put(field, nc > 0 ? sums.get(field) / nc : null);
            }

            ctx.put("counts", counts);
            ctx.put("sums", sums);
            ctx.put("avgs", avgs);
        }

        return ctx;
    }
}
