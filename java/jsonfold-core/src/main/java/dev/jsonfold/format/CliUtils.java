package dev.jsonfold.format;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class CliUtils {

    @SafeVarargs
    private static <K,V> LinkedHashMap<K,V> mapOf(
        Map.Entry<? extends K, ? extends V>... entries) {
        LinkedHashMap<K,V> map = new LinkedHashMap<>();

        for (Map.Entry<? extends K, ? extends V> e : entries) {
            map.put(e.getKey(), e.getValue());
        }

        return map;
    }
    
    static Object demoData() {
                
        Map<String, Object> root = new LinkedHashMap<>();

        root.put("meta", mapOf(
                Map.entry("version", 1),
                Map.entry("ok", true),
                Map.entry("name", "jsonfold demo"))
            );

        root.put("ids", List.of(1, 2, 3, 4, 5, 6));

        root.put("matrix", List.of(
                List.of(1, 2),
                List.of(3, 4),
                List.of(5, 6))
            );

        root.put("items", List.of(
                mapOf(
                    Map.entry("id", 1),
                    Map.entry("name", "alpha")
                ),
                mapOf(
                    Map.entry("id", 2),
                    Map.entry("name", "beta")
                    )
                )
            );

        root.put("long", List.of(
                "this is a long message that may force the block to stay expanded",
                "second",
                "third",
                "fourth"));

        root.put("single_array", List.of(1));
        root.put("single_object", Map.of("x", 2));


        root.put("long_array", IntStream.rangeClosed(1, 50)
                .mapToObj(i -> "a" + i)
                .collect(Collectors.toList()));
                
        root.put("wide_array", IntStream.rangeClosed(1, 9)
            .mapToObj(i -> "abcdefghijklmnopqrstuvwxyz" + i)
            .toList());
                
        root.put("wide_object", IntStream.rangeClosed(1, 9)
            .boxed()
            .collect(Collectors.toMap(
            i -> "abcdefghijk" + i,
            i -> "lmnopqrstuvwxyz" + i,
            (a, b) -> a,
            LinkedHashMap::new
        )));


        return root;
    }
}
