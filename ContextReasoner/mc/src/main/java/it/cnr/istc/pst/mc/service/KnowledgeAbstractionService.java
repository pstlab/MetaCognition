package it.cnr.istc.pst.mc.service;

import static it.cnr.istc.pst.mc.semantics.MetacognitionDictionary.NS;
import static it.cnr.istc.pst.mc.semantics.MetacognitionDictionary.NS_DUL;
import static it.cnr.istc.pst.mc.semantics.MetacognitionDictionary.NS_OWL;
import static it.cnr.istc.pst.mc.semantics.MetacognitionDictionary.NS_RDFS;
import static it.cnr.istc.pst.mc.semantics.MetacognitionDictionary.NS_SOHO;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.springframework.stereotype.Service;

import it.cnr.istc.pst.mc.api.AbstractionResponse;
import it.cnr.istc.pst.mc.api.AbstractionResponse.ActionSchema;
import it.cnr.istc.pst.mc.api.AbstractionResponse.FluentCondition;
import it.cnr.istc.pst.mc.api.AbstractionResponse.FluentConstraint;
import it.cnr.istc.pst.mc.api.AbstractionResponse.Parameter;
import it.cnr.istc.pst.mc.api.SchemaGroundingsResponse;
import it.cnr.istc.pst.mc.api.SchemaGroundingsResponse.FunctionGrounding;
import it.cnr.istc.pst.mc.api.SchemaGroundingsResponse.Triple;

/**
 * Read-only abstraction of inferred Function meta-knowledge. Grounded Function
 * individuals are grouped into contextual action schemas by their typed roles and
 * Fluent patterns. A Region change keeps its initial value as a precondition and
 * delete effect, and its resulting value as an add effect; no inferred planning
 * effect is asserted as factual knowledge.
 */
@Service
public class KnowledgeAbstractionService {

    private static final String CAN_PERFORM = NS_SOHO + "canBePerformedBy";
    private static final String TARGET = NS_SOHO + "hasTarget";
    private static final String PRECONDITION_ON = NS + "hasPreconditionOn";
    private static final String EFFECT_ON = NS_SOHO + "hasEffectOn";
    private static final String REQUIRES = NS + "requiresFluent";
    private static final String ASSERTS = NS + "assertsFluent";
    private static final String NEGATES = NS + "negatesFluent";
    private static final String CONCERNS_OBJECT = NS + "concernsObject";
    private static final String CONCERNS_QUALITY = NS + "concernsQuality";
    private static final String FLUENT_VALUE = NS + "hasFluentValue";
    private static final String INITIAL_VALUE = NS + "hasInitialValue";
    private static final String RESULTING_VALUE = NS + "hasResultingValue";

    private static final List<Role> FUNCTION_ROLES = List.of(
            new Role("canBePerformedBy", CAN_PERFORM),
            new Role("hasTarget", TARGET),
            new Role("hasPreconditionOn", PRECONDITION_ON),
            new Role("hasEffectOn", EFFECT_ON));
    private static final List<FluentRole> FLUENT_ROLES = List.of(
            new FluentRole("requiresFluent", REQUIRES),
            new FluentRole("assertsFluent", ASSERTS),
            new FluentRole("negatesFluent", NEGATES));
    private static final Set<String> GENERIC_TYPES = Set.of(
            NS_OWL + "Thing", NS_RDFS + "Resource", NS_RDFS + "Class",
            NS_DUL + "Entity", NS_DUL + "Object", NS_SOHO + "Function", NS + "Fluent");
    private static final Set<String> UNIVERSAL_TYPES = Set.of(
            NS_OWL + "Thing", NS_RDFS + "Resource", NS_RDFS + "Class", NS_DUL + "Entity");

    private final ContextReasonerService knowledge;

    public KnowledgeAbstractionService(ContextReasonerService knowledge) {
        this.knowledge = knowledge;
    }

    public AbstractionResponse abstractCurrentKnowledge() {
        return knowledge.readInferredModel(this::abstractModel);
    }

    public SchemaGroundingsResponse inspectCurrentSchema(String signatureId) {
        return knowledge.readInferredModel(model -> inspectSchema(model, signatureId));
    }

    /** Package-visible entry point for deterministic graph-level tests. */
    AbstractionResponse abstractModel(Model model) {
        List<Resource> functions = findFunctions(model);
        Map<String, Aggregate> grouped = new HashMap<>();
        for (Resource function : functions) {
            Grounding grounding = inspect(model, function);
            if (grounding == null) {
                continue;
            }
            grouped.computeIfAbsent(grounding.canonical(), ignored -> new Aggregate(grounding)).add(function);
        }

        List<ActionSchema> schemas = grouped.values().stream()
                .map(Aggregate::toSchema)
                .sorted(Comparator.comparing(ActionSchema::functionTypeUri)
                        .thenComparing(ActionSchema::signatureId))
                .toList();
        return new AbstractionResponse(schemas);
    }

    /** Package-visible entry point for graph-level inspection tests. */
    SchemaGroundingsResponse inspectSchema(Model model, String signatureId) {
        Map<String, Aggregate> grouped = new HashMap<>();
        for (Resource function : findFunctions(model)) {
            Grounding grounding = inspect(model, function);
            if (grounding != null) {
                grouped.computeIfAbsent(grounding.canonical(), ignored -> new Aggregate(grounding)).add(function);
            }
        }
        Aggregate match = grouped.values().stream()
                .filter(aggregate -> signatureId.equals(aggregate.toSchema().signatureId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown abstraction signature: " + signatureId));
        List<FunctionGrounding> groundings = match.functions.stream()
                .sorted(Comparator.comparing(this::resourceKey))
                .map(function -> inspectFunctionSubgraph(model, function))
                .toList();
        return new SchemaGroundingsResponse(match.toSchema(), groundings);
    }

    private FunctionGrounding inspectFunctionSubgraph(Model model, Resource function) {
        Set<org.apache.jena.rdf.model.Statement> statements = new HashSet<>();
        Set<Resource> participants = new HashSet<>();
        for (Role role : FUNCTION_ROLES) {
            collectStatements(model, function, role.uri(), statements, participants);
        }
        for (FluentRole role : FLUENT_ROLES) {
            Property property = model.createProperty(role.uri());
            for (org.apache.jena.rdf.model.Statement statement : model.listStatements(function, property, (RDFNode) null).toList()) {
                statements.add(statement);
                if (!statement.getObject().isResource()) continue;
                Resource fluent = statement.getResource();
                participants.add(fluent);
                collectStatements(model, fluent, CONCERNS_OBJECT, statements, participants);
                collectStatements(model, fluent, CONCERNS_QUALITY, statements, participants);
                boolean specializedValues = model.contains(fluent, model.createProperty(INITIAL_VALUE))
                        || model.contains(fluent, model.createProperty(RESULTING_VALUE));
                collectStatements(model, fluent, INITIAL_VALUE, statements, participants);
                collectStatements(model, fluent, RESULTING_VALUE, statements, participants);
                if (!specializedValues) {
                    collectStatements(model, fluent, FLUENT_VALUE, statements, participants);
                }
            }
        }
        participants.add(function);
        for (Resource participant : participants) {
            TypeRef type = selectMostSpecificType(model, participant, UNIVERSAL_TYPES);
            if (type != null) {
                statements.add(model.createStatement(participant, RDF.type, model.createResource(type.uri())));
            }
        }
        List<Triple> triples = statements.stream().map(this::toTriple)
                .sorted(Comparator.comparing(Triple::subject).thenComparing(Triple::predicateUri)
                        .thenComparing(Triple::object))
                .toList();
        return new FunctionGrounding(displayId(function), function.isAnon() ? "blankNode" : "uri", triples);
    }

    private void collectStatements(Model model, Resource subject, String propertyUri,
            Set<org.apache.jena.rdf.model.Statement> statements, Set<Resource> participants) {
        for (org.apache.jena.rdf.model.Statement statement : model.listStatements(
                subject, model.createProperty(propertyUri), (RDFNode) null).toList()) {
            statements.add(statement);
            if (statement.getObject().isResource()) participants.add(statement.getResource());
        }
    }

    private Triple toTriple(org.apache.jena.rdf.model.Statement statement) {
        RDFNode object = statement.getObject();
        String objectKind = object.isLiteral() ? "literal" : object.asResource().isAnon() ? "blankNode" : "uri";
        String objectValue = object.isLiteral() ? object.asLiteral().getLexicalForm() : displayId(object.asResource());
        return new Triple(displayId(statement.getSubject()), localName(statement.getPredicate().getURI()),
                statement.getPredicate().getURI(), objectValue, objectKind);
    }

    private String displayId(Resource resource) {
        return resource.isURIResource() ? resource.getURI() : "_:" + resource.getId().getLabelString();
    }

    private List<Resource> findFunctions(Model model) {
        Set<Resource> found = new HashSet<>();
        for (Role role : FUNCTION_ROLES) {
            model.listSubjectsWithProperty(model.createProperty(role.uri())).forEachRemaining(found::add);
        }
        for (FluentRole role : FLUENT_ROLES) {
            model.listSubjectsWithProperty(model.createProperty(role.uri())).forEachRemaining(found::add);
        }
        return found.stream().sorted(Comparator.comparing(this::resourceKey)).toList();
    }

    private Grounding inspect(Model model, Resource function) {
        TypeRef functionType = selectType(model, function);
        if (functionType == null) {
            return null;
        }

        Map<Resource, Set<String>> resourceRoles = new HashMap<>();
        for (Role role : FUNCTION_ROLES) {
            objects(model, function, role.uri()).forEach(resource ->
                    resourceRoles.computeIfAbsent(resource, ignored -> new TreeSet<>()).add(role.name()));
        }

        List<GroundFluent> fluents = new ArrayList<>();
        for (FluentRole fluentRole : FLUENT_ROLES) {
            for (Resource fluent : objects(model, function, fluentRole.uri())) {
                TypeRef fluentType = selectMostSpecificType(model, fluent, UNIVERSAL_TYPES);
                List<Resource> objects = objects(model, fluent, CONCERNS_OBJECT);
                List<Resource> qualities = objects(model, fluent, CONCERNS_QUALITY);
                List<Resource> initialValues = objects(model, fluent, INITIAL_VALUE);
                List<Resource> resultingValues = objects(model, fluent, RESULTING_VALUE);
                List<Resource> legacyValues = initialValues.isEmpty() && resultingValues.isEmpty()
                        ? objects(model, fluent, FLUENT_VALUE) : List.of();
                for (Resource object : objects) {
                    resourceRoles.computeIfAbsent(object, ignored -> new TreeSet<>()).add("concernsObject");
                    for (Resource quality : qualities) {
                        resourceRoles.computeIfAbsent(quality, ignored -> new TreeSet<>()).add("concernsQuality");
                        if (!initialValues.isEmpty() || !resultingValues.isEmpty()) {
                            List<Resource> initials = initialValues.isEmpty() ? java.util.Collections.singletonList(null) : initialValues;
                            List<Resource> results = resultingValues.isEmpty() ? java.util.Collections.singletonList(null) : resultingValues;
                            for (Resource initial : initials) {
                                if (initial != null) resourceRoles.computeIfAbsent(initial, ignored -> new TreeSet<>()).add("hasInitialValue");
                                for (Resource result : results) {
                                    if (result != null) resourceRoles.computeIfAbsent(result, ignored -> new TreeSet<>()).add("hasResultingValue");
                                    fluents.add(new GroundFluent(fluentRole.name(), fluentType, object, quality, initial, result, null));
                                }
                            }
                        } else {
                            for (Resource value : legacyValues) {
                                resourceRoles.computeIfAbsent(value, ignored -> new TreeSet<>()).add("hasFluentValue");
                                fluents.add(new GroundFluent(fluentRole.name(), fluentType, object, quality, null, null, value));
                            }
                        }
                    }
                }
            }
        }

        Map<Resource, TypeRef> types = new HashMap<>();
        resourceRoles.keySet().forEach(resource -> types.put(resource, selectType(model, resource)));
        if (types.values().stream().anyMatch(type -> type == null)) {
            return null; // an untyped participant cannot form a typed relational signature
        }

        List<Resource> ordered = resourceRoles.keySet().stream()
                .sorted(Comparator.comparing((Resource resource) -> nodeFingerprint(resource, resourceRoles, types, fluents))
                        .thenComparing(this::resourceKey))
                .toList();
        Map<Resource, String> variables = assignVariables(ordered, resourceRoles, types);

        List<Parameter> parameters = ordered.stream()
                .map(resource -> new Parameter(variables.get(resource), types.get(resource).name(),
                        types.get(resource).uri(), List.copyOf(resourceRoles.get(resource))))
                .sorted(Comparator.comparing(Parameter::variable))
                .toList();
        List<FluentConstraint> constraints = constraints(fluents, variables);
        List<FluentCondition> preconditions = preconditions(fluents, variables);
        List<FluentCondition> positive = positiveEffects(fluents, variables);
        List<FluentCondition> negative = negativeEffects(fluents, variables);

        String canonical = canonical(functionType, parameters, constraints, preconditions, positive, negative);
        return new Grounding(functionType, parameters, constraints, preconditions, positive, negative, canonical);
    }

    private String nodeFingerprint(Resource resource, Map<Resource, Set<String>> roles,
            Map<Resource, TypeRef> types, List<GroundFluent> fluents) {
        List<String> incident = new ArrayList<>();
        for (GroundFluent fluent : fluents) {
            String shape = fluentShape(fluent, types);
            if (resource.equals(fluent.object())) incident.add(fluent.role() + ":object:" + shape);
            if (resource.equals(fluent.quality())) incident.add(fluent.role() + ":quality:" + shape);
            if (resource.equals(fluent.initialValue())) incident.add(fluent.role() + ":initial:" + shape);
            if (resource.equals(fluent.resultingValue())) incident.add(fluent.role() + ":resulting:" + shape);
            if (resource.equals(fluent.legacyValue())) incident.add(fluent.role() + ":value:" + shape);
        }
        incident.sort(String::compareTo);
        return types.get(resource).uri() + "|" + String.join(",", roles.get(resource)) + "|" + String.join(",", incident);
    }

    private String fluentShape(GroundFluent fluent, Map<Resource, TypeRef> types) {
        return typeUri(fluent.object(), types) + ":" + typeUri(fluent.quality(), types) + ":"
                + typeUri(fluent.initialValue(), types) + ":" + typeUri(fluent.resultingValue(), types) + ":"
                + typeUri(fluent.legacyValue(), types);
    }

    private String typeUri(Resource resource, Map<Resource, TypeRef> types) {
        return resource == null ? "-" : types.get(resource).uri();
    }

    private Map<Resource, String> assignVariables(List<Resource> resources, Map<Resource, Set<String>> roles,
            Map<Resource, TypeRef> types) {
        Map<Resource, String> result = new LinkedHashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        for (Resource resource : resources) {
            String base = variableBase(roles.get(resource), types.get(resource).name());
            int index = counts.merge(base, 1, Integer::sum);
            result.put(resource, "?" + base + (index == 1 ? "" : index));
        }
        return result;
    }

    private String variableBase(Set<String> roles, String type) {
        if (roles.contains("canBePerformedBy")) return "agent";
        if (roles.contains("hasTarget")) return "target";
        if (roles.contains("hasPreconditionOn") || roles.contains("hasEffectOn") || roles.contains("concernsQuality")) return decap(type, "quality");
        if (roles.contains("hasInitialValue")) return "initial" + capitalize(type, "Value");
        if (roles.contains("hasResultingValue")) return "resulting" + capitalize(type, "Value");
        if (roles.contains("hasFluentValue")) return decap(type, "value");
        return decap(type, "parameter");
    }

    private String decap(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String cleaned = value.replaceAll("[^A-Za-z0-9_]", "");
        if (cleaned.isEmpty()) return fallback;
        return Character.toLowerCase(cleaned.charAt(0)) + cleaned.substring(1);
    }

    private String capitalize(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String cleaned = value.replaceAll("[^A-Za-z0-9_]", "");
        if (cleaned.isEmpty()) return fallback;
        return Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
    }

    private List<FluentConstraint> constraints(List<GroundFluent> fluents, Map<Resource, String> variables) {
        return fluents.stream().map(fluent -> new FluentConstraint(fluent.role(),
                        fluent.type() == null ? "Fluent" : fluent.type().name(),
                        fluent.type() == null ? NS + "Fluent" : fluent.type().uri(),
                        variables.get(fluent.object()), variables.get(fluent.quality()),
                        variables.get(fluent.initialValue() != null ? fluent.initialValue()
                                : fluent.role().equals("requiresFluent") ? fluent.legacyValue() : null),
                        variables.get(fluent.resultingValue() != null ? fluent.resultingValue()
                                : fluent.role().equals("requiresFluent") ? null : fluent.legacyValue())))
                .distinct()
                .sorted(Comparator.comparing(FluentConstraint::association)
                        .thenComparing(FluentConstraint::object).thenComparing(FluentConstraint::quality)
                        .thenComparing(constraint -> nullSafe(constraint.initialValue()))
                        .thenComparing(constraint -> nullSafe(constraint.resultingValue())))
                .toList();
    }

    private List<FluentCondition> preconditions(List<GroundFluent> fluents, Map<Resource, String> variables) {
        return fluents.stream()
                .filter(fluent -> fluent.initialValue() != null
                        || fluent.role().equals("requiresFluent") && fluent.legacyValue() != null)
                .map(fluent -> condition(fluent, fluent.initialValue() != null ? fluent.initialValue() : fluent.legacyValue(), variables))
                .distinct().sorted(conditionComparator()).toList();
    }

    private List<FluentCondition> positiveEffects(List<GroundFluent> fluents, Map<Resource, String> variables) {
        return fluents.stream()
                .filter(fluent -> fluent.resultingValue() != null
                        || fluent.role().equals("assertsFluent") && fluent.legacyValue() != null)
                .map(fluent -> condition(fluent,
                        fluent.resultingValue() != null ? fluent.resultingValue() : fluent.legacyValue(), variables))
                .distinct().sorted(conditionComparator()).toList();
    }

    private List<FluentCondition> negativeEffects(List<GroundFluent> fluents, Map<Resource, String> variables) {
        return fluents.stream()
                .filter(fluent -> fluent.initialValue() != null
                        || fluent.role().equals("negatesFluent") && fluent.legacyValue() != null)
                .map(fluent -> condition(fluent,
                        fluent.initialValue() != null ? fluent.initialValue() : fluent.legacyValue(), variables))
                .distinct().sorted(conditionComparator()).toList();
    }

    private FluentCondition condition(GroundFluent fluent, Resource value, Map<Resource, String> variables) {
        return new FluentCondition(variables.get(fluent.object()), variables.get(fluent.quality()), variables.get(value));
    }

    private Comparator<FluentCondition> conditionComparator() {
        return Comparator.comparing(FluentCondition::object).thenComparing(FluentCondition::quality)
                .thenComparing(FluentCondition::value);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private TypeRef selectType(Model model, Resource resource) {
        return selectMostSpecificType(model, resource, GENERIC_TYPES);
    }

    /** Selects one stable leaf type, suppressing the inferred superclass closure. */
    private TypeRef selectMostSpecificType(Model model, Resource resource, Set<String> ignoredTypes) {
        Set<String> candidates = model.listObjectsOfProperty(resource, RDF.type).toList().stream()
                .filter(RDFNode::isURIResource).map(RDFNode::asResource).map(Resource::getURI)
                .filter(uri -> !ignoredTypes.contains(uri)).collect(Collectors.toCollection(TreeSet::new));
        Set<String> leastSpecific = candidates.stream().filter(candidate -> candidates.stream()
                .anyMatch(other -> !candidate.equals(other) && isSubclassOf(model, other, candidate)))
                .collect(Collectors.toSet());
        candidates.removeAll(leastSpecific);
        if (candidates.isEmpty()) return null;
        String uri = candidates.stream()
                .sorted(Comparator.comparingInt(this::namespaceRank).thenComparing(value -> value))
                .findFirst().orElseThrow();
        return new TypeRef(localName(uri), uri);
    }

    private boolean isSubclassOf(Model model, String child, String parent) {
        if (child.equals(parent)) return false;
        Property subClassOf = RDFS.subClassOf;
        Set<Resource> seen = new HashSet<>();
        List<Resource> pending = new ArrayList<>();
        pending.add(model.createResource(child));
        while (!pending.isEmpty()) {
            Resource current = pending.remove(pending.size() - 1);
            if (!seen.add(current)) continue;
            for (RDFNode node : model.listObjectsOfProperty(current, subClassOf).toList()) {
                if (node.isURIResource()) {
                    if (parent.equals(node.asResource().getURI())) return true;
                    pending.add(node.asResource());
                }
            }
        }
        return false;
    }

    private int namespaceRank(String uri) {
        if (uri.startsWith(NS.getUri())) return 0;
        if (uri.startsWith(NS_SOHO.getUri())) return 1;
        if (uri.startsWith(NS_DUL.getUri())) return 3;
        return 2;
    }

    private List<Resource> objects(Model model, Resource subject, String propertyUri) {
        return model.listObjectsOfProperty(subject, model.createProperty(propertyUri)).toList().stream()
                .filter(RDFNode::isResource).map(RDFNode::asResource)
                .sorted(Comparator.comparing(this::resourceKey)).toList();
    }

    private String canonical(TypeRef functionType, List<Parameter> parameters,
            List<FluentConstraint> constraints, List<FluentCondition> preconditions,
            List<FluentCondition> positive, List<FluentCondition> negative) {
        return functionType.uri() + "|" + parameters + "|constraints=" + constraints + "|pre=" + preconditions
                + "|pos=" + positive + "|neg=" + negative;
    }

    private String signatureId(String canonical) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder("sha256:");
            for (int i = 0; i < 12; i++) value.append(String.format("%02x", digest[i]));
            return value.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String resourceKey(Resource resource) {
        return resource.isURIResource() ? "u:" + resource.getURI() : "b:" + resource.getId().getLabelString();
    }

    private String localName(String uri) {
        int split = Math.max(uri.lastIndexOf('#'), uri.lastIndexOf('/'));
        return split < 0 ? uri : uri.substring(split + 1);
    }

    private record Role(String name, String uri) { }
    private record FluentRole(String name, String uri) { }
    private record TypeRef(String name, String uri) { }
    private record GroundFluent(String role, TypeRef type, Resource object, Resource quality,
            Resource initialValue, Resource resultingValue, Resource legacyValue) { }
    private record Grounding(TypeRef functionType, List<Parameter> parameters, List<FluentConstraint> constraints,
            List<FluentCondition> preconditions, List<FluentCondition> positiveEffects,
            List<FluentCondition> negativeEffects, String canonical) { }

    private final class Aggregate {
        private final Grounding grounding;
        private final List<Resource> functions = new ArrayList<>();

        private Aggregate(Grounding grounding) {
            this.grounding = grounding;
        }

        private void add(Resource function) {
            functions.add(function);
        }

        private ActionSchema toSchema() {
            return new ActionSchema(grounding.functionType().name(), grounding.functionType().uri(),
                    signatureId(grounding.canonical()), functions.size(), grounding.parameters(),
                    grounding.constraints(), grounding.preconditions(), grounding.positiveEffects(), grounding.negativeEffects());
        }
    }
}
