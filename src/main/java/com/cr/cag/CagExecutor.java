package com.cr.cag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class CagExecutor
{
	@Autowired
	private CagProperties cag;
	
	@Bean
	public ThreadPoolTaskExecutor executor()
	{
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		
		executor.setCorePoolSize(1+cag.getMgWorkers());
		executor.setThreadNamePrefix("cag-executor-");
		
		return executor;
	}
}
