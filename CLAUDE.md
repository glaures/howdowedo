### Tech stack

Java, Spring Boot, MySQL, Thymeleaf

### Self-Improvement Loop
- After ANY correction from the user: update `tasks/lessons.md` with the pattern
- Write rules for yourself that prevent the same mistake
- Ruthlessly iterate on these lessons until the mistake rate drops
- Review lessons at session start for a project

### Verification Before Done
- Never mark a task complete without proving it works
- Diff behavior between main and your changes when relevant
- Ask yourself: "Would a staff engineer approve this?"
- Run tests, check logs, demonstrate correctness

### Demand Elegance (Balanced)
- For non-trivial changes: pause and ask "is there a more elegant way?"
- If a fix feels hacky: "Knowing everything I know now, implement the elegant solution"
- Skip this for simple, obvious fixes. Don't overengineer
- Challenge your own work before presenting it

## Core Principles
- **Simplicity First**: Make every change as simple as possible. Impact minimal code
- **No Laziness**: Find root causes. No temporary fixes. Senior developer standards

## Project General Instructions

- Always use the latest versions of dependencies.
- Always write Java code as the Spring Boot application.
- Always create application-*.yml files for configurtatoin that uses the local .env file for sensitive runtime parameters (secrets, etc.)
- Always use Maven for dependency management.
- Always create test cases for the generated code both positive and negative.
- Minimize the amount of code generated.
- Use `sandbox27.howarewedoing` as the group ID for the Maven project and base Java package.
- Do not use the Lombok library.
- Generate the Docker Compose file to run all components used by the application.
- use a domain-based folder structure that contains entities, repositores, services and dtos for the respective domains
- use Java records for DTOs
- use a common/errors folder for common Exceptions and handlers
