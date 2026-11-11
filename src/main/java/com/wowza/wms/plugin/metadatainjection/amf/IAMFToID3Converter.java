package com.wowza.wms.plugin.metadatainjection.amf;

import com.wowza.wms.amf.AMFDataList;
import com.wowza.wms.media.mp3.model.idtags.ID3Frames;

public interface IAMFToID3Converter
{

	public void convertAMFDataListToID3(AMFDataList amf, AMFToID3ConverterContext context, ID3Frames id3Frames);

	/**
	 * The string this converter writes as the ID3 TXXX value for the event, or null when the event
	 * is not convertible. Every output (ID3, emsg, WebRTC data channel) carries this same string.
	 */
	public String convertAMFDataListToPayload(AMFDataList amf);

}
