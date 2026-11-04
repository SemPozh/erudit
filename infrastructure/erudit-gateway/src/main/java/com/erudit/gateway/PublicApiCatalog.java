package com.erudit.gateway;

import java.io.IOException;
import java.util.*;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Immutable-at-runtime public projections of service-owned build-time contracts. */
@Component
public class PublicApiCatalog {
    private final Map<String, Map<String, Object>> documents;
    private final List<Endpoint> endpoints;

    public PublicApiCatalog() throws IOException {
        var sources = new TreeMap<String, Map<String, Object>>();
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        var yaml = new Yaml(new SafeConstructor(options));
        for (var resource : new PathMatchingResourcePatternResolver().getResources("classpath*:gateway-contracts/*.yaml")) {
            String name = Objects.requireNonNull(resource.getFilename()).replace(".yaml", "");
            try (var input = resource.getInputStream()) { sources.put(name, yaml.load(input)); }
        }
        if (sources.isEmpty()) { throw new IllegalStateException("No service OpenAPI contracts bundled"); }
        documents = project(sources);
        endpoints=new ArrayList<>();
        documents.values().forEach(doc -> objectMap(doc.get("paths")).forEach((path,item) -> {
            var methods=new HashSet<String>();
            objectMap(item).keySet().stream().filter(method -> Set.of("get","post","put","patch","delete","head","options").contains(method))
                    .forEach(method -> methods.add(method.toUpperCase(Locale.ROOT)));
            String pattern=Arrays.stream(path.split("/",-1)).map(part -> part.startsWith("{")?"[^/]+":java.util.regex.Pattern.quote(part))
                    .collect(java.util.stream.Collectors.joining("/"));
            endpoints.add(new Endpoint(java.util.regex.Pattern.compile("^"+pattern+"$"),methods));
        }));
    }

    public boolean supports(String method,String path) {
        String effective=method.equals("HEAD")?"GET":method;
        return endpoints.stream().anyMatch(endpoint -> endpoint.path().matcher(path).matches()
                && (effective.equals("OPTIONS")||endpoint.methods().contains(effective)));
    }
    private record Endpoint(java.util.regex.Pattern path,Set<String> methods){}

    static Map<String, Map<String, Object>> project(Map<String, Map<String, Object>> sources) {
        var result = new LinkedHashMap<String, Map<String, Object>>();
        var pathsSeen = new HashSet<String>();
        var operationsSeen = new HashSet<String>();
        sources.forEach((name, source) -> {
            Map<String, Object> paths = new LinkedHashMap<>();
            objectMap(source.get("paths")).forEach((path, value) -> {
                if (!path.startsWith("/api/v1/")) { return; }
                if (!pathsSeen.add(path)) { throw new IllegalStateException("Duplicate public path: " + path); }
                paths.put(path, value);
                objectMap(value).forEach((method, operation) -> {
                    if (Set.of("get", "post", "put", "patch", "delete", "head", "options", "trace").contains(method)) {
                        Object id = objectMap(operation).get("operationId");
                        if (!(id instanceof String) || !operationsSeen.add((String) id)) {
                            throw new IllegalStateException("Missing or duplicate operationId at " + path + ": " + id);
                        }
                    }
                });
            });
            Map<String, Object> document = new LinkedHashMap<>();
            document.put("openapi", source.get("openapi"));
            document.put("info", source.get("info"));
            document.put("servers", List.of(Map.of("url", "/")));
            document.put("paths", paths);
            var components = new LinkedHashMap<String, Object>();
            var pending = new LinkedHashSet<String>();
            collectRefs(paths, pending);
            var copied = new HashSet<String>();
            while (!pending.isEmpty()) {
                String ref = pending.iterator().next(); pending.remove(ref);
                if (!copied.add(ref)) { continue; }
                String[] tokens = ref.split("/");
                if (tokens.length != 4 || !ref.startsWith("#/components/")) {
                    throw new IllegalStateException("Unsupported public reference: " + ref);
                }
                Object value = objectMap(objectMap(source.get("components")).get(tokens[2])).get(tokens[3]);
                if (value == null) { throw new IllegalStateException("Unresolved public reference: " + ref); }
                @SuppressWarnings("unchecked")
                var group = (Map<String, Object>) components.computeIfAbsent(tokens[2], ignored -> new LinkedHashMap<>());
                group.put(tokens[3], value);
                collectRefs(value, pending);
            }
            document.put("components", components);
            // Retain only public tags; internal endpoint tags and DTOs never reach Swagger.
            var tags = new LinkedHashSet<String>();
            collectTags(paths, tags);
            var definitions = new LinkedHashMap<String, Object>();
            if (source.get("tags") instanceof List<?> declared) {
                declared.forEach(tag -> {
                    String tagName = (String) objectMap(tag).get("name");
                    if (tags.contains(tagName)) { definitions.put(tagName, tag); }
                });
            }
            tags.forEach(tag -> definitions.putIfAbsent(tag, Map.of("name", tag)));
            document.put("tags", new ArrayList<>(definitions.values()));
            result.put(name, objectMap(clean(document)));
        });
        return Collections.unmodifiableMap(result);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectMap(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }
    private static void collectRefs(Object node, Set<String> refs) {
        if (node instanceof Map<?, ?> map) {
            if (map.get("$ref") instanceof String ref) { refs.add(ref); }
            map.values().forEach(value -> collectRefs(value, refs));
        } else if (node instanceof List<?> list) { list.forEach(value -> collectRefs(value, refs)); }
    }
    private static void collectTags(Object node, Set<String> tags) {
        if (node instanceof Map<?, ?> map) {
            if (map.get("tags") instanceof List<?> values) { values.forEach(tag -> tags.add((String) tag)); }
            map.values().forEach(value -> collectTags(value, tags));
        } else if (node instanceof List<?> list) { list.forEach(value -> collectTags(value, tags)); }
    }
    private static Object clean(Object node) {
        if (node instanceof Map<?, ?> map) {
            var result = new LinkedHashMap<String, Object>();
            map.forEach((key, value) -> {
                if (!Set.of("security", "securitySchemes", "servers").contains(key)) { result.put((String) key, clean(value)); }
                else if (key.equals("servers")) { result.put("servers", List.of(Map.of("url", "/"))); }
            });
            return result;
        }
        if (node instanceof List<?> list) { return list.stream().map(PublicApiCatalog::clean).toList(); }
        return node;
    }
    Map<String, Map<String, Object>> documents() { return documents; }
}
