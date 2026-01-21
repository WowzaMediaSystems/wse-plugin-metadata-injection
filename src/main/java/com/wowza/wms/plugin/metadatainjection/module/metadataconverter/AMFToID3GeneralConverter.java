package com.wowza.wms.plugin.metadatainjection.module.metadataconverter;

import com.wowza.wms.amf.*;
import com.wowza.wms.media.mp3.model.idtags.*;

import java.util.List;

/** The beginnings of a general converter for AMF data of any structure to ID3 (incomplete and unused)
 *
 * @author scott
 *
 */
public class AMFToID3GeneralConverter implements IAMFToID3Converter
{

	public AMFToID3GeneralConverter()
	{

	}

	@SuppressWarnings("rawtypes")
	public void convertAMFDataListToID3(AMFDataList amfList, AMFToID3ConverterContext context, ID3Frames id3Frames)
	{
		while (true)
		{
			if (amfList.size() <= 1)
				break;

			// First object must be string identifying the type
			// Second object must be the object payload
			if (amfList.get(0).getType() != AMFData.DATA_TYPE_STRING && amfList.get(1).getType() != AMFData.DATA_TYPE_OBJECT)
				break;

			//String payloadType = amfList.getString(0);
			AMFDataObj payloadObj = amfList.getObject(1);

			List keys = payloadObj.getKeys();
			for (int i = 0; i < keys.size(); i++)
			{
				String key = (String)keys.get(i);

				AMFDataItem valueItem = (AMFDataItem)payloadObj.get(key);
				if (valueItem == null)
					break;

				String value = valueItem.toString();

				ID3V2FrameTextInformationUserDefined id3 = new ID3V2FrameTextInformationUserDefined();
				id3.setDescription(key);
				id3.setValue(value);
				id3Frames.putFrame(id3);
			}
			break;
		}

	}

}
