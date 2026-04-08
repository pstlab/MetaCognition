package it.cnr.istc.pst.mc.semantics;

import java.io.FileInputStream;
import java.io.InputStream;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 */
public class SemanticModel extends KnowledgeGraph<Model> {

    private static final Logger log = LoggerFactory.getLogger(SemanticModel.class);

    /**
     * Create a model by loading the TRIFFID ontology
     * 
     * @param ontoFile
     * @param ontoVersion
     * @param ontoFormat
     */
    public SemanticModel(String ontoFile, String ontoVersion, String ontoFormat) {
        super();
   
        // get ontology file
        String file = ontoFile.replace("#VER#", ontoVersion);
        log.debug("Loading ontology file: {}", file);
        // parse model from file
        try (InputStream in = new FileInputStream(file)) {
            // create the raw knowledge graph
            this.model = ModelFactory.createDefaultModel();
            // read the model
            this.model.read(in, "TODO", ontoFormat);

        } catch (Exception e) {
            throw new RuntimeException("Failed to load OWL file: " + file, e);
        }
    }

    /**
     * Create a model by loading an OWL file serialized in the specified format. 
     * 
     * See Apache Jena documentation for a full list of supported formats: https://jena.apache.org/documentation/io/rdf-input.html
     * 
     * @param owlFilePath - path to the knowledge file serilaized in the specified format
     * @param format - serialization format of the OWL file (e.g., "RDF/XML", "TURTLE", "JSON-LD", etc.)
     */
    public SemanticModel(String owlFilePath, String format) {
        // parse model from RDF/XML file
        try (InputStream in = new FileInputStream(owlFilePath)) {
            this.model = ModelFactory.createDefaultModel();
            this.model.read(in, null, format);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load OWL file: " + owlFilePath, e);
        }
    }

    /**
     * Get the underlying Jena Model representing the TRIFFID ontology.
     * 
     * This method allows access to the raw Jena Model for advanced operations, querying, or reasoning that may not be directly supported by the SemanticModel class. Users can use this model to perform SPARQL queries, apply additional reasoning, or manipulate the ontology as needed.
     * 
     * @return
     */
    public Model getModel() {
        return model;
    }

}
