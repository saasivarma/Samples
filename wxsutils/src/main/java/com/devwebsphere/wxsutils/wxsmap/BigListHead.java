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
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.devwebsphere.wxsutils.filter.Filter;
import com.devwebsphere.wxsutils.wxsmap.SetAddRemoveAgent.Operation;
import com.ibm.websphere.objectgrid.ObjectGridException;
import com.ibm.websphere.objectgrid.ObjectGridRuntimeException;
import com.ibm.websphere.objectgrid.ObjectMap;
import com.ibm.websphere.objectgrid.Session;
import com.ibm.websphere.objectgrid.UndefinedMapException;

/**
 * BigLists are split in buckets of at most BUCKET_SIZE items. Items can be added to the
 * left or right of the list using the push operation and popped from either side also.
 * The list is stored in two maps. One has meta data with the number of the left and
 * right bucket. The other holds the buckets as normal lists. The leftmost and rightmost
 * buckets can have less than BUCKET_SIZE items but buckets in the middle are always
 * full. Items can only be removed from the left or right using pop.
 * @author bnewport
 *
 */
public class BigListHead <V extends Serializable> implements Serializable 
{
	static Logger logger = Logger.getLogger(BigListHead.class.getName());
	
	static public enum LR { LEFT, RIGHT };
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -1121567440619553717L;

	
	int leftBucket;
	int rightBucket;
	/**
	 * This is the maximum size of a bucket
	 */
	int bucketSize;
	
	public BigListHead()
	{
		
	}

	ObjectMap getBucketMap(Session sess, ObjectMap map)
	{
		String bucketMapName = map.getName() + "_b";
		try
		{
			return sess.getMap(bucketMapName);
		}
		catch(UndefinedMapException e)
		{
			logger.log(Level.SEVERE, "Unknown map " + bucketMapName, e);
			throw new ObjectGridRuntimeException("Bucket map doesn't exist " + bucketMapName, e);
		}
	}
	
	String getBucketKey(Object key, int bucket)
	{
		StringBuilder sb = new StringBuilder(key.toString());
		sb.append("#");
		sb.append(Integer.toString(bucket));
		return sb.toString();
	}
	
	public BigListHead(Session sess, ObjectMap map, Object key, V value, int bSize)
		throws ObjectGridException
	{
		ObjectMap bmap = getBucketMap(sess, map);
		leftBucket = 0;
		rightBucket = leftBucket;
		bucketSize = bSize;
		String bkey = getBucketKey(key, leftBucket);
		ArrayList<V> list = new ArrayList<V>(1);
		list.add(value);
		bmap.insert(bkey, list);
		map.insert(key, this);
	}

	/**
	 * This assumes this class was read from the map with an update lock
	 * @param sess
	 * @param map
	 * @param key
	 * @param isLeft
	 * @param value
	 * @throws ObjectGridException
	 */
	public void push(Session sess, ObjectMap map, Object key, LR isLeft, V value)
		throws ObjectGridException
	{
		ObjectMap bmap = getBucketMap(sess, map);
		while(true)
		{
			// get current bucket
			String bkey = getBucketKey(key, isLeft == LR.LEFT ? leftBucket : rightBucket);
			ArrayList<V> list = (ArrayList<V>)bmap.getForUpdate(bkey);
			// if empty then insert empty list
			if(list == null)
			{
				list = new ArrayList<V>();
				bmap.insert(bkey, list);
			}
			// if current bucket has room then push value
			if(list.size() < bucketSize)
			{
				if(isLeft == LR.LEFT)
					list.add(0, value);
				else
					list.add(value);
				bmap.update(bkey, list);
				break;
			}
			else
				// otherwise move over and try next bucket
				if(isLeft == LR.LEFT)
					leftBucket--;
				else
					rightBucket++;
		}
		// update list head
		map.update(key, this);
	}
	
	public <K extends Serializable> V pop(Session sess, ObjectMap map, Object key, LR isLeft, K dirtyKey)
		throws ObjectGridException
	{
		ObjectMap bmap = getBucketMap(sess, map);
		String bkey = getBucketKey(key, isLeft == LR.LEFT ? leftBucket : rightBucket);
		ArrayList<V> list = (ArrayList<V>)bmap.get(bkey);
		V rc = null;
		if(list != null)
		{
			if(isLeft == LR.LEFT)
				rc = list.remove(0);
			else
				rc = list.remove(list.size() - 1);
			if(list.isEmpty()) // a bucket is now empty
			{
				bmap.remove(bkey);
				if(leftBucket == rightBucket) // the list is now empty
				{
					map.remove(key);
					if(dirtyKey != null)
					{
						removeFromDirtySet(sess, map, key, dirtyKey);
					}
				}
				else
				{
					if(isLeft == LR.LEFT)
						leftBucket++;
					else
						rightBucket--;
					map.update(key, this);
				}
			}
			else
			{
				bmap.update(bkey, list);
			}
		}
		return rc;
	}
	
	/**
	 * This removes the list key from the specified dirty set.
	 * @param <K>
	 * @param sess
	 * @param map
	 * @param key
	 * @param dirtyKey
	 * @throws ObjectGridException
	 */
	<K extends Serializable> void removeFromDirtySet(Session sess, ObjectMap map, Object key, K dirtyKey)
		throws ObjectGridException
	{
		if(dirtyKey != null)
		{
			if(logger.isLoggable(Level.FINE))
			{
				logger.log(Level.FINE, "Removing key [" + key + "] from dirtySet [" + dirtyKey + "]");
			}
			ObjectMap dirtyMap = sess.getMap(BigListPushAgent.getDirtySetMapNameForListMap(map.getName()));
			SetAddRemoveAgent.doOperation(sess, dirtyMap, dirtyKey, Operation.REMOVE, (Serializable)key);
		}
	}
	
	public int size(Session sess, ObjectMap map, Object key)
		throws ObjectGridException
	{
		ObjectMap bmap = getBucketMap(sess, map);
		if(leftBucket == rightBucket)
		{
			ArrayList<V> list = (ArrayList<V>)bmap.get(getBucketKey(key, leftBucket));
			return list.size();
		}
		else
		{
			ArrayList<V> lList = (ArrayList<V>)bmap.get(getBucketKey(key, leftBucket));
			ArrayList<V> rList = (ArrayList<V>)bmap.get(getBucketKey(key, rightBucket));
			int middleBucketCount = rightBucket - leftBucket - 1;
			return lList.size() + rList.size() + middleBucketCount * bucketSize;
		}
	}
	
	public void remove(Session sess, ObjectMap map, Object key)
		throws ObjectGridException
	{
		ObjectMap bmap = getBucketMap(sess, map);
		for(int i = leftBucket; i <= rightBucket; ++i)
		{
			bmap.remove(getBucketKey(key, i));
		}
		map.remove(key);
	}
	
	public <K extends Serializable> ArrayList<V> popAll(Session sess, ObjectMap map, Object key, K dirtyKey)
	throws ObjectGridException
	{
		ArrayList<V> list = new ArrayList<V>();
		ObjectMap bmap = getBucketMap(sess, map);
		for(int i = leftBucket; i <= rightBucket; ++i)
		{
			ArrayList<V> blist = (ArrayList<V>)bmap.remove(getBucketKey(key, i));
			list.addAll(blist);
		}
		map.remove(key);
		if(dirtyKey != null)
			removeFromDirtySet(sess, map, key, dirtyKey);
		return list;
	}

	public ArrayList<V> range(Session sess, ObjectMap map, Object key, int low, int high, Filter filter)
		throws ObjectGridException
	{
		int size = size(sess, map, key);
		try
		{
			if(low < 0 || high < 0 || high < low)
				throw new ObjectGridRuntimeException("Low and High are invalid (<0 || high < low");
			ObjectMap bmap = getBucketMap(sess, map);
			if(size > 0 && low < size)
			{
				if(high >= size)
					high = size - 1;
				ArrayList<V> rc = new ArrayList<V>(high - low + 1);
				ArrayList<V> lList = (ArrayList<V>)bmap.get(getBucketKey(key, leftBucket));
				
	
				int bucket = low / bucketSize + 1;
				int offset = low % bucketSize;
				
				offset -= lList.size();
				if(offset < 0)
				{
					bucket--;
					if(bucket != leftBucket)
						offset += bucketSize;
					else
						offset += lList.size();
				}
	
				ArrayList<V> current = (ArrayList<V>)bmap.get(getBucketKey(key, bucket));
				for(int i = low; i <= high; i++)
				{
					V elem = current.get(offset++);
					if(filter != null)
					{
						if(filter.filter(elem))
							rc.add(elem);
					}
					else
						rc.add(elem);
					if(offset >= current.size() && i != high)
					{
						bucket++;
						offset = 0;
						current = (ArrayList<V>)bmap.get(getBucketKey(key, bucket));
					}
				}
				return rc;
			}
			else
			{
				return new ArrayList<V>();
			}
		}
		catch(Exception e)
		{
			logger.log(Level.SEVERE, "Range Exception for key " + key.toString() + ",Low:" + low + ",High:" + high + ",Size:" + size, e);
			throw new ObjectGridRuntimeException(e);
		}
	}
	
	public void trim(Session sess, ObjectMap map, Object key, int size)
		throws ObjectGridException
	{
		try
		{
			if(size < 0)
				throw new ObjectGridRuntimeException("Cannot trim to " + size);
			if(size == 0)
				remove(sess, map, key);
			else
			{
				size = Math.min(size, size(sess, map, key));
				ObjectMap bmap = getBucketMap(sess, map);
				ArrayList<V> lList = (ArrayList<V>)bmap.get(getBucketKey(key, leftBucket));
				
				// calculate the offset of the right most element
				int bucket = (size - 1) / bucketSize + 1;
				int offset = (size - 1) % bucketSize;
				offset -= lList.size();
				if(offset < 0)
				{
					bucket--;
					if(bucket != leftBucket)
						offset += bucketSize;
					else
						offset += lList.size();
				}
				String bkey = getBucketKey(key, bucket);
				ArrayList<V> list = (ArrayList<V>)bmap.get(bkey);
				ArrayList<V> copy = new ArrayList<V>(offset + 1);
				for(int i = 0; i <= offset; ++i)
				{
					copy.add(list.get(i));
				}
				bmap.update(bkey, copy);
				for(int i = bucket + 1; i <= rightBucket; ++i)
				{
					bmap.remove(getBucketKey(key, i));
				}
				rightBucket = bucket;
				map.update(key, this);
			}
		}
		catch(Exception e)
		{
			logger.log(Level.SEVERE, "Exception:", e);
			throw new ObjectGridRuntimeException(e);
		}
	}
}