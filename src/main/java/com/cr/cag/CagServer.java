package com.cr.cag;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.AttributeKey;
import io.netty.util.internal.PlatformDependent;

import lombok.extern.slf4j.Slf4j;

@Component
@Sharable
@Slf4j
public class CagServer extends ChannelInboundHandlerAdapter
{
	private static final AttributeKey<Long> ALIVE = AttributeKey.valueOf("ALIVE");
	
	@Autowired
	private CagProperties cag;
	
	@Autowired
	private CagMongo mongo;
	
	@Autowired
	private CagDecoder decoder;
	
	private NioEventLoopGroup boss;
	private NioEventLoopGroup worker;
	
	private ConcurrentLinkedDeque<ChannelHandlerContext> channels = new ConcurrentLinkedDeque<>();
	
	private LongAdder opened = new LongAdder();
	private LongAdder closed = new LongAdder();
	private LongAdder active = new LongAdder();
	
	@PostConstruct
	public void startup()
	{
		boss = new NioEventLoopGroup(cag.getNtBosses());
		worker = new NioEventLoopGroup(cag.getNtWorkers());
		
		ServerBootstrap server = new ServerBootstrap()
				.channel(NioServerSocketChannel.class)
				.option(ChannelOption.RCVBUF_ALLOCATOR,new FixedRecvByteBufAllocator((int)cag.getNtRcvBuf().toBytes()))
				.option(ChannelOption.SO_BACKLOG,cag.getSoBackLog())
				.option(ChannelOption.SO_RCVBUF,(int)cag.getSoRcvBuf().toBytes())
				.option(ChannelOption.SO_REUSEADDR,true)
				.group(boss,worker)
				.childHandler(this);
		
		InetSocketAddress local = new InetSocketAddress(cag.getAddress(),cag.getPort());
		
		server.bind(local).addListener(f->
		{
			Throwable t = f.cause();
			if(t == null)
			{
				log.info("cag-server({}) ready: {boss: {}, worker:{}, allocator: {}, direct: {}}",
						local,boss.executorCount(),worker.executorCount(),ByteBufAllocator.DEFAULT,PlatformDependent.maxDirectMemory());
			}
			else
			{
				log.error("cag-server({}) binding failure",local,t);
				
				System.exit(-1);
			}
		});
		
		long it = cag.getNtIdleTime().toMillis()/5;
		boss.scheduleAtFixedRate(this::closeIdle,it,it,TimeUnit.MILLISECONDS);
	}
	
	@PreDestroy
	public void shutdown()
	{
		boss.shutdownGracefully();
		worker.shutdownGracefully();
	}
	
	@Override
	public void handlerAdded(ChannelHandlerContext ctx) throws Exception
	{
		ctx.pipeline().addFirst(decoder);
	}
	
	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception
	{
		if(log.isDebugEnabled())
			log.debug("cag-session({}) opened",ctx.channel().remoteAddress());
		
		ctx.channel().attr(ALIVE).set(System.currentTimeMillis());
		
		channels.offer(ctx);
		
		opened.add(1L);
		active.add(1L);
	}
	
	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception
	{
		if(log.isDebugEnabled())
			log.debug("cag-session({}) closed",ctx.channel().remoteAddress());
		
		closed.add(1L);
		active.add(-1L);
	}
	
	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception
	{
		Document m = (Document)msg;
		
		if(log.isDebugEnabled())
			log.debug("cag-session({}) received: {}",ctx.channel().remoteAddress(),CagUtil.toString(m));
		
		ctx.channel().attr(ALIVE).set(System.currentTimeMillis());
		
		mongo.offer(m);
	}
	
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception
	{
		if(!Optional.ofNullable(cause.getMessage()).map(cag.getNtIgnoreEx()::contains).orElse(false))
			log.warn("cag-session({}) caught an exception",ctx.channel().remoteAddress(),cause);
		
		ctx.close();
	}
	
	private void closeIdle()
	{
		ChannelHandlerContext last = channels.peekLast();
		if(last != null)
		{
			long a = System.currentTimeMillis()-cag.getNtIdleTime().toMillis();
			
			while(true)
			{
				ChannelHandlerContext ctx = channels.poll();
				
				Channel ch = ctx.channel();
				if(ch.isActive())
				{
					if(ch.attr(ALIVE).get() <= a)
						ch.close();
					else
						channels.offer(ctx);
				}
				
				if(ctx == last)
					break;
			}
		}
	}
	
	public long opened()
	{
		return opened.sumThenReset();
	}
	
	public long closed()
	{
		return closed.sumThenReset();
	}
	
	public long active()
	{
		return active.sum();
	}
}