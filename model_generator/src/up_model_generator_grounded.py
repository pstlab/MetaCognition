import argparse
from dataclasses import dataclass
from pathlib import Path

from unified_planning.io import PDDLWriter
from unified_planning.shortcuts import (
    BoolType,
    Fluent,
    InstantaneousAction,
    Not,
    Object,
    Problem,
    UserType,
)

from sparql_client import CONTEXT_REASONER_URL, ContextReasonerClient


MOVE_CAPABILITIES_QUERY = """
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX soho: <http://pst.istc.cnr.it/ontologies/2019/01/soho#>
PREFIX mc: <http://pst.istc.cnr.it/ontologies/2026/metacognition#>

SELECT ?aLabel ?lLabel
WHERE {
  ?f rdf:type mc:MoveTo .
  ?f soho:hasTarget ?l .
  ?l soho:hasLabel ?lLabel .
  ?f soho:canBePerformedBy ?a .
  ?a soho:hasLabel ?aLabel .
}
"""


@dataclass(frozen=True)
class MoveCapability:
    agent: str
    location: str


def parse_move_capabilities(query_result: dict) -> set[MoveCapability]:
    if "error" in query_result:
        reason = query_result.get("reason")
        details = f" ({reason})" if reason else ""
        raise RuntimeError(
            f"SPARQL query failed: {query_result['error']}{details}"
        )

    try:
        bindings = query_result["results"]["bindings"]
        capabilities = {
            MoveCapability(
                agent=binding["aLabel"]["value"],
                location=binding["lLabel"]["value"],
            )
            for binding in bindings
        }
    except (KeyError, TypeError) as error:
        raise ValueError("Invalid SPARQL result for move capabilities") from error

    if not capabilities:
        raise ValueError("The knowledge graph returned no move capabilities")

    return capabilities


def build_grounded_navigation_problem(
    capabilities: set[MoveCapability],
    agent_name: str,
    initial_location_name: str,
    goal_location_name: str,
) -> Problem:
    agent_locations = sorted(
        capability.location
        for capability in capabilities
        if capability.agent == agent_name
    )
    if not agent_locations:
        raise ValueError(f'Unknown agent or no move capabilities: "{agent_name}"')

    available_locations = set(agent_locations)
    for role, location_name in (
        ("initial", initial_location_name),
        ("goal", goal_location_name),
    ):
        if location_name not in available_locations:
            raise ValueError(
                f'Unknown {role} location for agent "{agent_name}": "{location_name}"'
            )

    agent_type = UserType("Agent")
    location_type = UserType("Location")

    at = Fluent("at", BoolType(), agent=agent_type, location=location_type)

    agent = Object(agent_name, agent_type)
    locations = {
        name: Object(name, location_type)
        for name in agent_locations
    }

    # Grounded actions: one per MoveTo instance (agent, target) from the
    # knowledge graph. The KG carries no source location, so each action
    # clears every other location (deleting an absent fact is a no-op) and
    # sets the target. The move capability is encoded by the action's
    # existence, so no separate can_move_to fluent is needed.
    actions = []
    for target_name, target in locations.items():
        move = InstantaneousAction(f"move_{agent_name}_to_{target_name}")
        move.add_precondition(Not(at(agent, target)))
        for other_name, other in locations.items():
            if other_name != target_name:
                move.add_effect(at(agent, other), False)
        move.add_effect(at(agent, target), True)
        actions.append(move)

    problem = Problem("metacognition_navigation_grounded")
    problem.add_fluent(at, default_initial_value=False)
    problem.add_object(agent)
    problem.add_objects(locations.values())
    for action in actions:
        problem.add_action(action)

    problem.set_initial_value(at(agent, locations[initial_location_name]), True)
    problem.add_goal(at(agent, locations[goal_location_name]))

    return problem


def generate_problem(
    sparql_url: str,
    agent_name: str,
    initial_location_name: str,
    goal_location_name: str,
) -> Problem:
    query_result = ContextReasonerClient(sparql_url).query(MOVE_CAPABILITIES_QUERY)
    capabilities = parse_move_capabilities(query_result)
    return build_grounded_navigation_problem(
        capabilities,
        agent_name,
        initial_location_name,
        goal_location_name,
    )


def write_pddl(problem: Problem, domain_output: Path, problem_output: Path) -> None:
    writer = PDDLWriter(problem)
    writer.write_domain(str(domain_output))
    writer.write_problem(str(problem_output))


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Generate a grounded Unified Planning navigation problem "
        "with one action per MoveTo instance from the knowledge graph."
    )
    parser.add_argument(
        "--sparql-url",
        "--fuseki-url",
        dest="sparql_url",
        default=CONTEXT_REASONER_URL,
    )
    parser.add_argument("--agent", default="mobipick")
    parser.add_argument("--initial", default="base_home")
    parser.add_argument("--goal", default="base_table1")
    parser.add_argument("--domain-output", type=Path, default=Path("domain.pddl"))
    parser.add_argument("--problem-output", type=Path, default=Path("problem.pddl"))
    args = parser.parse_args()

    problem = generate_problem(
        args.sparql_url,
        args.agent,
        args.initial,
        args.goal,
    )
    write_pddl(problem, args.domain_output, args.problem_output)

    print(problem)
    print(f"Wrote PDDL domain to {args.domain_output}")
    print(f"Wrote PDDL problem to {args.problem_output}")


if __name__ == "__main__":
    main()
