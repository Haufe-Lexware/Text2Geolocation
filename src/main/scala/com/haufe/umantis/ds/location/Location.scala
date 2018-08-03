package com.haufe.umantis.ds.location

import com.haufe.umantis.ds.utils.NormalizeSupport
import com.vitorsvieira.iso.ISOCountry

// Every case class extends Product and Serializable so we need it to execute getDistance
trait BaseCoordinates extends Product with Serializable {
  def latitude: Double
  def longitude: Double
}

case class GeoCoordinates(latitude: Double, longitude: Double) extends BaseCoordinates
case class GeoLocation(latitude: Double, longitude: Double, name: String) extends BaseCoordinates


trait Location extends NormalizeSupport {
  def getCoordinates(place: String,
                     countryCode: Option[String] = None):
  GeoCoordinates = {
    if (place == null)
      return GeoCoordinates(0.0, 0.0)

    findPlace(place, countryCode) match {
      case Some(coords) => GeoCoordinates(coords.latitude, coords.longitude)
      case None => GeoCoordinates(0.0, 0.0)
    }
  }

  def findPlace(place: String,
                country: Option[String] = None): Option[GeoLocation] = None

  def countryCodeToName(code: Option[String]): Option[String] = {
    code match {
      case Some(countryCode) =>
        try {
          Some(ISOCountry(countryCode.toUpperCase).englishName)
        } catch {
          case _: Exception => None
        }
      case _ => None
    }
  }

  // see https://en.wikipedia.org/wiki/Haversine_formula
  def getDistance[C <: BaseCoordinates](location1: C, location2: C): Float = {
    try {
      val lat1 = location1.latitude
      val lon1 = location1.longitude
      val lat2 = location2.latitude
      val lon2 = location2.longitude

      val R = 6371e3f // metres

      val phi1 = lat1.toRadians
      val phi2 = lat2.toRadians
      val deltaPhi = (lat2 - lat1).toRadians
      val deltaLambda = (lon2 - lon1).toRadians

      val a = Math.pow(Math.sin(deltaPhi / 2), 2) +
        Math.cos(phi1) * Math.cos(phi2) * Math.pow(Math.sin(deltaLambda / 2), 2)
      val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

      (R * c / 1000.0).toFloat // distance in kilometers
    } catch {
      case n: NullPointerException => 100000
    }
  }
}

object Location extends Location
