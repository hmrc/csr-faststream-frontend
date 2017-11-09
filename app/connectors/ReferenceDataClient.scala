/*
 * Copyright 2017 HM Revenue & Customs
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

package connectors

import config.{ CSRHttp, WSHttp }
import connectors.exchange.referencedata.{ ReferenceData, Scheme }
import play.api.libs.json.OFormat
//import uk.gov.hmrc.play.http.ws.WSHttp
import play.api.http.Status.OK

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.http.HeaderCarrier

object ReferenceDataClient extends ReferenceDataClient {
  val http: CSRHttp = CSRHttp
}

trait ReferenceDataClient {
  val http: WSHttp

  import config.FrontendAppConfig.faststreamConfig._
  val apiBaseUrl = s"${url.host}${url.base}"

  private val referenceDataCache = TrieMap[String, Any]()

  def allSchemes()(implicit hc: HeaderCarrier): Future[List[Scheme]] = getReferenceDataAsList[Scheme]("schemes", "/reference/schemes")

  private def getReferenceDataAsList[T](
    key: String,
    endpointPath: String)(implicit hc: HeaderCarrier, jsonFormat: OFormat[T]): Future[List[T]] = {
    val values: List[T] = referenceDataCache.getOrElse(key, List.empty[T]).asInstanceOf[List[T]]
    if (values.isEmpty) {
      http.GET(s"$apiBaseUrl$endpointPath").map { response =>
        if (response.status == OK) {
          val dataResponse = response.json.as[List[T]]
          referenceDataCache.update(key, dataResponse)
          dataResponse
        } else {
          throw new Exception(s"Error retrieving $key for")
        }
      }
    } else {
      Future.successful(values)
    }
  }

  private def getReferenceDataTyped[T](
    key: String,
    endpointPath: String
  )(implicit hc: HeaderCarrier, jsonFormat: OFormat[T]): Future[ReferenceData[T]] = {
    referenceDataCache.get(key).map(_.asInstanceOf[ReferenceData[T]]) match {
      case None =>
        http.GET(s"$apiBaseUrl$endpointPath").map { response =>
          if (response.status == OK) {
            val dataResponse = response.json.as[ReferenceData[T]]
            referenceDataCache.update(key, dataResponse)
            dataResponse
          } else {
            throw new Exception(s"Error retrieving $key for")
          }
        }
      case Some(referenceData) => Future.successful(referenceData)
    }
  }

}
