package com.ruoyi.lab.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import com.ruoyi.lab.service.ReservationPolicy.ValidatedReservation;
import org.springframework.stereotype.Component;

/** Stable SHA-256 request identity independent of JSON formatting. */
@Component
public class ReservationRequestHasher
{
    public String hash(ValidatedReservation request)
    {
        String canonical = request.deviceId() + "|" + request.startTime() + "|"
                + request.endTime() + "|" + request.purpose() + "|"
                + (request.remark() == null ? "" : request.remark());
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
