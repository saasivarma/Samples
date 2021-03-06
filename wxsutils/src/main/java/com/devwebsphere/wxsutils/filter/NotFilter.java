//
//This sample program is provided AS IS and may be used, executed, copied and
//modified without royalty payment by customer (a) for its own instruction and
//study, (b) in order to develop applications designed to run with an IBM
//WebSphere product, either for customer's own internal use or for redistribution
//by customer, as part of such an application, in customer's own products. "
//
//5724-J34 (C) COPYRIGHT International Business Machines Corp. 2009
//All Rights Reserved * Licensed Materials - Property of IBM
//
package com.devwebsphere.wxsutils.filter;

/**
 * This negates the result of a Filter
 * @author bnewport
 *
 */
public class NotFilter extends Filter 
{
	/**
	 * 
	 */
	private static final long serialVersionUID = -4277619313098301369L;
	Filter f;
	
	public NotFilter() {}
	
	public NotFilter(Filter f)
	{
		this.f = f;
	}

	@Override
	public boolean filter(Object fo) 
	{
		return !f.filter(fo);
	}

	public String toString()
	{
		return "!" + f.toString();
	}
}
