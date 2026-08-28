package it.cnr.istc.pst.mc.api;

import java.nio.charset.StandardCharsets;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import it.cnr.istc.pst.mc.service.ContextReasonerService;
import it.cnr.istc.pst.mc.service.KnowledgeAbstractionService;

import java.io.ByteArrayOutputStream;

/**
 * 
 */
@RestController
@RequestMapping("/metacognition/api")
public class MetacognitionRestController {

    private static final Logger log = LoggerFactory.getLogger(MetacognitionRestController.class);

    @Autowired
    private ContextReasonerService service; 

    @Autowired
    private KnowledgeAbstractionService abstractionService;

    /** Returns contextual action schemas abstracted from current inferred meta-knowledge. */
    @GetMapping(value = "/abstraction", produces = "application/json")
    public AbstractionResponse getAbstraction() {
        return this.abstractionService.abstractCurrentKnowledge();
    }

    /** Returns inferred planning-relevant subgraphs contributing to one schema. */
    @GetMapping(value = "/abstraction/{signatureId}/groundings", produces = "application/json")
    public SchemaGroundingsResponse getSchemaGroundings(@PathVariable String signatureId) {
        return this.abstractionService.inspectCurrentSchema(signatureId);
    }


    /**
     * 
     * @param request
     * @return
     */
    @PostMapping(
        value = "/sparql/select", 
        consumes = "application/json", 
        produces = "application/json")
    public String doQuerySelectPost(@RequestBody SparqlSelectRequest request) {
        // check requets body
        if (request == null || request.getSparql() == null || request.getSparql().isBlank()) {
            throw new IllegalArgumentException("Missing SPARQL query");
        }       
        
    

        Query query = QueryFactory.create(request.getSparql());
        if (!query.isSelectType()) {
            throw new IllegalArgumentException("Only SELECT queries are allowed on this endpoint");
        }

        log.debug("Processing SPARQL query {}", request.getSparql());

        // check raw flag - default value is false, so the query is run on the inference model by default
        boolean raw = request.getRaw() != null && request.getRaw();
        // run SELECT query
        ResultSet res = this.service.select(raw, request.getSparql());
        // convert result set to JSON
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // format result as JSON
        ResultSetFormatter.outputAsJSON(out, res);
        return out.toString(StandardCharsets.UTF_8);
    }

    /**
     * 
     * @return
     */
    @PostMapping("/load/mobipick/uc1")
    public String doLoadMobipickDescription() {
        // call the service
        this.service.loadMobipickUc1();
        return "Ok";
    }

}
