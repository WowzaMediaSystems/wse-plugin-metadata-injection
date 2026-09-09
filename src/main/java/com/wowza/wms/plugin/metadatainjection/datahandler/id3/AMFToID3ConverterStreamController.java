package com.wowza.wms.plugin.metadatainjection.datahandler.id3;

import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.logging.WMSLoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class AMFToID3ConverterStreamController
{
	private boolean dataConversionEnabled = false;
	private int maxVerboseConversionMessages = 5;
	private int maxFailedConversionMessages = 5;
	// One stream may be packetized by both the Cupertino (HLS/TS) and CMAF packetizers at the same time
	private final List<IAMFToID3DataHandler> dataHandlers = new CopyOnWriteArrayList<>();
	private boolean enableDataConversion = true;

	public AMFToID3ConverterStreamController(IApplicationInstance appInstance, String streamName)
	{
		// Properties
		maxVerboseConversionMessages = appInstance.getProperties()
				.getPropertyInt("amfToID3ConversionVerboseMaximum", maxVerboseConversionMessages);
		WMSLoggerFactory.getLogger(AMFToID3ConverterStreamController.class)
				.info("AMFToID3ConverterStreamController property amfToID3ConversionVerboseMaximum:" + maxVerboseConversionMessages);
		maxFailedConversionMessages = appInstance.getProperties()
				.getPropertyInt("amfToID3ConversionFailedMaximum", maxFailedConversionMessages);
		WMSLoggerFactory.getLogger(AMFToID3ConverterStreamController.class)
				.info("AMFToID3ConverterStreamController property amfToID3ConversionFailedMaximum:" + maxFailedConversionMessages);
		enableDataConversion = appInstance.getProperties().getPropertyBoolean("amfToID3ConversionEnabled", false);
		WMSLoggerFactory.getLogger(AMFToID3ConverterStreamController.class)
				.info("AMFToID3ConverterStreamController property amfToID3ConversionEnabled:" + (enableDataConversion ?
						"true" :
						"false"));

	}

	/**
	 * @deprecated use {@link #addDataHandler(IAMFToID3DataHandler)}
	 */
	@Deprecated
	public void setDataHandler(AMFToID3LiveStreamPacketizerDataHandler amfToID3LiveStreamPacketizerDataHandler)
	{
		addDataHandler(amfToID3LiveStreamPacketizerDataHandler);
	}

	public void addDataHandler(IAMFToID3DataHandler dataHandler)
	{
		if (dataHandler == null)
			return;

		if (!dataHandlers.contains(dataHandler))
			dataHandlers.add(dataHandler);

		dataHandler.setMaxFailedConversionMessages(maxFailedConversionMessages);
		dataHandler.setMaxVerboseConversionMessages(maxVerboseConversionMessages);

		if (enableDataConversion)
			enableDataConversion();

		// Sync the new handler with the current state (enableDataConversion() is a no-op if already enabled)
		dataHandler.setEnabled(dataConversionEnabled);
	}

	public void removeDataHandler(IAMFToID3DataHandler dataHandler)
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

	public void enableDataConversion()
	{
		if (!this.dataConversionEnabled)
		{
			this.dataConversionEnabled = true;

			for (IAMFToID3DataHandler dataHandler : dataHandlers)
				dataHandler.setEnabled(true);
		}
	}

	public void disableDataConversion()
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
