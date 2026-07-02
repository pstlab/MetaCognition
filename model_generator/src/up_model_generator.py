import argparse
from dataclasses import dataclass
from pathlib import Path

from unified_planning.io import PDDLWriter
from unified_planning.shortcuts import (
    BoolType,
    Equals,
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


def build_navigation_problem(
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
    can_move_to = Fluent(
        "can_move_to", BoolType(), agent=agent_type, location=location_type
    )

    move = InstantaneousAction(
        "move",
        agent=agent_type,
        source=location_type,
        target=location_type,
    )
    agent_parameter = move.parameter("agent")
    source_parameter = move.parameter("source")
    target_parameter = move.parameter("target")
    move.add_precondition(at(agent_parameter, source_parameter))
    move.add_precondition(can_move_to(agent_parameter, target_parameter))
    move.add_precondition(Not(Equals(source_parameter, target_parameter)))
    move.add_effect(at(agent_parameter, source_parameter), False)
    move.add_effect(at(agent_parameter, target_parameter), True)

    problem = Problem("metacognition_navigation")
    problem.add_fluent(at, default_initial_value=False)
    problem.add_fluent(can_move_to, default_initial_value=False)
    problem.add_action(move)

    agent = Object(agent_name, agent_type)
    locations = {
        name: Object(name, location_type)
        for name in agent_locations
    }
    problem.add_object(agent)
    problem.add_objects(locations.values())

    for location in locations.values():
        problem.set_initial_value(can_move_to(agent, location), True)
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
    return build_navigation_problem(
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
        description="Generate a simple Unified Planning navigation problem."
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
