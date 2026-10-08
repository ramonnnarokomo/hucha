package com.ramonnnarokomo.hucha.analytics;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ramonnnarokomo.hucha.analytics.AnalyticsDtos.Summary;
import com.ramonnnarokomo.hucha.security.CurrentUser;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analytics;

    public AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/summary")
    public Summary summary(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "6") int months) {
        return analytics.summary(CurrentUser.id(jwt), months);
    }
}
