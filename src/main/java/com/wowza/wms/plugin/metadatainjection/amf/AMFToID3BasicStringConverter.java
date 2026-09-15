package com.wowza.wms.plugin.metadatainjection.amf;

import com.wowza.wms.amf.*;
import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.media.mp3.model.idtags.*;

public class AMFToID3BasicStringConverter implements IAMFToID3Converter
{

	public static final String ID_PAYLOAD = "payload";
	public static final String ID_WOWZA_CONVERTER = "wowzaConverter";
	public static final String WOWZA_CONVERTER_TYPE_BASIC_STRING = "basic_string";

	private static final Class<AMFToID3BasicStringConverter> CLASS = AMFToID3BasicStringConverter.class;
	private static final String CLASS_NAME = CLASS.getSimpleName();

	private final WMSLogger logger;

	private int maxVerboseConversionMessages = 20;
	private int countVerboseMessages = 0;

	public AMFToID3BasicStringConverter(IApplicationInstance appInstance)
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

			AMFDataObj payloadObj = amfList.getObject(1);
			if (!payloadObj.containsKey(ID_WOWZA_CONVERTER))
				break;
			if (!payloadObj.containsKey(ID_PAYLOAD))
				break;

			AMFData amfData = payloadObj.get(ID_PAYLOAD);
			if (!(amfData instanceof AMFDataItem))
				break;

			String payload = amfData.toString();

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
