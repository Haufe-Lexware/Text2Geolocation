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

import org.scalatest.tagobjects.Slow
import org.scalatest._
import Matchers._


class DbpediaLocationSpec extends LocationSpec {
  override def geo[T <: Location]: Location = DbpediaLocation

  "getCoordinates" should "return correct results" taggedAs Slow in {
    // here I'm using dist() and Place() to ease testing: in practice I'm checking
    // if distances as the crow flies of cities are within reasonable limits

    dist(Place("Barcelona (El Prat de Llobrega" /* sic. */), Place("Barcelona")) should be < 20f

    dist(Place("Soliera di Bomporto", "it"), Place("Modena")) should be < 20f

    dist(Place("Los Angeles"), Place("San Francisco")) shouldBe 550f +- 100f

    val napIt = Place("Naples").coords(geo)
    val romDe = Place("Rom").coords(geo) // Rome in German

    val napUs = Place("Naples", "us").coords(geo)
    val miami = Place("Miami").coords(geo)
    geo.getDistance(napIt, romDe) shouldBe 200f +- 50f
    geo.getDistance(napUs, miami) shouldBe 170f +- 20f
    geo.getDistance(napUs, napIt) shouldBe 8626f +- 20f

    dist(Place("iracemapolis"), Place("sao paulo")) shouldBe 142f +- 5f
    dist(Place("iracemapolis", "br"), Place("sao paulo")) shouldBe 142f +- 5f

    dist(Place("madrid", "es"), Place("ciudad de mexico", "mx")) shouldBe 9072f +- 10f

    val nonExistingPlace = Place("sdfsdfsdfsdf sdfsdf sdf  sdfsdfsdf", "es")
    nonExistingPlace.coords(geo) shouldBe GeoCoordinates(0, 0)
  }

  "createQuery" must "replace filters correctly" in {
    val query = DbpediaLocation.createQuery(
      Array("te", "st", "query"),
      serverLanguage = "fr",
      locationFilter = DbpediaLocation.baseContainsFilter,
      langFilter = DbpediaLocation.baseLangFilter,
      baseQueryToUse = DbpediaLocation.baseQuery
    )
    query should include (
      """?label bif:contains "'te' and 'st' and 'query*'" option (score ?sc)""")
    query should not include """,desc(?sc)"""

    DbpediaLocation.createQuery(Array("te", "st", "query")) should include (""",desc(?sc)""")

    val query2 = DbpediaLocation.createQuery(
      Array("Guastalla"),
      serverLanguage = "it",
      locationFilter = DbpediaLocation.baseEqualsFilter,
      langFilter = DbpediaLocation.baseLangFilter,
      language = "it",
      baseQueryToUse = DbpediaLocation.baseQuery
    )
    query2 should include ("""filter langMatches(lang(?label),'it')""")
  }

  it must "create correct queries that do not fail" in {
    val query = DbpediaLocation.createQuery(Array("Guastalla"),
      locationFilter = DbpediaLocation.baseEqualsFilter, language = "it")
    DbpediaLocation.queryServer(query, "it") should not be empty
  }
}
