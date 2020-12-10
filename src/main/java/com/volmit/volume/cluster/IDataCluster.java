package com.volmit.volume.cluster;

import java.util.List;

import com.volmit.volume.lang.collections.GList;
import com.volmit.volume.lang.collections.GMap;

public interface IDataCluster {

	ICluster<?> getCluster(String key);

	GList<String> k();

	GMap<String, ICluster<?>> map();

	GMap<String, String> getComments();

	void remove(String key);

	void removeComment(String key);

	void removeComments();

	boolean hasComment(String key);

	String getComment(String key);

	void setComment(String key, String comment);

	DataCluster crop(String key);

	DataCluster copy();

	boolean has(String key);

	boolean has(String key, Class<?> c);

	<T> T get(String key);

	void set(String key, Object o);

	String getString(String key);

	Boolean getBoolean(String key);

	Integer getInt(String key);

	Long getLong(String key);

	Float getFloat(String key);

	Double getDouble(String key);

	Short getShort(String key);

	GList<String> getStringList(String key);

	void set(String key, String o);

	void set(String key, boolean o);

	void set(String key, int o);

	void set(String key, long o);

	void set(String key, float o);

	void set(String key, double o);

	void set(String key, short o);

	void set(String key, GList<String> o);

	void set(String key, List<String> o);
}
