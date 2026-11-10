package it.cnr.istc.pst.mc.api;

import java.util.List;

/** JSON representation of contextual action schemas derived from inferred functions. */
public record AbstractionResponse(List<ActionSchema> schemas) {

    public record ActionSchema(
            String functionType,
            String functionTypeUri,
            String signatureId,
            int groundingCount,
            List<Parameter> parameters,
            List<AlignmentConstraint> alignmentConstraints,
            List<StateCondition> preconditions,
            List<StateCondition> positiveEffects,
            List<StateCondition> negativeEffects) {
    }

    public record Parameter(
            String variable,
            String type,
            String typeUri,
            List<String> roles) {
    }

    public record StateCondition(String object, String quality, String value) {
    }

    /** A typed belief alignment. Lists preserve multi-participant bindings without inventing pairs. */
    public record AlignmentConstraint(
            String association,
            String alignmentType,
            String alignmentTypeUri,
            List<String> objects,
            List<String> qualities,
            List<String> values,
            List<String> initialValues,
            List<String> resultingValues) {
    }
}
