package it.cnr.istc.pst.mc.api;

import java.util.List;

/** Read-only inspection view of the inferred groundings behind one action schema. */
public record SchemaGroundingsResponse(
        AbstractionResponse.ActionSchema schema,
        List<FunctionGrounding> groundings) {

    public record FunctionGrounding(String id, String kind, List<Triple> triples) { }

    public record Triple(
            String subject,
            String predicate,
            String predicateUri,
            String object,
            String objectKind) { }
}
