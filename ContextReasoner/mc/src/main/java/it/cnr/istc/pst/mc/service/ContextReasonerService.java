package it.cnr.istc.pst.mc.service;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import it.cnr.istc.pst.mc.MetacognitionProperties;
import it.cnr.istc.pst.mc.semantics.RuleReasoner;
import it.cnr.istc.pst.mc.semantics.SemanticModel;

/**
 * 
 */
@Service
public class ContextReasonerService {

    private static final Logger log = LoggerFactory.getLogger(ContextReasonerService.class);

    private final Dataset lock;             // for thread safety transactional access to the underlying models
    private final SemanticModel model;
    private final RuleReasoner reasoner;

    /**
     * SituationAwarenessService constructor. 
     * 
     * Initializes the semantic model and the rule reasoner.
     * 
     * @param prop
     */
    protected ContextReasonerService(MetacognitionProperties prop) {

        // create transactional lock object
        this.lock = DatasetFactory.createTxnMem();

        // initialize the semantic model
        this.model = new SemanticModel(
            prop.getOntologyPath(), 
            prop.getOntologyVersion(), 
            prop.getOntologyFormat());

        // initialize the rule reasoner with the semantic model and the rule file path
        this.reasoner = new RuleReasoner(
            this.model, 
            prop.getRulesPath());

        // print model size
        log.info("SituationAwareness service initialized");
        log.info("Raw model size: {}", this.model.size());
        log.info("Inference model size: {}", this.reasoner.size());
    }


    /**
     * 
     * @param raw - boolean true if the query must be processed on the raw model or on the inference model otherwise
     * @param sparql - the SPARQL query to execute
     */
    public ResultSet select(boolean raw, String sparql) {
        // open read-level transaction
        this.lock.begin(ReadWrite.READ);
        try {

            // log the SELECT being executed
            log.info("Executing {} SELECT query: {}", raw ? "raw" : "inferred", sparql);
            // execute the query on the correct model
            ResultSet rs = raw
                    ? model.executeSelect(sparql)           // raw model
                    : reasoner.executeSelect(sparql);       // inference model

            // detach results from the transaction before closing it
            return ResultSetFactory.copyResults(rs);

        } finally {
            // release the lock
            this.lock.end();
        }
    }
}
