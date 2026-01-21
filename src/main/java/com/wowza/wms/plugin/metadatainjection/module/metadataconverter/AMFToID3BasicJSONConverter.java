package com.wowza.wms.plugin.metadatainjection.module.metadataconverter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wowza.wms.amf.*;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.media.mp3.model.idtags.*;

import java.util.HashMap;

public class AMFToID3BasicJSONConverter implements IAMFToID3Converter
{

	public static final String WOWZA_CONVERTER_TYPE_BASIC_JSON = "basic_json";

	private int maxVerboseConversionMessages = 5;
	private int countVerboseMessages = 0;

	public AMFToID3BasicJSONConverter()
	{

	}

	public void setMaxVerboseConversionMessages(int maxVerboseConversionMessages)
	{
		this.maxVerboseConversionMessages = maxVerboseConversionMessages;
	}

	public void convertAMFDataListToID3(AMFDataList amfList, AMFToID3ConverterContext context, ID3Frames id3Frames)
	{
		while (true)
		{
			if (!AMFToID3Converter.hasCorrectStructure(amfList))
				break;

			String payloadType = amfList.getString(0);
			String payload = "{}";

			AMFDataObj payloadObj = amfList.getObject(1);

			HashMap<String, Object> j = AMFToID3Converter.AMFObjtoJSON(payloadObj);
			j.remove("wowzaConverter");
			j.put("eventType", payloadType);
			try
			{
				payload = new ObjectMapper().writeValueAsString(j);
			}
			catch (JsonProcessingException e)
			{
				e.printStackTrace();
			}

			// Create ID3	
			ID3V2FrameTextInformationUserDefined id3 = new ID3V2FrameTextInformationUserDefined();
			id3.setDescription(payloadType);
			id3.setValue(payload);
			id3Frames.putFrame(id3);

			countVerboseMessages++;
			if (countVerboseMessages < maxVerboseConversionMessages)
			{
				WMSLoggerFactory.getLogger(AMFToID3Converter.class)
						.info("AMFToID3BasicJSONConverter[" + context.getContextString() + "]: Converted AMF structure: " + AMFToDebugFormatter.amfToDebug(
								amfList).replace('\n', '|'));
				WMSLoggerFactory.getLogger(AMFToID3Converter.class)
						.info("AMFToID3BasicJSONConverter[" + context.getContextString() + "]: To ID3 AMF desc:" + payloadType + " value:" + payload.replace(
								'\n', '|'));
			}

			break;
		}
	}

}
