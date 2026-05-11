package it.cnr.istc.pst.mc;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 
 */
@Component
@ConfigurationProperties(prefix = "mc")
public class MetacognitionProperties {

    private String ontologyPath;
    private String ontologyVersion;
    private String ontologyFormat;
    private String rulesPath;

    public String getOntologyFormat() {
        return ontologyFormat.trim();
    }

    public String getOntologyPath() {
        return ontologyPath;
    }
    
    public String getOntologyVersion() {
        return ontologyVersion.trim();
    }

    public String getRulesPath() {
        return rulesPath.trim();
    }

    public void setOntologyFormat(String ontologyFormat) {
        this.ontologyFormat = ontologyFormat;
    }

    public void setOntologyPath(String ontologyPath) {
        this.ontologyPath = ontologyPath;
    }

    public void setOntologyVersion(String ontologyVersion) {
        this.ontologyVersion = ontologyVersion;
    }

    public void setRulesPath(String rulesPath) {
        this.rulesPath = rulesPath;
    }

}

