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
- abstract grounded Functions into deterministic, typed action schemas;
- inspect the Function and Fluent subgraphs supporting each schema; and
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
- `requiresFluent`, `assertsFluent`, and `negatesFluent` — diagnostic constraints.

Fluents are reified Diagnosis concepts. They identify the object and quality under
consideration through `concernsObject` and `concernsQuality`, and constrain values
through Fluent-specific properties.

The currently supported change diagnosis is `RegionChangeFluent`. Its
`hasInitialValue` and `hasResultingValue` describe a Region transition for the same
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
  -> Fluent/Diagnosis constraints
  -> contextual action schemas
```

Schemas are not grouped by Function type alone. The abstraction also considers the
planning-relevant roles, the most specific useful types of their participants, and
the Fluent structure. Consequently, two `MoveTo` individuals with equivalent
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
- `MetacognitionRestController` is a thin HTTP layer returning explicit DTO records,
  never raw Jena objects.
- The static web pages consume the REST API and remain read-only, except for the
  explicit scenario-loading action available from the SPARQL interface.

## Technology

- Java 17
- Spring Boot 4.0.5 and Spring Web MVC
- Apache Jena 5.1.0 (`Model`, `InfModel`, SPARQL, RDFS, and production rules)
- Jackson JSON serialization
- Maven
- JUnit 5, AssertJ, Spring MockMvc, and Mockito for tests
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

The inspection interface intentionally shows planning-relevant subgraphs and the
most specific useful types instead of serializing the complete inferred closure.

## REST API

All endpoints use the `/metacognition/api` prefix.

### Query knowledge

```http
POST /metacognition/api/sparql/select
Content-Type: application/json

{
  "sparql": "SELECT ?function WHERE { ?function a <http://pst.istc.cnr.it/ontologies/2026/metacognition#MoveTo> }",
  "raw": false
}
```

Only SPARQL `SELECT` is accepted. `raw` defaults to `false`, which queries the
inferred model. Results use the standard SPARQL Results JSON format.

### Obtain contextual action schemas

```http
GET /metacognition/api/abstraction
```

The endpoint refreshes and reads the current inferred model, but does not modify the
knowledge graph. A schema includes its Function type, deterministic signature ID,
grounding count, typed parameters, Fluent constraints, preconditions, positive
effects, and negative effects.

Example fragment:

```json
{
  "schemas": [
    {
      "functionType": "MoveTo",
      "signatureId": "sha256:...",
      "groundingCount": 2,
      "fluentConstraints": [
        {
          "association": "assertsFluent",
          "fluentType": "RegionChangeFluent",
          "object": "?target",
          "quality": "?pose",
          "initialValue": "?initialDiscretePoseRegion",
          "resultingValue": "?resultingDiscretePoseRegion"
        }
      ],
      "preconditions": [
        { "object": "?target", "quality": "?pose", "value": "?initialDiscretePoseRegion" }
      ],
      "positiveEffects": [
        { "object": "?target", "quality": "?pose", "value": "?resultingDiscretePoseRegion" }
      ],
      "negativeEffects": [
        { "object": "?target", "quality": "?pose", "value": "?initialDiscretePoseRegion" }
      ]
    }
  ]
}
```

### Inspect schema groundings

```http
GET /metacognition/api/abstraction/{signatureId}/groundings
```

Returns the selected schema plus each inferred Function individual that implements
it and its planning-relevant triples. Temporary rule-generated resources may appear
as blank nodes; schema equality does not depend on those identifiers.

### Load the bundled example scenario

```http
POST /metacognition/api/load/mobipick/uc1
```

This is the only endpoint above that changes the raw knowledge model. It asserts the
bundled Mobipick UC1 environment and robot description and should be used as demo
data, not as a general-purpose ingestion API.

## Extending cognitive functionality

The safest extension workflow is ontology first, rule second, abstraction third.

### 1. Extend the ontology

Add or refine Function, Fluent/Diagnosis, Quality, Region, capability, and role
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
their conclusions may create contextual Function and Fluent resources. Rule-created
resources may use `makeTemp`, because the abstraction groups them structurally.

A change Fluent should connect all identities needed by the schema rather than rely
on Java-side intuition. For `RegionChangeFluent`, provide:

```text
Function -> Fluent
Fluent -> concernsObject -> object
Fluent -> concernsQuality -> quality
Fluent -> hasInitialValue -> initial Region
Fluent -> hasResultingValue -> resulting Region
```

Do not write a rule that directly applies the resulting Region to the current
quality merely to express an action effect.

### 3. Extend abstraction deliberately

If a new Fluent subtype has different transition semantics, update
`KnowledgeAbstractionService` explicitly. Keep graph traversal out of controllers,
reuse `ContextReasonerService.readInferredModel`, preserve grounded-resource identity
when assigning variables, filter overly generic inferred types consistently, and
sort every public collection used by schema signatures.

Do not create another Jena model or reasoner in the REST layer. Do not expose Jena
`Resource`, `Model`, `Statement`, iterators, or result sets through Jackson; extend
the API records instead.

### 4. Update inspection and tests

Update `abstraction_inspection.html` when a new semantic construct needs a concise
visual representation. Keep the page read-only and derive its content from the API.

At minimum, add tests for:

- multiple identity-distinct groundings collapsing into one structural schema;
- different structures of the same Function type producing different schemas;
- identity reuse across Function and Fluent roles;
- the new Fluent's precondition/effect mapping;
- deterministic output;
- read-only abstraction behavior;
- REST response compatibility; and
- integration with the actual ontology and production-rule files.

Run `./mvnw test` and `./mvnw package` before committing ontology/rule or Java
changes together.

## Current boundaries

- The model is in-memory; persistence and a general external ABox ingestion API are
  not currently provided.
- The public SPARQL endpoint is intentionally read-only and accepts only `SELECT`.
- The abstraction supports the planning-relevant Function and Fluent relations
  implemented by the current ontology. New semantic patterns should be added
  explicitly rather than inferred heuristically.
- The application currently has no authentication layer; deploy it behind suitable
  access controls if it is exposed beyond a trusted development environment.
