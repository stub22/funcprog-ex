# weather-svc

## Weather Service - A functional programming exercise

This folder contains a Scala 2.13 project, based on the http4s-io giter8 project skeleton.

This weather-svc serves brief summary weather forecasts, using information fetched from this 
[National Weather Service API](https://www.weather.gov/documentation/services-web-api) 

This software is written for Scala 2.13.  It has been tested with
 * JDK 11 (Graal-VM)
 * SBT 1.7.3
 * Operating System:  Microsoft Windows 10

### BUILDING AND RUNNING

This project may be built and run using sbt.

One reasonable sequence to build and run is:

`sbt clean compile`

`sbt run`

Once the server is running you may access it at the URLs described below.

You may also run some provided tests (which do not require that our service is running) using

`sbt test`

Note that these tests access the backend api.weather.gov service.

### ACCESSING THE SERVICE

Our service launches on port 8088.  Changing this port requires modifying the scala code in 
AppServer.scala (specifically `AppServerBuilder.makeServerResourceForHttpApp`).

The weather-svc offers two different URL endpoints, which may be accessed using HTTP GET.

These endpoints expect different formats for the latitude+longitude arguments.

We have not yet attempted to determine how many digits of precision can be supplied without causing an error.

    Path argument example
    http://localhost:8080/check-weather-wpath/40.2222,-97.0997

    Query param arguments example
    http://localhost:8088/check-weather-wquery?lat=40.2222&lon=-97.0997

### NOTES ON IMPLEMENTATION

This service is implemented using Typelevel [http4s](https://http4s.org/).

Our scala code is based on the [http4s-io](https://github.com/http4s/http4s-io.g8) project template.

JSON is encoded+decoded using [Circe](https://circe.github.io/circe/).

To fetch weather info from the backend api.weather.gov, we use a sequence of two calls
to the "points" and then "gridpoints" services.  These services are accessed by URLs
with coordinates in the URI-PATH, not in query parameters.

Example URL for the "points" service, with lat,long at the end of the PATH:

https://api.weather.gov/points/39.7456,-97.0892

In the JSON results from that service we find this followup URL

https://api.weather.gov/gridpoints/TOP/31,80/forecast

One problem we face is that this latter gridpoints service often fails, for reasons we have
not analyzed.  When these failures occur, we attempt to return a useful error message to 
our API user (as a JSON-serialized `Msg_WeatherReport`), as well as logging information to our
service console.

### NOTES ON CODE STYLE

 * Limited use of scala wildcard imports.  Usually we try to confine these imports to a narrow scope, such as a particular trait or method.

 * Limited use of scala "object" singletons.

 * Preference for explicit typing and invocations over sugary structures like for-comprehensions.  

#### Code Naming Conventions

 * Msg_Xyz : case classes used to hold HTTP responses have names starting with "Msg_".  
   * We use this pattern for both backend and frontend responses.

 * JsonEncoder_Xyz : Json encoding contexts (using circe) start with "JsonEncoder_" or "JsonDecoder_"
