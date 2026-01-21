package com.wowza.wms.plugin.metadatainjection.module.metadataconverter;

import com.wowza.wms.amf.*;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.media.mp3.model.idtags.*;

public class AMFToID3BasicStringConverter implements IAMFToID3Converter
{

	public static final String ID_PAYLOAD = "payload";
	public static final String ID_WOWZA_CONVERTER = "wowzaConverter";
	public static final String WOWZA_CONVERTER_TYPE_BASIC_STRING = "basic_string";

	private int maxVerboseConversionMessages = 20;
	private int countVerboseMessages = 0;

	public AMFToID3BasicStringConverter()
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
				WMSLoggerFactory.getLogger(AMFToID3Converter.class)
						.info("AMFToID3BasicStringConverter[" + context.getContextString() + "]: Converted AMF structure: " + AMFToDebugFormatter.amfToDebug(
								amfList).replace('\n', '|'));
				WMSLoggerFactory.getLogger(AMFToID3Converter.class)
						.info("AMFToID3BasicStringConverter[" + context.getContextString() + "]: To ID3 AMF desc:" + payloadType + " value:" + payload.replace(
								'\n', '|'));
			}

			break;
		}

	}

}
