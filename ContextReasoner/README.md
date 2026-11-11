# MetaCognition Context Reasoner

The Context Reasoner is a Spring Boot application that maintains an Apache Jena
knowledge graph for robot metacognition. It combines an OWL/RDF domain model with
Jena production rules to infer which robot Functions are feasible in the current
context. It then exposes both the inferred graph and a higher-level abstraction of
grounded Function individuals through REST APIs and browser-based inspection tools.

The project is intended for researchers and developers who want to:

- describe robot embodiments, capabilities, qualities, and environmental state;
- materialize contextual Function instances such as `MoveTo` from the current ABox;
- inspect raw and inferred knowledge with SPARQL;
- graphically edit the asserted scenario ABox and explicitly refresh inference;
- abstract grounded Functions into deterministic, typed action schemas;
- inspect the Function and Belief Alignment subgraphs supporting each schema; and
- extend the ontology and production rules with new cognitive functionality.

The current implementation is a context reasoner and abstraction/inspection layer.
It does not execute robot actions or apply inferred planning effects to the factual
world state.

## Semantic model

The ontology distinguishes current factual knowledge from inferred
**meta-knowledge** about possible Functions.

A Function is grounded by Jena rules when its contextual requirements are met. Its
planning-relevant neighborhood can include:

- `canBePerformedBy` — the agent able to perform the Function;
- `hasTarget` — the Function target;
- `hasPreconditionOn` and `hasEffectOn` — relevant qualities;
- `requiresAlignment`, `assertsAlignment`, and `negatesAlignment` — diagnostic constraints.

Belief Alignments are reified Diagnosis concepts. They identify the objects and qualities under
consideration through `concernsObject` and `concernsQuality`, and constrain values
through `hasBeliefAlignmentValue` or alignment-specific properties.

The currently supported alignments include `RegionGuardAlignment`,
`RegionChangeAlignment`, `QualityBindingAlignment`, and
`TriplesAssertionAlignment`. A `RegionChangeAlignment` uses
`hasInitialValue` and `hasResultingValue` to describe a Region transition for the same
object quality. In an abstract action schema this means:

```text
precondition     object.quality = initial Region
negative effect  remove object.quality = initial Region
positive effect  add object.quality = resulting Region
```

These are planning semantics only. Inference does **not** replace the current
`dul:hasRegion` value in the factual ABox.

## From knowledge to action schemas

The abstraction pipeline is:

```text
Function ontology type
  -> inferred grounded Function individuals
  -> typed relational signatures
  -> Belief Alignment/Diagnosis constraints
  -> contextual action schemas
```

Schemas are not grouped by Function type alone. The abstraction also considers the
planning-relevant roles, the most specific useful types of their participants, and
the Belief Alignment structure. Consequently, two `MoveTo` individuals with equivalent
structure but different resource identities collapse into one schema, while two
`MoveTo` individuals with different typed structures remain separate schemas.

Within each grounding, the same RDF resource always maps to the same abstract
variable. For example, a `Pose` used by `hasPreconditionOn`, `hasEffectOn`, and
`concernsQuality` is represented by one shared variable. Schema output and signature
identifiers are deterministic for the same inferred model and do not depend on Jena
iteration order or temporary blank-node labels.

## Software architecture

```text
Browser / REST client
        |
MetacognitionRestController
        |
        +-- KnowledgeAbstractionService
        |       |
        |       +-- ContextReasonerService.readInferredModel(...)
        |
        +-- KnowledgeEditingService
        |       |
        |       +-- ContextReasonerService.readRawModel/writeRawModel
        |
        +-- ContextReasonerService.select(...)
                |
        +-------+--------+
        |                |
 SemanticModel      RuleReasoner
  raw Jena Model    inferred Jena InfModel
        |                |
        +-- OWL/RDF -----+-- Jena rules
```

The main responsibilities are:

- `SemanticModel` loads and updates the raw Jena `Model` containing the ontology and
  factual ABox.
- `RuleReasoner` attaches Jena's `GenericRuleReasoner` in hybrid mode and maintains
  the inferred `InfModel`. It includes RDFS reasoning through the rules file.
- `ContextReasonerService` is the application-level knowledge service. It owns the
  raw/inferred models, protects access with read/write transactions, refreshes
  inference at the intended lifecycle points, and exposes detached SPARQL results.
- `KnowledgeAbstractionService` reads the current inferred model through
  `ContextReasonerService`; it never creates a second reasoner or changes triples.
- `KnowledgeEditingService` exposes only the labeled, connected scenario ABox subgraph,
  validates RDF terms, and keeps ontology vocabulary resources out of the editable
  subject set. ABox mutations mark inference as stale until explicitly refreshed.
- `MetacognitionRestController` is a thin HTTP layer returning explicit DTO records,
  never raw Jena objects.
- The SPARQL and abstraction pages are read-only. The dedicated ABox editor performs
  controlled asserted-ABox changes; it cannot directly edit inferred triples or
  ontology axioms.

## Technology

- Java 17
- Spring Boot 4.0.5 and Spring Web MVC
- Apache Jena 5.1.0 (`Model`, `InfModel`, SPARQL, RDFS, and production rules)
- Jackson JSON serialization
- Maven
- JUnit 5, AssertJ, and Spring MockMvc for tests
- Dependency-free HTML, CSS, JavaScript, and SVG for the web interfaces

The Maven model also declares DL4J/ND4J and extJWNL dependencies for broader
cognitive experimentation; the context-reasoning and abstraction path documented
here does not currently use them.

## Repository layout and intended use

```text
MetaCognition/
|-- onto/
|   |-- mc_v0.4.rdf              # current ontology and example/asserted knowledge
|   |-- mc_v0.3.rdf              # earlier ontology version
|   |-- cognition.rules          # Jena inference/production rules
|   `-- catalog-v001.xml         # ontology import/catalog resolution
|-- ContextReasoner/
|   |-- README.md                # this guide
|   `-- mc/                      # Spring Boot Maven application
|       |-- pom.xml
|       `-- src/
|           |-- main/java/.../mc/
|           |   |-- api/         # controllers and JSON request/response DTOs
|           |   |-- semantics/   # Jena model, vocabulary, and reasoner adapters
|           |   `-- service/     # application and abstraction services
|           |-- main/resources/
|           |   |-- application.properties
|           |   |-- onto -> repository-level onto/
|           |   `-- static/      # SPARQL and abstraction inspection pages
|           `-- test/java/...    # unit, REST, read-only, and integration tests
`-- model_generator/
    `-- src/sparql_client.py      # auxiliary Python SPARQL-client work
```

The `src/main/resources/onto` entry is a link to the repository-level `onto`
directory. Edit the authoritative files under `onto/`; do not maintain a second
copy inside the Java module.

Important Java files include:

- `ContextReasonerApplication.java` — Spring Boot entry point; `/` forwards to the
  SPARQL interface.
- `MetacognitionProperties.java` — binds the `mc.*` ontology and rule settings.
- `MetacognitionDictionary.java` — centralized ontology namespace constants.
- `KnowledgeGraph.java` — common Jena query operations.
- `SemanticModel.java` — raw model loading and domain assertion helpers.
- `RuleReasoner.java` — rule loading, inference-model construction, and refresh.
- `ContextReasonerService.java` — thread-safe application access to knowledge.
- `KnowledgeAbstractionService.java` — schema extraction and grounding inspection.
- `KnowledgeEditingService.java` — controlled asserted-ABox reads and writes.
- `KnowledgeEditorDtos.java` — explicit RDF term/triple editor JSON DTOs.
- `AbstractionResponse.java` and `SchemaGroundingsResponse.java` — public JSON DTOs.
- `MetacognitionRestController.java` — REST endpoints.

## Build and run

Requirements:

- JDK 17 or newer;
- Maven 3.9 or the Maven wrapper supplied with the application; and
- a checkout that preserves the `src/main/resources/onto` link, or equivalent
  `mc.ontology-path` and `mc.rules-path` configuration.

From `ContextReasoner/mc`:

```bash
./mvnw test
./mvnw spring-boot:run
```

If the wrapper cannot be used, run the equivalent commands with `mvn`.
By default, the server listens on <http://localhost:8080>.

Configuration is in `src/main/resources/application.properties`:

```properties
mc.ontology-version=0.4
mc.ontology-path=src/main/resources/onto/mc_v#VER#.rdf
mc.ontology-format=RDF/XML
mc.rules-path=src/main/resources/onto/cognition.rules
```

`#VER#` in the ontology path is replaced with `mc.ontology-version` at startup.
Paths are currently filesystem paths, so launch the application from the Maven
module directory unless you supply absolute or otherwise valid replacement paths.

To build an executable JAR:

```bash
./mvnw package
java -jar target/mc-0.0.1-SNAPSHOT.jar
```

Because ontology and rule settings currently refer to external filesystem paths,
ensure those paths are available when starting the packaged application.

## Web interfaces

After starting the application:

- <http://localhost:8080/> or `/sparql_gui.html` opens the SPARQL console. Queries
  target inferred knowledge by default; select the raw option to query only asserted
  knowledge.
- `/abstraction_inspection.html` opens the knowledge-inspection view. The left side
  lists detected schemas and expandable JSON. Selecting a schema shows its grounded
  Function individuals, concise signatures, related triples, and an interactive
  directed SVG graph. Nodes can be dragged to improve readability; edges point from
  RDF subject to object.
- `/knowledge_editor.html` opens the asserted-knowledge ABox editor. It visualizes
  the scenario as two linked draggable directed graphs. The overview contains only
  asserted physical entities (locations, environmental objects, robot/embodiment,
  and robot parts). Selecting one expands a bounded local ABox subgraph beneath it,
  including its asserted qualities, regions, and literal values. Select a detail
  edge to inspect, update, or remove its exact triple, or use the form to add one.
  Changes do not silently rerun rules:
  the page reports stale inference and provides a separate **Refresh inference**
  action. To keep the diagram readable, coordinate and label-value edges are hidden
  and anonymous RDF resources are shown with labels derived from their asserted
  label or a concise asserted type. Their real blank-node identifiers remain available
  in the selected-triple form for exact editing.

The inspection interface intentionally shows planning-relevant subgraphs and the
most specific useful types instead of serializing the complete inferred closure.

## REST API

The application API uses the `/metacognition/api` prefix and exchanges UTF-8 JSON
unless an endpoint explicitly says otherwise. The examples below assume
`BASE=http://localhost:8080/metacognition/api`.

```bash
export BASE=http://localhost:8080/metacognition/api
```

All application endpoints currently return HTTP 200 on success. Mutation endpoints
return a status document rather than HTTP 201/204.

### Endpoint summary

| Method | Canonical path | Purpose | Changes knowledge? |
|---|---|---|---|
| `GET` | `/knowledge/abox` | Read the editable asserted scenario graph | No |
| `POST` | `/knowledge/abox/reset` | Restore the startup ontology, recreate the example ABox, and refresh inference | Yes |
| `POST` | `/knowledge/triples` | Add one asserted ABox triple | Yes |
| `PUT` | `/knowledge/triples` | Replace one asserted ABox triple | Yes |
| `POST` | `/knowledge/triples/remove` | Remove one asserted ABox triple | Yes |
| `POST` | `/reasoning/refresh` | Rebind the Jena reasoner to the current asserted model | Inferred model only |
| `POST` | `/sparql/select` | Run a read-only SPARQL `SELECT` on raw or inferred knowledge | No ABox change |
| `GET` | `/abstraction` | Return contextual action schemas from current inferred knowledge | No |
| `GET` | `/abstraction/{signatureId}/groundings` | Inspect inferred Function individuals behind one schema | No |

For compatibility, `GET /knowledge/uc1` aliases `GET /knowledge/abox`, and
`POST /load/mobipick/uc1` aliases `POST /knowledge/abox/reset`. New integrations
should use the canonical ABox paths.

Spring Boot Actuator also exposes `GET /actuator` for endpoint discovery and
`GET /actuator/health` using its standard health JSON. The root path `/` is not an
API endpoint; it forwards browsers to `/sparql_gui.html`.

### Inference lifecycle

The raw model contains the ontology plus asserted ABox. The inferred model is the
Jena `InfModel` produced from that raw model and `onto/cognition.rules`.

- Adding, replacing, or removing a triple changes only the asserted model and
  returns `{"inferenceDirty":true}`.
- `POST /reasoning/refresh` explicitly rebinds the reasoner and returns
  `{"inferenceDirty":false}`.
- Resetting the example ABox also refreshes inference and clears the dirty state.
- `GET /abstraction` and the grounding-inspection endpoint read the currently
  materialized inference model; they do not silently refresh it.
- A SPARQL request with `"raw":false` rebinds inference before executing the query
  and therefore also clears the dirty state. A request with `"raw":true` does not.

For predictable batching, edit all required triples first, call the explicit
refresh endpoint once, and only then request abstractions or groundings.

### RDF JSON representation

Editor endpoints never serialize Jena objects. An RDF term has this shape:

```json
{
  "value": "_:example",
  "kind": "blankNode",
  "datatype": null,
  "language": null
}
```

`kind` is one of `uri`, `blankNode`, or `literal`. URI values, including predicate
values, must be absolute. Blank-node values may be written as `_:label` or `label`.
For literals, `datatype` and `language` are optional; do not set both. When changing
or removing an existing literal, copy its complete representation from the graph
response so the RDF statement matches exactly.

An RDF triple is represented as:

```json
{
  "subject": {
    "value": "_:object1",
    "kind": "blankNode",
    "datatype": null,
    "language": null
  },
  "predicate": "urn:example:inspectionStatus",
  "object": {
    "value": "checked",
    "kind": "literal",
    "datatype": "http://www.w3.org/2001/XMLSchema#string",
    "language": ""
  }
}
```

### Read the asserted ABox

```bash
curl -s "$BASE/knowledge/abox"
```

Example response fragment:

```json
{
  "triples": [
    {
      "subject": {
        "value": "_:b123",
        "kind": "blankNode",
        "datatype": null,
        "language": null
      },
      "predicate": "http://pst.istc.cnr.it/ontologies/2019/01/soho#hasLabel",
      "object": {
        "value": "milk_box",
        "kind": "literal",
        "datatype": "http://www.w3.org/2001/XMLSchema#string",
        "language": ""
      }
    }
  ],
  "resourceLabels": {
    "_:b123": "milk_box"
  },
  "inferenceDirty": false
}
```

`triples` contains statements whose subjects belong to the connected, labeled
scenario ABox. TBox resources may occur as objects of ABox assertions such as
`rdf:type`, but ontology axioms are not returned as editable triples.
`resourceLabels` maps RDF resource identifiers to concise display names for clients;
it does not rename or modify resources.

### Reset the example ABox

```bash
curl -i -X POST "$BASE/knowledge/abox/reset"
```

The response body is plain text:

```text
Ok
```

Reset is destructive with respect to current in-memory ABox edits. It restores the
raw model snapshot captured immediately after loading the ontology, recreates one
fresh Mobipick example ABox, and refreshes inference. Repeated calls do not
accumulate generated resources. In the example, the manipulation location is also
a navigation location: it has the traversability and reachability dispositions
needed to navigate to and interact with the graspable object.

### Add an asserted triple

Use a subject identifier obtained from `GET /knowledge/abox`, or a new non-vocabulary
absolute URI. This example uses an existing blank node:

```bash
curl -s -X POST "$BASE/knowledge/triples" \
  -H 'Content-Type: application/json' \
  -d '{
    "triple": {
      "subject": {"value":"_:b123","kind":"blankNode","datatype":null,"language":null},
      "predicate": "urn:example:inspectionStatus",
      "object": {"value":"checked","kind":"literal","datatype":"http://www.w3.org/2001/XMLSchema#string","language":""}
    }
  }'
```

Response:

```json
{"inferenceDirty":true}
```

Ontology vocabulary resources cannot be edited as ABox subjects. The operation is
idempotent at RDF-model level: adding an already present statement does not create
a duplicate, although inference is still marked dirty.

### Replace an asserted triple

`previous` must exactly match an existing editable statement. `replacement` uses
the same RDF term format:

```bash
curl -s -X PUT "$BASE/knowledge/triples" \
  -H 'Content-Type: application/json' \
  -d '{
    "previous": {
      "subject": {"value":"_:b123","kind":"blankNode","datatype":null,"language":null},
      "predicate": "urn:example:inspectionStatus",
      "object": {"value":"checked","kind":"literal","datatype":"http://www.w3.org/2001/XMLSchema#string","language":""}
    },
    "replacement": {
      "subject": {"value":"_:b123","kind":"blankNode","datatype":null,"language":null},
      "predicate": "urn:example:inspectionStatus",
      "object": {"value":"approved","kind":"literal","datatype":"http://www.w3.org/2001/XMLSchema#string","language":""}
    }
  }'
```

Successful response: `{"inferenceDirty":true}`.

### Remove an asserted triple

Removal uses `POST` rather than `DELETE` because the exact RDF statement, including
literal metadata and blank-node identity, is supplied as a JSON request body:

```bash
curl -s -X POST "$BASE/knowledge/triples/remove" \
  -H 'Content-Type: application/json' \
  -d '{
    "triple": {
      "subject": {"value":"_:b123","kind":"blankNode","datatype":null,"language":null},
      "predicate": "urn:example:inspectionStatus",
      "object": {"value":"approved","kind":"literal","datatype":"http://www.w3.org/2001/XMLSchema#string","language":""}
    }
  }'
```

Successful response: `{"inferenceDirty":true}`. Removing or replacing a statement
outside the editable ABox returns HTTP 400.

### Refresh inference

```bash
curl -s -X POST "$BASE/reasoning/refresh"
```

Response:

```json
{"inferenceDirty":false}
```

This rebinds the existing Jena reasoner. It does not construct another model, edit
the ABox, or apply inferred action effects to current factual state.

### Query raw or inferred knowledge with SPARQL

```bash
curl -s -X POST "$BASE/sparql/select" \
  -H 'Content-Type: application/json' \
  -d '{
    "sparql": "SELECT ?function WHERE { ?function a <http://pst.istc.cnr.it/ontologies/2026/metacognition#MoveTo> } ORDER BY ?function",
    "raw": false
  }'
```

Only `SELECT` is accepted. `raw` is optional and defaults to `false`. Inferred
queries refresh/rebind inference first; raw queries read assertions without doing
so. The response follows the standard SPARQL Results JSON format:

```json
{
  "head": {"vars": ["function"]},
  "results": {
    "bindings": [
      {
        "function": {"type": "bnode", "value": "..."}
      }
    ]
  }
}
```

### Obtain contextual action schemas

```bash
curl -s "$BASE/abstraction"
```

This is read-only and does not refresh stale inference. Output is deterministic for
the same inferred model and sorted independently of Jena iteration order. Example:

```json
{
  "schemas": [
    {
      "functionType": "MoveTo",
      "functionTypeUri": "http://pst.istc.cnr.it/ontologies/2026/metacognition#MoveTo",
      "signatureId": "sha256:...",
      "groundingCount": 2,
      "parameters": [
        {
          "variable": "?embodiedAgent",
          "type": "EmbodiedAgent",
          "typeUri": "http://pst.istc.cnr.it/ontologies/2026/metacognition#EmbodiedAgent",
          "roles": ["canBePerformedBy"]
        }
      ],
      "alignmentConstraints": [
        {
          "association": "assertsAlignment",
          "alignmentType": "RegionChangeAlignment",
          "alignmentTypeUri": "http://pst.istc.cnr.it/ontologies/2026/metacognition#RegionChangeAlignment",
          "objects": ["?robotEmbodiment"],
          "qualities": ["?pose"],
          "values": [],
          "initialValues": ["?initialDiscretePoseRegion"],
          "resultingValues": ["?resultingDiscretePoseRegion"]
        }
      ],
      "preconditions": [
        {"object":"?robotEmbodiment","quality":"?pose","value":"?initialDiscretePoseRegion"}
      ],
      "positiveEffects": [
        {"object":"?robotEmbodiment","quality":"?pose","value":"?resultingDiscretePoseRegion"}
      ],
      "negativeEffects": [
        {"object":"?robotEmbodiment","quality":"?pose","value":"?initialDiscretePoseRegion"}
      ]
    }
  ]
}
```

Schemas are grouped by typed relational and alignment structure, not Function type
alone. `signatureId` is deterministic and is the key used by the grounding endpoint.

### Inspect the groundings of one schema

Use a signature returned by `/abstraction`:

```bash
curl -s "$BASE/abstraction/sha256:.../groundings"
```

Example response shape:

```json
{
  "schema": {"functionType":"MoveTo","signatureId":"sha256:..."},
  "groundings": [
    {
      "id": "_:ruleGeneratedFunction",
      "kind": "blankNode",
      "triples": [
        {
          "subject": "MoveTo",
          "predicate": "hasTarget",
          "predicateUri": "http://pst.istc.cnr.it/ontologies/2019/01/soho#hasTarget",
          "object": "RobotEmbodiment",
          "objectKind": "blankNode"
        }
      ]
    }
  ]
}
```

The actual `schema` object is complete and has the same structure returned by
`/abstraction`. Grounding triples are a concise planning-relevant inferred subgraph,
not the complete RDFS closure. Rule-created resources may be blank nodes. An unknown
signature returns HTTP 400.

### Validation errors

Domain validation errors return HTTP 400 with a JSON body:

```json
{"message":"The predicate must be an absolute URI"}
```

Examples include a missing triple, a non-`SELECT` SPARQL query, an unknown schema
signature, an invalid URI, or attempting to replace/remove a statement outside the
editable ABox. Framework-level errors such as malformed JSON use Spring Boot's
standard error response.

## Integration and usage

### Typical external-client workflow

1. Optionally reset the bundled example with `POST /knowledge/abox/reset`.
2. Read `GET /knowledge/abox` and retain exact RDF terms, especially blank-node
   identifiers and literal datatype/language fields.
3. Submit one or more add, replace, or remove requests. Each response should report
   `inferenceDirty: true`.
4. Call `POST /reasoning/refresh` once after the edit batch.
5. Call `GET /abstraction` and select schemas by `signatureId`.
6. Call `/abstraction/{signatureId}/groundings` when the contributing inferred
   Function individuals or triples are needed.
7. Use `/sparql/select` for additional raw or inferred read-only queries.

The browser tools implement this same flow: the ABox editor mutates assertions and
marks inference stale; the refresh button rebinds reasoning; the inspection page
reads resulting schemas and groundings.

### Minimal JavaScript integration

```javascript
const api = "http://localhost:8080/metacognition/api";

await fetch(`${api}/knowledge/abox/reset`, { method: "POST" });

const graph = await fetch(`${api}/knowledge/abox`).then(response => response.json());
console.log(graph.triples, graph.resourceLabels, graph.inferenceDirty);

// After any ABox mutation:
await fetch(`${api}/reasoning/refresh`, { method: "POST" });

const abstraction = await fetch(`${api}/abstraction`).then(response => response.json());
for (const schema of abstraction.schemas) {
  console.log(schema.functionType, schema.signatureId, schema.groundingCount);
}
```

### Deployment and integration constraints

- Knowledge is in-memory. Restarting the process discards editor changes.
- Resetting the example ABox deliberately discards current in-memory ABox changes.
- Blank-node identifiers are valid only for the current model instance. Re-read the
  ABox after a reset or application restart rather than caching them permanently.
- There is no authentication, authorization, CORS policy, persistence layer, or
  multi-user conflict control in the current application. Use a trusted network or
  place the service behind an appropriate gateway before remote deployment.
- The API is intended for controlled scenario editing, reasoning inspection, and
  planning-model generation. It is not a general unrestricted RDF store.
- No endpoint executes robot Functions or applies schema effects to factual state.

## Extending cognitive functionality

The safest extension workflow is ontology first, rule second, abstraction third.

### 1. Extend the ontology

Add or refine Function, Belief Alignment/Diagnosis, Quality, Region, capability, and role
definitions in the current `onto/mc_v*.rdf` file. Reuse existing ontology namespaces
and properties where their semantics apply. Update `MetacognitionDictionary` when a
new namespace is introduced or Java code genuinely needs a new constant.

Keep these levels separate:

- the TBox defines intensional concepts and restrictions;
- the asserted ABox describes the current world and robot state;
- production-rule conclusions describe feasible Function meta-knowledge;
- action-schema effects describe possible state transitions, not current facts.

### 2. Add or revise Jena rules

Rules belong in `onto/cognition.rules`. Their antecedents inspect the current ABox;
their conclusions may create contextual Function and Belief Alignment resources. Rule-created
resources may use `makeTemp`, because the abstraction groups them structurally.
This is the single production-rule source loaded by `RuleReasoner`, through the
`mc.rules-path` setting and the `src/main/resources/onto` link. In particular,
`inferPickGraspable` is defined there; Java scenario-loading code only asserts its
ABox premises and never creates inferred `PickGraspable` individuals. Application
startup fails if the configured rule file is missing or empty.

A change alignment should connect all identities needed by the schema rather than rely
on Java-side intuition. For `RegionChangeAlignment`, provide:

```text
Function -> Belief Alignment
Belief Alignment -> concernsObject -> object
Belief Alignment -> concernsQuality -> quality
Belief Alignment -> hasInitialValue -> initial Region
Belief Alignment -> hasResultingValue -> resulting Region
```

Do not write a rule that directly applies the resulting Region to the current
quality merely to express an action effect.

### 3. Extend abstraction deliberately

If a new Belief Alignment subtype has different transition semantics, update
`KnowledgeAbstractionService` explicitly. Keep graph traversal out of controllers,
reuse `ContextReasonerService.readInferredModel`, preserve grounded-resource identity
when assigning variables, filter overly generic inferred types consistently, and
sort every public collection used by schema signatures.

Do not create another Jena model or reasoner in the REST layer. Do not expose Jena
`Resource`, `Model`, `Statement`, iterators, or result sets through Jackson; extend
the API records instead.

### 4. Update inspection and tests

Update `abstraction_inspection.html` when a new semantic construct needs a concise
visual representation. Keep that page read-only and derive its content from the
API. Use `knowledge_editor.html` only for controlled scenario ABox editing.

At minimum, add tests for:

- multiple identity-distinct groundings collapsing into one structural schema;
- different structures of the same Function type producing different schemas;
- identity reuse across Function and Belief Alignment roles;
- the new alignment's precondition/effect mapping;
- deterministic output;
- read-only abstraction behavior;
- REST response compatibility; and
- integration with the actual ontology and production-rule files.

Run `./mvnw test` and `./mvnw package` before committing ontology/rule or Java
changes together.

## Current boundaries

- The model is in-memory; persistence and a general external ABox ingestion API are
  not currently provided.
- The public SPARQL endpoint is intentionally read-only and accepts only `SELECT`;
  state changes use the dedicated ABox editor endpoints.
- The abstraction supports the planning-relevant Function and Belief Alignment relations
  implemented by the current ontology. New semantic patterns should be added
  explicitly rather than inferred heuristically.
- The application currently has no authentication layer; deploy it behind suitable
  access controls if it is exposed beyond a trusted development environment.
