# CSC4006 | Research and Development Project

This command-line tool mines the Git version history of a Java-based system to compute the source code change entropy over, and detect the refactorings applied within, each period (sequence of commits) of its evolution.

The project uses the Gradle build tool and is supported by a GitLab CI pipeline.

### How to Run

Obtain the application JAR file by doing one of the following:

* Running `./gradlew shadow` in the project root to build the JAR file, which will be created at `build/libs/csc4006-project-1.0-SNAPSHOT-all.jar`

* Downloading the JAR file as an artifact from a successful pipeline run on GitLab

Run the application using a command such as:

```
java -jar csc4006-project-1.0-SNAPSHOT-all.jar \
   --repository-path "path/to/git/repository" \
   --period-length 100 \
   --mode 1
   ```

Results data will be written to `results.csv`

### Modes

There are seven modes in which the application can run:

1. Record the entropy of each period
2. Record the entropy of each file in each period
3. Record the entropy of each file in each period and mark periods in which the file was refactored
4. Record the entropy of each file in each period in which the file was changed
5. Record the entropy of each file in each period in which the file was changed and mark periods in which the file was refactored
6. Record the percentage change in the entropy of each file between periods in which the file was changed but not refactored
7. Record the percentage change in mean entropy of each file across periods in which the file was changed but not refactored, before and after periods in which the file was refactored

### Filters

The application supports filtering of files to consider in its analysis. The following filter types are supported:

* File types to include (e.g., `.java` files only)
* File path patterns to exclude (e.g., paths containing `Test.java`)
* Refactoring types to include

To customise filters, simply modify the `filters-config.yaml` file.
