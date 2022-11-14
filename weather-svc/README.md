# weather-svc
Weather Service - A Functional Programming exercise

This folder contains a Scala 2.13 project, based on the http4s-io giter8 project skeleton.

This weather-svc serves weather forecasts using information fetched from http://api.weather.gov.
This service is implemented using http4s, following the structure of the http4s-io example project.
This project may be built and run using sbt.

This software is written for Scala 2.13.  It has been tested with
    JDK 11 (Graal-VM)
    SBT 1.7.3
    Operating System:  Windows 10

One reasonable sequence to build and run is:
  sbt clean compile
  sbt run
Once the server is running you may access it at the URLs described below.

You may also run some provided tests (which do not require that our service is running) using
  sbt test
Note that these tests access the backend api.weather.gov service.

Our service launches on port 8088.  Changing this port requires modifying the scala code in 
AppServer.scala (specifically AppServerBuilder.makeServerResourceForHttpApp).

The weather-svc offers two different URL endpoints, which may be accessed using HTTP GET.
These endpoints expect different formats for the latitude+longitude arguments.

    Path argument example
    http://localhost:8080/check-weather-wpath/40.2222,-97.0997

    Query param arguments example
    http://localhost:8088/check-weather-wquery?lat=40.2222&lon=-97.0997

### IMPLEMENTATION
To fetch weather info from backend api.weather.gov, we use a sequence of two calls
to the "points" and then "gridpoints" services.  These services are accessed by URLs
with coordinates in the URI-PATH, not in query parameters.
Example URL for the "points" service, with lat,long at the end of the PATH:
https://api.weather.gov/points/39.7456,-97.0892
In the JSON results of that service we find
https://api.weather.gov/gridpoints/TOP/31,80/forecast


One problem we face is that this gridpoints service often fails, for reasons we have
not analyzed.   When these failures occur, we attempt to return a useful error message to 
our API user (as a JSON-serialized Msg_WeatherReport), as well as logging information to our
service console.

### NOTES ON CODE STYLE
Limited use of scala wildcard imports.  Usually we try to confine these imports to use
within a narrow scope, such as a particular trait or method.

Limited use of scala "object" singletons.

General preference for explicit flatMaps or kleislis, over for-comprehensions.

#### Naming Conventions
    Msg_Xyz : case classes used in building responses have names starting with "Msg_"
    JsonEncoder_Xyz : Json encoding contexts start with "JsonEncoder_" or "JsonDecoder_"
