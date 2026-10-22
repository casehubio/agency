package io.casehub.agency.summarisation.yaml.deployment;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.jackson.YamlMappers;
import io.casehub.agency.summarisation.yaml.PipelineDefinition;
import io.casehub.agency.summarisation.yaml.PipelineValidator;
import io.casehub.agency.summarisation.yaml.PipelineWrapper;
import io.casehub.agency.summarisation.yaml.SummariserRegistry;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

class SummarisationYamlProcessor {

    private static final String FEATURE = "casehub-summarisation-yaml";
    private static final String YAML_PATH = "META-INF/summarisation/";
    private static final ObjectMapper MAPPER = YamlMappers.create();

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    static List<PipelineDefinition> discoverPipelines(ClassLoader classLoader) throws IOException {
        var pipelines = new ArrayList<PipelineDefinition>();
        var resources = classLoader.getResources(YAML_PATH);
        while (resources.hasMoreElements()) {
            var url = resources.nextElement();
            try (var stream = url.openStream()) {
                if (stream != null) {
                    // Directory listing not directly available via classloader,
                    // consumers place individual YAML files
                }
            }
        }
        return pipelines;
    }

    static PipelineDefinition parsePipeline(InputStream yamlStream) throws IOException {
        return MAPPER.readValue(yamlStream, PipelineWrapper.class).pipeline();
    }

    static List<PipelineValidator.ValidationError> validatePipeline(
            PipelineDefinition definition, SummariserRegistry registry) {
        return new PipelineValidator().validate(definition, registry);
    }
}
