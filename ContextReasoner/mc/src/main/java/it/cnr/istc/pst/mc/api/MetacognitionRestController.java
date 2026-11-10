package it.cnr.istc.pst.mc.api;

import java.nio.charset.StandardCharsets;
import java.util.Map;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import it.cnr.istc.pst.mc.service.ContextReasonerService;
import it.cnr.istc.pst.mc.service.KnowledgeAbstractionService;
import it.cnr.istc.pst.mc.service.KnowledgeEditingService;
import it.cnr.istc.pst.mc.api.KnowledgeEditorDtos.KnowledgeGraphResponse;
import it.cnr.istc.pst.mc.api.KnowledgeEditorDtos.ReasoningStatus;
import it.cnr.istc.pst.mc.api.KnowledgeEditorDtos.TripleMutationRequest;
import it.cnr.istc.pst.mc.api.KnowledgeEditorDtos.TripleUpdateRequest;

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

    @Autowired
    private KnowledgeEditingService editingService;

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

    /** Returns the asserted, editable UC1 ABox subgraph (never the ontology TBox). */
    @GetMapping(value = { "/knowledge/abox", "/knowledge/uc1" }, produces = "application/json")
    public KnowledgeGraphResponse getEditableKnowledge() {
        return this.editingService.currentGraph();
    }

    @PostMapping(value = "/knowledge/triples", consumes = "application/json", produces = "application/json")
    public ReasoningStatus addTriple(@RequestBody TripleMutationRequest request) {
        if (request == null || request.triple() == null) throw new IllegalArgumentException("Missing RDF triple");
        this.editingService.add(request.triple());
        return new ReasoningStatus(this.service.isInferenceDirty());
    }

    @PostMapping(value = "/knowledge/triples/remove", consumes = "application/json", produces = "application/json")
    public ReasoningStatus removeTriple(@RequestBody TripleMutationRequest request) {
        if (request == null || request.triple() == null) throw new IllegalArgumentException("Missing RDF triple");
        this.editingService.remove(request.triple());
        return new ReasoningStatus(this.service.isInferenceDirty());
    }

    @PutMapping(value = "/knowledge/triples", consumes = "application/json", produces = "application/json")
    public ReasoningStatus updateTriple(@RequestBody TripleUpdateRequest request) {
        if (request == null || request.previous() == null || request.replacement() == null)
            throw new IllegalArgumentException("Missing previous or replacement RDF triple");
        this.editingService.update(request.previous(), request.replacement());
        return new ReasoningStatus(this.service.isInferenceDirty());
    }

    @PostMapping(value = "/reasoning/refresh", produces = "application/json")
    public ReasoningStatus refreshReasoning() {
        this.editingService.refreshInference();
        return new ReasoningStatus(false);
    }

    /** Returns actionable validation feedback to the browser editor and API clients. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", exception.getMessage() == null ? "Invalid request" : exception.getMessage()));
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
    @PostMapping({ "/knowledge/abox/reset", "/load/mobipick/uc1" })
    public String doLoadMobipickDescription() {
        // call the service
        this.service.loadMobipickUc1();
        return "Ok";
    }

}
