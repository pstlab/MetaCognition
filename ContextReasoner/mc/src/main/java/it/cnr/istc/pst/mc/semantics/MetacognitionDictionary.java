package it.cnr.istc.pst.mc.semantics;

/**
 * 
 */
public enum MetacognitionDictionary {

    // name spaces
    NS("http://pst.istc.cnr.it/ontologies/2026/metacognition#"),
    NS_RDF("http://www.w3.org/1999/02/22-rdf-syntax-ns#"),
    NS_RDFS("http://www.w3.org/2000/01/rdf-schema#"),
    NS_OWL("http://www.w3.org/2002/07/owl#"),
    NS_XSD("http://www.w3.org/2001/XMLSchema#"),
    NS_DUL("http://www.loa-cnr.it/ontologies/DUL.owl#"),
    NS_SOHO("http://pst.istc.cnr.it/ontologies/2019/01/soho#");

    private String uri;

    MetacognitionDictionary(String uri) {
        this.uri = uri;
    }

    public String getUri() {
        return uri;
    }

    @Override
    public String toString() {
        return this.uri;
    }

}
