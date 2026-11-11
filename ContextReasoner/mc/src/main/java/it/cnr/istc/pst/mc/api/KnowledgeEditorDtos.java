package it.cnr.istc.pst.mc.api;

import java.util.List;
import java.util.Map;

/** Explicit JSON contracts for controlled editing of the asserted UC1 ABox. */
public final class KnowledgeEditorDtos {

    private KnowledgeEditorDtos() { }

    public record RdfTerm(String value, String kind, String datatype, String language) { }

    public record RdfTriple(RdfTerm subject, String predicate, RdfTerm object) { }

    public record KnowledgeGraphResponse(List<RdfTriple> triples, Map<String, String> resourceLabels,
            boolean inferenceDirty) { }

    public record TripleMutationRequest(RdfTriple triple) { }

    public record TripleUpdateRequest(RdfTriple previous, RdfTriple replacement) { }

    public record ReasoningStatus(boolean inferenceDirty) { }
}
