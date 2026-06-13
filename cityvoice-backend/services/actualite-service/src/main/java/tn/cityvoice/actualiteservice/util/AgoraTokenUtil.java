package tn.cityvoice.actualiteservice.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.*;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.Base64;

/**
 * Génération de tokens Agora AccessToken2.
 *
 * Implémentation basée sur le SDK officiel Agora Java :
 * github.com/AgoraIO/Tools/tree/master/DynamicKey/AgoraDynamicKey/java
 *
 * Format du token (identique au SDK officiel) :
 *   "007" + Base64( zlib(
 *     content = sign(uint16+32bytes) + message(raw bytes)
 *   ))
 *
 * où message = put(appId) + put(issueTs) + put(expire) + put(salt)
 *              + put(numServices) + [put(serviceType) + service.pack()]
 *
 * ServiceRtc.pack() = put(channelName) + put(uid) + put(numPriv) + [(put(key)+put(val))...]
 *
 * put(String/byte[]) = uint16(len) + bytes  (avec préfixe de longueur)
 * put(short/int)     = valeur LE brute (sans préfixe)
 */
public final class AgoraTokenUtil {

    private static final String VERSION = "007";

    // Service RTC
    private static final short SERVICE_RTC = 1;

    // Privilèges RTC
    private static final short PRIV_JOIN_CHANNEL  = 1;
    private static final short PRIV_PUBLISH_AUDIO = 2;
    private static final short PRIV_PUBLISH_VIDEO = 3;
    private static final short PRIV_PUBLISH_DATA  = 4;

    private AgoraTokenUtil() {}

    // ──────────────────────────────────────────────────────────────────────
    // Point d'entrée public
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Génère un token RTC Agora AccessToken2.
     *
     * @param appId           App ID (32 hex chars depuis console.agora.io)
     * @param appCertificate  Primary Certificate (32 hex chars)
     * @param channelName     Nom du canal
     * @param uid             UID (0 = wildcard)
     * @param tokenExpireSecs Durée de validité en secondes (ex : 3600)
     */
    public static String buildRtcToken(String appId,
                                       String appCertificate,
                                       String channelName,
                                       int uid,
                                       int tokenExpireSecs) throws Exception {

        int issueTs = (int) (System.currentTimeMillis() / 1000);
        int salt    = new SecureRandom().nextInt(100000000);
        String uidStr = (uid == 0) ? "" : String.valueOf(uid);

        // ── Construction du message (même logique que AccessToken2.build()) ──
        AgoraByteBuf msgBuf = new AgoraByteBuf();
        msgBuf.put(appId);                  // put(String) = uint16(32) + appId_bytes
        msgBuf.put(issueTs);                // uint32 LE
        msgBuf.put(tokenExpireSecs);        // uint32 LE (durée relative en secondes)
        msgBuf.put(salt);                   // uint32 LE
        msgBuf.put((short) 1);              // numServices = 1

        // ── Service RTC (même logique que ServiceRtc.pack()) ──
        msgBuf.put(SERVICE_RTC);            // serviceType = 1

        // Privilèges (triés par clé croissante)
        TreeMap<Short, Integer> privs = new TreeMap<>();
        privs.put(PRIV_JOIN_CHANNEL,  tokenExpireSecs);
        privs.put(PRIV_PUBLISH_AUDIO, tokenExpireSecs);
        privs.put(PRIV_PUBLISH_VIDEO, tokenExpireSecs);
        privs.put(PRIV_PUBLISH_DATA,  tokenExpireSecs);

        // Ordre confirmé par décodage d'un token officiel Agora Console :
        // numPrivileges + [key+val]... PUIS channelName + uid
        msgBuf.put((short) privs.size());   // numPrivileges — EN PREMIER

        for (Map.Entry<Short, Integer> e : privs.entrySet()) {
            msgBuf.put(e.getKey());         // uint16 privilege key
            msgBuf.put(e.getValue());       // uint32 privilege value
        }

        msgBuf.put(channelName);            // channelName APRÈS les privilèges
        msgBuf.put(uidStr);                 // uid

        byte[] msgBytes = msgBuf.asBytes();

        // ── Signature (certificat hex → bytes binaires comme clé HMAC) ──
        byte[] sig = hmacSHA256(appCertificate, msgBytes);

        // ── Contenu final = signature (avec uint16 prefix) + message (brut) ──
        // Réplique exacte du SDK officiel :
        //   ByteBuf content = new ByteBuf();
        //   content.put(sign);                   → uint16(32) + sig_bytes
        //   content.buffer.put(buffer.asBytes()) → message en bytes bruts (pas de prefix)
        AgoraByteBuf contentBuf = new AgoraByteBuf();
        contentBuf.put(sig);                // uint16(32) + sig — EN PREMIER
        contentBuf.put(msgBytes, msgBytes.length); // message en bytes BRUTS (pas de prefix)

        // ── Compression ZLIB + Base64 ──
        byte[] compressed = zlibCompress(contentBuf.asBytes());
        return VERSION + Base64.getEncoder().encodeToString(compressed);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Cryptographie
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Clé HMAC = bytes UTF-8 de la chaîne hexadécimale du certificat
     * (identique à appCertificate.getBytes("UTF-8") dans le SDK officiel Agora Java).
     * Ex : "2572da1948e04669925ab0d3e97996d1" → 32 bytes ASCII
     */
    private static byte[] hmacSHA256(String appCertificate, byte[] data) throws Exception {
        byte[] key = appCertificate.getBytes("UTF-8");  // 32 chars hex → 32 bytes ASCII
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Compression ZLIB
    // ──────────────────────────────────────────────────────────────────────

    private static byte[] zlibCompress(byte[] data) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, false);
        try (DeflaterOutputStream dos = new DeflaterOutputStream(bos, deflater)) {
            dos.write(data);
        }
        return bos.toByteArray();
    }
}
