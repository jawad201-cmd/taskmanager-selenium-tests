# Task Manager — Selenium Test Suite

Headless Chrome Selenium tests for the Task Manager web application, designed to run inside the markhobson/maven-chrome Docker container as a stage of the Jenkins pipeline.

## Stack
Java 17, Maven, Selenium 4.21.0, JUnit 5.10.2, headless Chrome.

## Running locally
mvn -B test -Dapp.url=http://localhost:8081

## Running in Docker (the way Jenkins runs it)
docker run --rm --network host -v "$(pwd):/work" -w /work -e APP_URL=http://localhost:8081 markhobson/maven-chrome:latest mvn -B test
