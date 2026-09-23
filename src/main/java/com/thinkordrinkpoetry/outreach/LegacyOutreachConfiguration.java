package com.thinkordrinkpoetry.outreach;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LegacyOutreachProperties.class)
class LegacyOutreachConfiguration {
}
