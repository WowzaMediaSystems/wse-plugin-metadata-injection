package com.wowza.wms.plugin.metadatainjection.amf;

import com.wowza.wms.application.IApplication;
import com.wowza.wms.application.IApplicationInstance;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Registry of per-stream {@link AMFToID3ConverterStreamController}s, keyed by application,
 * application instance and stream name.
 * <p>
 * A stream may be packetized by several packetizers at once (Cupertino and CMAF), whose
 * create/destroy callbacks run concurrently. Registration and release are therefore performed
 * atomically per key via {@link ConcurrentMap#compute}, so a controller can never be dropped
 * while a handler is being attached to it and two packetizers can never end up on different
 * controllers for the same stream.
 */
public class AMFToID3ApplicationsManager
{

	private static final ConcurrentMap<String, AMFToID3ConverterStreamController> controllerMap = new ConcurrentHashMap<>();
	private static final AMFToID3ApplicationsManager singleton = new AMFToID3ApplicationsManager();

	/**
	 * Attach a data handler to the controller for this stream, creating the controller if needed.
	 * Atomic with respect to {@link #unregisterDataHandler}.
	 *
	 * @return the controller the handler was attached to
	 */
	public AMFToID3ConverterStreamController registerDataHandler(IApplicationInstance appInstance, String streamName, IAMFToID3DataHandler dataHandler)
	{
		String id = getControllerID(appInstance, streamName);

		return controllerMap.compute(id, (key, controller) -> {
			if (controller == null)
				controller = new AMFToID3ConverterStreamController(appInstance, streamName);
			controller.addDataHandler(dataHandler);
			return controller;
		});
	}

	/**
	 * Detach a data handler from the controller for this stream and drop the controller once no
	 * handler references it. Atomic with respect to {@link #registerDataHandler}.
	 */
	public void unregisterDataHandler(IApplicationInstance appInstance, String streamName, IAMFToID3DataHandler dataHandler)
	{
		String id = getControllerID(appInstance, streamName);

		controllerMap.computeIfPresent(id, (key, controller) -> {
			controller.removeDataHandler(dataHandler);
			return controller.hasDataHandlers() ? controller : null;
		});
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
		return singleton;
	}

}
