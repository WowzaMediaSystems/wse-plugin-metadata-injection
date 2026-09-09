package com.wowza.wms.plugin.metadatainjection.datahandler.id3;

import com.wowza.wms.application.*;

import java.util.*;

public class AMFToID3ApplicationsManager
{

	private static Map<String, AMFToID3ConverterStreamController> controllerMap = new HashMap<String, AMFToID3ConverterStreamController>();
	private static AMFToID3ApplicationsManager singleton = null;

	public AMFToID3ConverterStreamController createController(IApplicationInstance appInstance, String streamName)
	{
		String id = getControllerID(appInstance, streamName);

		if (!controllerMap.containsKey(id))
		{
			AMFToID3ConverterStreamController controller = new AMFToID3ConverterStreamController(appInstance, streamName);
			controllerMap.put(id, controller);
		}
		return controllerMap.get(id);
	}

	public AMFToID3ConverterStreamController getController(IApplicationInstance appInstance, String streamName)
	{
		IApplication app = appInstance.getApplication();
		return getController(app.getName(), appInstance.getName(), streamName);
	}

	public AMFToID3ConverterStreamController getController(String appName, String appInstName, String streamName)
	{
		String id = getControllerID(appName, appInstName, streamName);

		return controllerMap.get(id);
	}

	public void removeController(IApplicationInstance appInstance, String streamName)
	{
		String id = getControllerID(appInstance, streamName);

		if (controllerMap.containsKey(id))
			controllerMap.remove(id);
	}

	private String getControllerID(IApplicationInstance appInstance, String streamName)
	{
		IApplication app = appInstance.getApplication();
		return getControllerID(app.getName(), appInstance.getName(), streamName);
	}

	private String getControllerID(String appName, String appInstName, String streamName)
	{
		if (appInstName == null)
			appInstName = IApplicationInstance.DEFAULT_APPINSTANCE_NAME;

		String id = appName + "|" + appInstName + "|" + streamName;
		return id;
	}

	// Here for unit testing
	public static AMFToID3ApplicationsManager getAppsManager()
	{
		if (singleton == null)
			singleton = new AMFToID3ApplicationsManager();

		return singleton;
	}

}
