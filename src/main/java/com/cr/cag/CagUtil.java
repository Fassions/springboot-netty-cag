package com.cr.cag;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.bson.Document;
import org.springframework.util.unit.DataSize;

import io.netty.buffer.ByteBuf;

public class CagUtil
{
	private static final ThreadLocal<DateFormat> DATE = ThreadLocal.withInitial(()->new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS"));
	private static final ThreadLocal<NumberFormat> NUM = ThreadLocal.withInitial(NumberFormat::getInstance);
	private static final char[] HEX = "0123456789ABCDEF".toCharArray();
	
	public static String toString(Document msg)
	{
		StringBuilder sb = new StringBuilder();
		
		sb.append('{');
		
		msg.forEach((k,v)->
		{
			if(sb.length() > 1)
				sb.append(", ");
			sb.append(k);
			sb.append('=');
			if(v instanceof String)
			{
				String s = (String)v;
				if(s.length() <= 200)
					sb.append(s);
				else
					sb.append(s,0,200-3).append("...");
			}
			else if(v instanceof Date)
				sb.append(DATE.get().format((Date)v));
			else
				sb.append(v);
		});
		
		sb.append('}');
		
		for(int i=0,l=sb.length();i<l;i++)
		{
			char c = sb.charAt(i);
			if(c == '\r' || c == '\n')
				sb.setCharAt(i,' ');
		}
		
		return sb.toString();
	}
	
	public static String toString(Number number, String suffix)
	{
		return suffix!=null?NUM.get().format(number)+suffix:NUM.get().format(number);
	}
	public static String toString(Number number)
	{
		return toString(number,null);
	}
	
	public static String toString(DataSize size)
	{
		if(size.toTerabytes() > 0)
			return Math.round(size.toGigabytes()/102.4F)/10.F + " TB";
		else if(size.toGigabytes() > 0)
			return Math.round(size.toMegabytes()/102.4F)/10.F + " GB";
		else if(size.toMegabytes() > 0)
			return Math.round(size.toKilobytes()/102.4F)/10.F + " MB";
		else if(size.toKilobytes() > 0)
			return Math.round(size.toBytes()/102.4F)/10.F + " KB";
		else if(size.toBytes() > 0)
			return size.toBytes() + " B";
		else
			return "0";
	}
	
	public static String toString(ByteBuf msg, int index, int length)
	{
		StringBuilder sb = new StringBuilder(12+length*3);
		
		sb.append('<');
		sb.append(length);
		sb.append('>');
		
		msg.forEachByte(index,length,b->
		{
			sb.append(' ')
			.append(HEX[b>>4&0x0f])
			.append(HEX[b&0x0f]);
			
			return true;
		});
		
		return sb.toString();
	}
}
