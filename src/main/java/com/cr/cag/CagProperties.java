package com.cr.cag;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DataSizeUnit;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.util.unit.DataUnit;

import lombok.Data;

@ConfigurationProperties("cag")
@Component
@Data
public class CagProperties
{
	private String address = "0.0.0.0";
	private int port = 21339;
	
	private int ntBosses = 2;
	private int ntWorkers = 0;
	@DataSizeUnit(DataUnit.KILOBYTES)
	private DataSize ntRcvBuf = DataSize.ofKilobytes(64);
	@DurationUnit(ChronoUnit.SECONDS)
	private Duration ntIdleTime = Duration.ofSeconds(300);
	private Set<String> ntIgnoreEx = new ConcurrentSkipListSet<String>();
	
	private int soBackLog = 1024;
	@DataSizeUnit(DataUnit.KILOBYTES)
	private DataSize soRcvBuf = DataSize.ofKilobytes(256);
	
	@DurationUnit(ChronoUnit.SECONDS)
	private Duration dcBufTime = Duration.ofSeconds(10);
	@DataSizeUnit(DataUnit.KILOBYTES)
	private DataSize dcMaxSize = DataSize.ofMegabytes(200);
	private String dcCharset = "UTF-8";
	private boolean dcIgnoreText = false;
	
	private int mgWorkers = 10;
	@DataSizeUnit(DataUnit.KILOBYTES)
	private DataSize mgBatch = DataSize.ofMegabytes(50);
	@DurationUnit(ChronoUnit.SECONDS)
	private Duration mgDelay = Duration.ofSeconds(20);
	@DurationUnit(ChronoUnit.HOURS)
	private Duration mgClean = Duration.ofHours(2);
	private String mgCollection = "COL_HTTPS_ORGINFO";
}
