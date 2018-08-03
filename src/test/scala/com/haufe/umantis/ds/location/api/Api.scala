package com.haufe.umantis.ds.location.api

import java.util.concurrent.Executors

import com.haufe.umantis.ds.location.{DbpediaLocation, GeoCoordinates}
import com.twitter.concurrent.NamedPoolThreadFactory
import com.twitter.finagle.{Http, ListeningServer}
import com.twitter.util.{Await, FuturePool}
import io.circe.generic.auto._
import io.finch._
import io.finch.circe.dropNullValues._
import io.finch.syntax._


case class PlaceRequest
(
  name: String,
  country: Option[String]
)

case class PlaceResponse
(
  latitude: Float,
  longitude: Float
)

object Api {

  def main(args: Array[String]): Unit = {
    val server = serveApi()
    Await.ready(server)
  }
  def serveApi(): ListeningServer = {

    val futureExecutor = FuturePool(
      Executors.newFixedThreadPool(
        Runtime.getRuntime.availableProcessors(),
        new NamedPoolThreadFactory("ComputeFinchRequests", makeDaemons = true)
      )
    )

    val coordinates: Endpoint[GeoCoordinates] =
      post("coordinates" :: jsonBody[PlaceRequest]) { req: PlaceRequest =>
        futureExecutor {
          val coordinates = DbpediaLocation.getCoordinates(req.name, req.country)
          Ok(coordinates)
        }
      }.handle {
        case e: Error.NotPresent => BadRequest(e)
      }

    val service = Bootstrap
      .serve[Application.Json](coordinates)
      .toService

    Http.server.serve(":8080", service)
  }
}
