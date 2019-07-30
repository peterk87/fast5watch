FROM openjdk:8-alpine

COPY target/uberjar/fast5watch.jar /fast5watch/app.jar

EXPOSE 3000

CMD ["java", "-jar", "/fast5watch/app.jar"]
