package com.cr.cag;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
public class CagScheduler
{
	@Bean
	public ThreadPoolTaskScheduler scheduler()
	{
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		
		scheduler.setThreadNamePrefix("cas-scheduler-");
		
		return scheduler;
	}
}
