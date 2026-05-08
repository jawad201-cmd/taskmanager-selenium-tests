# Task Manager — Selenium Test Suite

Headless Chrome Selenium tests for the Task Manager web application, designed to run inside the `markhobson/maven-chrome` Docker container as a stage of the Jenkins pipeline.

## Stack

- Java 17
- Maven
- Selenium Java 4.21.0
- JUnit Jupiter 5.10.2
- Headless Chrome (provided by `markhobson/maven-chrome`)

## Test cases (19 total)

Numbered 01 through 19. They cover page load, DOM presence, CRUD against the task list, persistence across reload, special-character handling, and the `/health` endpoint.

## Running locally

```bash
mvn -B test -Dapp.url=http://localhost:8081
```

## Running in Docker (the way Jenkins runs it)

```bash
docker run --rm \
  --network host \
  -v "$(pwd):/work" \
  -w /work \
  -e APP_URL=http://localhost:8081 \
  markhobson/maven-chrome:latest \
  mvn -B test
```

## Reading test reports

After a run, JUnit XML reports land in `target/surefire-reports/`. Jenkins picks them up via the `junit` step.

## Required selectors on the application

These tests assume the following stable IDs and classes exist on the Task Manager frontend:

- `#appHeading` — application heading
- `#taskInput` — text input for new task
- `#addBtn` — add button
- `#taskList` — list container
- `.task-item` — individual task wrapper inside the list
- `.task-text` — span/div showing the task text
- `.done-btn` — toggle-done button
- `.delete-btn` — delete button
- `.done` — class added to a `.task-item` once marked done
