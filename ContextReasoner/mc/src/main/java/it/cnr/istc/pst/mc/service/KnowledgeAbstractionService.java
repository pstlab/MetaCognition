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
import it.cnr.istc.pst.mc.api.AbstractionResponse.AlignmentConstraint;
import it.cnr.istc.pst.mc.api.AbstractionResponse.Parameter;
import it.cnr.istc.pst.mc.api.AbstractionResponse.StateCondition;
import it.cnr.istc.pst.mc.api.AbstractionResponse.TriplePattern;
import it.cnr.istc.pst.mc.api.SchemaGroundingsResponse;
import it.cnr.istc.pst.mc.api.SchemaGroundingsResponse.FunctionGrounding;
import it.cnr.istc.pst.mc.api.SchemaGroundingsResponse.Triple;

/**
 * Read-only abstraction of inferred Function meta-knowledge. Grounded Function
 * individuals are grouped into contextual action schemas by their typed roles and
 * Belief Alignment patterns. A Region change keeps its initial value as a precondition and
 * delete effect, and its resulting value as an add effect; no inferred planning
 * effect is asserted as factual knowledge.
 */
@Service
public class KnowledgeAbstractionService {

    private static final String CAN_PERFORM = NS_SOHO + "canBePerformedBy";
    private static final String TARGET = NS_SOHO + "hasTarget";
    private static final String REQUIRES_CAPABILITY = NS_SOHO + "requiresCapability";
    private static final String PRECONDITION_ON = NS + "hasPreconditionOn";
    private static final String EFFECT_ON = NS_SOHO + "hasEffectOn";
    private static final String REQUIRES = NS + "requiresAlignment";
    private static final String ASSERTS = NS + "assertsAlignment";
    private static final String NEGATES = NS + "negatesAlignment";
    private static final String CONCERNS_OBJECT = NS + "concernsObject";
    private static final String CONCERNS_QUALITY = NS + "concernsQuality";
    private static final String ALIGNMENT_VALUE = NS + "hasBeliefAlignmentValue";
    private static final String INITIAL_VALUE = NS + "hasInitialValue";
    private static final String RESULTING_VALUE = NS + "hasResultingValue";
    private static final String CONNECT_ASSERTION_ALIGNMENT = NS + "ConnectAssertionAlignment";
    private static final String CONNECTS = NS + "connects";

    private static final List<Role> FUNCTION_ROLES = List.of(
            new Role("canBePerformedBy", CAN_PERFORM),
            new Role("hasTarget", TARGET),
            new Role("requiresCapability", REQUIRES_CAPABILITY),
            new Role("hasPreconditionOn", PRECONDITION_ON),
            new Role("hasEffectOn", EFFECT_ON));
    private static final List<AlignmentRole> ALIGNMENT_ROLES = List.of(
            new AlignmentRole("requiresAlignment", REQUIRES),
            new AlignmentRole("assertsAlignment", ASSERTS),
            new AlignmentRole("negatesAlignment", NEGATES));
    private static final Set<String> GENERIC_TYPES = Set.of(
            NS_OWL + "Thing", NS_RDFS + "Resource", NS_RDFS + "Class",
            NS_DUL + "Entity", NS_DUL + "Object", NS_SOHO + "Function", NS + "BeliefAlignment");
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
        for (AlignmentRole role : ALIGNMENT_ROLES) {
            Property property = model.createProperty(role.uri());
            for (org.apache.jena.rdf.model.Statement statement : model.listStatements(function, property, (RDFNode) null).toList()) {
                statements.add(statement);
                if (!statement.getObject().isResource()) continue;
                Resource alignment = statement.getResource();
                participants.add(alignment);
                collectStatements(model, alignment, CONCERNS_OBJECT, statements, participants);
                collectStatements(model, alignment, CONCERNS_QUALITY, statements, participants);
                boolean specializedValues = model.contains(alignment, model.createProperty(INITIAL_VALUE))
                        || model.contains(alignment, model.createProperty(RESULTING_VALUE));
                collectStatements(model, alignment, INITIAL_VALUE, statements, participants);
                collectStatements(model, alignment, RESULTING_VALUE, statements, participants);
                if (!specializedValues) {
                    collectStatements(model, alignment, ALIGNMENT_VALUE, statements, participants);
                }
                collectReifiedStatement(model, alignment, statements, participants);
                collectImplicitAssertionStatement(model, alignment, statements, participants);
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

    private void collectReifiedStatement(Model model, Resource alignment,
            Set<org.apache.jena.rdf.model.Statement> statements, Set<Resource> participants) {
        for (Property property : List.of(RDF.subject, RDF.predicate, RDF.object)) {
            for (org.apache.jena.rdf.model.Statement statement : model.listStatements(
                    alignment, property, (RDFNode) null).toList()) {
                statements.add(statement);
                // The predicate is a constant in the triple pattern, not an action parameter.
                if (!property.equals(RDF.predicate) && statement.getObject().isResource()) {
                    participants.add(statement.getResource());
                }
            }
        }
    }

    private void collectImplicitAssertionStatement(Model model, Resource alignment,
            Set<org.apache.jena.rdf.model.Statement> statements, Set<Resource> participants) {
        if (!model.contains(alignment, RDF.type, model.createResource(CONNECT_ASSERTION_ALIGNMENT))) return;
        List<Resource> subjects = objects(model, alignment, CONCERNS_QUALITY);
        List<Resource> objects = objects(model, alignment, CONCERNS_OBJECT);
        if (subjects.size() != 1 || objects.size() != 1) return;
        org.apache.jena.rdf.model.Statement asserted = model.createStatement(
                subjects.get(0), model.createProperty(CONNECTS), objects.get(0));
        if (model.contains(asserted)) {
            statements.add(asserted);
            participants.add(subjects.get(0));
            participants.add(objects.get(0));
        }
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
        for (AlignmentRole role : ALIGNMENT_ROLES) {
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

        List<GroundAlignment> alignments = new ArrayList<>();
        for (AlignmentRole alignmentRole : ALIGNMENT_ROLES) {
            for (Resource alignment : objects(model, function, alignmentRole.uri())) {
                TypeRef alignmentType = selectMostSpecificType(model, alignment, UNIVERSAL_TYPES);
                List<Resource> objects = objects(model, alignment, CONCERNS_OBJECT);
                List<Resource> qualities = objects(model, alignment, CONCERNS_QUALITY);
                List<Resource> initialValues = objects(model, alignment, INITIAL_VALUE);
                List<Resource> resultingValues = objects(model, alignment, RESULTING_VALUE);
                List<Resource> values = initialValues.isEmpty() && resultingValues.isEmpty()
                        ? objects(model, alignment, ALIGNMENT_VALUE) : List.of();
                ReifiedTriple triple = statementTriple(model, alignment, alignmentType, objects, qualities);
                addRoles(resourceRoles, objects, "concernsObject");
                addRoles(resourceRoles, qualities, "concernsQuality");
                addRoles(resourceRoles, values, "hasBeliefAlignmentValue");
                addRoles(resourceRoles, initialValues, "hasInitialValue");
                addRoles(resourceRoles, resultingValues, "hasResultingValue");
                if (triple != null) {
                    addRoles(resourceRoles, List.of(triple.subject()), "assertionSubject");
                    addRoles(resourceRoles, List.of(triple.object()), "assertionObject");
                }
                alignments.add(new GroundAlignment(alignmentRole.name(), alignmentType, objects, qualities,
                        values, initialValues, resultingValues, triple));
            }
        }

        Map<Resource, TypeRef> types = new HashMap<>();
        resourceRoles.keySet().forEach(resource -> types.put(resource, selectType(model, resource)));
        if (types.values().stream().anyMatch(type -> type == null)) {
            return null; // an untyped participant cannot form a typed relational signature
        }

        List<Resource> ordered = resourceRoles.keySet().stream()
                .sorted(Comparator.comparing((Resource resource) -> nodeFingerprint(resource, resourceRoles, types, alignments))
                        .thenComparing(this::resourceKey))
                .toList();
        Map<Resource, String> variables = assignVariables(ordered, resourceRoles, types);

        List<Parameter> parameters = ordered.stream()
                .map(resource -> new Parameter(variables.get(resource), types.get(resource).name(),
                        types.get(resource).uri(), List.copyOf(resourceRoles.get(resource))))
                .sorted(Comparator.comparing(Parameter::variable))
                .toList();
        List<AlignmentConstraint> constraints = constraints(alignments, variables);
        List<StateCondition> preconditions = preconditions(alignments, variables);
        List<StateCondition> positive = positiveEffects(alignments, variables);
        List<StateCondition> negative = negativeEffects(alignments, variables);

        String canonical = canonical(functionType, parameters, constraints, preconditions, positive, negative);
        return new Grounding(functionType, parameters, constraints, preconditions, positive, negative, canonical);
    }

    private void addRoles(Map<Resource, Set<String>> roles, List<Resource> resources, String role) {
        resources.forEach(resource -> roles.computeIfAbsent(resource, ignored -> new TreeSet<>()).add(role));
    }

    private String nodeFingerprint(Resource resource, Map<Resource, Set<String>> roles,
            Map<Resource, TypeRef> types, List<GroundAlignment> alignments) {
        List<String> incident = new ArrayList<>();
        for (GroundAlignment alignment : alignments) {
            String shape = alignmentShape(alignment, types);
            if (alignment.objects().contains(resource)) incident.add(alignment.role() + ":object:" + shape);
            if (alignment.qualities().contains(resource)) incident.add(alignment.role() + ":quality:" + shape);
            if (alignment.values().contains(resource)) incident.add(alignment.role() + ":value:" + shape);
            if (alignment.initialValues().contains(resource)) incident.add(alignment.role() + ":initial:" + shape);
            if (alignment.resultingValues().contains(resource)) incident.add(alignment.role() + ":resulting:" + shape);
            if (alignment.triple() != null && alignment.triple().subject().equals(resource))
                incident.add(alignment.role() + ":assertionSubject:" + shape);
            if (alignment.triple() != null && alignment.triple().object().equals(resource))
                incident.add(alignment.role() + ":assertionObject:" + shape);
        }
        incident.sort(String::compareTo);
        return types.get(resource).uri() + "|" + String.join(",", roles.get(resource)) + "|" + String.join(",", incident);
    }

    private String alignmentShape(GroundAlignment alignment, Map<Resource, TypeRef> types) {
        return typeUris(alignment.objects(), types) + ":" + typeUris(alignment.qualities(), types) + ":"
                + typeUris(alignment.values(), types) + ":" + typeUris(alignment.initialValues(), types) + ":"
                + typeUris(alignment.resultingValues(), types)
                + (alignment.triple() == null ? "" : ":triple=" + types.get(alignment.triple().subject()).uri()
                        + "|" + alignment.triple().predicateUri() + "|"
                        + types.get(alignment.triple().object()).uri());
    }

    private String typeUris(List<Resource> resources, Map<Resource, TypeRef> types) {
        return resources.stream().map(resource -> types.get(resource).uri()).sorted().collect(Collectors.joining(",", "[", "]"));
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
        if (roles.contains("hasBeliefAlignmentValue")) return decap(type, "value");
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

    private List<AlignmentConstraint> constraints(List<GroundAlignment> alignments, Map<Resource, String> variables) {
        return alignments.stream().map(alignment -> new AlignmentConstraint(alignment.role(),
                        alignment.type() == null ? "BeliefAlignment" : alignment.type().name(),
                        alignment.type() == null ? NS + "BeliefAlignment" : alignment.type().uri(),
                        variableList(alignment.objects(), variables), variableList(alignment.qualities(), variables),
                        variableList(alignment.values(), variables), variableList(alignment.initialValues(), variables),
                        variableList(alignment.resultingValues(), variables), triplePattern(alignment, variables)))
                .distinct()
                .sorted(Comparator.comparing(AlignmentConstraint::association)
                        .thenComparing(AlignmentConstraint::alignmentType)
                        .thenComparing(constraint -> String.join(",", constraint.objects()))
                        .thenComparing(constraint -> String.join(",", constraint.qualities())))
                .toList();
    }

    private TriplePattern triplePattern(GroundAlignment alignment, Map<Resource, String> variables) {
        if (alignment.triple() == null) return null;
        return new TriplePattern(variables.get(alignment.triple().subject()),
                localName(alignment.triple().predicateUri()), alignment.triple().predicateUri(),
                variables.get(alignment.triple().object()));
    }

    private ReifiedTriple reifiedTriple(Model model, Resource alignment) {
        List<Resource> subjects = objects(model, alignment, RDF.subject.getURI());
        List<Resource> predicates = objects(model, alignment, RDF.predicate.getURI());
        List<Resource> objects = objects(model, alignment, RDF.object.getURI());
        if (subjects.isEmpty() && predicates.isEmpty() && objects.isEmpty()) return null;
        if (subjects.size() != 1 || predicates.size() != 1 || objects.size() != 1
                || !predicates.get(0).isURIResource()) {
            throw new IllegalArgumentException("Legacy RDF-reified alignment must contain exactly one resource triple: "
                    + displayId(alignment));
        }
        return new ReifiedTriple(subjects.get(0), predicates.get(0).getURI(), objects.get(0));
    }

    /**
     * Resolves a directional RDF assertion carried by an alignment. Legacy RDF
     * reification remains readable, while OWL-native assertion subtypes fix the
     * predicate and use typed alignment roles for their endpoints.
     */
    private ReifiedTriple statementTriple(Model model, Resource alignment, TypeRef alignmentType,
            List<Resource> objects, List<Resource> qualities) {
        ReifiedTriple legacy = reifiedTriple(model, alignment);
        if (legacy != null) return legacy;
        if (alignmentType == null || !CONNECT_ASSERTION_ALIGNMENT.equals(alignmentType.uri())) return null;
        if (qualities.size() != 1 || objects.size() != 1) {
            throw new IllegalArgumentException("ConnectAssertionAlignment must concern exactly one "
                    + "ReachabilityDisposition and one InteractingObject: " + displayId(alignment));
        }
        return new ReifiedTriple(qualities.get(0), CONNECTS, objects.get(0));
    }

    private List<String> variableList(List<Resource> resources, Map<Resource, String> variables) {
        return resources.stream().map(variables::get).sorted().toList();
    }

    private List<StateCondition> preconditions(List<GroundAlignment> alignments, Map<Resource, String> variables) {
        return alignments.stream().flatMap(alignment -> {
                    if (!alignment.initialValues().isEmpty()) return conditions(alignment, alignment.initialValues(), variables).stream();
                    // Multi-participant alignments (for example a QualityBinding)
                    // are relational constraints, not a Cartesian set of scalar
                    // object/quality/value conditions. They remain explicit in
                    // alignmentConstraints and are flattened only when scalar.
                    if (alignment.role().equals("requiresAlignment") && isScalar(alignment))
                        return conditions(alignment, alignment.values(), variables).stream();
                    return java.util.stream.Stream.empty();
                }).distinct().sorted(conditionComparator()).toList();
    }

    private List<StateCondition> positiveEffects(List<GroundAlignment> alignments, Map<Resource, String> variables) {
        return alignments.stream().flatMap(alignment -> {
                    if (!alignment.resultingValues().isEmpty()) return conditions(alignment, alignment.resultingValues(), variables).stream();
                    if (alignment.role().equals("assertsAlignment") && isScalar(alignment))
                        return conditions(alignment, alignment.values(), variables).stream();
                    return java.util.stream.Stream.empty();
                }).distinct().sorted(conditionComparator()).toList();
    }

    private List<StateCondition> negativeEffects(List<GroundAlignment> alignments, Map<Resource, String> variables) {
        return alignments.stream().flatMap(alignment -> {
                    if (!alignment.initialValues().isEmpty()) return conditions(alignment, alignment.initialValues(), variables).stream();
                    if (alignment.role().equals("negatesAlignment") && isScalar(alignment))
                        return conditions(alignment, alignment.values(), variables).stream();
                    return java.util.stream.Stream.empty();
                }).distinct().sorted(conditionComparator()).toList();
    }

    private boolean isScalar(GroundAlignment alignment) {
        return alignment.objects().size() == 1 && alignment.qualities().size() == 1 && alignment.values().size() == 1;
    }

    private List<StateCondition> conditions(GroundAlignment alignment, List<Resource> values,
            Map<Resource, String> variables) {
        List<StateCondition> result = new ArrayList<>();
        for (Resource object : alignment.objects()) {
            for (Resource quality : alignment.qualities()) {
                for (Resource value : values) {
                    result.add(new StateCondition(variables.get(object), variables.get(quality), variables.get(value)));
                }
            }
        }
        return result;
    }

    private Comparator<StateCondition> conditionComparator() {
        return Comparator.comparing(StateCondition::object).thenComparing(StateCondition::quality)
                .thenComparing(StateCondition::value);
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
            List<AlignmentConstraint> constraints, List<StateCondition> preconditions,
            List<StateCondition> positive, List<StateCondition> negative) {
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
    private record AlignmentRole(String name, String uri) { }
    private record TypeRef(String name, String uri) { }
    private record GroundAlignment(String role, TypeRef type, List<Resource> objects, List<Resource> qualities,
            List<Resource> values, List<Resource> initialValues, List<Resource> resultingValues,
            ReifiedTriple triple) { }
    private record ReifiedTriple(Resource subject, String predicateUri, Resource object) { }
    private record Grounding(TypeRef functionType, List<Parameter> parameters, List<AlignmentConstraint> constraints,
            List<StateCondition> preconditions, List<StateCondition> positiveEffects,
            List<StateCondition> negativeEffects, String canonical) { }

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
