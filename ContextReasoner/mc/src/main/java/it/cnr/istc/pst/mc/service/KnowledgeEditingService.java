package it.cnr.istc.pst.mc.service;

import static it.cnr.istc.pst.mc.semantics.MetacognitionDictionary.NS_SOHO;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.rdf.model.AnonId;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.springframework.stereotype.Service;

import it.cnr.istc.pst.mc.api.KnowledgeEditorDtos.KnowledgeGraphResponse;
import it.cnr.istc.pst.mc.api.KnowledgeEditorDtos.RdfTerm;
import it.cnr.istc.pst.mc.api.KnowledgeEditorDtos.RdfTriple;

/**
 * Controlled editor for the asserted scenario subgraph. The ontology TBox is not
 * exposed as editable data; mutations are confined to the ABox connected to
 * labeled scenario individuals. Inference is refreshed only on explicit request.
 */
@Service
public class KnowledgeEditingService {

    private static final String HAS_LABEL = NS_SOHO + "hasLabel";

    private final ContextReasonerService knowledge;

    public KnowledgeEditingService(ContextReasonerService knowledge) {
        this.knowledge = knowledge;
    }

    public KnowledgeGraphResponse currentGraph() {
        return knowledge.readRawModel(model -> {
            List<Statement> statements = editableStatements(model);
            return new KnowledgeGraphResponse(
                statements.stream().map(this::toTriple)
                        .sorted(Comparator.comparing((RdfTriple triple) -> triple.subject().value())
                                .thenComparing(RdfTriple::predicate)
                                .thenComparing(triple -> triple.object().value()))
                        .toList(),
                resourceLabels(model, statements), knowledge.isInferenceDirty());
        });
    }

    /** Readable presentation names; blank-node identifiers remain only in RDF edit payloads. */
    private Map<String, String> resourceLabels(Model model, List<Statement> statements) {
        Map<String, Resource> resources = new TreeMap<>();
        statements.forEach(statement -> {
            Resource subject = statement.getSubject();
            resources.put(toTerm(subject).value(), subject);
            if (statement.getObject().isResource()) {
                Resource object = statement.getResource();
                resources.put(toTerm(object).value(), object);
            }
        });
        Map<String, Integer> counters = new java.util.HashMap<>();
        Map<String, String> labels = new LinkedHashMap<>();
        resources.forEach((key, resource) -> {
            String label = explicitLabel(model, resource);
            if (label == null) label = resource.isURIResource() ? localName(resource) : mostSpecificTypeName(model, resource);
            if (label == null || label.isBlank()) label = "Resource";
            if (resource.isAnon() && explicitLabel(model, resource) == null) {
                int number = counters.merge(label, 1, Integer::sum);
                label = label + " " + number;
            }
            labels.put(key, label);
        });
        return labels;
    }

    private String explicitLabel(Model model, Resource resource) {
        Statement statement = resource.getProperty(model.createProperty(HAS_LABEL));
        return statement != null && statement.getObject().isLiteral() ? statement.getString() : null;
    }

    private String mostSpecificTypeName(Model model, Resource resource) {
        return model.listObjectsOfProperty(resource, RDF.type).toList().stream()
                .filter(RDFNode::isURIResource).map(RDFNode::asResource)
                .map(this::localName).sorted().findFirst().orElse(null);
    }

    private String localName(Resource resource) {
        String local = resource.getLocalName();
        return local == null || local.isBlank() ? resource.getURI() : local;
    }

    public void add(RdfTriple triple) {
        knowledge.writeRawModel(model -> {
            Statement statement = toStatement(model, triple);
            requireEditableSubjectForAdd(model, statement.getSubject());
            model.add(statement);
            return null;
        });
    }

    public void remove(RdfTriple triple) {
        knowledge.writeRawModel(model -> {
            Statement statement = toStatement(model, triple);
            requireExistingEditable(model, statement);
            model.remove(statement);
            return null;
        });
    }

    public void update(RdfTriple previous, RdfTriple replacement) {
        knowledge.writeRawModel(model -> {
            Statement oldStatement = toStatement(model, previous);
            Statement newStatement = toStatement(model, replacement);
            requireExistingEditable(model, oldStatement);
            requireEditableSubjectForAdd(model, newStatement.getSubject());
            model.remove(oldStatement);
            model.add(newStatement);
            return null;
        });
    }

    public void refreshInference() {
        knowledge.refreshInference();
    }

    private List<Statement> editableStatements(Model model) {
        Set<Resource> editable = editableResources(model);
        List<Statement> statements = new ArrayList<>();
        editable.forEach(subject -> statements.addAll(model.listStatements(subject, null, (RDFNode) null).toList()));
        return statements.stream().distinct().toList();
    }

    private Set<Resource> editableResources(Model model) {
        Set<Resource> editable = new HashSet<>();
        ArrayDeque<Resource> pending = new ArrayDeque<>();
        model.listSubjectsWithProperty(model.createProperty(HAS_LABEL)).forEachRemaining(pending::add);
        while (!pending.isEmpty()) {
            Resource current = pending.removeFirst();
            if (!editable.add(current)) continue;
            for (Statement statement : model.listStatements(current, null, (RDFNode) null).toList()) {
                if (!statement.getPredicate().equals(RDF.type) && statement.getObject().isResource()) {
                    Resource object = statement.getResource();
                    if (object.isAnon() || !isVocabularyResource(object)) pending.add(object);
                }
            }
            for (Statement statement : model.listStatements(null, null, current).toList()) {
                Resource subject = statement.getSubject();
                if (subject.isAnon() || !isVocabularyResource(subject)) pending.add(subject);
            }
        }
        return editable;
    }

    private boolean isVocabularyResource(Resource resource) {
        if (!resource.isURIResource()) return false;
        String uri = resource.getURI();
        return uri.startsWith("http://www.w3.org/1999/02/22-rdf-syntax-ns#")
                || uri.startsWith("http://www.w3.org/2000/01/rdf-schema#")
                || uri.startsWith("http://www.w3.org/2002/07/owl#")
                || uri.startsWith("http://www.loa-cnr.it/ontologies/DUL.owl#")
                || uri.startsWith("http://pst.istc.cnr.it/ontologies/2019/01/soho#")
                || uri.startsWith("http://pst.istc.cnr.it/ontologies/2026/metacognition#");
    }

    private void requireExistingEditable(Model model, Statement statement) {
        if (!editableResources(model).contains(statement.getSubject()) || !model.contains(statement)) {
            throw new IllegalArgumentException("The triple is not part of the editable scenario ABox");
        }
    }

    private void requireEditableSubjectForAdd(Model model, Resource subject) {
        if (editableResources(model).contains(subject) || subject.isAnon()) return;
        if (subject.isURIResource() && !isVocabularyResource(subject)) return;
        throw new IllegalArgumentException("Ontology vocabulary resources cannot be edited as ABox subjects");
    }

    private Statement toStatement(Model model, RdfTriple triple) {
        if (triple == null || triple.subject() == null || triple.object() == null
                || triple.predicate() == null || triple.predicate().isBlank()) {
            throw new IllegalArgumentException("A complete RDF triple is required");
        }
        validateUri(triple.predicate(), "predicate");
        Resource subject = resource(model, triple.subject(), "subject");
        RDFNode object = node(model, triple.object());
        return model.createStatement(subject, model.createProperty(triple.predicate()), object);
    }

    private Resource resource(Model model, RdfTerm term, String position) {
        if (term.value() == null || term.value().isBlank()) throw new IllegalArgumentException("Missing " + position);
        if ("uri".equals(term.kind())) {
            validateUri(term.value(), position);
            return model.createResource(term.value());
        }
        if ("blankNode".equals(term.kind())) {
            String label = term.value().startsWith("_:") ? term.value().substring(2) : term.value();
            if (label.isBlank()) throw new IllegalArgumentException("Missing blank-node label");
            return model.createResource(AnonId.create(label));
        }
        throw new IllegalArgumentException("The " + position + " must be a URI or blank node");
    }

    private RDFNode node(Model model, RdfTerm term) {
        if (!"literal".equals(term.kind())) return resource(model, term, "object");
        String value = term.value() == null ? "" : term.value();
        if (term.language() != null && !term.language().isBlank()) return model.createLiteral(value, term.language());
        if (term.datatype() != null && !term.datatype().isBlank()) {
            validateUri(term.datatype(), "datatype");
            return model.createTypedLiteral(value, TypeMapper.getInstance().getSafeTypeByName(term.datatype()));
        }
        return model.createLiteral(value);
    }

    private void validateUri(String value, String position) {
        try {
            if (!URI.create(value).isAbsolute()) throw new IllegalArgumentException();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("The " + position + " must be an absolute URI");
        }
    }

    private RdfTriple toTriple(Statement statement) {
        return new RdfTriple(toTerm(statement.getSubject()), statement.getPredicate().getURI(),
                toTerm(statement.getObject()));
    }

    private RdfTerm toTerm(RDFNode node) {
        if (node.isLiteral()) {
            Literal literal = node.asLiteral();
            return new RdfTerm(literal.getLexicalForm(), "literal", literal.getDatatypeURI(), literal.getLanguage());
        }
        Resource resource = node.asResource();
        return new RdfTerm(resource.isURIResource() ? resource.getURI() : "_:" + resource.getId().getLabelString(),
                resource.isURIResource() ? "uri" : "blankNode", null, null);
    }
}
