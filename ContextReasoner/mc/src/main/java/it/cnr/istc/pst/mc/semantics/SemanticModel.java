package it.cnr.istc.pst.mc.semantics;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.nd4j.linalg.cpu.nativecpu.bindings.Nd4jCpu.reduce_max_bp;
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
        log.info("Loading ontology file: {}", file);
        // parse model from file
        try (InputStream in = new FileInputStream(file)) {
            // create the raw knowledge graph
            this.model = ModelFactory.createDefaultModel();

            // read the model
            this.model.read(
                in, 
                MetacognitionDictionary.NS.getUri(),
                ontoFormat);

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

     /**
     * 
     * @param classUri
     * @return
     */
    public Resource assertResourceOfType(String classUri) {

        // retrieve resource class
        Resource type = this.model.getResource(classUri);
        if (type == null) {
            throw new RuntimeException("Unknown class with URI \"" + classUri + "\". Check the ontology model.");
        }

        // create resource
        Resource res = this.model.createResource(type);
        return res;
    }

    /**
     * 
     * @param subject
     * @param propertyUri
     * @param object
     */
    public void assertProrperty(Resource subject, String propertyUri, Resource object) {
        // check if property exists
        Property prop = this.model.getProperty(propertyUri);
        // check if exists
        if (prop == null) {
            throw new RuntimeException("Property \"" + propertyUri + "\" not found. Check the ontology model.");
        }

        // add property to resource
        subject.addProperty(
            prop, 
            object);
    }

    /**
     * 
     * @param subject
     * @param propertyUri
     * @param literal
     */
    public void assertDataProperty(Resource subject, String propertyUri, String literal) {
        // check if property exists
        Property prop = this.model.getProperty(propertyUri);
        // check if exists
        if (prop == null) {
            throw new RuntimeException("Property \"" + propertyUri + "\" not found. Check the ontology model.");
        }

        // add property to resource
        subject.addLiteral(
            prop, 
            literal);
    }

    /**
     * 
     * @param subject
     * @param propertyUri
     * @param data
     */
    public void assertDataProperty(Resource subject, String propertyUri, double data) {
        // check if property exists
        Property prop = this.model.getProperty(propertyUri);
        // check if exists
        if (prop == null) {
            throw new RuntimeException("Property \"" + propertyUri + "\" not found. Check the ontology model.");
        }

        // add property to resource
        subject.addLiteral(
            prop, 
            data);
    }

    /**
     * Create the subgraph describing the structure of the mobipick embodiment.
     * 
     * The method returns the resource created into the knowledge graph
     */
    public Resource assertMobipickEmbodiment() {

        // create robot part - base
        Resource rBase = this.assertResourceOfType(
            MetacognitionDictionary.NS.getUri() + "RobotPart");
  
        // create navigation capability 
        Resource rBaseCap1 = this.assertResourceOfType(
            MetacognitionDictionary.NS.getUri() + "NavigationCapability"
        );
        
        // assert part's capabilities
        this.assertProrperty(
            rBase, 
            MetacognitionDictionary.NS_DUL.getUri() + "hasQuality", 
            rBaseCap1);
 
        this.assertDataProperty(
            rBase, 
            MetacognitionDictionary.NS_SOHO.getUri() + "hasLabel", 
            "mobipick_base");


        // create robot part - camera
        Resource rCamera = this.assertResourceOfType(
            MetacognitionDictionary.NS.getUri() + "RobotPart"
        ); 
        
        // create object detection capability
        Resource rCameraCap1 = this.assertResourceOfType(
            MetacognitionDictionary.NS.getUri() + "ObjectDetection"
        ); 

        // assert part's capabilities
        this.assertProrperty(
            rCamera  , 
            MetacognitionDictionary.NS_DUL.getUri() + "hasQuality", 
            rCameraCap1);

        this.assertDataProperty(
            rCamera, 
            MetacognitionDictionary.NS_SOHO.getUri() + "hasLabel", 
            "mobipick_camera");

        // create robot part - arm
        Resource rArm = this.assertResourceOfType(
            MetacognitionDictionary.NS.getUri() + "RobotPart"
        );
    
        // assert part relation
        this.assertProrperty(
            rArm, 
            MetacognitionDictionary.NS_DUL.getUri() + "hasPart", 
            rCamera);

        this.assertDataProperty(
            rArm, 
            MetacognitionDictionary.NS_SOHO.getUri() + "hasLabel", 
            "mobipick_arm");

        // create robot embodiment
        Resource rEmbo = this.assertResourceOfType(
            MetacognitionDictionary.NS.getUri() + "RobotEmbodiment"
        );
        
        // assert embodiment structure
        this.assertProrperty(
            rEmbo, 
            MetacognitionDictionary.NS_DUL.getUri() + "hasPart", 
            rBase);
        
        this.assertProrperty(
            rEmbo, 
            MetacognitionDictionary.NS_DUL.getUri() + "hasPart", 
            rArm);

        this.assertDataProperty(
            rEmbo, 
            MetacognitionDictionary.NS_SOHO.getUri() + "hasLabel", 
            "mobipick_embodiment");

        // create embodied agent
        Resource agent = this.assertResourceOfType(
            MetacognitionDictionary.NS.getUri() + "EmbodiedAgent"
        );
        // assert embodiment constituent
        this.assertProrperty(
            agent, 
            MetacognitionDictionary.NS_DUL.getUri() + "hasConstituent", 
            rEmbo);

        this.assertDataProperty(
            agent, 
            MetacognitionDictionary.NS_SOHO.getUri() + "hasLabel", 
            "mobipick");

        // return the resource describing the agent
        return agent;
    }


    /**
     * 
     * @param coordinates
     * @return
     */
    public Resource assertDiscretePose(double[] coordinates) {

        // check coordinates
        if (coordinates == null || coordinates.length < 3) {
            throw new RuntimeException("Missing coordinate data for mc:Pose");
        }

    
        // create pose region
        Resource poseRegion = this.assertResourceOfType(
            MetacognitionDictionary.NS.getUri() + "DiscretePoseRegion"
        );

        // assert pose data
        this.assertDataProperty(
            poseRegion, 
            MetacognitionDictionary.NS.getUri() + "hasCoordinateX", 
            coordinates[0]);

        this.assertDataProperty(
            poseRegion, 
            MetacognitionDictionary.NS.getUri() + "hasCoordinateY", 
            coordinates[1]);

        this.assertDataProperty(
            poseRegion, 
            MetacognitionDictionary.NS.getUri() + "hasCoordinateZ", 
            coordinates[2]);

        // create pose resource
        Resource pose = this.assertResourceOfType(
            MetacognitionDictionary.NS.getUri() + "Pose"
        );

        // associate pose with region
        this.assertProrperty(
            pose, 
            MetacognitionDictionary.NS_DUL.getUri() + "hasRegion", 
            poseRegion);

        // return created pose 
        return pose;
    }

    /**
     * 
     * @param coordinates
     * @return
     */
    public Resource assertDiscreteOrientation(double[] coordinates) {

        // check coordinates
        if (coordinates == null || coordinates.length < 4) {
            throw new RuntimeException("Missing coordinate data for mc:Orientation");
        }

        // create orientation region
        Resource oriRegion = this.assertResourceOfType(
            MetacognitionDictionary.NS.getUri() + "DiscreteOrientationRegion"
        );

        // assert pose data
        this.assertDataProperty(
            oriRegion, 
            MetacognitionDictionary.NS.getUri() + "hasCoordinateX", 
            coordinates[0]);

        this.assertDataProperty(
            oriRegion, 
            MetacognitionDictionary.NS.getUri() + "hasCoordinateY", 
            coordinates[1]);

        this.assertDataProperty(
            oriRegion, 
            MetacognitionDictionary.NS.getUri() + "hasCoordinateZ", 
            coordinates[2]);

        this.assertDataProperty(
            oriRegion, 
            MetacognitionDictionary.NS.getUri() + "hasCoordinateW", 
            coordinates[3]);

        // create orientation resource
        Resource ori = this.assertResourceOfType(
            MetacognitionDictionary.NS.getUri() + "Orientation"
        );

        // associate pose with region
        this.assertProrperty(
            ori, 
            MetacognitionDictionary.NS_DUL.getUri() + "hasRegion", 
            oriRegion);

        // return created pose 
        return ori;
    }

    /**
     * 
     * @param label
     * @param pData
     * @param oData
     * @return
     */
    public Resource assertStaticObject(String label, double[] pData, double[] oData) {

        // create pose
        Resource pose = this.assertDiscretePose(pData);
        // create orientation resource
        Resource ori = this.assertDiscreteOrientation(oData);


        // assert static object resource
        Resource obj = this.assertResourceOfType(
            MetacognitionDictionary.NS.getUri() + "StaticObject"
        );

        // assert the object's qualities
        this.assertProrperty(
            obj, 
            MetacognitionDictionary.NS_DUL.getUri() + "hasQuality", 
            pose);

        this.assertProrperty(
            obj, 
            MetacognitionDictionary.NS_DUL.getUri() + "hasQuality", 
            ori);

        this.assertDataProperty(
            obj, 
            MetacognitionDictionary.NS_SOHO.getUri() + "hasLabel", 
            label);

        // return object
        return obj;
    }

    /**
     * 
     * @param label
     * @param pData
     * @param oData
     * @return
     */
    public Resource assertEnvironmentLocation(String label, double[] pData, double[] oData) {

        // create pose
        Resource pose = this.assertDiscretePose(pData);
        // create orientation
        Resource orientation = this.assertDiscreteOrientation(oData);

        // create disposition
        Resource disp = this.assertResourceOfType(
            MetacognitionDictionary.NS.getUri() + "TraversabilityDisposition"
        );

        // assert environment location 
        Resource loc = this.assertResourceOfType(
            MetacognitionDictionary.NS.getUri() + "NavigationLocation"
        );

        // assert the location's qualities
        this.assertProrperty(
            loc, 
            MetacognitionDictionary.NS_DUL.getUri() + "hasQuality", 
            pose);

        this.assertProrperty(
            loc, 
            MetacognitionDictionary.NS_DUL.getUri() + "hasQuality", 
            orientation);

        this.assertProrperty(
            loc, 
            MetacognitionDictionary.NS_DUL.getUri() + "hasQuality", 
            disp);

        this.assertDataProperty(
            loc, 
            MetacognitionDictionary.NS_SOHO.getUri() + "hasLabel", 
            label);

        // return location
        return loc;
    }

}
