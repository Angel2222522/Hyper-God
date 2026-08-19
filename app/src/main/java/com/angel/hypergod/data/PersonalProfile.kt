package com.angel.hypergod.data

import android.content.Context
import com.angel.hypergod.security.FileCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID

data class PersonalProfile(
    val fullName: String = "",
    val alternativeNames: String = "",
    val birthDate: String = "",
    val birthPlace: String = "",
    val taxNumber: String = "",
    val socialSecurityNumber: String = "",
    val address: String = "",
    val email: String = "",
    val phone: String = "",
    val documentNumbers: String = ""
) {
    val isEmpty: Boolean get() = listOf(
        fullName, alternativeNames, birthDate, birthPlace, taxNumber,
        socialSecurityNumber, address, email, phone, documentNumbers
    ).all(String::isBlank)
}

/** Small Keystore-encrypted profile file; no identifier is written to preferences or logs. */
object PersonalProfileStore {
    suspend fun load(context: Context): PersonalProfile = withContext(Dispatchers.IO) {
        val source = profileFile(context)
        if (!source.isFile) return@withContext PersonalProfile()
        val plain = context.cacheDir.resolve("profile/read_${UUID.randomUUID()}.json").apply { parentFile?.mkdirs() }
        try {
            FileCrypto.decryptToTemp(source, plain)
            val bytes = plain.readBytes()
            require(bytes.size <= MAX_PROFILE_BYTES) { "Το προσωπικό μητρώο είναι υπερβολικά μεγάλο." }
            fromJson(JSONObject(bytes.toString(Charsets.UTF_8)))
        } finally {
            plain.delete()
        }
    }

    suspend fun save(context: Context, profile: PersonalProfile) = withContext(Dispatchers.IO) {
        validate(profile)
        FileCrypto.ensureKeyAvailableForNewDocument(context)
        val bytes = toJson(profile).toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_PROFILE_BYTES)
        val destination = profileFile(context).apply { parentFile?.mkdirs() }
        ByteArrayInputStream(bytes).use { input -> FileCrypto.encrypt(input, destination, MAX_PROFILE_BYTES.toLong()) }
    }

    internal fun toJson(profile: PersonalProfile): JSONObject = JSONObject().apply {
        put("version", 1)
        put("fullName", profile.fullName)
        put("alternativeNames", profile.alternativeNames)
        put("birthDate", profile.birthDate)
        put("birthPlace", profile.birthPlace)
        put("taxNumber", profile.taxNumber)
        put("socialSecurityNumber", profile.socialSecurityNumber)
        put("address", profile.address)
        put("email", profile.email)
        put("phone", profile.phone)
        put("documentNumbers", profile.documentNumbers)
    }

    internal fun fromJson(json: JSONObject): PersonalProfile {
        require(json.optInt("version", 1) == 1) { "Μη υποστηριζόμενη έκδοση προσωπικού μητρώου." }
        return PersonalProfile(
            fullName = json.safeString("fullName"),
            alternativeNames = json.safeString("alternativeNames"),
            birthDate = json.safeString("birthDate"),
            birthPlace = json.safeString("birthPlace"),
            taxNumber = json.safeString("taxNumber"),
            socialSecurityNumber = json.safeString("socialSecurityNumber"),
            address = json.safeString("address"),
            email = json.safeString("email"),
            phone = json.safeString("phone"),
            documentNumbers = json.safeString("documentNumbers")
        ).also(::validate)
    }

    private fun profileFile(context: Context): File = context.filesDir.resolve("profile/profile.hgp")

    private fun validate(profile: PersonalProfile) {
        profile.fullName.requireBound(200)
        profile.alternativeNames.requireBound(500)
        profile.birthDate.requireBound(40)
        profile.birthPlace.requireBound(200)
        profile.taxNumber.requireBound(40)
        profile.socialSecurityNumber.requireBound(40)
        profile.address.requireBound(500)
        profile.email.requireBound(200)
        profile.phone.requireBound(80)
        profile.documentNumbers.requireBound(1_000)
    }

    private fun JSONObject.safeString(key: String): String = optString(key).also { it.requireBound(1_000) }
    private fun String.requireBound(max: Int) { require(length <= max) { "Ένα πεδίο του προσωπικού μητρώου είναι υπερβολικά μεγάλο." } }
    private const val MAX_PROFILE_BYTES = 16 * 1024
}
