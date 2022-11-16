# weather-svc

## Weather Service - A functional programming exercise

This folder contains a Scala 2.13 project, based on the [http4s-io](https://github.com/http4s/http4s-io.g8) giter8 project skeleton.

This code implements a standalone HTTP server offering brief summary weather forecasts, using information fetched from this 
[National Weather Service API](https://www.weather.gov/documentation/services-web-api) 

This software is written for the Scala 2.13 platform.  It has been tested with
 * JDK 11 (Graal-VM)
 * SBT 1.7.3
 * Operating System:  Microsoft Windows 10

### BUILDING AND RUNNING

This project may be built and launched using sbt.

One reasonable sequence to build and run is:

`sbt clean compile`

`sbt run`

Once the server is running you may access it at the URLs described below.

When you are finished playing with the service, you will need to kill the running process using `Ctrl-C` or equivalent.

You may also run a few basic tests (which do not require that our service is running) using

`sbt test`

Note that these tests do not open up a frontend web service port, but they do access the backend `api.weather.gov` service, which may return different results at different times.

### ACCESSING THE SERVICE

Our server listens on port 8080.  Changing this port requires modifying the scala code in 
[AppServer.scala](./src/main/scala/com/appstract/fpex/weather/AppServer.scala) (specifically `AppServerBuilder.makeServerResourceForHttpApp`).

The weather-svc offers two different URL endpoints, which may be accessed using HTTP GET, from a web browser or other HTTP client.

These two URL endpoints accept two different formats for the latitude+longitude arguments.

In both cases, our weather-svc simply passes along whatever lat+lon input it is given to the backend `api.weather.gov` services,
without performing any validation.

We have not yet attempted to determine how many digits of precision can be supplied without causing an error.

    Path argument example
    http://localhost:8080/check-weather-wpath/40.2222,-97.0997

    Query param arguments example
    http://localhost:8080/check-weather-wquery?lat=40.2222&lon=-97.0997

A successful HTTP response will looks this
    
    {"messageType":"WEATHER_REPORT","latLonPairTxt":"39.7451,-97.0997","summary":"Sunny","temperatureDescription":"cold"}


### ERROR REPORTING

If the URL submitted to weather-svc does not match a form that the weather-svc expects, the client receives an HTTP 404 Not Found response.

If the URL format is OK, but the weather-svc cannot access the backend weather data for the submitted location, then client will receive an OK (Status 200) response whose body contains a detailed JSON error description. This error will look different depending on whether the error occurs in the first stage backend operation `fetchAreaInfoOrError` or the second stage `fetchCurrentForecastPeriodOrError`.

Below are two example first stage errors from `fetchAreaInfoOrError`.
The first one reflects malformed location input. 

Note the backend URL embedded inside the error message.  We have noticed that these backend URLs tend to fail in an intermittent fashion.
The same location input may lead to success one minute, and an error the next minute!

    {"messageType":"WEATHER_ERROR","latLonPairTxt":"blarp","errorName":"BACKEND_ERR","errorInfo":"BackendError(opName=fetchAreaInfoOrError, opArgs=Request(method=GET, uri=https://api.weather.gov/points/blarp, httpVersion=HTTP/1.1, headers=Headers()), exc=org.http4s.client.UnexpectedStatus: unexpected HTTP status: 404 Not Found for request GET https://api.weather.gov/points/blarp)"}

    {"messageType":"WEATHER_ERROR","latLonPairTxt":"33.2210,-88.0055","errorName":"BACKEND_ERR","errorInfo":"BackendError(opName=fetchAreaInfoOrError, opArgs=Request(method=GET, uri=https://api.weather.gov/points/33.2210,-88.0055, httpVersion=HTTP/1.1, headers=Headers()), exc=org.http4s.client.UnexpectedStatus: unexpected HTTP status: 301 Moved Permanently for request GET https://api.weather.gov/points/33.2210,-88.0055)"}

An example second stage error from `fetchCurrentForecastPeriodOrError`.  Again, these errors occur intermittently.

    {"messageType":"WEATHER_ERROR","latLonPairTxt":"44.7755,-99.9923","errorName":"BACKEND_ERR","errorInfo":"BackendError(opName=fetchCurrentForecastPeriodOrError, opArgs=Request(method=GET, uri=https://api.weather.gov/gridpoints/ABR/67,61/forecast, httpVersion=HTTP/1.1, headers=Headers()), exc=org.http4s.client.UnexpectedStatus: unexpected HTTP status: 500 Internal Server Error for request GET https://api.weather.gov/gridpoints/ABR/67,61/forecast)"}

### COMMNICATION WITH BACKEND

To fetch weather info from the backend api.weather.gov, we use a sequence of two calls
to the "points" and then "gridpoints" services.  These services are accessed by URLs
with coordinates in the URI-PATH, not in query parameters.

Example URL for the `points` service, with lat,long at the end of the PATH:

    https://api.weather.gov/points/39.7456,-97.0892

In the JSON results from that service, we find this followup URL for the `gridpoints` service, which we follow to retrieve detailed forecast info.

    https://api.weather.gov/gridpoints/TOP/31,80/forecast

But one problem we face is that this latter `gridpoints` service often fails intermittently.  We have not attempted to determine the source of these failures.

When these failures occur, we attempt to return a useful error message to our API user (as a JSON-serialized `Msg_WeatherReport`), 
as well as logging information to our service console.

### SOURCE FILES OVERVIEW

The server code is implemented in 8 .scala files located in the [com.appstract.fpex.weather](src/main/scala/com/appstract/fpex/weather) package.

We may roughly divide these files into 3 functional groups:

#### Web Server : Launch and Request Handling
 * AppMain.scala
 * AppServer.scala
 * AppWebRoutes.scala

#### Backend Data Fetching and Error Handling
 * BackendForecastApi.scala
 * BackendForecastImpl.scala

#### Frontend Weather Report Generation
 * ReporterApi.scala
 * ReporterImpl.scala
 * Temperatures.scala

### LIBRARIES 

This server is implemented using Typelevel [http4s](https://http4s.org/) and [cats-effect](https://typelevel.org/cats-effect/).

JSON is encoded+decoded using [circe](https://circe.github.io/circe/).

Our scala code is based on the [http4s-io](https://github.com/http4s/http4s-io.g8) project template (as of November 2022).
Our dependencies all came from this template, and have not been modified in our build files.

### Regarding Purity

Our scala code is mostly pure in the FP sense, but does use impure logging side-effects via [log4s](https://github.com/Log4s/log4s).

### CODE STYLE CHOICES

 * Limited use of scala wildcard imports.  Usually we try to confine these imports to a narrow scope, such as a particular trait or method.

 * Limited use of scala "object" singletons.  No use of the "Companion Object" pattern.

 * Preference for explicit typing and invocations over sugary structures like for-comprehensions.  

#### Code Naming Conventions

 * `Msg_Xyz` : case classes used to hold HTTP responses have names starting with `Msg_`.  
   * We use this pattern for both backend and frontend responses.

 * `JsonEncoder_Xyz` : Json encoding/decoding contexts (using `circe` library) start with `JsonEncoder_` or `JsonDecoder_`
