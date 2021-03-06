/**
 * Copyright (C) 2007 Rui Shen (rui.shen@gmail.com) All Right Reserved
 * File     : LRUCache.java
 * Created	: 2007-3-1
 * 
 * ****************************************************************************
 * Copyright (C) 2007 Rui Shen (rui.shen@gmail.com)
 * http://chmpane.sourceforge.net, All Right Reserved. 
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  [1] Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *  [2] Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in
 *      the documentation and/or other materials provided with the
 *      distribution.
 *  [3] Neither the name "CHMPane" nor the names of its contributors may
 *      be used to endorse or promote products derived from this software
 *      without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * *****************************************************************************
 */
package cn.rui.chm;

import java.util.Map;
import java.util.TreeMap;

/**
 * Is it LRU?
 */
class LRUCache<K extends Comparable<K>, V> {
	
	Map<K, Item> cacheMap = new TreeMap<K, Item>();
	
	private int capacity;
	
	public LRUCache(int capacity) {
		if (capacity < 1)
			throw new IllegalArgumentException("capacity must be positive integer");
		this.capacity = capacity;
	}
	
	public synchronized V get(K key) {
		Item item = cacheMap.get(key);
		if (item == null)
			return null;
		
		for(Item i: cacheMap.values()) {
			i.hits --;
		}
		
		item.hits += 2;
		return item.value;
	}
	
	public synchronized V prune() {
		if (cacheMap.size() >= capacity) {
			Item kick = null;
			for (Item item: cacheMap.values()) {
				if (kick == null || kick.hits > item.hits) {
					kick = item;
				}
			}
			cacheMap.remove(kick.key);
			return kick.value;
		}
		return null;
	}
	
	public synchronized void put(K key, V val) {
		if (cacheMap.containsKey(key)) { // just refresh the value
			cacheMap.put(key, new Item(key, val));
			return;
		}
		prune();
		cacheMap.put(key, new Item(key, val));
	}

	public synchronized void clear() {
		cacheMap.clear();
	}
	
	public int size() {
		return cacheMap.size();
	}
	
	public String toString() {
		return "LRUCache " + size() + "/" + capacity + ": " + cacheMap.toString();
	}
	
	class Item {
		K key;
		V value;
		int hits;
		
		public Item(K key, V value) {
			this.key = key;
			this.value = value;
			this.hits = 1;
		}
		
		public String toString() {
			return "(" + hits + ")";
		}
	}
}
