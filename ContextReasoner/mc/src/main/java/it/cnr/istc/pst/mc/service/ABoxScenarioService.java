package it.cnr.istc.pst.mc.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import it.cnr.istc.pst.mc.api.ScenarioDtos.ScenarioCatalog;
import it.cnr.istc.pst.mc.api.ScenarioDtos.ScenarioInfo;

/**
 * Discovers distributable Turtle ABoxes and atomically replaces the current
 * asserted scenario with a selected file. The ontology baseline is retained and
 * inference is refreshed only after the complete ABox parses successfully.
 */
@Service
public class ABoxScenarioService {

    private static final String LOCATION_PATTERN = "classpath*:onto/aboxes/*.ttl";
    private static final String PROGRAMMATIC_ID = "programmatic-uc1";

    private final ContextReasonerService knowledge;
    private final PathMatchingResourcePatternResolver resources = new PathMatchingResourcePatternResolver();

    public ABoxScenarioService(ContextReasonerService knowledge) {
        this.knowledge = knowledge;
    }

    public ScenarioCatalog catalog() {
        String active = knowledge.getActiveScenario();
        List<ScenarioInfo> files = scenarioResources().stream()
                .map(resource -> info(resource, active))
                .sorted(Comparator.comparing(ScenarioInfo::label))
                .toList();
        var scenarios = new java.util.ArrayList<ScenarioInfo>();
        scenarios.add(new ScenarioInfo(PROGRAMMATIC_ID, "Programmatic Mobipick UC1",
                "Java scenario builder", PROGRAMMATIC_ID.equals(active)));
        scenarios.addAll(files);
        return new ScenarioCatalog(List.copyOf(scenarios), active);
    }

    public ScenarioInfo load(String id) {
        if (PROGRAMMATIC_ID.equals(id)) {
            knowledge.loadMobipickUc1();
            return catalog().scenarios().stream().filter(ScenarioInfo::active).findFirst().orElseThrow();
        }
        Resource resource = scenarioResources().stream()
                .filter(candidate -> scenarioId(candidate).equals(id))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown ABox scenario: " + id));
        Model abox = ModelFactory.createDefaultModel();
        try (InputStream input = resource.getInputStream()) {
            RDFDataMgr.read(abox, input, Lang.TURTLE);
        } catch (RuntimeException | IOException exception) {
            throw new IllegalArgumentException("Cannot parse ABox scenario " + id + ": "
                    + exception.getMessage(), exception);
        }
        knowledge.loadABoxScenario(id, abox);
        return info(resource, id);
    }

    private List<Resource> scenarioResources() {
        try {
            return Arrays.stream(resources.getResources(LOCATION_PATTERN))
                    .filter(Resource::isReadable)
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot discover ABox scenarios", exception);
        }
    }

    private ScenarioInfo info(Resource resource, String active) {
        String id = scenarioId(resource);
        return new ScenarioInfo(id, label(id), "classpath:onto/aboxes/" + resource.getFilename(),
                id.equals(active));
    }

    private String scenarioId(Resource resource) {
        String filename = resource.getFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".ttl")) {
            throw new IllegalStateException("ABox scenario has no Turtle filename: " + resource);
        }
        return filename.substring(0, filename.length() - 4);
    }

    private String label(String id) {
        String[] words = id.replace('-', '_').split("_+");
        return Arrays.stream(words)
                .map(word -> word.isBlank() ? word
                        : Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(java.util.stream.Collectors.joining(" "));
    }
}
