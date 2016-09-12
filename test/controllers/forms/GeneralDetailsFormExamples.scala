/*
 * Copyright 2016 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers.forms

import connectors.exchange.FastPassDetails
import forms.GeneralDetailsForm
import mappings.{ AddressExamples, DayMonthYear }
import org.joda.time.{ DateTime, LocalDate }

object GeneralDetailsFormExamples {
  val ValidOutsideUKDetails = Map[String, String](
    "firstName" -> "John",
    "lastName" -> "Biggs",
    "preferredName" -> "john",
    "dateOfBirth.day" -> "1",
    "dateOfBirth.month" -> "2",
    "dateOfBirth.year" -> "1990",
    "address.line1" -> "Line 1",
    "outsideUk" -> "true",
    "country" -> "France",
    "phone" -> "123456789",
    "fastPassDetails.applicable" -> "false"
  )

  val InvalidUKAddressWithoutPostCode = ValidOutsideUKDetails - "outsideUk"

  val ValidUKAddress = InvalidUKAddressWithoutPostCode + ("postCode" -> "A1 2BC")

  val InvalidAddressDoBInFuture = ValidUKAddress + ("dateOfBirth.year" -> yearInTheFuture)

  val OutsideUKMandatoryFields = List(
    "firstName",
    "lastName",
    "preferredName",
    "dateOfBirth.day",
    "dateOfBirth.month",
    "dateOfBirth.year",
    "address.line1",
    "phone",
    "fastPassDetails.applicable"
  )

  val ValidUKAddressForm = GeneralDetailsForm.Data("firstName", "lastName", "preferredName", DayMonthYear("1", "2", birthYear),
    outsideUk = None, AddressExamples.FullAddress, Some("A1 2BC"), None, Some("1234567890"), FastPassDetails(applicable = false))

  val ValidNonUKAddressForm = GeneralDetailsForm.Data("firstName", "lastName", "preferredName", DayMonthYear("1", "2", birthYear),
    outsideUk = Some(true), AddressExamples.FullAddress, None, Some("France"), Some("1234567890"), FastPassDetails(applicable = false))

  val ValidFormUrlEncodedBody = Seq(
    "firstName" -> ValidUKAddressForm.firstName,
    "lastName" -> ValidUKAddressForm.lastName,
    "preferredName" -> ValidUKAddressForm.preferredName,
    "dateOfBirth.day" -> ValidUKAddressForm.dateOfBirth.day,
    "dateOfBirth.month" -> ValidUKAddressForm.dateOfBirth.month,
    "dateOfBirth.year" -> ValidUKAddressForm.dateOfBirth.year,
    "address.line1" -> ValidUKAddressForm.address.line1,
    "address.line2" -> ValidUKAddressForm.address.line2.getOrElse(""),
    "address.line3" -> ValidUKAddressForm.address.line3.getOrElse(""),
    "address.line4" -> ValidUKAddressForm.address.line4.getOrElse(""),
    "postCode" -> ValidUKAddressForm.postCode.getOrElse(""),
    "phone" -> ValidUKAddressForm.phone.map(_.toString).getOrElse(""),
    "fastPassDetails.applicable" -> ValidUKAddressForm.fastPassDetails.applicable.toString
  )


  private def yearInTheFuture = DateTime.now().plusYears(2).year().get().toString

  def birthYear = LocalDate.now.minusYears(18).year().get().toString

  def now = LocalDate.now
}
