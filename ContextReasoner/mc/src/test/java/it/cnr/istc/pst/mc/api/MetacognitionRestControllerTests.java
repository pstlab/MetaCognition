package it.cnr.istc.pst.mc.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import it.cnr.istc.pst.mc.service.KnowledgeAbstractionService;
import it.cnr.istc.pst.mc.api.SchemaGroundingsResponse.FunctionGrounding;

class MetacognitionRestControllerTests {

    @Test
    void abstractionEndpointReturnsJsonResponse() throws Exception {
        KnowledgeAbstractionService abstraction = org.mockito.Mockito.mock(KnowledgeAbstractionService.class);
        when(abstraction.abstractCurrentKnowledge()).thenReturn(new AbstractionResponse(List.of()));
        MetacognitionRestController controller = new MetacognitionRestController();
        ReflectionTestUtils.setField(controller, "abstractionService", abstraction);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(get("/metacognition/api/abstraction"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.schemas").isArray());
    }

    @Test
    void groundingInspectionEndpointReturnsJsonResponse() throws Exception {
        KnowledgeAbstractionService abstraction = org.mockito.Mockito.mock(KnowledgeAbstractionService.class);
        AbstractionResponse.ActionSchema schema = new AbstractionResponse.ActionSchema(
                "MoveTo", "urn:type:MoveTo", "sha256:abc", 1, List.of(), List.of(), List.of(), List.of());
        when(abstraction.inspectCurrentSchema("sha256:abc"))
                .thenReturn(new SchemaGroundingsResponse(schema,
                        List.of(new FunctionGrounding("_:function", "blankNode", List.of()))));
        MetacognitionRestController controller = new MetacognitionRestController();
        ReflectionTestUtils.setField(controller, "abstractionService", abstraction);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(get("/metacognition/api/abstraction/sha256:abc/groundings"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.schema.functionType").value("MoveTo"))
                .andExpect(jsonPath("$.groundings[0].triples").isArray());
    }
}
