package it.cnr.istc.pst.mc;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

import it.cnr.istc.pst.mc.api.AbstractionResponse.ActionSchema;
import it.cnr.istc.pst.mc.api.KnowledgeEditorDtos.KnowledgeGraphResponse;
import it.cnr.istc.pst.mc.api.KnowledgeEditorDtos.RdfTerm;
import it.cnr.istc.pst.mc.api.KnowledgeEditorDtos.RdfTriple;
import it.cnr.istc.pst.mc.service.ContextReasonerService;
import it.cnr.istc.pst.mc.service.KnowledgeAbstractionService;
import it.cnr.istc.pst.mc.service.KnowledgeEditingService;

@SpringBootTest
class McApplicationTests {

	@Autowired
	private ContextReasonerService contextReasoner;

	@Autowired
	private KnowledgeAbstractionService abstraction;

	@Autowired
	private KnowledgeEditingService editing;

	@Test
	void contextLoads() {
	}

	@Test
	void revisedMoveToRuleProducesPartAwareGuardAndRegionChangeAlignment() {
		contextReasoner.loadMobipickUc1();

		ActionSchema moveTo = abstraction.abstractCurrentKnowledge().schemas().stream()
				.filter(schema -> schema.functionType().equals("MoveTo"))
				.findFirst().orElseThrow();

		assertThat(moveTo.alignmentConstraints()).filteredOn(constraint ->
				constraint.alignmentType().equals("RegionChangeAlignment")).isNotEmpty().allSatisfy(constraint -> {
			assertThat(constraint.alignmentType()).isEqualTo("RegionChangeAlignment");
			assertThat(constraint.initialValues()).isNotEmpty();
			assertThat(constraint.resultingValues()).isNotEmpty();
			assertThat(constraint.initialValues()).isNotEqualTo(constraint.resultingValues());
		});
		assertThat(moveTo.alignmentConstraints()).filteredOn(constraint ->
				constraint.alignmentType().equals("RegionGuardAlignment")).isNotEmpty().allSatisfy(guard -> {
			assertThat(guard.objects()).isNotEmpty();
			assertThat(guard.qualities()).isNotEmpty();
			assertThat(guard.values()).isNotEmpty();
		});
		assertThat(moveTo.parameters()).anySatisfy(parameter -> {
			assertThat(parameter.type()).isEqualTo("NavigationReferencePart");
			assertThat(parameter.roles()).contains("concernsObject");
		});
		assertThat(moveTo.parameters()).anySatisfy(parameter -> {
			assertThat(parameter.type()).isEqualTo("NavigationCapability");
			assertThat(parameter.roles()).contains("requiresCapability");
		});
		assertThat(moveTo.parameters()).anySatisfy(parameter -> {
			assertThat(parameter.type()).isEqualTo("Pose");
			assertThat(parameter.roles()).contains(
					"hasPreconditionOn", "hasEffectOn", "concernsQuality");
		});
		assertThat(moveTo.preconditions()).isNotEmpty();
		assertThat(moveTo.positiveEffects()).isNotEmpty();
		assertThat(moveTo.negativeEffects()).isNotEmpty();
		assertThat(moveTo.alignmentConstraints()).filteredOn(constraint ->
				constraint.alignmentType().equals("RegionChangeAlignment")).allSatisfy(constraint -> {
			assertThat(moveTo.preconditions()).anySatisfy(condition ->
					assertThat(constraint.initialValues()).contains(condition.value()));
			assertThat(moveTo.negativeEffects()).anySatisfy(condition ->
					assertThat(constraint.initialValues()).contains(condition.value()));
			assertThat(moveTo.positiveEffects()).anySatisfy(condition ->
					assertThat(constraint.resultingValues()).contains(condition.value()));
		});
	}

	@Test
	void pickRuleProducesTypedAlignmentSchemaFromUc1Reachability() {
		contextReasoner.loadMobipickUc1();

		ActionSchema pick = abstraction.abstractCurrentKnowledge().schemas().stream()
				.filter(schema -> schema.functionType().equals("PickGraspable"))
				.findFirst().orElseThrow();

		assertThat(pick.parameters()).anySatisfy(parameter -> {
			assertThat(parameter.type()).isEqualTo("InteractingObject");
			assertThat(parameter.roles()).contains("hasTarget", "concernsObject");
		});
		assertThat(pick.alignmentConstraints()).extracting(constraint -> constraint.alignmentType())
				.contains("RegionGuardAlignment", "QualityBindingAlignment",
						"RegionChangeAlignment", "TriplesAssertionAlignment");
		assertThat(pick.alignmentConstraints()).filteredOn(constraint ->
				constraint.alignmentType().equals("QualityBindingAlignment"))
				.singleElement().satisfies(binding -> {
					assertThat(binding.objects()).hasSize(2);
					assertThat(binding.qualities()).hasSize(2);
					assertThat(binding.values()).hasSize(1);
				});
		assertThat(pick.parameters()).anySatisfy(parameter ->
				assertThat(parameter.roles()).contains("rdfSubject"));
		assertThat(pick.alignmentConstraints()).filteredOn(constraint ->
				constraint.alignmentType().equals("TriplesAssertionAlignment"))
				.singleElement().satisfies(assertion -> {
					assertThat(assertion.association()).isEqualTo("negatesAlignment");
					assertThat(assertion.triple()).isNotNull();
					assertThat(assertion.triple().predicate()).isEqualTo("connects");
					assertThat(assertion.triple().predicateUri())
							.isEqualTo("http://pst.istc.cnr.it/ontologies/2026/metacognition#connects");
				});
		assertThat(pick.preconditions()).hasSize(2);
		assertThat(pick.positiveEffects()).hasSize(1);
		assertThat(pick.negativeEffects()).hasSize(1);
	}

	@Test
	void reachableUc1LocationSupportsNavigationAndManipulationAndCanBeEdited() {
		contextReasoner.loadMobipickUc1();
		KnowledgeGraphResponse graph = editing.currentGraph();

		RdfTriple connects = graph.triples().stream()
				.filter(triple -> triple.predicate().endsWith("#connects"))
				.filter(triple -> graph.triples().stream().anyMatch(type ->
						type.subject().equals(triple.object())
								&& type.predicate().endsWith("rdf-syntax-ns#type")
								&& type.object().value().endsWith("#InteractingObject")))
				.findFirst().orElseThrow();
		RdfTerm reachability = connects.subject();
		RdfTerm location = graph.triples().stream()
				.filter(triple -> triple.predicate().endsWith("DUL.owl#hasQuality"))
				.filter(triple -> triple.object().equals(reachability))
				.map(RdfTriple::subject).findFirst().orElseThrow();

		assertThat(graph.triples().stream()
				.filter(triple -> triple.subject().equals(location))
				.filter(triple -> triple.predicate().endsWith("rdf-syntax-ns#type"))
				.map(triple -> triple.object().value()).toList())
				.anyMatch(uri -> uri.endsWith("#NavigationLocation"));
		assertThat(graph.triples().stream()
				.filter(triple -> triple.subject().equals(location))
				.filter(triple -> triple.predicate().endsWith("rdf-syntax-ns#type"))
				.map(triple -> triple.object().value()).toList())
				.anyMatch(uri -> uri.endsWith("#ManipulationLocation"));

		RdfTriple marker = new RdfTriple(connects.object(), "urn:test:editorStatus",
				new RdfTerm("reachable", "literal", "http://www.w3.org/2001/XMLSchema#string", ""));
		RdfTriple replacement = new RdfTriple(connects.object(), "urn:test:editorStatus",
				new RdfTerm("verified", "literal", "http://www.w3.org/2001/XMLSchema#string", ""));
		try {
			editing.add(marker);
			assertThat(contextReasoner.isInferenceDirty()).isTrue();
			assertThat(editing.currentGraph().triples()).contains(marker);

			editing.update(marker, replacement);
			assertThat(editing.currentGraph().triples()).contains(replacement).doesNotContain(marker);

			editing.remove(replacement);
			assertThat(editing.currentGraph().triples()).doesNotContain(replacement);
		} finally {
			// Keep the shared Spring test model clean even if an assertion above fails.
			try {
				if (editing.currentGraph().triples().contains(marker)) editing.remove(marker);
				if (editing.currentGraph().triples().contains(replacement)) editing.remove(replacement);
			} finally {
				editing.refreshInference();
			}
		}
		assertThat(contextReasoner.isInferenceDirty()).isFalse();
	}

	@Test
	void loadingUc1AgainResetsEditedABoxInsteadOfAccumulatingIt() {
		contextReasoner.loadMobipickUc1();
		KnowledgeGraphResponse first = editing.currentGraph();
		RdfTerm subject = first.triples().stream().findFirst().orElseThrow().subject();
		RdfTriple marker = new RdfTriple(subject, "urn:test:mustDisappearOnReset",
				new RdfTerm("temporary", "literal", "http://www.w3.org/2001/XMLSchema#string", ""));
		editing.add(marker);
		assertThat(contextReasoner.isInferenceDirty()).isTrue();

		contextReasoner.loadMobipickUc1();
		KnowledgeGraphResponse reset = editing.currentGraph();

		assertThat(reset.triples()).hasSameSizeAs(first.triples());
		assertThat(reset.triples()).noneMatch(triple ->
				triple.predicate().equals("urn:test:mustDisappearOnReset"));
		assertThat(reset.resourceLabels().values()).filteredOn("table1"::equals).hasSize(1);
		assertThat(reset.inferenceDirty()).isFalse();
	}

}
