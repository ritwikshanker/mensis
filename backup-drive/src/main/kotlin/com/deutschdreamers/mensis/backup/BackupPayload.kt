package com.deutschdreamers.mensis.backup

import com.deutschdreamers.mensis.backup.drive.DriveFile
import com.deutschdreamers.mensis.data.crypto.ContainerKey
import com.deutschdreamers.mensis.data.crypto.EncryptedContainer
import com.deutschdreamers.mensis.data.export.MensisExport
import com.deutschdreamers.mensis.data.export.MensisJson
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * What goes to Drive: the complete JSON export, gzipped and sealed in an [EncryptedContainer] with
 * the stored backup key. Only ciphertext, its size and a few non-health app properties leave the phone.
 */
object BackupPayload {
    /** Design §12: "mensis-backup.enc". Every backup has this name; Drive tells them apart by id. */
    const val FILE_NAME: String = "mensis-backup.enc"

    const val PROP_FORMAT_VERSION: String = "formatVersion"
    const val PROP_SCHEMA_VERSION: String = "schemaVersion"
    const val PROP_DEVICE: String = "device"
    const val PROP_FINGERPRINT: String = "fingerprint"
    const val PROP_KEY_ID: String = "keyId"

    private const val HMAC = "HmacSHA256"
    private val FINGERPRINT_LABEL = "mensis/backup-fingerprint/v1".toByteArray()
    private const val KEY_ID_HEX_CHARS = 16

    fun seal(
        export: MensisExport,
        key: ContainerKey,
    ): ByteArray = EncryptedContainer.seal(MensisJson.encode(export), key)

    /**
     * Identifies the backed-up content without revealing it: an HMAC, under a key derived from the
     * backup key, of the export with its timestamp blanked. Equal fingerprints mean nothing changed
     * since the last backup, so the upload can be skipped. Without the password it says nothing.
     */
    fun fingerprint(
        export: MensisExport,
        key: ContainerKey,
    ): String {
        val fingerprintKey = hmac(key.bytes, FINGERPRINT_LABEL)
        try {
            return hmac(fingerprintKey, MensisJson.encode(export.copy(exportedAt = ""))).toHex()
        } finally {
            fingerprintKey.fill(0)
        }
    }

    /**
     * Which password a backup was sealed under, from the salt alone. The salt is in every file's
     * header anyway, so this reveals nothing new; it lets pruning tell backups under an old
     * password from current ones.
     */
    fun keyId(key: ContainerKey): String = sha256(key.salt).toHex().take(KEY_ID_HEX_CHARS)

    fun appProperties(
        export: MensisExport,
        fingerprint: String,
        keyId: String,
        device: String,
    ): Map<String, String> =
        mapOf(
            PROP_FORMAT_VERSION to export.formatVersion.toString(),
            PROP_SCHEMA_VERSION to export.schemaVersion.toString(),
            PROP_DEVICE to device.take(MAX_DEVICE_CHARS),
            PROP_FINGERPRINT to fingerprint,
            PROP_KEY_ID to keyId,
        )

    /** Files Mensis wrote; anything else in the app folder is left alone. */
    fun isBackup(file: DriveFile): Boolean = file.name == FILE_NAME

    fun md5(bytes: ByteArray): String = MessageDigest.getInstance("MD5").digest(bytes).toHex()

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun hmac(
        key: ByteArray,
        data: ByteArray,
    ): ByteArray = Mac.getInstance(HMAC).apply { init(SecretKeySpec(key, HMAC)) }.doFinal(data)

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    /** Drive limits a key plus its value to 124 bytes; model names are far shorter. */
    private const val MAX_DEVICE_CHARS = 60
}
