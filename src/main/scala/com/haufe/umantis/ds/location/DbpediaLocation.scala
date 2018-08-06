/**
  * This program is free software: you can redistribute it and/or modify
  * it under the terms of the GNU General Public License as published by
  * the Free Software Foundation, either version 3 of the License, or
  * (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  * GNU General Public License for more details.
  *
  * You should have received a copy of the GNU General Public License
  * along with this program.  If not, see <https://www.gnu.org/licenses/>.
  */

package com.haufe.umantis.ds.location

import org.apache.jena.query.QuerySolution
import org.apache.jena.sparql.engine.http.QueryEngineHTTP

import scala.annotation.tailrec

import scala.collection.JavaConverters._


object DbpediaLocation extends Location {
  org.apache.jena.query.ARQ.init()

  val countryCodeToLanguage: Map[String, String] = Map[String, String](
    "it" -> "it",
    "nl" -> "nl",
    "be" -> "nl",
    "pl" -> "pl",
    "at" -> "de",
    "de" -> "de",
    "ru" -> "ru",
    "pt" -> "pt",
    "br" -> "pt",
    "id" -> "id",
    "es" -> "es",
    "mx" -> "es",
    "co" -> "es",
    "ar" -> "es",
    "kr" -> "ko",
    "ae" -> "ar",
    "fr" -> "fr",
    "se" -> "sv"
    //    "jp" -> "ja", // might be forbidden in China
  ).withDefaultValue("en")

  val baseQuery: String =
    """
      |PREFIX dbo: <http://dbpedia.org/ontology/>
      |PREFIX dbp: <http://dbpedia.org/property/>
      |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
      |PREFIX geo: <http://www.w3.org/2003/01/geo/wgs84_pos#>
      |PREFIX bif:<bif:>
      |select distinct ?city ?label ?lat ?long ?latd ?latm ?lats ?longd ?longm ?longs ?latns ?longew %%%Score%%% where {
      |  ?city a ?locationtype ;
      |    rdfs:label ?label .
      |  OPTIONAL { ?city geo:lat ?lat }
      |  OPTIONAL { ?city geo:long ?long }
      |  OPTIONAL { ?city dbp:latd ?latd }
      |  OPTIONAL { ?city dbp:latm ?latm }
      |  OPTIONAL { ?city dbp:lats ?lats }
      |  OPTIONAL { ?city dbp:latns ?latns }
      |  OPTIONAL { ?city dbp:longd ?longd }
      |  OPTIONAL { ?city dbp:longm ?longm }
      |  OPTIONAL { ?city dbp:longs ?longs }
      |  OPTIONAL { ?city dbp:longew ?longew }
      |  FILTER (?locationtype in (dbo:Place, dbo:Settlement))
      |  %%%LocationFilter%%%
      |  %%%LanguageFilter%%%
      |}
      |order by strlen(str(?label))%%%ScoreOrder%%%
      |limit 20
    """.stripMargin

  val baseLangFilter = """filter langMatches(lang(?label),'%%%Language%%%')"""
  val baseEqualsFilter = """filter (?label="%%%Place%%%"@%%%EqualsLang%%%)"""
  val baseContainsFilter = """?label bif:contains "%%%Place%%%" option (score ?sc)"""

  override def findPlace(place: String,
                country: Option[String] = None): Option[GeoLocation] = {

    // initializing Apache Jena. If already initialized this function does nothing
    org.apache.jena.query.ARQ.init()

    val language = country match {
      case Some(code) => countryCodeToLanguage(code.toLowerCase)
      case None => "en"
    }

    val cleanUpPlace = place
      .split(" - ")(0)
      .trim
      .split("""[^\p{Ll}\p{Lu}0-9]""")
      .filter(x => x.length > 0)
      .map(_.toLowerCase)

    // let's try in English first
    doQuery(cleanUpPlace, country, "en") match {
      case Some(r) => return Some(r);
      case None => ;
    }

    // let's try in a different server
    if (language != "en")
      doQuery(cleanUpPlace, country, language) match {
      case Some(r) => return Some(r);
      case None => ;
    }

//    // let's try in the German server
//    if (language != "de")
//      doQuery(cleanUpPlace, country, "de") match {
//      case Some(r) => return Some(r);
//      case None => ;
//    }

    // not looking good. let's try with all the subwords of the query
    val possibleLocations = place
      .split("""[^\p{Ll}\p{Lu}0-9]""")
      .map(_.trim)
      .filter(x => x.length > 2)
      .map(_.toLowerCase)
      .toSeq

    // not looking good at all. let's try with a subset of the query,
    // we stop as soon as we find a result
    val combinations = (possibleLocations.length to 0 by -1).flatMap(possibleLocations.combinations)

    @tailrec
    def looping(locations: IndexedSeq[Seq[String]], language: String, idx: Int = 0):
    Option[GeoLocation] = {
      if (idx == locations.length)
        return None
      val location = locations(idx)
      doQuery(location.toArray, country, language) match {
        case Some(r) => Some(r)
        case None => looping(locations, language, idx + 1)
      }
    }

    looping(combinations, "en") match {
      case Some(r) => return Some(r);
      case None => ;
    }

    if (language != "en")
      looping(combinations, language) match {
      case Some(r) => return Some(r);
      case None => ;
    }

    // last chance: we also normalize the input
    val possibleLocationsNormalized = possibleLocations.map(x => normalize(x))
    val combinationsNormalized =
      (possibleLocationsNormalized.length to 0 by -1)
        .flatMap(possibleLocationsNormalized.combinations)

    looping(combinationsNormalized, "en") match {
      case Some(r) => return Some(r);
      case None => ;
    }

    if (language != "en")
      looping(combinationsNormalized, language) match {
      case Some(r) => return Some(r);
      case None => ;
    }

    None
  }

  def doQuery(locations: Array[String],
              countryCode: Option[String],
              language: String,
              langFilter: String = "",
              locationFilter: String = baseContainsFilter):
  Option[GeoLocation] = {
//    println(s"${locations.mkString(" ")}, $countryCode, $language, $langFilter, ")

    val query = createQuery(locations, language, locationFilter, langFilter, language)
    val solutions = queryServer(query, language)
    processSolutions(solutions, countryCode)
  }

  def createQuery(locations: Array[String],
                  serverLanguage: String = "en",
                  locationFilter: String = baseContainsFilter,
                  langFilter: String = "",
                  language: String = "en",
                  baseQueryToUse: String = baseQuery):
  String = {
    if (locationFilter == baseContainsFilter) {
      // for bif:contains we need to have "'berlin'", not "berlin"
      val locationString = locations.map(loc =>
        if (loc.length >= 4) s"'$loc*'" else s"'$loc'" // partial matching allowed if word size >= 4
      ).mkString(" and ")

      def scoreOrderReplacement(serverLanguage: String): String = {
        // only english and german servers support ordering by score
        if (Array("en", "de").contains(serverLanguage)) ",desc(?sc)" else ""
      }

      baseQueryToUse
        .replace("%%%LocationFilter%%%",
          locationFilter.replace("%%%Place%%%", locationString))
        .replace("%%%Score%%%", "?sc")
        .replace("%%%ScoreOrder%%%", scoreOrderReplacement(serverLanguage))
        .replace("%%%LanguageFilter%%%",
          langFilter.replace("%%%Language%%%", language))
    } else {

      val locationString = locationFilter
        .replace("%%%Place%%%", locations.mkString(" "))
        .replace("%%%EqualsLang%%%", language)

      baseQueryToUse
        .replace("%%%LocationFilter%%%", locationString)
        .replace("%%%Score%%%", "")
        .replace("%%%ScoreOrder%%%", "")
        .replace("%%%LanguageFilter%%%",
          langFilter.replace("%%%Language%%%", language))
    }
  }

  def queryServer(queryString: String, serverLang: String): Array[QuerySolution] = {
    val serverURL = getDbpediaURL(serverLang)
    // println(serverURL)
    // println(queryString)

    // not constructing the query and validating it but rather querying the remote endpoint
    // directly because:
    // "If you are using Jena to send a query with Virtuoso-specific features, you need to direct
    // create a QueryEngineHTTP (which is a QueryExecution) and provide just the 2 strings,
    // endpoint and query string. Otherwise, Jena validates the query locally but it isn't
    // valid SPARQL hence it fails.
    // https://stackoverflow.com/questions/26008521/sparql-query-error-with-optiontransitive-on-jena
    //    val query = QueryFactory.create(queryString)
    //    val qexec = QueryExecutionFactory.sparqlService(serverURL, query)
    val qexec = new QueryEngineHTTP(serverURL, queryString)

    try {

      return qexec.execSelect.asScala.toArray
    }
    catch {
      case _: org.apache.jena.sparql.engine.http.QueryExceptionHTTP => Array()
    }
    finally {
      qexec.close()
    }
    Array()
  }

  def processSolutions(solutions: Array[QuerySolution], countryCode: Option[String]):
  Option[GeoLocation] = {
    countryCodeToName(countryCode) match {

      case Some(queryCountryName) =>
        var bestCoordinates: Option[GeoLocation] = None
        solutions.foreach(sol => {
          getLatLon(sol) match {
            case None => ; // no coordinates, we go on in the loop
            case Some(coords) => // some coordinates, let's check the country code
              getCountryName(sol) match {
                case Some(countryName) =>
                  if (queryCountryName.contains(countryName) ||
                    countryName.contains(queryCountryName))
                  // we got coordinates,
                  // the country code is the same as the query,
                  // we got the result, return it now!
                    return Some(coords)
                // else the solution is wrong, we ignore it
                case None =>
                  // we are not sure about the country,
                  // we save the solution (only the first we found)
                  // and query again to check if there's one with country information
                  if (bestCoordinates.isEmpty)
                    bestCoordinates = Some(coords)
              }
          }
        })
        bestCoordinates

      case None =>
        solutions.foreach(sol => {
          getLatLon(sol) match {
            case Some(coords) => return Some(coords)
            case None => ;
          }
        })
        None
    }
  }

  def getLatLon(sol: QuerySolution): Option[GeoLocation] = {
    val name = if (sol.contains("label")) sol.getLiteral("label").getString else "None"

    if (sol.contains("lat") && sol.contains("long")) {
      // nice float lat/lon
      val lat = sol.getLiteral("lat").getDouble
      val long = sol.getLiteral("long").getDouble
      return Some(GeoLocation(lat, long, name))
    } else {
      // degrees minutes seconds
      if (Seq("latns", "longew", "latd", "longd").forall(sol.contains)) { // these there must be
        def num(lit: String): Double =
        { if (sol.contains(lit)) sol.getLiteral(lit).getDouble else 0.0 }
        def dir(lit: String): Double =
        { if (Seq("N", "E").contains(sol.getLiteral(lit).getString)) 1.0 else -1.0 } // S, W => -1

        val latitude =
          dir("latns") * (num("latd") +
            num("latm") / 60.0 + num("lats") / 3600)
        val longitude =
          dir("longew") * (num("longd") +
            num("longm") / 60.0 + num("longs") / 3600)
        return Some(GeoLocation(latitude, longitude, name))
      }
    }
    None
  }

  def getCountryName(sol: QuerySolution): Option[String] = {
    if (sol.contains("city")) {
      val URI = sol.getResource("city").getURI

      val countryQuery =
        """
          |PREFIX dbo: <http://dbpedia.org/ontology/>
          |select * where {
          |  %%%Resource%%% dbo:country ?country
          |  OPTIONAL { ?country rdfs:label ?countrylabel }
          |  FILTER langMatches(lang(?countrylabel),'en')
          |}
          |limit 1
        """.stripMargin.trim
      val resource = "<" + URI + ">"
      val res = queryServer(
        countryQuery.replaceAll("%%%Resource%%%", resource), "en")
      if (res.nonEmpty && res(0).contains("country")) {
        return Some(res(0)
          .getResource("country")
          .getLocalName
          .replaceAll("_", " "))
      }
    }
    None
  }

  def getDbpediaURL(language: String = "en"): String = {
    val baseURL = "http://dbpedia.org/sparql"
    val lang = language.toLowerCase
    if (lang == "en")
      baseURL
    else
      baseURL.replace("dbpedia", s"$lang.dbpedia")
  }
}
