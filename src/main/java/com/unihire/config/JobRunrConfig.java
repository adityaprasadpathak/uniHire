package com.unihire.config;

import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.sql.common.SqlStorageProviderFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

import org.jobrunr.configuration.JobRunr;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.context.ApplicationContext;

@Configuration
public class JobRunrConfig {

    @Bean
    public StorageProvider storageProvider(DataSource dataSource) {
        return SqlStorageProviderFactory.using(dataSource);
    }

    @Bean
    public JobScheduler jobScheduler(StorageProvider storageProvider, ApplicationContext applicationContext) {
        return JobRunr.configure()
                .useJobActivator(applicationContext::getBean)
                .useStorageProvider(storageProvider)
                .useBackgroundJobServer()
                .useDashboard(8000)
                .initialize()
                .getJobScheduler();
    }
}
