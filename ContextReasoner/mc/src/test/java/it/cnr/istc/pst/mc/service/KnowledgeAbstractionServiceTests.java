package it.cnr.istc.pst.mc.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;

import it.cnr.istc.pst.mc.api.AbstractionResponse;
import it.cnr.istc.pst.mc.api.AbstractionResponse.ActionSchema;
import it.cnr.istc.pst.mc.api.SchemaGroundingsResponse;

class KnowledgeAbstractionServiceTests {

    private static final String MC = "http://pst.istc.cnr.it/ontologies/2026/metacognition#";
    private static final String SOHO = "http://pst.istc.cnr.it/ontologies/2019/01/soho#";
    private final KnowledgeAbstractionService service = new KnowledgeAbstractionService(null);

    @Test
    void equivalentGroundingsCollapseIntoOneSchema() {
        Model model = ModelFactory.createDefaultModel();
        addGrounding(model, "one", true, true, true, true, true);
        addGrounding(model, "two", true, true, true, true, true);

        AbstractionResponse response = service.abstractModel(model);

        assertThat(response.schemas()).hasSize(1);
        assertThat(response.schemas().get(0).groundingCount()).isEqualTo(2);
    }

    @Test
    void sameFunctionTypeWithDifferentRolesProducesDifferentSchemas() {
        Model model = ModelFactory.createDefaultModel();
        addGrounding(model, "complete", true, true, false, false, false);
        addGrounding(model, "withoutEffect", true, false, false, false, false);

        AbstractionResponse response = service.abstractModel(model);

        assertThat(response.schemas()).hasSize(2);
        assertThat(response.schemas()).extracting(ActionSchema::functionType).containsOnly("MoveTo");
    }

    @Test
    void preservesIdentityAndMapsAllFluentRoles() {
        Model model = ModelFactory.createDefaultModel();
        addGrounding(model, "one", true, true, true, true, true);

        ActionSchema schema = service.abstractModel(model).schemas().get(0);

        String qualityVariable = schema.parameters().stream()
                .filter(parameter -> parameter.roles().contains("hasPreconditionOn"))
                .findFirst().orElseThrow().variable();
        assertThat(schema.parameters().stream().filter(parameter -> parameter.variable().equals(qualityVariable))
                .findFirst().orElseThrow().roles())
                .contains("hasEffectOn", "concernsQuality");
        assertThat(schema.preconditions()).singleElement().satisfies(condition ->
                assertThat(condition.quality()).isEqualTo(qualityVariable));
        assertThat(schema.positiveEffects()).singleElement().satisfies(condition ->
                assertThat(condition.quality()).isEqualTo(qualityVariable));
        assertThat(schema.negativeEffects()).singleElement().satisfies(condition ->
                assertThat(condition.quality()).isEqualTo(qualityVariable));
    }

    @Test
    void abstractionDoesNotModifyModel() {
        Model model = ModelFactory.createDefaultModel();
        addGrounding(model, "one", true, true, true, true, true);
        Model before = ModelFactory.createDefaultModel().add(model);

        service.abstractModel(model);

        assertThat(model.isIsomorphicWith(before)).isTrue();
        assertThat(model.size()).isEqualTo(before.size());
    }

    @Test
    void schemaInspectionReturnsEachGroundingAndPlanningSubgraph() {
        Model model = ModelFactory.createDefaultModel();
        addGrounding(model, "one", true, true, true, true, true);
        addGrounding(model, "two", true, true, true, true, true);
        String signatureId = service.abstractModel(model).schemas().get(0).signatureId();

        SchemaGroundingsResponse response = service.inspectSchema(model, signatureId);

        assertThat(response.groundings()).hasSize(2);
        assertThat(response.groundings()).allSatisfy(grounding -> {
            assertThat(grounding.triples()).extracting(SchemaGroundingsResponse.Triple::predicate)
                    .contains("canBePerformedBy", "hasTarget", "hasPreconditionOn", "hasEffectOn",
                            "requiresFluent", "assertsFluent", "negatesFluent",
                            "concernsObject", "concernsQuality", "hasFluentValue", "type");
            assertThat(grounding.triples().stream().filter(triple -> triple.predicate().equals("type"))
                    .collect(java.util.stream.Collectors.groupingBy(SchemaGroundingsResponse.Triple::subject)))
                    .allSatisfy((subject, types) -> assertThat(types).hasSize(1));
        });
    }

    private void addGrounding(Model model, String suffix, boolean precondition, boolean effect,
            boolean requires, boolean asserts, boolean negates) {
        Resource function = typed(model, "function-" + suffix, "MoveTo");
        Resource agent = typed(model, "agent-" + suffix, "EmbodiedAgent");
        Resource target = typed(model, "target-" + suffix, "RobotEmbodiment");
        Resource quality = typed(model, "quality-" + suffix, "Pose");
        Resource value = typed(model, "value-" + suffix, "DiscretePoseRegion");
        function.addProperty(model.createProperty(SOHO + "canBePerformedBy"), agent)
                .addProperty(model.createProperty(SOHO + "hasTarget"), target);
        if (precondition) function.addProperty(model.createProperty(MC + "hasPreconditionOn"), quality);
        if (effect) function.addProperty(model.createProperty(SOHO + "hasEffectOn"), quality);
        if (requires) addFluent(model, function, "requiresFluent", "required-" + suffix, target, quality, value);
        if (asserts) addFluent(model, function, "assertsFluent", "asserted-" + suffix, target, quality, value);
        if (negates) addFluent(model, function, "negatesFluent", "negated-" + suffix, target, quality, value);
    }

    private void addFluent(Model model, Resource function, String role, String id,
            Resource object, Resource quality, Resource value) {
        Resource fluent = typed(model, id, "Fluent");
        function.addProperty(model.createProperty(MC + role), fluent);
        fluent.addProperty(model.createProperty(MC + "concernsObject"), object)
                .addProperty(model.createProperty(MC + "concernsQuality"), quality)
                .addProperty(model.createProperty(MC + "hasFluentValue"), value);
    }

    private Resource typed(Model model, String id, String type) {
        return model.createResource("urn:test:" + id).addProperty(RDF.type, model.createResource(MC + type));
    }
}
