FROM ubuntu:18.04
#FROM openjdk

RUN apt-get update && apt-get install -yq --no-install-recommends openjdk-11-jdk && apt-get clean
RUN apt-get update && apt-get install -yq --no-install-recommends gradle && apt-get clean

RUN mkdir /code
WORKDIR /code
ADD . /code

#RUN ./gradlew  build
# compile the code
#RUN ./gradlew  assemble
