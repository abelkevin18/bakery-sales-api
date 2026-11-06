package com.darkbit.bakerysales.storage.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SupabaseProperties.class)
public class StorageConfiguration {
}
