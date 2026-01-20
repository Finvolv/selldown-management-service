# Build stage
FROM 612359323314.dkr.ecr.ap-south-1.amazonaws.com/mvn-build:3-openjdk-17 AS BUILD

ARG APP_VERSION PASSWORD USERNAME ARTIFACTORY_PATH
ENV USERNAME=$USERNAME PASSWORD=$PASSWORD ARTIFACTORY_PATH=$ARTIFACTORY_PATH

ENV PROJECT_DIR /app
WORKDIR $PROJECT_DIR

# Copy the project source
COPY . $PROJECT_DIR

RUN mvn versions:set -DnewVersion=$APP_VERSION
RUN mvn clean deploy -Dmaven.test.skip=true

# Final stage
FROM 612359323314.dkr.ecr.ap-south-1.amazonaws.com/java:eclipse-17 AS RUN

ENV PROJECT_DIR /app
WORKDIR $PROJECT_DIR

ENV JAR_FILE selldown-management-service*.jar

COPY --from=BUILD $PROJECT_DIR/target/$JAR_FILE .

# Expose the port your application runs on
EXPOSE 8080

# Command to run your application
CMD java $JAVA_OPTS -jar $PROJECT_DIR/$JAR_FILE