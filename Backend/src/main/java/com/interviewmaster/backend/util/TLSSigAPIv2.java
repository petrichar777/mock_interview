package com.interviewmaster.backend.util;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Base64;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 腾讯云 TRTC UserSig 生成器，算法与官方 TLSSigAPIv2 保持一致。
 * 不引入官方 jar 里的 org.json 依赖，编码格式固定，无需动态 JSON。
 */
public final class TLSSigAPIv2 {

    private static final Pattern USER_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,32}$");
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final long sdkAppId;
    private final String secretKey;
    private final Clock clock;

    public TLSSigAPIv2(long sdkAppId, String secretKey) {
        this(sdkAppId, secretKey, Clock.systemUTC());
    }

    TLSSigAPIv2(long sdkAppId, String secretKey, Clock clock) {
        if (sdkAppId <= 0) {
            throw new IllegalArgumentException("TRTC SDKAppID 必须大于 0");
        }
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException("TRTC SecretKey 不能为空");
        }
        if (clock == null) {
            throw new IllegalArgumentException("Clock 不能为空");
        }
        this.sdkAppId = sdkAppId;
        this.secretKey = secretKey;
        this.clock = clock;
    }

    public String genUserSig(String userId, long expireSeconds) {
        if (userId == null || !USER_ID_PATTERN.matcher(userId).matches()) {
            throw new IllegalArgumentException("TRTC 用户 ID 只能由字母、数字、下划线、短横线组成，长度 1~32");
        }
        if (expireSeconds <= 0) {
            throw new IllegalArgumentException("UserSig 有效期必须大于 0");
        }
        long currentTime = clock.instant().getEpochSecond();
        String signature = hmacSha256(userId, currentTime, expireSeconds);
        String sigDocument = "{\"TLS.ver\":\"2.0\""
                + ",\"TLS.identifier\":\"" + userId + "\""
                + ",\"TLS.sdkappid\":" + sdkAppId
                + ",\"TLS.expire\":" + expireSeconds
                + ",\"TLS.time\":" + currentTime
                + ",\"TLS.sig\":\"" + signature + "\"}";
        return encodeTencentBase64(deflate(sigDocument.getBytes(StandardCharsets.UTF_8)));
    }

    private String hmacSha256(String userId, long currentTime, long expireSeconds) {
        String contentToSign = "TLS.identifier:" + userId + "\n"
                + "TLS.sdkappid:" + sdkAppId + "\n"
                + "TLS.time:" + currentTime + "\n"
                + "TLS.expire:" + expireSeconds + "\n";
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getEncoder().encodeToString(
                    mac.doFinal(contentToSign.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("failed to sign TRTC user sig", ex);
        }
    }

    private byte[] deflate(byte[] input) {
        Deflater deflater = new Deflater();
        try {
            deflater.setInput(input);
            deflater.finish();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private String encodeTencentBase64(byte[] input) {
        // 腾讯云使用自定义 URL 安全字符表：+ -> *，/ -> -，= -> _
        return Base64.getEncoder().encodeToString(input)
                .replace('+', '*')
                .replace('/', '-')
                .replace('=', '_');
    }
}
