# weather-svc
Weather Service - A Functional Programming exercise

This folder contains a Scala 2.13 project, based on the http4s-io giter8 project skeleton.

This weather-svc serves weather forecasts using information fetched from http://api.weather.gov.
This service is implemented using http4s, following the structure of the http4s-io example project.
To build and run this project, use sbt.  

This service launches on port 8088.  Changing this port requires modifying the scala code in 
AppServer.scala (specifically AppServerBuilder.mkServerResourceForHttpApp).

The weather-svc offers a single feature, which is accessed using HTTP GET at a URL like:
    http://localhost:8088/check-weather?lat=

To fetch weather info from backend api.weather.gov, we use a sequence of two calls
to the "points" and then "gridpoints" services.  These services are accessed by URLs
with coordinates in the URI-PATH, not in query parameters.
Example URL for the "points" service, with lat,long at the end of the PATH:
https://api.weather.gov/points/39.7456,-97.0892
In the JSON results of that service we find
https://api.weather.gov/gridpoints/TOP/31,80/forecast


One problem we face is that this gridpoints service fails for many locations.
It is important that we return a useful error message when these failures occur.


Notable differences
    Limited use of scala wildcard imports
    Limited use of scala "object" singletons
    Preference for Kleislis over for-comprehensions
    Naming Conventions
        Msg_Xyz : case classes used in building responses have names starting with "Msg_"
        JsonEnc_Xyz : Json encoding contexts start with "JsonEnc_"

// TODO:  Allow user to supply lat-long in different shapes, e.g. as two Floats, Decimals, or Strings.