# weather-svc
Weather Service - A Functional Programming exercise

This folder contains a Scala 2.13 project, based on the http4s-io giter8 project skeleton.

This weather-svc serves weather forecasts using information fetched from http://api.weather.gov.
This service is implemented using http4s, following the structure of the http4s-io example project.
This project may be built and run using sbt.

One reasonable sequence to build and run is:
  sbt clean compile
  sbt run

This software has been tested with
    JDK 11 (Graal-VM)
    SBT 1.7.3
    Operating System:  Windows 10

This service launches on port 8088.  Changing this port requires modifying the scala code in 
AppServer.scala (specifically AppServerBuilder.mkServerResourceForHttpApp).

The weather-svc offers two different URL endpoints, which may be accessed using HTTP GET.
These endpoints expect different formats for the 

    Path argument example
    http://localhost:8080/check-weather-wpath/40.2222,-97.0997

    Query param arguments example
    http://localhost:8088/check-weather?lat=40.2222&lon=-97.0997

To fetch weather info from backend api.weather.gov, we use a sequence of two calls
to the "points" and then "gridpoints" services.  These services are accessed by URLs
with coordinates in the URI-PATH, not in query parameters.
Example URL for the "points" service, with lat,long at the end of the PATH:
https://api.weather.gov/points/39.7456,-97.0892
In the JSON results of that service we find
https://api.weather.gov/gridpoints/TOP/31,80/forecast


One problem we face is that this gridpoints service fails for many locations.
When these failures occur, we should return a useful error message to our end user.
In this toy API implementation, our goal is simply to report all the technical 
details we have available.


Notable differences
    Limited use of scala wildcard imports
    Limited use of scala "object" singletons
    Preference for explicit flatMaps or kleislis, over for-comprehensions
    Naming Conventions
        Msg_Xyz : case classes used in building responses have names starting with "Msg_"
        JsonEnc_Xyz : Json encoding contexts start with "JsonEnc_"

// TODO:  Allow user to supply lat-long in different shapes, e.g. as two Floats, Decimals, or Strings.