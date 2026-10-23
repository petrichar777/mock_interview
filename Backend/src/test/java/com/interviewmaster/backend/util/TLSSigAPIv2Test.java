package com.interviewmaster.backend.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.zip.Inflater;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class TLSSigAPIv2Test {

    private static final long SDK_APP_ID = 1400000000L;
    private static final String SECRET_KEY = "test-secret";
    private static final long FIXED_EPOCH_SECOND = 1_700_000_000L;

    @Test
    void genUserSig_producesVerifiableSignature() throws Exception {
        Clock clock = Clock.fixed(Instant.ofEpochSecond(FIXED_EPOCH_SECOND), ZoneOffset.UTC);
        TLSSigAPIv2 sigApi = new TLSSigAPIv2(SDK_APP_ID, SECRET_KEY, clock);

        String userSig = sigApi.genUserSig("u123", 7200);

        assertTrue(userSig.chars().noneMatch(ch -> ch == '+' || ch == '/' || ch == '='));
        Map<String, Object> document = decode(userSig);
        assertEquals("2.0", document.get("TLS.ver"));
        assertEquals("u123", document.get("TLS.identifier"));
        assertEquals(SDK_APP_ID, ((Number) document.get("TLS.sdkappid")).longValue());
        assertEquals(7200, ((Number) document.get("TLS.expire")).longValue());
        assertEquals(FIXED_EPOCH_SECOND, ((Number) document.get("TLS.time")).longValue());

        String content = "TLS.identifier:u123\n"
                + "TLS.sdkappid:" + SDK_APP_ID + "\n"
                + "TLS.time:" + FIXED_EPOCH_SECOND + "\n"
                + "TLS.expire:7200\n";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expectedSignature = Base64.getEncoder().encodeToString(
                mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        assertEquals(expectedSignature, document.get("TLS.sig"));
    }

    @Test
    void genUserSig_sameClock_returnsSameValue() {
        Clock clock = Clock.fixed(Instant.ofEpochSecond(FIXED_EPOCH_SECOND), ZoneOffset.UTC);
        TLSSigAPIv2 sigApi = new TLSSigAPIv2(SDK_APP_ID, SECRET_KEY, clock);

        assertEquals(sigApi.genUserSig("u1", 3600), sigApi.genUserSig("u1", 3600));
    }

    @Test
    void genUserSig_invalidUserId_throwsIllegalArgumentException() {
        TLSSigAPIv2 sigApi = new TLSSigAPIv2(SDK_APP_ID, SECRET_KEY);

        assertThrows(IllegalArgumentException.class, () -> sigApi.genUserSig("bad id", 7200));
        assertThrows(IllegalArgumentException.class, () -> sigApi.genUserSig("a".repeat(33), 7200));
        assertThrows(IllegalArgumentException.class, () -> sigApi.genUserSig(null, 7200));
    }

    @Test
    void genUserSig_invalidExpire_throwsIllegalArgumentException() {
        TLSSigAPIv2 sigApi = new TLSSigAPIv2(SDK_APP_ID, SECRET_KEY);

        assertThrows(IllegalArgumentException.class, () -> sigApi.genUserSig("u1", 0));
        assertThrows(IllegalArgumentException.class, () -> sigApi.genUserSig("u1", -1));
    }

    @Test
    void constructor_invalidConfiguration_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new TLSSigAPIv2(0, SECRET_KEY));
        assertThrows(IllegalArgumentException.class, () -> new TLSSigAPIv2(SDK_APP_ID, " "));
        assertThrows(IllegalArgumentException.class, () -> new TLSSigAPIv2(SDK_APP_ID, null));
        assertThrows(IllegalArgumentException.class,
                () -> new TLSSigAPIv2(SDK_APP_ID, SECRET_KEY, null));
    }

    private Map<String, Object> decode(String userSig) throws Exception {
        String base64 = userSig.replace('*', '+').replace('-', '/').replace('_', '=');
        byte[] compressed = Base64.getDecoder().decode(base64);
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(compressed);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0) {
                    break;
                }
                output.write(buffer, 0, count);
            }
            return new ObjectMapper().readValue(
                    output.toByteArray(), new TypeReference<Map<String, Object>>() { });
        } finally {
            inflater.end();
        }
    }
}
