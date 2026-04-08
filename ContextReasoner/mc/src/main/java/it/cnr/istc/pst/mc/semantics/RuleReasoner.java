package it.cnr.istc.pst.mc.semantics;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.reasoner.rulesys.GenericRuleReasoner;
import org.apache.jena.reasoner.rulesys.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 
 */
public class RuleReasoner extends KnowledgeGraph<InfModel> {

    private static final Logger log = LoggerFactory.getLogger(RuleReasoner.class);

    private SemanticModel base;            // attach the rule reasoner to a base semantic model
    private List<Rule> rules;

    /**
     * 
     * @param semanticModel
     * @param ruleFile
     */
    public RuleReasoner(SemanticModel semanticModel, String ruleFile) {
        super();
        // set parameters
        this.base = semanticModel;
   
        // load rules from file
        this.rules = loadRulesFromFile(ruleFile);

        // create the reaonser
        GenericRuleReasoner reasoner = new GenericRuleReasoner(this.rules);
        // attach the resoner to the model
        reasoner.setMode(GenericRuleReasoner.HYBRID);
        reasoner.setDerivationLogging(true);
        reasoner.setOWLTranslation(false);
        reasoner.setTransitiveClosureCaching(true);

        // create the inference model by applying the reasoner to the semantic model
        this.model = ModelFactory.createInfModel(reasoner, this.base.getModel());
    }

    /**
     * 
     * @param file
     * @return
     */
    private static List<Rule> loadRulesFromFile(String file) {

        try {

            log.info("Loading rule file {}", file);
            // check rule file path
            Path path = Path.of(file);
            // check if file exists
            if (!Files.exists(path)) {
                return Collections.emptyList();
            }

            // read the file 
            String content = Files.readString(path, StandardCharsets.UTF_8);
            // check if there exists any content inside the file
            if (content == null || content.trim().isEmpty()) {
                return Collections.emptyList();
            }

            BufferedReader br = new BufferedReader(new StringReader(content));
            Rule.Parser parser = Rule.rulesParserFromReader(br);
            return Rule.parseRules(parser);

            //return Rule.parseRules(ruleFilePath);

        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load rules from " + file, ex);
        }
    }

    /**
     * Update inference according to the current state of the underlying model
     */
    public void refreshInferenceModel() {
        // simply rebind the inference model to the underlying knowledge graph
        this.model.rebind();
    }

    /**
     * 
     */
    @Override
    public ResultSet executeSelect(String sparql) throws IllegalArgumentException {
        // first be sure to rebind the inference model
        this.refreshInferenceModel();
        return super.executeSelect(sparql);
    }
}
