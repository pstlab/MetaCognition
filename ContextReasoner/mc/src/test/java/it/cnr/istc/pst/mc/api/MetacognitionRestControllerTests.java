package it.cnr.istc.pst.mc.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import it.cnr.istc.pst.mc.service.ContextReasonerService;
import it.cnr.istc.pst.mc.service.KnowledgeAbstractionService;

@SpringBootTest
@AutoConfigureMockMvc
class MetacognitionRestControllerTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ContextReasonerService knowledge;

    @Autowired
    private KnowledgeAbstractionService abstraction;

    @Test
    void abstractionEndpointReturnsJsonResponse() throws Exception {
        knowledge.loadMobipickUc1();

        mvc.perform(get("/metacognition/api/abstraction"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.schemas").isArray());
    }

    @Test
    void groundingInspectionEndpointReturnsJsonResponse() throws Exception {
        knowledge.loadMobipickUc1();
        String signatureId = abstraction.abstractCurrentKnowledge().schemas().stream()
                .filter(schema -> schema.functionType().equals("MoveTo"))
                .findFirst().orElseThrow().signatureId();

        mvc.perform(get("/metacognition/api/abstraction/{signatureId}/groundings", signatureId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.schema.functionType").value("MoveTo"))
                .andExpect(jsonPath("$.schema.alignmentConstraints").isArray())
                .andExpect(jsonPath("$.groundings[0].triples").isArray());
    }

    @Test
    void knowledgeEditorEndpointReturnsJsonResponse() throws Exception {
        knowledge.loadMobipickUc1();

        mvc.perform(get("/metacognition/api/knowledge/abox"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.triples").isArray())
                .andExpect(jsonPath("$.resourceLabels").isMap())
                .andExpect(jsonPath("$.inferenceDirty").value(false))
                .andExpect(jsonPath("$.triples[0].subject.kind").exists());
    }

    @Test
    void bundledABoxScenariosCanBeDiscoveredAndLoaded() throws Exception {
        mvc.perform(get("/metacognition/api/knowledge/scenarios"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.scenarios[?(@.id == 'mobipick_baseline_part_aware')]").exists());

        mvc.perform(post("/metacognition/api/knowledge/scenarios/{scenarioId}/load",
                        "mobipick_baseline_part_aware"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("mobipick_baseline_part_aware"))
                .andExpect(jsonPath("$.active").value(true));

        mvc.perform(get("/metacognition/api/knowledge/abox"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.triples").isNotEmpty())
                .andExpect(jsonPath("$.inferenceDirty").value(false));

        String pickPremises = """
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                PREFIX dul: <http://www.loa-cnr.it/ontologies/DUL.owl#>
                PREFIX soho: <http://pst.istc.cnr.it/ontologies/2019/01/soho#>
                PREFIX mc: <http://pst.istc.cnr.it/ontologies/2026/metacognition#>
                SELECT * WHERE {
                  ?a dul:hasConstituent ?e . ?e a mc:RobotEmbodiment ;
                     dul:hasQuality ?g, ?n, ?ep .
                  ?g a soho:GraspObjectCapability . ?n a mc:NavigationCapability . ?ep a mc:Pose .
                  ?l a mc:ManipulationLocation ; dul:hasQuality ?r, ?lp .
                  ?r a mc:ReachabilityDisposition ; mc:connects ?o . ?o a mc:InteractingObject ; dul:hasQuality ?op .
                  ?lp a mc:Pose ; dul:hasRegion ?lr . ?op a mc:Pose ; dul:hasRegion ?or .
                } LIMIT 1
                """;
        assertThat(knowledge.select(false, pickPremises).hasNext())
                .as("the loaded Turtle ABox must satisfy the PickGraspable rule premises").isTrue();

        mvc.perform(get("/metacognition/api/abstraction"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemas[?(@.functionType == 'MoveTo')]").exists())
                .andExpect(jsonPath("$.schemas[?(@.functionType == 'PickGraspable')]").exists());
    }
}
