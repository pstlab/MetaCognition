package it.cnr.istc.pst.mc.semantics;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFactory;
import org.apache.jena.rdf.model.Model;


/**
 * 
 */
public abstract class KnowledgeGraph<T extends Model> {

    protected T model;

    /**
     *  KnowledgeGraph constructor.
     * 
     * @param model
     */
    protected KnowledgeGraph() {}

    /**
     * 
     * @return
     */
    public T getModel() {
        return this.model;
    }

    /**
     * 
     * @return
     */
    public long size() {
        return this.model.size();
    }


    /**
     * Executes a SPARQL SELECT query on the inference model and returns the results in JSON format.
      *
     * @param sparql
     * @return
     * @throws IllegalArgumentException
     */
    public ResultSet executeSelect(String sparql) throws IllegalArgumentException {
        // preare the query
        Query query = QueryFactory.create(sparql);
        // check input query format
        if (!query.isSelectType()) {
            throw new IllegalArgumentException("Only SELECT queries are allowed on this endpoint");
        }

        // execute the query adn return result set
        try (QueryExecution qexec = QueryExecutionFactory.create(query, this.model)) {
            // get the result set
            ResultSet rs = qexec.execSelect();
            // make a detatched copy of the result
            ResultSet copy = ResultSetFactory.copyResults(rs);
            return copy;
        }
    }

    /**
     * Executes a SPARQL ASK query on the inference model and returns the result as a boolean value in JSON format.
     *
     * @param sparql
     * @return
     * @throws IllegalAccessException
     */
    public boolean executeAsk(String sparql) throws IllegalAccessException {
        // prepare SPARQL query
        Query query = QueryFactory.create(sparql);
        // check query type
        if (!query.isAskType()) {
            throw new IllegalArgumentException("Only ASK queries are allowed on this endpoint");
        }

        try (QueryExecution qexec = QueryExecutionFactory.create(query, this.model)) {
            boolean result = qexec.execAsk();
            return result;
        }
    }

    /**
     * Executes a SPARQL CONSTRUCT query on the inference model and returns the resulting RDF graph in Turtle format.
     *
     * @param sparql
     * @return
     * @throws IllegalAccessException
     */
    public Model executeConstruct(String sparql) throws IllegalAccessException {
        // prepare SPARQL query
        Query query = QueryFactory.create(sparql);
        // check query type
        if (!query.isConstructType()) {
            throw new IllegalArgumentException("Only CONSTRUCT queries are allowed on this endpoint");
        }

        // execute query
        try (QueryExecution qexec = QueryExecutionFactory.create(query, this.model)) {
            // resulting RDF graph
            Model graph = qexec.execConstruct();
            // return the created knowledge submodel
            return graph;
        }
    }

    /**
     * Executes a SPARQL DESCRIBE query on the inference model and returns the resulting RDF graph in Turtle format.
     *
     * @param sparql
     * @return
     * @throws IllegalAccessException
     */
    public Model executeDescribe(String sparql) throws IllegalArgumentException {  
        // preapre the SPARQL query
        Query query = QueryFactory.create(sparql);
        // check query type
        if (!query.isDescribeType()) {
            throw new IllegalArgumentException("Only DESCRIBE queries are allowed on this endpoint");
        }

        // execute the SPARQL query
        try (QueryExecution qexec = QueryExecutionFactory.create(query, this.model)) {
            // get the resulted knowledge graph
            Model graph = qexec.execDescribe();
            // return the resulted knowledge graph
            return graph;
        }
    }
}
