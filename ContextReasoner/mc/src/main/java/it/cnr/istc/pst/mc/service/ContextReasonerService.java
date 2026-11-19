package it.cnr.istc.pst.mc.service;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.function.Function;

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
    private final Model baseModel;
    private final RuleReasoner reasoner;
    private volatile boolean inferenceDirty;
    private volatile String activeScenario;

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

        // Immutable reset point: ontology/TBox and any knowledge shipped in the
        // ontology file, before the programmatic UC1 ABox is asserted.
        this.baseModel = ModelFactory.createDefaultModel().add(this.model.getModel());
        this.baseModel.setNsPrefixes(this.model.getModel().getNsPrefixMap());

        // initialize the rule reasoner with the semantic model and the rule file path
        this.reasoner = new RuleReasoner(
            this.model, 
            prop.getRulesPath());

        // print model size
        log.info("Metacognition service initialized");
        log.info("Raw model size: {}", this.model.size());
        log.info("Inference model size: {}", this.reasoner.size());
        this.inferenceDirty = false;
        this.activeScenario = "ontology-only";
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
            if (!raw) this.inferenceDirty = false;

            // detach results from the transaction before closing it
            return ResultSetFactory.copyResults(rs);

        } finally {
            // release the lock
            this.lock.end();
        }
    }

    /**
     * Runs a read-only operation against the current materialized inference model.
     * The model remains protected by the service read lock for the complete
     * operation; callers must not retain or mutate it. ABox editor changes become
     * visible here after the explicit inference refresh operation.
     */
    public <T> T readInferredModel(Function<Model, T> reader) {
        this.lock.begin(ReadWrite.READ);
        try {
            return reader.apply(this.reasoner.getModel());
        } finally {
            this.lock.end();
        }
    }

    /** Runs a read-only operation against the asserted ontology/ABox model. */
    public <T> T readRawModel(Function<Model, T> reader) {
        this.lock.begin(ReadWrite.READ);
        try {
            return reader.apply(this.model.getModel());
        } finally {
            this.lock.end();
        }
    }

    /** Mutates asserted ABox knowledge transactionally without silently refreshing inference. */
    public <T> T writeRawModel(Function<Model, T> writer) {
        this.lock.begin(ReadWrite.WRITE);
        try {
            T result = writer.apply(this.model.getModel());
            this.lock.commit();
            this.inferenceDirty = true;
            return result;
        } finally {
            this.lock.end();
        }
    }

    /** Explicitly re-materializes inference after one or more ABox edits. */
    public void refreshInference() {
        this.lock.begin(ReadWrite.WRITE);
        try {
            this.reasoner.refreshInferenceModel();
            this.inferenceDirty = false;
            this.lock.commit();
        } finally {
            this.lock.end();
        }
    }

    public boolean isInferenceDirty() {
        return this.inferenceDirty;
    }

    public String getActiveScenario() {
        return this.activeScenario;
    }

    /** Replaces the asserted scenario with a parsed ABox while retaining the TBox. */
    public void loadABoxScenario(String scenarioId, Model abox) {
        if (scenarioId == null || scenarioId.isBlank() || abox == null) {
            throw new IllegalArgumentException("A scenario identifier and parsed ABox are required");
        }
        this.lock.begin(ReadWrite.WRITE);
        try {
            this.model.getModel().removeAll();
            this.model.getModel().add(this.baseModel);
            this.model.getModel().add(abox);
            this.model.getModel().setNsPrefixes(this.baseModel.getNsPrefixMap());
            this.model.getModel().setNsPrefixes(abox.getNsPrefixMap());
            this.reasoner.refreshInferenceModel();
            this.inferenceDirty = false;
            this.activeScenario = scenarioId;
            this.lock.commit();
        } catch (RuntimeException exception) {
            this.model.getModel().removeAll();
            this.model.getModel().add(this.baseModel);
            this.reasoner.refreshInferenceModel();
            this.inferenceDirty = false;
            this.activeScenario = "ontology-only";
            this.lock.abort();
            throw exception;
        } finally {
            this.lock.end();
        }
    }


    /** Restores the original ontology model and recreates the Mobipick UC1 ABox. */
    public void loadMobipickUc1() {

        // open write-level transaction
        this.lock.begin(ReadWrite.WRITE);
        try {

            // Reset first so repeated calls never accumulate scenario blank nodes
            // or retain edits made through the ABox editor.
            this.model.getModel().removeAll();
            this.model.getModel().add(this.baseModel);
            this.model.getModel().setNsPrefixes(this.baseModel.getNsPrefixMap());

            // assert stati objects of the environment
            log.info("Asssert static objects of the environment");            
            // assert static object
            Resource sObj = this.model.assertStaticObject(
                "table1", 
                new double[] {
                    18.300,
                    15,18,
                    0.0
                }, 
                new double[] {
                    0.0,
                    0.0,
                    -0.707,
                    0.707
                });

            log.debug("Asserted static object {}", sObj.getURI());

            sObj = this.model.assertStaticObject(
                "table2", 
                new double[] {
                    19.565,
                    13.76,
                    0.0
                }, 
                new double[] {
                    0.0,
                    0.0,
                    0.0,
                    1.0
                });

            log.debug("Asserted static object {}", sObj.getURI());
            
            sObj = this.model.assertStaticObject(
                "table3", 
                new double[] {
                    21.165,
                    13.84,
                    0.0
                }, 
                new double[] {
                    0.0,
                    0.0,
                    0.0,
                    1.0
                });

            log.debug("Asserted static object {}", sObj.getURI());

            sObj = this.model.assertStaticObject(
                "table4", 
                new double[] {
                    22.700,
                    15.05,
                    0.0
                }, 
                new double[] {
                    0.0,
                    0.0,
                    0.0,
                    1.0
                });

            log.debug("Asserted static object {}", sObj.getURI());

            // assert environment locations of the environment
            log.info("Asssert environment locations");
            // assert location
            Resource homeLocation = this.model.assertEnvironmentLocation(
                "base_home", 
                new double[] {
                    20.50,
                    15.20,
                    0.0
                },
                new double[] {
                    0.0,
                    0.0,
                    1.0,
                    0.0
                });

            log.debug("Asserted environment location {}", homeLocation.getURI());
            
            Resource manipulationLocation = this.model.assertEnvironmentLocation(
                "base_table1", 
                new double[] {
                    19.23,
                    15.40,
                    0.0
                },
                new double[] {
                    0.0,
                    0.0,
                    -0.707,
                    0.707
                });

            log.debug("Asserted environment location {}", manipulationLocation.getURI());

            Resource table2Location = this.model.assertEnvironmentLocation(
                "base_table2", 
                new double[] {
                    19.39,
                    14.66,
                    0.0
                },
                new double[] {
                    0.0,
                    0.0,
                    0.0,
                    1.0
                });

            log.debug("Asserted environment location {}", table2Location.getURI());

            Resource graspable = this.model.assertInteractingObject(
                "milk_box",
                new double[] { 18.30, 15.18, 0.80 },
                new double[] { 0.0, 0.0, 0.0, 1.0 });
                
            Resource reachability = this.model.assertReachability(manipulationLocation, graspable);
            log.debug("Asserted reachability {} from location {} to object {}",
                reachability.getURI(), manipulationLocation.getURI(), graspable.getURI());

            Resource table3Location = this.model.assertEnvironmentLocation(
                "base_table3", 
                new double[] {
                    21.01,
                    14.77,
                    0.0                    
                }, 
                new double[] {
                    0.0,
                    0.0,
                    0.0,
                    1.0
                });

            log.debug("Asserted environment location {}", table3Location.getURI());

            Resource table4Location = this.model.assertEnvironmentLocation(
                "base_table4", 
                new double[] {
                    21.80,
                    14.90,
                    0.0
                }, 
                new double[] {
                    0.0,
                    0.0,
                    0.707,
                    0.707
                });

            log.debug("Asserted environment location {}", table4Location.getURI());

            // The UC1 navigation graph is directed. Each location owns one
            // ReachabilityDisposition whose connects edges identify its possible
            // destinations. This is the ABox evidence consumed by MoveTo rule2.
            Resource[] navigationLocations = {
                homeLocation, manipulationLocation, table2Location, table3Location, table4Location
            };
            for (Resource source : navigationLocations) {
                for (Resource destination : navigationLocations) {
                    if (!source.equals(destination)) {
                        this.model.assertNavigationConnection(source, destination);
                    }
                }
            }

            // create robot description 
            log.info("Load Mobipick description into the Knowledge Graph");
            // assert mobipick agent
            Resource agent = this.model.assertMobipickEmbodiment();

            
            log.debug("URI of the resource associated with the mobipick agent {}", agent.getURI());
                
            // Materialize inference from the newly recreated ABox.
            this.reasoner.refreshInferenceModel();
            this.inferenceDirty = false;
            this.activeScenario = "programmatic-uc1";
            this.lock.commit();

        } catch (RuntimeException exception) {
            // The Dataset is used as a lock and does not transactionally own the
            // Jena Model, so restore the known-good reset point explicitly.
            this.model.getModel().removeAll();
            this.model.getModel().add(this.baseModel);
            this.reasoner.refreshInferenceModel();
            this.inferenceDirty = false;
            this.lock.abort();
            throw exception;
        } finally {
            this.lock.end();
        }
    }
}
