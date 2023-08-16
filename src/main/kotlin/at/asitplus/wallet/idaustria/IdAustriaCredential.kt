package at.asitplus.wallet.idaustria

import at.asitplus.wallet.lib.data.CredentialSubject
import kotlinx.datetime.LocalDate
import kotlinx.datetime.serializers.LocalDateIso8601Serializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
@SerialName("IdAustria2023")
class IdAustriaCredential : CredentialSubject {

    @SerialName("firstname")
    val firstname: String

    @SerialName("lastname")
    val lastname: String

    @SerialName("date-of-birth")
    @Serializable(with = LocalDateIso8601Serializer::class)
    val dateOfBirth: LocalDate

    constructor(id: String, firstname: String, lastname: String, dateOfBirth: LocalDate) : super(id = id) {
        this.firstname = firstname
        this.lastname = lastname
        this.dateOfBirth = dateOfBirth
    }

    override fun toString(): String {
        return "IdAustriaCredential(id='$id', firstname='$firstname', lastname='$lastname', dateOfBirth=$dateOfBirth)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IdAustriaCredential

        if (id != other.id) return false
        if (firstname != other.firstname) return false
        if (lastname != other.lastname) return false
        return dateOfBirth == other.dateOfBirth
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + firstname.hashCode()
        result = 31 * result + lastname.hashCode()
        result = 31 * result + dateOfBirth.hashCode()
        return result
    }

}