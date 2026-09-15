package com.wowza.wms.plugin.metadatainjection.amf;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wowza.wms.amf.*;
import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.media.mp3.model.idtags.*;

import java.util.HashMap;

public class AMFToID3BasicJSONConverter implements IAMFToID3Converter
{

	public static final String WOWZA_CONVERTER_TYPE_BASIC_JSON = "basic_json";
	private static final Class<AMFToID3BasicJSONConverter> CLASS = AMFToID3BasicJSONConverter.class;
	private static final String CLASS_NAME = CLASS.getSimpleName();

	private final WMSLogger logger;
	private int maxVerboseConversionMessages = 5;
	private int countVerboseMessages = 0;

	public AMFToID3BasicJSONConverter(IApplicationInstance appInstance)
	{
		logger = WMSLoggerFactory.getLoggerObj(CLASS, appInstance);
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
				logger.info(CLASS_NAME + "[" + context.getContextString() + "]: Converted AMF structure: " + AMFToDebugFormatter.amfToDebug(
								amfList).replace('\n', '|'));
				logger.info(CLASS_NAME + "[" + context.getContextString() + "]: To ID3 AMF desc:" + payloadType + " value:" + payload.replace(
								'\n', '|'));
			}

			break;
		}
	}

}
