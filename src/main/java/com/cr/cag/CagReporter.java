package com.cr.cag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import io.netty.util.internal.PlatformDependent;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class CagReporter
{
	@Autowired
	private CagServer server;
	
	@Autowired
	private CagDecoder decoder;
	
	@Autowired
	private CagMongo mongo;
	
	@Scheduled(cron="0 * * * * ?")
	private void report()
	{
		String _o = CagUtil.toString(server.opened());
		String _c = CagUtil.toString(server.closed());
		String _a = CagUtil.toString(server.active());
		String _r = CagUtil.toString(DataSize.ofBytes(decoder.read()));
		String _s = CagUtil.toString(mongo.saved());
		String _f = CagUtil.toString(mongo.failure());
		Runtime r = Runtime.getRuntime();
		String _h = CagUtil.toString(DataSize.ofBytes(r.totalMemory()-r.freeMemory()));
		String _d = CagUtil.toString(DataSize.ofBytes(PlatformDependent.usedDirectMemory()));
		
		log.info("cag-report: {opened: {}, closed: {}, active: {}, read: {}, saved: {}, failure: {}, heap: {}, direct: {}}",_o,_c,_a,_r,_s,_f,_h,_d);
	}
}
