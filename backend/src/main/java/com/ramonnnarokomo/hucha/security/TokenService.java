package com.ramonnnarokomo.hucha.security;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.ramonnnarokomo.hucha.shared.HuchaProperties;
import com.ramonnnarokomo.hucha.user.User;

@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final HuchaProperties properties;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, HuchaProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public AccessToken issueAccessToken(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.security().accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("email", user.getEmail())
                .claim("name", user.getFullName())
                .claim("role", user.getRole().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(value, properties.security().accessTokenTtl().toSeconds());
    }

    public record AccessToken(String value, long expiresInSeconds) {
    }
}
