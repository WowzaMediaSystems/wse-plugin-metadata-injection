package com.wowza.wms.plugin.metadatainjection.amf;

import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.logging.WMSLoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Per-stream on/off switch for AMF to ID3 conversion, fanned out to every packetizer data handler
 * attached to the stream.
 * <p>
 * Handlers are attached from packetizer create callbacks and toggled from the REST API, which
 * run on different threads. All state transitions are serialized on this instance so a handler
 * added mid-toggle always ends up in the same state as its siblings.
 */
public class AMFToID3ConverterStreamController
{
	private volatile boolean dataConversionEnabled = false;
	private final int maxVerboseConversionMessages;
	private final int maxFailedConversionMessages;
	// One stream may be packetized by both the Cupertino (HLS/TS) and CMAF packetizers at the same time
	private final List<IAMFToID3DataHandler> dataHandlers = new CopyOnWriteArrayList<>();
	// Initial state from the amfToID3ConversionEnabled application property
	private final boolean enabledByProperty;

	public AMFToID3ConverterStreamController(IApplicationInstance appInstance, String streamName)
	{
		// Properties
		maxVerboseConversionMessages = appInstance.getProperties().getPropertyInt("amfToID3ConversionVerboseMaximum", 5);
		WMSLoggerFactory.getLogger(AMFToID3ConverterStreamController.class)
				.info("AMFToID3ConverterStreamController property amfToID3ConversionVerboseMaximum:" + maxVerboseConversionMessages);
		maxFailedConversionMessages = appInstance.getProperties().getPropertyInt("amfToID3ConversionFailedMaximum", 5);
		WMSLoggerFactory.getLogger(AMFToID3ConverterStreamController.class)
				.info("AMFToID3ConverterStreamController property amfToID3ConversionFailedMaximum:" + maxFailedConversionMessages);
		enabledByProperty = appInstance.getProperties().getPropertyBoolean("amfToID3ConversionEnabled", false);
		WMSLoggerFactory.getLogger(AMFToID3ConverterStreamController.class)
				.info("AMFToID3ConverterStreamController property amfToID3ConversionEnabled:" + (enabledByProperty ? "true" : "false"));

	}

	public synchronized void addDataHandler(IAMFToID3DataHandler dataHandler)
	{
		if (dataHandler == null)
			return;

		if (!dataHandlers.contains(dataHandler))
			dataHandlers.add(dataHandler);

		dataHandler.setMaxFailedConversionMessages(maxFailedConversionMessages);
		dataHandler.setMaxVerboseConversionMessages(maxVerboseConversionMessages);

		if (enabledByProperty)
			enableDataConversion();

		// Sync the new handler with the current state (enableDataConversion() is a no-op if already enabled)
		dataHandler.setEnabled(dataConversionEnabled);
	}

	public synchronized void removeDataHandler(IAMFToID3DataHandler dataHandler)
	{
		if (dataHandler == null)
			return;

		dataHandlers.remove(dataHandler);
		dataHandler.setEnabled(false);
	}

	public boolean hasDataHandlers()
	{
		return !dataHandlers.isEmpty();
	}

	public synchronized void enableDataConversion()
	{
		if (!this.dataConversionEnabled)
		{
			this.dataConversionEnabled = true;

			for (IAMFToID3DataHandler dataHandler : dataHandlers)
				dataHandler.setEnabled(true);
		}
	}

	public synchronized void disableDataConversion()
	{
		WMSLoggerFactory.getLogger(AMFToID3ConverterStreamController.class)
				.info("AMFToID3ConverterStreamController.disableDataConversion");

		if (this.dataConversionEnabled)
		{
			for (IAMFToID3DataHandler dataHandler : dataHandlers)
				dataHandler.setEnabled(false);

			this.dataConversionEnabled = false;
		}
	}

}
