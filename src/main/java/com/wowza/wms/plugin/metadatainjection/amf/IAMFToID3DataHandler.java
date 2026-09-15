package com.wowza.wms.plugin.metadatainjection.amf;

/**
 * Common control surface for packetizer data handlers that convert AMF data events to ID3.
 * Allows {@link AMFToID3ConverterStreamController} to drive both the HLS (Cupertino) and
 * CMAF (emsg) handlers for the same stream.
 */
public interface IAMFToID3DataHandler
{

	void setEnabled(boolean enabled);

	boolean isEnabled();

	void setMaxFailedConversionMessages(int maxFailedConversionMessages);

	void setMaxVerboseConversionMessages(int maxVerboseConversionMessages);

}
