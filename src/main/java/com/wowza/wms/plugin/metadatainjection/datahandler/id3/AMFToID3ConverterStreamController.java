package com.wowza.wms.plugin.metadatainjection.datahandler.id3;

import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.logging.WMSLoggerFactory;

public class AMFToID3ConverterStreamController
{
	private IApplicationInstance appInstance;
	private String streamName;
	private boolean dataConversionEnabled = false;
	private int maxVerboseConversionMessages = 5;
	private int maxFailedConversionMessages = 5;
	private AMFToID3LiveStreamPacketizerDataHandler dataHandler2;
	private boolean enableDataConversion = true;

	public AMFToID3ConverterStreamController(IApplicationInstance appInstance, String streamName)
	{
		this.appInstance = appInstance;
		this.streamName = streamName;

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

	public void setDataHandler(AMFToID3LiveStreamPacketizerDataHandler amfToID3LiveStreamPacketizerDataHandler)
	{
		this.dataHandler2 = amfToID3LiveStreamPacketizerDataHandler;

		if (enableDataConversion)
			enableDataConversion();
		if (dataHandler2 != null)
		{
			this.dataHandler2.setMaxFailedConversionMessages(maxFailedConversionMessages);
			this.dataHandler2.setMaxVerboseConversionMessages(maxVerboseConversionMessages);
		}
	}

	public void enableDataConversion()
	{
		if (!this.dataConversionEnabled)
		{
			this.dataConversionEnabled = true;

			if (this.dataHandler2 != null)
				this.dataHandler2.setEnabled(true);
		}
	}

	public void disableDataConversion()
	{
		WMSLoggerFactory.getLogger(AMFToID3ConverterStreamController.class)
				.info("AMFToID3ConverterStreamController.disableDataConversion");

		if (this.dataConversionEnabled)
		{
			if (this.dataHandler2 != null)
				this.dataHandler2.setEnabled(false);

			this.dataConversionEnabled = false;
		}
	}

}
