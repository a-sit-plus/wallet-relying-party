package at.asit.wallet.relyingparty

import at.asitplus.openid.dcql.DCQLClaimsPathPointerSegment
import at.asitplus.wallet.eupidsdjwt.EU_PID_SD_JWT_VCT
import at.asitplus.wallet.eupidsdjwt.EuPidSdJwtDataElements
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.openid.CredentialPresentationRequestBuilder
import at.asitplus.wallet.lib.openid.PresentationMechanismEnum
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class TransactionRequestTest {
    @Test
    fun `address subfields are requested as nested claim paths`() = runTest {
        at.asitplus.wallet.eupidsdjwt.Initializer.initWithVCK()

        val dcqlQuery = CredentialPresentationRequestBuilder(
            listOf(
                TransactionRequestCredential(
                    credentialType = EU_PID_SD_JWT_VCT,
                    representation = ConstantIndex.CredentialRepresentation.SD_JWT.name,
                    attributes = listOf(
                        EuPidSdJwtDataElements.FAMILY_NAME,
                        EuPidSdJwtDataElements.ADDRESS_STREET,
                    ),
                )
            ).map { it.toRequestOptionsCredential() }
        ).toDCQLRequest().shouldNotBeNull().dcqlQuery

        val paths = dcqlQuery.credentials.first().claims.shouldNotBeNull().map { claim ->
            claim.path.map { (it as DCQLClaimsPathPointerSegment.NameSegment).name }
        }
        paths shouldContain listOf(EuPidSdJwtDataElements.FAMILY_NAME)
        // "address.street_address" must be requested as a nested path, not a literal dotted claim name
        paths shouldContain listOf("address", "street_address")
    }

    @Test
    fun deserializationWorks() {
        val tmp = """{
          "presentationMechanismIdentifier": "dcql_query",
          "presentationDefinition": null,
          "dcqlQuery": {
          "credentials": [
            {
              "id": "41f55fc2-c938-4c0b-90c6-df7416f98e24",
              "meta": {
                "vct_values": [
                  "urn:eudi:pid:1"
                ]
              },
              "claims": [
                {
                  "path": [
                    "family_name"
                  ]
                },
                {
                  "path": [
                    "given_name"
                  ]
                },
                {
                  "path": [
                    "birthdate"
                  ]
                },
                {
                  "path": [
                    "nationalities"
                  ]
                },
                {
                  "path": [
                    "date_of_expiry"
                  ]
                },
                {
                  "path": [
                    "issuing_authority"
                  ]
                },
                {
                  "path": [
                    "issuing_country"
                  ]
                }
              ],
              "format": "dc+sd-jwt"
            },
            {
              "id": "732064b3-fed2-4d74-9fff-f7e7fef62ad3",
              "meta": {
                "doctype_value": "eu.europa.ec.eudi.pid.1"
              },
              "claims": [
                {
                  "path": [
                    "eu.europa.ec.eudi.pid.1",
                    "family_name"
                  ]
                },
                {
                  "path": [
                    "eu.europa.ec.eudi.pid.1",
                    "given_name"
                  ]
                },
                {
                  "path": [
                    "eu.europa.ec.eudi.pid.1",
                    "birth_date"
                  ]
                },
                {
                  "path": [
                    "eu.europa.ec.eudi.pid.1",
                    "nationality"
                  ]
                },
                {
                  "path": [
                    "eu.europa.ec.eudi.pid.1",
                    "expiry_date"
                  ]
                },
                {
                  "path": [
                    "eu.europa.ec.eudi.pid.1",
                    "issuing_authority"
                  ]
                },
                {
                  "path": [
                    "eu.europa.ec.eudi.pid.1",
                    "issuing_country"
                  ]
                }
              ],
              "format": "mso_mdoc"
            }
          ],
          "credential_sets": [
            {
              "options": [
                ["41f55fc2-c938-4c0b-90c6-df7416f98e24"],
                ["732064b3-fed2-4d74-9fff-f7e7fef62ad3"]
              ]
            }
          ]
        }
        }"""
        val request = Json.decodeFromString<TransactionRequest>(tmp)
        request.presentationDefinition.shouldBeNull()
        request.deviceRequest.shouldBeNull()
        request.presentationMechanism shouldBe PresentationMechanismEnum.DCQL
        request.dcqlQuery.shouldNotBeNull()
        request.dcqlQuery.credentials shouldHaveSize 2
        request.dcqlQuery.credentialSets.shouldNotBeNull() shouldHaveSize 1
        request.dcqlQuery.credentialSets.shouldNotBeNull().first().options shouldHaveSize 2
        request.dcqlQuery.credentialSets.shouldNotBeNull().first().options.forEach {
            it shouldHaveSize 1
        }
    }
}