package com.appstract.fpex.weather

import cats.effect.{IO, Resource}

import munit.CatsEffectSuite
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.{EntityBody, HttpRoutes, Method, Request, Response, Status, Uri}
import org.log4s
import org.log4s.Logger

private trait JokeSpecs

class JokeRouteSpec extends CatsEffectSuite {

	val OP_NM_JOKE = WeatherRoutes.OP_NAME_JOKE

	test("jokeRoutes returns status code 200") {
		val log: Logger = log4s.getLogger
		val jokeResponseIO = invokeJokeRoute()

		assertIO(jokeResponseIO.map(resp => {
			val r: Response[IO] = resp
			val b: EntityBody[IO] = resp.body		//   type EntityBody[+F[_]] = Stream[F, Byte]
			val bas : fs2.Stream[IO, Byte] = b
			val cbs: fs2.Stream.CompileOps[IO, IO, Byte] = bas.compile
			val cbsv: IO[Vector[Byte]] = cbs.toVector
			log.info(s"JokeRouteSpec resp=${resp}, body=${resp.body}, body.toString=${resp.body.toString()}")
			resp.status
		}), Status.Ok)
	}

	private[this] def invokeJokeRoute() : IO[Response[IO]] = {
		// Builds the response-out effect for a simulated request-in message.
		val log: Logger = log4s.getLogger
		val jokeUriPath = s"/${OP_NM_JOKE}"
		val jokeUri = Uri.unsafeFromString(jokeUriPath)
		log.info(s"JokeRouteSpec.invokeJokeRoute made jokeUri: ${jokeUri}")
		val requestIO: Request[IO] = Request[IO](Method.GET, jokeUri) //  uri"/greetingForUser/world")
		val jokeSuppFact = new JokeSupplierFactory {}
		val embCliRes: Resource[IO, Client[IO]] = EmberClientBuilder.default[IO].build
		log.info(s"JokeRouteSpec.invokeJokeRoute built embCliRes=${embCliRes}")
		val routeRes: Resource[IO, HttpRoutes[IO]] = for {
			cli <- embCliRes
			jokeSupp: JokeSupplier = jokeSuppFact.getImpl(cli)
			route: HttpRoutes[IO] = WeatherRoutes.jokeRoutes(jokeSupp)
		} yield(route)
		// Now we have a potential-route wrapped in a resource
		routeRes.use(hr => {
			hr.orNotFound(requestIO)
		})
	}

	def checkSomeStuff : Unit = {
		import fs2.{Pure, Stream}

		val dummyIO: IO[Int] = IO { println("BEING RUN!!"); 1 + 1 }
		val s0: Stream[Pure, Nothing] = Stream.empty
		val s1: Stream[Pure, Int] = Stream.emit(1)
		val s1a: Stream[Pure, Int] = Stream(1,2,3) // variadic
		val s1b: Stream[Pure, Int] = Stream.emits(List(1,2,3))
		val x1: Seq[Int] = (Stream(1,2,3) ++ Stream(4,5)).toList
		val x2 = Stream(1,2,3).map(_ + 1).toList
		val x3 = Stream(1,2,3).filter(_ % 2 != 0).toList
		val x4 = Stream(1,2,3).fold(0)(_ + _).toList
		val x5 = Stream(None,Some(2),Some(3)).collect { case Some(i) => i }.toList
		val x6 = Stream.range(0,5).intersperse(42).toList
		val x7 = Stream(1,2,3).flatMap(i => Stream(i,i)).toList
		val x8 = Stream(1,2,3).repeat.take(9).toList
		val x9 = Stream(1,2,3).repeatN(2).toList
	}
}

/***
  Representation of the HTTP response to send back to the client
 *
 * @param status [[Status]] code and message
 * @param headers [[Headers]] containing all response headers
 * @param body EntityBody[F] representing the possible body of the response
 * @param attributes [[org.typelevel.vault.Vault]] containing additional
 *                   parameters which may be used by the http4s backend for
 *                   additional processing such as java.io.File object

final class Response[F[_]] private (
    val status: Status,
    val httpVersion: HttpVersion,
    val headers: Headers,
    val body: EntityBody[F],		//   type EntityBody[+F[_]] = Stream[F, Byte]
    val attributes: Vault,
) extends Message[F]
    with Product
    with Serializable {
  type SelfF[F0[_]] = Response[F0]
 */