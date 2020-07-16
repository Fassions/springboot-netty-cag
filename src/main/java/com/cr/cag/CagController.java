package com.cr.cag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/")
@Slf4j
public class CagController
{
	@Autowired
	private CagDecoder decoder;
	
	@PostMapping(value= {"/dump/{seconds}","/dump"})
	public void dump(@PathVariable(value="seconds",required=false) Integer seconds) throws Exception
	{
		int s = seconds!=null?seconds:300;
		
		if(log.isDebugEnabled())
			log.debug("cag-api dump: {} s",s);
		
		decoder.dump(s);
	}
}
