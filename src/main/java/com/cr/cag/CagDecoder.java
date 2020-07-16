package com.cr.cag;

import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.LongAdder;

import javax.annotation.PostConstruct;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.AttributeKey;

import lombok.extern.slf4j.Slf4j;

@Component
@Sharable
@Slf4j
public class CagDecoder extends ChannelInboundHandlerAdapter
{
	private static final AttributeKey<ByteBuf> BUF = AttributeKey.valueOf("BUF");
	private static final AttributeKey<Long> UPT = AttributeKey.valueOf("UPT");
	
	private static final Document WAITING = new Document();
	private static final Document FAILURE = new Document();
	
	private static final ThreadLocal<SimpleDateFormat> DATE = ThreadLocal.withInitial(()->new SimpleDateFormat("yyyyMMddHHmmssSSS"));
	
	@Autowired
	private CagProperties cag;
	
	private Charset charset;
	
	private LongAdder read = new LongAdder();
	
	private volatile long dump = System.currentTimeMillis();
	
	@PostConstruct
	private void init()
	{
		charset = Charset.forName(cag.getDcCharset());
	}
	
	@Override
	public void handlerRemoved(ChannelHandlerContext ctx) throws Exception
	{
		ByteBuf buf = ctx.channel().attr(BUF).getAndSet(null);
		if(buf != null)
			buf.release();
	}
	
	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception
	{
		ByteBuf m = (ByteBuf)msg;
		read.add(m.readableBytes());
		
		ByteBuf buf = ctx.channel().attr(BUF).getAndSet(null);
		if(buf == null)
			buf = m;
		else if(System.currentTimeMillis()-ctx.channel().attr(UPT).getAndSet(null) >= cag.getDcBufTime().toMillis())
		{
			log.warn("cag-session({}) discarded buffer: {}}",ctx.channel().remoteAddress(),dump(buf));
			
			buf.release();
			buf = m;
		}
		else if(buf instanceof CompositeByteBuf)
			buf = ((CompositeByteBuf)buf).addComponent(true,m);
		else
			buf = ByteBufAllocator.DEFAULT.compositeDirectBuffer(Integer.MAX_VALUE).addComponents(true,buf,m);
		
		if(log.isDebugEnabled())
			log.debug("cag-session({}) read: {}}",ctx.channel().remoteAddress(),dump(m));
		
		channelRead(ctx,buf);
	}
	
	private void channelRead(ChannelHandlerContext ctx, ByteBuf buf) throws Exception
	{
		int r = buf.readerIndex();
		int w = buf.writerIndex();
		
		while(true)
		{
			int p = buf.indexOf(buf.readerIndex(),w,(byte)0);
			if(p < 0)
			{
				log.warn("cag-session({}) discarded dump: {}}",ctx.channel().remoteAddress(),dump(buf,r,w));
				
				buf.release();
				
				return;
			}
			
			Document m = decode(ctx,buf.readerIndex(p));
			if(m != null)
			{
				if(r < p)
					log.warn("cag-session({}) discarded debris: {}}",ctx.channel().remoteAddress(),dump(buf,r,p));
				
				if(m == WAITING)
				{
					//if(log.isDebugEnabled())
					//	log.debug("cag-session({}) buffered: {}}",ctx.channel().remoteAddress(),dump(buf,p,w));
					
					ctx.channel().attr(BUF).set(buf);
					ctx.channel().attr(UPT).set(System.currentTimeMillis());
					
					return;
				}
				
				r = buf.readerIndex();
				
				if(m == FAILURE)
					log.warn("cag-session({}) discarded frame: {}} ",ctx.channel().remoteAddress(),dump(buf,p,r));
				else
					super.channelRead(ctx,m);
				
				ctx.writeAndFlush(buf.copy(p,4*9));
				
				if(r == w)
				{
					buf.release();
					
					return;
				}
			}
			else
				buf.skipBytes(1);
		}
	}
	
	private Document decode(ChannelHandlerContext ctx, ByteBuf buf)
	{
		if(buf.readableBytes() < 100)
			return WAITING;
		
		int p = buf.readerIndex();
		
		//int MsgProtocal = buf.getIntLE(p);
		//p += 4;
		//int MsgID = buf.getIntLE(p);
		//p += 4;
		if(buf.forEachByte(p,8,b->b==0) != -1)
			return null;
		p += 8;
		
		int MsgLen = buf.getIntLE(p);
		p += 4;
		if(MsgLen < 100)
			return null;
		if(MsgLen > cag.getDcMaxSize().toBytes())
			return null;
		
		int MsgMaxLen = buf.getIntLE(p);
		p += 4;
		if(MsgMaxLen < MsgLen)
			return null;
		
		//int MsgFieldNum = buf.getIntLE(p);
		p += 4;
		//if(MsgFieldNum != 0)
		//	return null;
		
		//int MsgDataType = buf.getIntLE(p);
		p += 4;
		//switch(MsgDataType)
		//{
		//case 1:
		//	if(MsgLen > 100*1024)
		//		return null;
		//	break;
		//case 2:
		//	if(MsgLen > 20*1024*1024)
		//		return null;
		//	break;
		//case 3:
		//	if(MsgLen > cag.getDcMaxSize().toBytes())
		//		return null;
		//	break;
		//default:
		//	return null;
		//}
		
		//int MsgCommand = buf.getIntLE(p);
		//p += 4;
		//int MsgCode = buf.getIntLE(p);
		//p += 4;
		//int SysCode = buf.getIntLE(p);
		//p += 4;
		//int PackagePtotocal = buf.getIntLE(p);
		//p += 4;
		//int PackageType = buf.getIntLE(p);
		//p += 4;
		//int Uid = buf.getIntLE(p);
		//p += 4;
		//int ClientIP = buf.getIntLE(p);
		//p += 4;
		//byte[] Compid = new byte[48];
		//buf.getBytes(p,Compid);
		//p += 48;
		if(buf.forEachByte(p,76,b->b==0) != -1)
			return null;
		p += 76;
		
		if(buf.readableBytes() < MsgLen)
			return WAITING;
		
		try
		{
			Document m = new Document();
			
			for(int i=100;i<MsgLen;)
			{
				int h = buf.getShortLE(p);
				p += 2;
				//int t = buf.getShortLE(p);
				p += 2;
				int d = buf.getIntLE(p);
				p += 4;
				
				String k = buf.getCharSequence(p,h-8-1,charset).toString();
				p += h-8;
				
				String v = d>0?buf.getCharSequence(p,d-1,charset).toString():"";
				p += d;
				
				m.put(k,v);
				
				i += h+d;
			}
			
			if(m.isEmpty())
				throw new Exception("empty message");
			
			m.computeIfPresent("time",(k,v)->
			{
				try
				{
					return DATE.get().parse(v.toString());
				}
				catch(Exception e)
				{
					return v;
				}
			});
			
			if(cag.isDcIgnoreText())
				m.put("text","");
			
			m.put("size",MsgLen);
			m.put("insertTime",new Date());
			m.put("STATE",0);
			
			return m;
		}
		catch(Exception e)
		{
			return FAILURE;
		}
		finally
		{
			buf.skipBytes(MsgLen);
		}
	}
	
	public long read()
	{
		return read.sumThenReset();
	}
	
	public void dump(int seconds)
	{
		dump = System.currentTimeMillis()+seconds*1000L;
	}
	
	private String dump(ByteBuf buf, int from, int to)
	{
		int length = to - from;
		return System.currentTimeMillis()>dump?length+" bytes":CagUtil.toString(buf,from,length);
	}
	private String dump(ByteBuf buf)
	{
		return dump(buf,buf.readerIndex(),buf.writerIndex());
	}
}
