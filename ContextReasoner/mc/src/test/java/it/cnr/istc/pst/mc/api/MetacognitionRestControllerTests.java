package it.cnr.istc.pst.mc.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
}
