package it.cnr.istc.pst.mc;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

import it.cnr.istc.pst.mc.api.AbstractionResponse.ActionSchema;
import it.cnr.istc.pst.mc.service.ContextReasonerService;
import it.cnr.istc.pst.mc.service.KnowledgeAbstractionService;

@SpringBootTest
class McApplicationTests {

	@Autowired
	private ContextReasonerService contextReasoner;

	@Autowired
	private KnowledgeAbstractionService abstraction;

	@Test
	void contextLoads() {
	}

	@Test
	void revisedMoveToRuleProducesRegionChangeConstraint() {
		contextReasoner.loadMobipickUc1();

		ActionSchema moveTo = abstraction.abstractCurrentKnowledge().schemas().stream()
				.filter(schema -> schema.functionType().equals("MoveTo"))
				.findFirst().orElseThrow();

		assertThat(moveTo.fluentConstraints()).isNotEmpty().allSatisfy(constraint -> {
			assertThat(constraint.fluentType()).isEqualTo("RegionChangeFluent");
			assertThat(constraint.initialValue()).isNotNull();
			assertThat(constraint.resultingValue()).isNotNull();
			assertThat(constraint.initialValue()).isNotEqualTo(constraint.resultingValue());
		});
		assertThat(moveTo.preconditions()).isNotEmpty();
		assertThat(moveTo.positiveEffects()).isNotEmpty();
		assertThat(moveTo.negativeEffects()).isNotEmpty();
		assertThat(moveTo.fluentConstraints()).allSatisfy(constraint -> {
			assertThat(moveTo.preconditions()).anySatisfy(condition ->
					assertThat(condition.value()).isEqualTo(constraint.initialValue()));
			assertThat(moveTo.negativeEffects()).anySatisfy(condition ->
					assertThat(condition.value()).isEqualTo(constraint.initialValue()));
			assertThat(moveTo.positiveEffects()).anySatisfy(condition ->
					assertThat(condition.value()).isEqualTo(constraint.resultingValue()));
		});
	}

}
