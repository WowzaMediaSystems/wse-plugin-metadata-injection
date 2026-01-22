package com.wowza.wms.plugin.metadatainjection.httpprovider;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.*;
import com.wowza.wms.amf.*;
import com.wowza.wms.application.*;
import com.wowza.wms.http.*;
import com.wowza.wms.logging.*;
import com.wowza.wms.plugin.metadatainjection.module.ID3AndPDTInjectionModule;
import com.wowza.wms.stream.*;
import com.wowza.wms.vhost.*;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;

public class HTTPProviderMetadataInjection extends HTTPProvider2Base
{
	private static final int MAXGUIDLIST = 250;    //so we don't eat all the memory with this list if server runs forever
	private static final int MAXDELAY = 30000; //30 seconds
	private static final int MAXREPEAT = 10;
	private static final int MAXREPEATDELAY = 5000; //5 seconds
	private static final String LOGPREFIX = "MetadataInjection:";

	private static HashMap<String,Integer> countVerboseMessages = new HashMap<String, Integer>();
	private static HashMap<String,Integer> maxVerboseConversionMessages = new  HashMap<String,Integer>();
	static WMSLogger log = null;

	private static LinkedHashMap<String, ArrayList<Date>> injects = new LinkedHashMap<String, ArrayList<Date>>(MAXGUIDLIST)
	{
		@Override
		protected boolean removeEldestEntry(Entry<String, ArrayList<Date>> entry)
		{
			return size() > MAXGUIDLIST;
		}
	};

	public HTTPProviderMetadataInjection()
	{
		log = WMSLoggerFactory.getLogger(HTTPProviderMetadataInjection.class);
	}

	public void onBind(IVHost vhost, HostPort hostPort)
	{
		log.info(LOGPREFIX + "Started v" + ID3AndPDTInjectionModule.MODULE_VERSION + " port:" + hostPort);
		super.onBind(vhost, hostPort);
	}

	public void onHTTPRequest(IVHost vhost, IHTTPRequest req, IHTTPResponse resp)
	{
		String guid = UUID.randomUUID().toString();
		try
		{
			resp.setHeader("Content-Type", "application/json");
			resp.setHeader("Access-Control-Allow-Origin", "*");
			String body = new String(req.getMsgBytes());
			// log.info(LOGPREFIX + guid + ": message received:" + req.getRequestURL() + " " + body);
			if (!doHTTPAuthentication(vhost, req, resp))
			{
				return;
			}
			if (req.getMethod().equals("GET"))
			{
				String[] splits = req.getRequestURL().split("/");
				if (splits.length == 6)
				{
					guid = splits[splits.length - 1];
					if (!injects.containsKey(guid))
					{
						resp.setResponseCode(404);
					}
					else
					{
						resp.setResponseCode(200);
						try
						{
							ArrayList<Date> successArray = injects.get(guid);
							OutputStream out = resp.getOutputStream();
							String msg = "{}";
							if (successArray == null || successArray.size() == 0)
							{
								msg = "{\"status\":\"waiting\", \"guid\":\"" + guid + "\", \"count\":" + 0 + ", \"inserted_at\":[]}";
							}
							else
							{
								msg = "{\"status\":\"success\", \"guid\":\"" + guid + "\", \"count\":" + successArray.size() + ", \"inserted_at\":[";
								SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
								simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
								boolean first = true;
								synchronized(successArray)
								{
									for (Date dt : successArray)
									{
										if (!first)
										{
											msg += ",";
										}
										first = false;
										msg += "\"" + simpleDateFormat.format(dt) + "\"";
									}
									msg += "]}";
								}
							}
							out.write(msg.getBytes());

						}
						catch (Exception e)
						{
							log.error(LOGPREFIX + guid + ": ", e);
						}
					}
				}
				else
				{
					//just return version
					resp.setResponseCode(200);
					OutputStream out = resp.getOutputStream();
					try
					{
						out.write(new String(
								"{\"name\":\"" + LOGPREFIX + "\",\"version\":\"" + ID3AndPDTInjectionModule.MODULE_VERSION + "\"}").getBytes());
					}
					catch (Exception e)
					{
						log.error(LOGPREFIX + guid + ": ", e);
					}
				}
				return;
			}
			if (!req.getMethod().equals("POST"))
			{
				failResponse(resp, guid, "not allowed", 405);
				return;
			}

			if (!validateUrl(req.getRequestURL()))
			{
				failResponse(resp, guid, "incorrect url", 400);
				return;
			}

			IMediaStream stream = getStreamFromUrl(vhost, req, resp, guid);
			if (stream == null)
			{
				//getStreamFromUrl logs the errors and warnings, just return
				return;
			}
			IApplicationInstance appInst = getAppInstanceFromUrl(vhost, req, resp, guid);
			if (appInst == null)
			{
				//getAppNameFromUrl logs the errors and warnings, just return
				return;
			}
			JsonNode actualObj = null;
			try
			{
				ObjectMapper mapper = new ObjectMapper();
				actualObj = mapper.readTree(body);
			}
			catch (JsonParseException e)
			{
				//try and return a cleaner error
				String msg = e.getLocalizedMessage();
				int idx = msg.indexOf(" (for Object");
				if (idx > 0)
				{
					msg = msg.substring(0, idx);
				}
				failResponse(resp, guid,
						"invalid json " + msg + " line:" + e.getLocation().getLineNr() + " col:" + e.getLocation().getColumnNr(),
						400);
				return;
			}
			catch (Exception e)
			{
				failResponse(resp, guid, "invalid json", 400);
				return;
			}

			if (actualObj == null)
			{
				failResponse(resp, guid, "no data", 400);
				return;
			}

			boolean async = false;
			JsonNode asyncObj = actualObj.get("async");
			if (asyncObj != null)
			{
				async = asyncObj.booleanValue();
			}

			injects.put(guid, new ArrayList<Date>());
			if (async)
			{
				Thread t1 = new Thread(new InjectMetadataThread(appInst, stream, guid, actualObj), "InjectMetadataThread");
				t1.start();
				String msg = "{\"status\":\"success\", \"guid\":\"" + guid + "\"}";
				sendResponse(resp, msg, 201);
			}
			else
			{
				boolean ok = injectMetadata(appInst, stream, guid, actualObj);
				ArrayList<Date> successArray = injects.get(guid);
				if (ok && successArray != null)
				{
					String msg = "{\"status\":\"success\", \"guid\":\"" + guid + "\", \"count\":" + successArray.size() + ", \"inserted_at\":[";
					synchronized(successArray)
					{
						SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
						simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
						boolean first = true;
						for (Date dt : successArray)
						{
							if (!first)
							{
								msg += ",";
							}
							first = false;
							msg += "\"" + simpleDateFormat.format(dt) + "\"";
						}
					}
					msg += "]}";
					sendResponse(resp, msg, 201);
				}
				else
				{
					failResponse(resp, guid, "could not inject metadata", 400);
				}
			}
		}
		catch (Exception e)
		{
			failResponse(resp, guid, e.getMessage(), 500);
		}
	}

	@Override
	public boolean doHTTPAuthentication(IVHost vhost, IHTTPRequest req, IHTTPResponse resp)
	{
		boolean useMetadataApiKey = vhost.getProperties().getPropertyBoolean("useMetadataApiKey", false);
		if(!useMetadataApiKey)
		{
			return super.doHTTPAuthentication(vhost, req, resp);
		}

		String metadataApiKey = null;
		String appName = null;
		String[] splits = req.getRequestURL().split("/");
		for (int idx = 0; idx < splits.length; idx++)
		{
			if (splits[idx].equals("applications"))
			{
				appName = splits[idx + 1];
			}
		}
		metadataApiKey = vhost.getProperties().getPropertyStr("metadataApiKey", metadataApiKey);
		if (appName != null)
		{
			IApplication app = vhost.getApplication(appName);
			if (app != null)
			{
				IApplicationInstance i = app.getAppInstance("_definst_");
				if (i != null)
				{
					metadataApiKey = i.getProperties().getPropertyStr("metadataApiKey", metadataApiKey);
				}
			}

		}
		if (metadataApiKey != null && !metadataApiKey.isEmpty())
		{
			//and metadatApiKey is defined, make sure its valid
			if (!metadataApiKey.equals(req.getHeader("metadata-api-key")))
			{
				log.warn(LOGPREFIX + "metadataApiKey defined but not valid from request");
				resp.setResponseCode(401);
				return false;
			}
		}
		return true;
	}

	private IMediaStream getStreamFromUrl(IVHost vhost, IHTTPRequest req, IHTTPResponse resp, String guid)
	{
		String appName = null;
		String appInstanceName = "_definst_";
		String streamName = null;
		String[] splits = req.getRequestURL().split("/");
		for (int idx = 0; idx < splits.length; idx++)
		{
			if (splits[idx].equals("applications"))
			{
				appName = splits[idx + 1];
			}
			if (splits[idx].equals("instances"))
			{
				appInstanceName = splits[idx + 1];
			}
			if (splits[idx].equals("streams"))
			{
				streamName = splits[idx + 1];
			}
		}

		// Find application, application instance and stream running in WSE
		IApplication app = vhost.getApplication(appName);
		if (app == null)
		{
			failResponse(resp, guid, "application not found: " + appName, 404);
			return null;
		}

		IApplicationInstance appInstance = app.getAppInstance(appInstanceName);
		if (appInstance == null)
		{
			failResponse(resp, guid, "application instance not found: " + appInstanceName, 404);
			return null;
		}

		MediaStreamMap streams = appInstance.getStreams();
		if (streams == null)
		{
			failResponse(resp, guid, "no streams", 400);
			return null;
		}

		IMediaStream stream = streams.getStream(streamName);
		if (stream == null)
		{
			failResponse(resp, guid, "stream not found: " + streamName, 404);
			return null;
		}

		return stream;
	}

	private IApplicationInstance getAppInstanceFromUrl(IVHost vhost, IHTTPRequest req, IHTTPResponse resp, String guid)
	{
		IApplicationInstance appInst = null;
		String appName = null;
		String appInstanceName = "_definst_";

		String[] splits = req.getRequestURL().split("/");
		for (int idx = 0; idx < splits.length; idx++)
		{
			if (splits[idx].equals("applications"))
			{
				appName = splits[idx + 1];
			}
			if (splits[idx].equals("instances"))
			{
				appInstanceName = splits[idx + 1];
			}
		}

		// Find application, application instance and stream running in WSE
		IApplication app = vhost.getApplication(appName);
		if (app == null)
		{
			failResponse(resp, guid, "application not found: " + appName, 404);
			return null;
		}

		IApplicationInstance appInstance = app.getAppInstance(appInstanceName);
		if (appInstance == null)
		{
			failResponse(resp, guid, "application instance not found: " + appInstanceName, 404);
			return null;
		}
		return app.getAppInstance(appInstanceName);
	}

	public static boolean injectMetadata(IApplicationInstance appInst, IMediaStream stream, String guid, JsonNode actualObj)
	{
		boolean retVal = false;
		try
		{
			String appName = appInst.getApplication().getName();
			//must have event and data
			JsonNode eventObj = actualObj.get("event");
			if (eventObj == null)
			{
				log.warn(LOGPREFIX + guid + ": payload does not contain event.");
				return retVal;
			}
			String event = eventObj.textValue();

			//get the options
			int delay = 0;
			int repeatCount = 1;
			int repeatInterval = 0;
			boolean injectTime = false;
			boolean id3 = false;

			JsonNode delayObj = actualObj.get("delay");
			if (delayObj != null)
			{
				delay = delayObj.intValue();
			}
			if (delay > MAXDELAY)
			{
				delay = MAXDELAY;
			}

			JsonNode repeatObj = actualObj.get("repeat");
			if (repeatObj != null)
			{
				repeatCount = repeatObj.intValue();
			}
			if (repeatCount > MAXREPEAT)
			{
				repeatCount = MAXREPEAT;
			}
			JsonNode intervalObj = actualObj.get("repeatInterval");
			if (intervalObj != null)
			{
				repeatInterval = intervalObj.intValue();
			}
			if (repeatInterval > MAXREPEATDELAY)
			{
				repeatInterval = MAXREPEATDELAY;
			}
			JsonNode injectTimeObj = actualObj.get("injectTime");
			if (injectTimeObj != null)
			{
				injectTime = injectTimeObj.booleanValue();
			}
			JsonNode id3Obj = actualObj.get("id3");
			if (id3Obj != null)
			{
				id3 = id3Obj.booleanValue();
			}

			JsonNode wowzaConverterObj = actualObj.get("wowzaConverter");

			JsonNode dataObj = actualObj.get("data");
			try
			{
				Thread.sleep(delay);

				for (int i = 0; i < repeatCount; i++)
				{
					AMFDataObj amfData = null;
					if (dataObj == null)
					{
						amfData = new AMFDataObj();
					}
					else
					{
						amfData = jsonObjToAMFDataObj(dataObj);
					}
					if (injectTime)
					{
						amfData.put("injectTime", new Date().getTime());
					}
					if (id3)
					{
						amfData.put("wowzaConverter", "basic_json");
					}
					if (wowzaConverterObj != null)
					{
						amfData.put("wowzaConverter", wowzaConverterObj.asText());
					}
					amfData.put("guid", guid);

					// Send the data event
					if(!maxVerboseConversionMessages.containsKey(appName))
					{
						int max = appInst.getProperties().getPropertyInt("amfToID3ConversionVerboseMaximum", 20);
						maxVerboseConversionMessages.put(appName,max);
					}
					if(!countVerboseMessages.containsKey(appName))
					{
						countVerboseMessages.put(appName,0);
					}
					countVerboseMessages.put(appName,countVerboseMessages.get(appName)+1);
					if (countVerboseMessages.get(appName) < maxVerboseConversionMessages.get(appName))
					{
						log.info(
								LOGPREFIX + guid + ": sending AMF event: " + event + "(" + (i + 1) + " of " + repeatCount + ")");
					}
					ArrayList<Date> successArray = injects.get(guid);
					if (successArray != null)
					{
						synchronized(successArray)
						{
							successArray.add(new Date());
						}
					}
					stream.sendDirect(event, amfData);
					((MediaStream)stream).processSendDirectMessages();
					if (i + 1 < repeatCount)  //don't sleep on the last one
					{
						Thread.sleep(repeatInterval);
					}
				}
				retVal = true;
			}
			catch (Exception e)
			{
				log.error(LOGPREFIX + guid + ": ", e);
			}
		}
		catch (Exception e)
		{
			log.error(LOGPREFIX + guid + ": ", e);
		}
		return retVal;
	}

	private static AMFDataObj jsonObjToAMFDataObj(JsonNode jsonObj)
	{
		AMFDataObj amfData = new AMFDataObj();

		Iterator<Entry<String, JsonNode>> iterator = jsonObj.fields();
		while (iterator.hasNext())
		{
			AMFData item = null;
			Entry<String, JsonNode> obj = iterator.next();
			item = jsonValueToAMFDataItem(obj.getValue());
			if (item != null)
			{
				amfData.put(obj.getKey(), item);
			}
		}
		return amfData;
	}

	private static AMFData jsonValueToAMFDataItem(JsonNode jsonValue)
	{
		AMFData item = null;
		if (jsonValue.isObject())
		{
			item = jsonObjToAMFDataObj(jsonValue);
		}
		else if (jsonValue.isArray())
		{
			item = new AMFDataArray();
			for (int idx = 0; idx < jsonValue.size(); idx++)
			{
				((AMFDataArray)item).add(jsonValueToAMFDataItem(jsonValue.get(idx)));
			}
		}
		else if (jsonValue.isTextual())
		{
			item = new AMFDataItem(jsonValue.asText());
		}
		else if (jsonValue.isBoolean())
		{
			item = new AMFDataItem(jsonValue.asBoolean());
		}
		else if (jsonValue.isDouble())
		{
			item = new AMFDataItem(jsonValue.asDouble());
		}
		else if (jsonValue.isInt())
		{
			item = new AMFDataItem(jsonValue.asInt());
		}
		else if (jsonValue.isLong())
		{
			item = new AMFDataItem(jsonValue.asLong());
		}
		else
		{
			item = new AMFDataItem(jsonValue.asText());
		}
		return item;
	}

	private boolean validateUrl(String url)
	{
		String[] splits = url.split("/");
		boolean retVal = (splits.length >= 8 &&
				splits[0].equals("v1") &&
				splits[1].equals("server") &&
				splits[2].equals("plugin") &&
				splits[3].equals("metaDataInjection"));
		retVal = retVal && (url.indexOf("applications") >= 0);
		retVal = retVal && (url.indexOf("streams") >= 0);
		return retVal;
	}

	private void failResponse(IHTTPResponse resp, String guid, String msg, int httpCode)
	{
		log.warn(LOGPREFIX + guid + ": " + msg + ".");
		String jsonString = " {\"status\":\"failed\", ";
		if (guid != null)
		{
			jsonString += "\"guid\":\"" + guid + "\", ";
		}
		jsonString += "\"reason\":\"" + msg + "\"}";

		sendResponse(resp, jsonString, httpCode);
	}

	private void sendResponse(IHTTPResponse resp, String msg, int httpCode)
	{
		resp.setHeader("Content-Type", "application/json");
		resp.setResponseCode(httpCode);
		OutputStream out = resp.getOutputStream();
		try
		{
			out.write(msg.getBytes());
		}
		catch (Exception e)
		{
			log.error(LOGPREFIX + " ", e);
		}
	}

	public class InjectMetadataThread implements Runnable
	{
		IMediaStream stream;
		IApplicationInstance appInst;
		String guid;
		JsonNode actualObj;

		public InjectMetadataThread(IApplicationInstance appInst, IMediaStream stream, String guid, JsonNode actualObj)
		{
			this.appInst = appInst;
			this.stream = stream;
			this.guid = guid;
			this.actualObj = actualObj;
		}

		public void run()
		{
			log.info(LOGPREFIX + guid + ": Staring metadata Inject as thread");
			boolean ok = HTTPProviderMetadataInjection.injectMetadata(appInst, stream, guid, actualObj);
			ArrayList<Date> successArray = injects.get(guid);
			String msg = "";
			if (ok && successArray != null)
			{
				synchronized(successArray)
				{
					SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
					simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
					boolean first = true;
					for (Date dt : successArray)
					{
						if (!first)
						{
							msg += ",";
						}
						first = false;
						msg += simpleDateFormat.format(dt);
					}
				}
			}
			log.info(LOGPREFIX + guid + ": Finished metadata Inject as thread. Inserted at:" + msg);
		}
	}

}