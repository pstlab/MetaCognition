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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.cnr.istc.pst.mc.service.ContextReasonerService;

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

        // prepare SPARQL query
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

}
