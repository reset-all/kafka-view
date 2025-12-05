FROM eclipse-temurin:17-jre
WORKDIR /app
# Copy the built jar from the builder stage
COPY --from=builder /workspace/target/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]

