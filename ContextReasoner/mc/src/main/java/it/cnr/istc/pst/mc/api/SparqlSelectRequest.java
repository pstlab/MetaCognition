package it.cnr.istc.pst.mc.api;

/**
 * 
 */
public class SparqlSelectRequest {

    private String sparql;              // a string with a SELECT query
    private Boolean raw;                // truw if the query must be executed on the raw model, otherwise it is executed on the inference model

    // set constructor visibility
    protected SparqlSelectRequest(){}

    /**
     * 
     * @return
     */
    public String getSparql() {
        return sparql;
    }

    /**
     * 
     * @param sparql
     */
    public void setSparql(String sparql) {
        this.sparql = sparql;
    }

    /**
     * 
     * @return
     */
    public Boolean getRaw() {
        return raw;
    }

    /**
     * 
     * @param raw
     */
    public void setRaw(Boolean raw) {
        this.raw = raw;
    }
}