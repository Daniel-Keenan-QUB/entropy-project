# CSC4006 | Research and Development Project

This repository holds the source code for the application developed in support of my project entitled _An Investigation of the Effects of Refactoring on Software Entropy._

The application is a software tool which mines the version history of a Git repository and for each change period in its evolution:

* computes the source code change entropy
* detects and counts the refactorings applied

The project uses the Gradle build tool and features a GitLab CI pipeline.

### How to Run

Obtain the application JAR by either:

* Running `./gradlew shadow` in the project root to build the JAR, which will be created in `build/libs/csc4006-project-1.0-SNAPSHOT-all.jar`

Or

* Downloading it as an artifact from a successful pipeline run on GitLab

Run the application using a command such as:

```
java -jar csc4006-project-1.0-SNAPSHOT-all.jar \
   --repository-path "path/to/git/repository" \
   --change-period-size 100 \
   --file-type-whitelist "java scala"
   ```
