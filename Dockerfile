FROM java:8
WORKDIR /
ADD HTTPServer.jar HTTPServer.jar
EXPOSE 9999
CMD java -jar HTTPServer.jar
