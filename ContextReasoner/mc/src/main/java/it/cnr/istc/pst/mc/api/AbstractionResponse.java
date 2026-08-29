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
            List<FluentConstraint> fluentConstraints,
            List<FluentCondition> preconditions,
            List<FluentCondition> positiveEffects,
            List<FluentCondition> negativeEffects) {
    }

    public record Parameter(
            String variable,
            String type,
            String typeUri,
            List<String> roles) {
    }

    public record FluentCondition(String object, String quality, String value) {
    }

    /** A typed diagnostic constraint, including transition endpoints when present. */
    public record FluentConstraint(
            String association,
            String fluentType,
            String fluentTypeUri,
            String object,
            String quality,
            String initialValue,
            String resultingValue) {
    }
}
