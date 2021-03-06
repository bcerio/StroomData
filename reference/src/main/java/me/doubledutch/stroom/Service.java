package me.doubledutch.stroom;

import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import me.doubledutch.stroom.streams.*;
import javax.script.*;

import me.doubledutch.stroom.perf.*;

public abstract class Service implements Runnable{
	public static int HTTP=0;
	public static int QUERY=1;
	public static int JAVASCRIPT=2;
	public static int SAMPLE=3;

	public int type=HTTP;

	public String url=null;

	private int BATCH_SIZE=1000;
	public long index=-1;

	private Map<String,MockStreamConnection> mockMap=new HashMap<String,MockStreamConnection>();
	private StreamHandler streamHandler=null;

	private boolean isRunning=false;
	private boolean shouldBeRunning=false;
	private Thread thread=null;
	private boolean isDisabled=false;

	private String id;

	private String script=null;

	public ScriptEngine jsEngine;
	public Invocable jsInvocable;
	private JSONObject config;

	private BatchMetric lastMetric=null;

	private Map<String,StreamConnection> streamMap=new HashMap<String,StreamConnection>();

	public Service(StreamHandler streamHandler,JSONObject obj) throws Exception{
		this.streamHandler=streamHandler;
		this.config=obj;
		id=obj.getString("id");
		if(obj.has("batch_size")){
			setBatchSize(obj.getInt("batch_size"));
		}
		if(obj.has("disabled")){
			isDisabled=obj.getBoolean("disabled");
		}

		JSONObject streams=obj.getJSONObject("streams");
		Iterator<String> keyIt=streams.keys();

		while(keyIt.hasNext()){
			String key=keyIt.next();
			streamMap.put(key,openStream(new URI(streams.getString(key))));
		}

		// input=openStream(new URI(obj.getString("input_stream")));
		// output=openStream(new URI(obj.getString("output_stream")));
		if(streams.has("output") && (!streams.has("state"))){
			streamMap.put("state",openStream(new URI(streams.getString("output")+".state")));
		}

		String strType=obj.getString("type");
		if(strType.equals("http")){
			type=HTTP;
			url=obj.getString("url");
		}else if(strType.equals("javascript")){
			type=JAVASCRIPT;
			/*
			script=obj.getString("script");
			String scriptData=Utility.readFile(script);
			ScriptEngineManager mgr = new ScriptEngineManager();
	        jsEngine = mgr.getEngineByName("JavaScript");
	        // jsEngine = mgr.getEngineByName("nashorn");
	        jsInvocable = (Invocable) jsEngine;
	        Bindings bindings=jsEngine.getBindings(ScriptContext.ENGINE_SCOPE);
	        bindings.put("stdout",System.out);
	        for(String key:streamMap.keySet()){
	        	bindings.put(key,streamMap.get(key));
	        }
			jsEngine.eval(scriptData);*/
			// reloadScript();
		}
	}

	public void addBatchMetric(BatchMetric metric){
		this.lastMetric=metric;
	}

	private void reloadScript() throws Exception{
		if(!config.has("type"))return;
		if(config.getString("type").equals("javascript")){
			if(jsEngine!=null){
				// Figure out a better way to dispose or recycle
				jsEngine=null;
			}
			script=config.getString("script");
			String scriptData=ScriptManager.get().getScript(script);
			ScriptEngineManager mgr = new ScriptEngineManager();
	        jsEngine = mgr.getEngineByName("JavaScript");
	        // jsEngine = mgr.getEngineByName("nashorn");
	        jsInvocable = (Invocable) jsEngine;
	        Bindings bindings=jsEngine.getBindings(ScriptContext.ENGINE_SCOPE);
	        bindings.put("stdout",System.out);
	        for(String key:streamMap.keySet()){
	        	bindings.put(key,streamMap.get(key));
	        }
	        /*
	        Bindings bindings = jsEngine.getBindings(ScriptContext.ENGINE_SCOPE);
    		bindings.put("stdout", MonolithServer.scriptOut);
			*/
			jsEngine.eval(scriptData);
		}
	}

	public void setDisabled(boolean value) throws JSONException{
		isDisabled=value;
		config.put("disabled",value);
	}

	public JSONObject getConfiguration(){
		return config;
	}

	public StreamConnection getStream(String name){
		return streamMap.get(name);
	}

	public String getId(){
		return id;
	}

	private void setBatchSize(int size){
		BATCH_SIZE=size;
	}

	public int getBatchSize(){
		return BATCH_SIZE;
	}

	private String getStreamName(URI stream){
		String path=stream.getPath();
		if(!path.startsWith("/stream/"))return null;
		return path.substring(path.lastIndexOf("/")+1); // TODO: possibly make smarter and less breakable
	}

	
	public boolean isRunning(){
		return isRunning;
	}

	public void isRunning(boolean value){
		isRunning=value;
	}	

	public boolean shouldBeRunning(){
		return shouldBeRunning;
	}

	public abstract void reset() throws Exception;

	public void start() throws Exception{
		if(isDisabled)return;
		reloadScript();
		shouldBeRunning=true;
		thread=new Thread(this,"Service."+id);
		thread.start();
	}

	public void stop(){
		shouldBeRunning=false;
		while(isRunning){
			try{
				Thread.sleep(50);
			}catch(Exception e){}
		}
	}

	public abstract void run();

	public JSONObject toJSON() throws JSONException{
		JSONObject obj=new JSONObject(config.toString());
		// obj.put("id",id);
		if(isRunning){
			obj.put("state","RUNNING");
		}else{
			obj.put("state","STOPPED");
		}
		obj.put("partitions",1);
		obj.put("disabled",isDisabled);
		obj.put("batch_size",BATCH_SIZE);
		obj.put("index",index);

		if(lastMetric!=null){
			obj.put("rate",lastMetric.getRate());
			obj.put("metrics",lastMetric.toJSON());
		}else{
			obj.put("rate",0);
			obj.put("metrics",new JSONObject());
		}
		if(!isRunning){
			obj.put("rate",0);
		}
		// obj.put("config",config);
		return obj;
	}

	public StreamConnection openStream(URI stream) throws IOException{
		String scheme=stream.getScheme();
		String streamName=getStreamName(stream);
		if(scheme.equals("local")){
			String host=stream.getHost();
			if(host.equals("mock")){
				if(!mockMap.containsKey(streamName)){
					mockMap.put(streamName,new MockStreamConnection());
				}
				return mockMap.get(streamName);
			}else if(host.equals("direct")){
				return new LocalStreamConnection(streamHandler.getOrCreateStream(streamName));
			}
		}
		return null;
	}
}