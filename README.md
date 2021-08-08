# CSC4006 | Research and Development Project

This command-line tool measures the source code change entropy observed throughout the evolution of a Git repository.

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

### Modes

There are seven modes in which the application can run:

1. Record entropy for each period
2. Record file entropy for each period
3. Record file entropy for each period and mark periods in which file was refactored
4. Record file entropy for each period in which file was changed
5. Record file entropy for each period in which file was changed and mark periods in which file was refactored
6. Record percentage change in file entropy between periods in which file was changed but not refactored
7. Record percentage change in mean file entropy (across periods in which file was changed but not refactored) before and after periods in which file was refactored

### Filters

The filters config file `filters-config.yaml` allows the files considered during analysis to be filtered by:

1. specifying the only file types to include (e.g., `.java` files only)
2. specifying the file path patterns to exclude (e.g., all paths containing `Test.java`)
3. specifying the only refactoring types to include

Simply modify the list of items under each of these filter groups in the file.
