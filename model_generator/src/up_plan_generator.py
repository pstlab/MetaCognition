"""
POC: Generate a plan using UP's OneshotPlanner.

This demonstrates using UP to not just build the planning problem,
but also to solve it using a real planner (Fast Downward or Tamer)
and produce a validated plan.
"""

import argparse
from dataclasses import dataclass

import unified_planning as up
from unified_planning.shortcuts import OneshotPlanner, PlanValidator, Problem

# Suppress UP engine credits output
up.shortcuts.get_environment().credits_stream = None

from up_model_generator_grounded import (
    MOVE_CAPABILITIES_QUERY,
    MoveCapability,
    parse_move_capabilities,
    build_grounded_navigation_problem,
    ContextReasonerClient,
    CONTEXT_REASONER_URL,
)


@dataclass
class PlanResult:
    plan: "up.plans.SequentialPlan"
    plan_length: int


def solve(problem: Problem) -> PlanResult:
    """
    Solve the planning problem using UP's OneshotPlanner.
    
    Returns the plan found by the planner.
    """
    # Get a planner that supports the problem's features
    with OneshotPlanner(problem_kind=problem.kind) as planner:
        print(f"Using planner: {planner.name}")
        
        # Solve the problem
        result = planner.solve(problem)
        
        # Check if a plan was found (SOLVED or SOLVED_SATISFICING)
        if result.status.name in ("SOLVED", "SOLVED_SATISFICING"):
            print(f"Plan found with {len(result.plan.actions)} action(s)")
            return PlanResult(
                plan=result.plan,
                plan_length=len(result.plan.actions),
            )
        else:
            raise RuntimeError(
                f"Planner returned status: {result.status.name}. "
                f"No plan found."
            )


def validate(problem: Problem, plan) -> bool:
    """
    Validate the plan using UP's PlanValidator.
    """
    with PlanValidator(name="sequential_plan_validator") as validator:
        result = validator.validate(problem, plan)
        return bool(result)


def generate_and_solve(
    sparql_url: str,
    agent_name: str,
    initial_location_name: str,
    goal_location_name: str,
) -> tuple[Problem, PlanResult]:
    """
    Build the planning problem from the knowledge graph and solve it.
    """
    # Query the knowledge graph for move capabilities
    query_result = ContextReasonerClient(sparql_url).query(MOVE_CAPABILITIES_QUERY)
    capabilities = parse_move_capabilities(query_result)
    
    # Build the grounded planning problem
    problem = build_grounded_navigation_problem(
        capabilities,
        agent_name,
        initial_location_name,
        goal_location_name,
    )
    
    # Solve the problem
    result = solve(problem)
    
    return problem, result


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Generate and solve a grounded navigation planning problem."
    )
    parser.add_argument(
        "--sparql-url",
        default=CONTEXT_REASONER_URL,
        help="SPARQL endpoint URL",
    )
    parser.add_argument("--agent", default="mobipick")
    parser.add_argument("--initial", default="base_home")
    parser.add_argument("--goal", default="base_table1")
    args = parser.parse_args()
    
    print("Building planning problem from knowledge graph...")
    problem, result = generate_and_solve(
        args.sparql_url,
        args.agent,
        args.initial,
        args.goal,
    )
    
    print("\n=== Problem ===")
    print(problem)
    
    print("\n=== Plan ===")
    print(f"Plan length: {result.plan_length}")
    print("\nActions:")
    for i, action_instance in enumerate(result.plan.actions, 1):
        print(f"  {i}. {action_instance.action.name}")
    
    print("\n=== Plan Validation ===")
    is_valid = validate(problem, result.plan)
    print(f"Plan is valid: {is_valid}")


if __name__ == "__main__":
    main()
