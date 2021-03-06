//
//This sample program is provided AS IS and may be used, executed, copied and
//modified without royalty payment by customer (a) for its own instruction and 
//study, (b) in order to develop applications designed to run with an IBM 
//WebSphere product, either for customer's own internal use or for redistribution 
//by customer, as part of such an application, in customer's own products. "
//
//5724-J34 (C) COPYRIGHT International Business Machines Corp. 2005
//All Rights Reserved * Licensed Materials - Property of IBM
//
package com.devwebsphere.wxsutils.wxsmap;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.devwebsphere.wxsutils.WXSUtils;
import com.devwebsphere.wxsutils.jmx.agent.AgentMBeanImpl;
import com.devwebsphere.wxsutils.wxsagent.ReduceAgentFactory;
import com.ibm.websphere.objectgrid.ObjectGridRuntimeException;
import com.ibm.websphere.objectgrid.ObjectMap;
import com.ibm.websphere.objectgrid.Session;
import com.ibm.websphere.objectgrid.datagrid.ReduceGridAgent;

/**
 * This is used to fetch all the values for a set of keys within a given partition using a single hop.
 */
public class GetAllAgent<K extends Serializable, V extends Serializable> implements ReduceGridAgent {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6568906743945108310L;
	static Logger logger = Logger.getLogger(GetAllAgent.class.getName());

	public List<K> batch;

	public static class Factory<K extends Serializable, V extends Serializable> implements ReduceAgentFactory<GetAllAgent<K, V>, K, V, Map<K, V>> {

		public GetAllAgent<K, V> newAgent(List<K> keys) {
			GetAllAgent<K, V> a = new GetAllAgent<K, V>();
			a.batch = keys;
			return a;
		}

		public GetAllAgent<K, V> newAgent(Map<K, V> map) {
			throw new ObjectGridRuntimeException("NOT IMPLEMENTED");
		}

		public K getKey(GetAllAgent<K, V> a) {
			return a.batch.get(0);
		}

		public Map<K, V> emptyResult() {
			return Collections.emptyMap();
		}

	};

	public Object reduce(Session sess, ObjectMap map) {
		return null;
	}

	public Object reduce(Session sess, ObjectMap map, Collection arg2) {
		AgentMBeanImpl bean = WXSUtils.getAgentMBeanManager().getBean(sess.getObjectGrid().getName(), this.getClass().getName());
		long startNS = System.nanoTime();
		try {
			ObjectMap m = sess.getMap(map.getName());
			Map<K, V> results = new HashMap<K, V>();
			for (K k : batch) {
				results.put(k, (V) m.get(k));
			}
			bean.getKeysMetric().logTime(System.nanoTime() - startNS);
			return results;
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Exception", e);
			bean.getKeysMetric().logException(e);
			throw new ObjectGridRuntimeException(e);
		}
	}

	public Object reduceResults(Collection maps) {
		Collection<Map<K, V>> list = maps;
		Map<K, V> union = new HashMap<K, V>();
		for (Map<K, V> m : list) {
			union.putAll(m);
		}
		return union;
	}

	public GetAllAgent() {
	}
}
