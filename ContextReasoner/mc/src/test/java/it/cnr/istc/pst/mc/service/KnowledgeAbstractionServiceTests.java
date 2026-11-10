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
    void preservesIdentityAndMapsAllAlignmentRoles() {
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
        assertThat(schema.alignmentConstraints()).hasSize(3).allSatisfy(constraint -> {
            assertThat(constraint.alignmentType()).isEqualTo("RegionChangeAlignment");
            assertThat(constraint.qualities()).containsExactly(qualityVariable);
            assertThat(constraint.initialValues()).singleElement().satisfies(value -> assertThat(value).startsWith("?initial"));
            assertThat(constraint.resultingValues()).singleElement().satisfies(value -> assertThat(value).startsWith("?resulting"));
            assertThat(constraint.initialValues()).isNotEqualTo(constraint.resultingValues());
        });
    }

    @Test
    void regionChangeProducesDeleteAndAddEffectsWithoutExplicitNegatesAlignment() {
        Model model = ModelFactory.createDefaultModel();
        addGrounding(model, "change", true, true, false, true, false);

        ActionSchema schema = service.abstractModel(model).schemas().get(0);
        String initial = schema.alignmentConstraints().get(0).initialValues().get(0);
        String resulting = schema.alignmentConstraints().get(0).resultingValues().get(0);

        assertThat(schema.preconditions()).singleElement().satisfies(condition ->
                assertThat(condition.value()).isEqualTo(initial));
        assertThat(schema.negativeEffects()).singleElement().satisfies(condition ->
                assertThat(condition.value()).isEqualTo(initial));
        assertThat(schema.positiveEffects()).singleElement().satisfies(condition ->
                assertThat(condition.value()).isEqualTo(resulting));
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
                            "requiresAlignment", "assertsAlignment", "negatesAlignment",
                            "concernsObject", "concernsQuality", "hasInitialValue", "hasResultingValue", "type")
                    .doesNotContain("hasBeliefAlignmentValue");
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
        Resource initialValue = typed(model, "initial-value-" + suffix, "DiscretePoseRegion");
        Resource resultingValue = typed(model, "resulting-value-" + suffix, "DiscretePoseRegion");
        function.addProperty(model.createProperty(SOHO + "canBePerformedBy"), agent)
                .addProperty(model.createProperty(SOHO + "hasTarget"), target);
        if (precondition) function.addProperty(model.createProperty(MC + "hasPreconditionOn"), quality);
        if (effect) function.addProperty(model.createProperty(SOHO + "hasEffectOn"), quality);
        if (requires) addAlignment(model, function, "requiresAlignment", "required-" + suffix, target, quality, initialValue, resultingValue);
        if (asserts) addAlignment(model, function, "assertsAlignment", "asserted-" + suffix, target, quality, initialValue, resultingValue);
        if (negates) addAlignment(model, function, "negatesAlignment", "negated-" + suffix, target, quality, initialValue, resultingValue);
    }

    private void addAlignment(Model model, Resource function, String role, String id,
            Resource object, Resource quality, Resource initialValue, Resource resultingValue) {
        Resource alignment = typed(model, id, "RegionChangeAlignment");
        function.addProperty(model.createProperty(MC + role), alignment);
        alignment.addProperty(model.createProperty(MC + "concernsObject"), object)
                .addProperty(model.createProperty(MC + "concernsQuality"), quality)
                .addProperty(model.createProperty(MC + "hasInitialValue"), initialValue)
                .addProperty(model.createProperty(MC + "hasResultingValue"), resultingValue);
    }

    private Resource typed(Model model, String id, String type) {
        return model.createResource("urn:test:" + id).addProperty(RDF.type, model.createResource(MC + type));
    }
}
