package com.cr.cag;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

import javax.annotation.PostConstruct;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import com.mongodb.WriteConcern;
import com.mongodb.client.MongoCollection;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class CagMongo
{
	@Autowired
	private CagProperties cag;
	
	@Autowired
	private MongoTemplate mongo;
	
	@Autowired
	private ThreadPoolTaskExecutor executor;
	
	private LinkedBlockingQueue<List<Document>> list = new LinkedBlockingQueue<>();
	private Queue<Document> queue = new ConcurrentLinkedQueue<>();
	private Semaphore semaphore = new Semaphore(0);
	
	private LongAdder stock = new LongAdder();
	private LongAdder saved = new LongAdder();
	private LongAdder failure = new LongAdder();
	
	@PostConstruct
	public void startup()
	{
		try
		{
			mongo.collectionExists(cag.getMgCollection());
			
			for(int w=cag.getMgWorkers();w>0;w--)
				list.offer(new ArrayList<>());
			
			executor.execute(this::boss);
			
			log.info("cag-mongo ready");
		}
		catch(Exception e)
		{
			log.error("cag-mongo connecting failure",e);
			
			System.exit(-1);
		}
	}
	
	public void offer(Document msg)
	{
		queue.offer(msg);
		
		int s = msg.getInteger("size");
		semaphore.release((s+1024-1)>>10);
		stock.add(s);
	}
	
	private void boss()
	{
		while(true)
		{
			try
			{
				List<Document> l = list.take();
				
				int kb = (int)cag.getMgBatch().toKilobytes();
				if(!semaphore.tryAcquire(kb,cag.getMgDelay().getSeconds(),TimeUnit.SECONDS))
				{
					kb = Math.min(kb,semaphore.availablePermits());
					if(kb > 0)
						semaphore.acquire(kb);
					else
						continue;
				}
				
				int b = 0;
				while(kb > 0)
				{
					Document m = queue.poll();
					
					l.add(m);
					
					int s = m.getInteger("size");
					b += s;
					kb -= (s+1024-1)>>10;
				}
				semaphore.acquire(-kb);
				
				int _b = b;
				executor.execute(()->worker(l,_b));
			}
			catch(InterruptedException e)
			{
				return;
			}
		}
	}
	
	private void worker(List<Document> msg, int batch)
	{
		long t = System.currentTimeMillis();
		int b = batch;
		int f = 0;
		
		MongoCollection<Document> c = null;
		try
		{
			if(c == null)
				c = mongo.getDb().getCollection(cag.getMgCollection());
			
			c.insertMany(msg);
		}
		catch(Exception e)
		{
			for(Document m : msg)
			{
				try
				{
					if(c == null)
						c = mongo.getDb().getCollection(cag.getMgCollection());
					
					c.insertOne(m);
				}
				catch(Exception ex)
				{
					b -= m.getInteger("size");
					f ++;
					
					log.warn("cag-mongo saving failuer: {}",CagUtil.toString(m),ex);
				}
			}
		}
		finally
		{
			int s = msg.size()-f;
			
			msg.clear();
			list.offer(msg);
			
			stock.add(-batch);
			
			saved.add(s);
			failure.add(f);
			
			if(s > 0)
			{
				String _l = CagUtil.toString(s);
				String _b = CagUtil.toString(DataSize.ofBytes(b));
				String _t = CagUtil.toString(System.currentTimeMillis()-t," ms");
				String _q = CagUtil.toString(queue.size());
				String _s = CagUtil.toString(DataSize.ofBytes(stock.sum()));
				
				if(f == 0)
					log.info("cag-mongo saved: {list: {}, batch: {}, time: {}, queue: {}, stock: {}}",_l,_b,_t,_q,_s);
				else
					log.warn("cag-mongo saved: {loop: {}, batch: {}, time: {}, queue: {}, stock: {}}",_l,_b,_t,_q,_s);
			}
		}
	}
	
	public long saved()
	{
		return saved.sumThenReset();
	}
	
	public long failure()
	{
		return failure.sumThenReset();
	}
	
	@Scheduled(cron="0 0 0/2 * * ?")
	private void clean()
	{
		long c = cag.getMgClean().toMillis();
		if(c > 0)
		{
			try
			{
				Document f = new Document("insertTime",new Document("$lt",new Date(System.currentTimeMillis()-c)));
				mongo.getDb().getCollection(cag.getMgCollection()).withWriteConcern(WriteConcern.UNACKNOWLEDGED).deleteMany(f);
				
				log.info("cag-mongo cleaned");
			}
			catch(Exception e)
			{
				log.warn("cag-mongo cleaning failuer",e);
			}
		}
	}
}

