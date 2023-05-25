package org.springframework.boot.context.config;

import org.springframework.beans.factory.config.YamlProcessor;
import org.springframework.core.io.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class YamlLoader extends YamlProcessor {

    public YamlLoader(Resource resource) {
        setResources(resource);
    }

    public List<Map<String, Object>> load() {
        List<Map<String, Object>> result = new ArrayList<>();
        process((properties, map) -> result.add(getFlattenedMap(map)));
        return result;
    }
}
