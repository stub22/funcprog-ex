# weather-svc
Weather Service - A Functional Programming exercise

This folder contains a Scala 2.13 project, based on the http4s-io giter8 project skeleton.

This weather-svc serves weather forecasts using information fetched from http://api.weather.gov.
This service is implemented using http4s, following the structure of the http4s-io example project.
To build and run this project, use sbt.  

This service launches on port 8088.  Changing this port requires modifying the scala code in _____.

The weather-svc offers a single feature, which is accessed using HTTP GET at a URL like:
    http://localhost:8088/check-weather?lat=


Notable differences
    Limited wildcard imports
    Limited use of scala "object" singletons
    Preference for Kleislis over for-comprehensions
    Naming Conventions
        Msg_Xyz : case classes used in building responses have names starting with "Msg_"
        JsonEnc_Xyz : Json encoding contexts start with "JsonEnc_"
