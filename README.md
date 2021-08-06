# CSC4006 | Research and Development Project

This command-line tool mines the version history of a Git repository, and for each change period (sequence of commits of given length) in its evolution:

* computes the source code change entropy
* counts the occurrences of each refactoring type applied

The project uses the Gradle build tool and is supported by a GitLab CI pipeline.

### How to Run

Obtain the application JAR by either:

* Running `./gradlew shadow` in the project root to build the JAR, which will be created at `build/libs/csc4006-project-1.0-SNAPSHOT-all.jar`

Or

* Downloading it as an artifact from a successful pipeline run on GitLab

Run the application using a command such as:

```
java -jar csc4006-project-1.0-SNAPSHOT-all.jar \
   --repository-path "path/to/git/repository" \
   --period-length 100 \
   --mode 1
   ```
