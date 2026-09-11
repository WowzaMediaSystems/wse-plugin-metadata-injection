package com.wowza.wms.plugin.metadatainjection.amf;

import java.util.ArrayList;
import java.util.HashMap;

import com.wowza.wms.amf.AMFData;
import com.wowza.wms.amf.AMFDataArray;
import com.wowza.wms.amf.AMFDataItem;
import com.wowza.wms.amf.AMFDataList;
import com.wowza.wms.amf.AMFDataObj;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.media.mp3.model.idtags.ID3Frames;

/** Top level converter that detects "wowza_converter" param, chooses converter and creates ID3 frames
 *
 * @author scott
 *
 */
public class AMFToID3Converter
{
	private AMFToID3BasicStringConverter basicStringConverter = null;
	private AMFToID3BasicJSONConverter basicJSONConverter = null;
	private int maxFailedConversionMessages = 20;
	private int countFailedConversionMessages = 0;
	private int maxVerboseConversionMessages = 20;
	private String contextStr = "";

	public AMFToID3Converter()
	{
		// Create all converters here

		// "basic_string"
		basicStringConverter = new AMFToID3BasicStringConverter();
		basicStringConverter.setMaxVerboseConversionMessages(maxVerboseConversionMessages);

		basicJSONConverter = new AMFToID3BasicJSONConverter();
		basicJSONConverter.setMaxVerboseConversionMessages(maxVerboseConversionMessages);
	}

	public void convertAMFDataListToID3(AMFDataList amfList, AMFToID3ConverterContext context, ID3Frames id3Frames)
	{
		try
		{
			while (true)
			{
				// First object must be string identifying the type
				// Second object must be the object payload
				if (!hasCorrectStructure(amfList))
					break;

				AMFDataObj payloadObj = amfList.getObject(1);

				// Get converter ID.  Some AMF data (ex. latency beacon) may not have it
				String converterID = null;
				String metaType = "unknown";
				try
				{
					converterID = payloadObj.get(AMFToID3BasicStringConverter.ID_WOWZA_CONVERTER).toString();
				}
				catch (Exception e)
				{

				}
				try
				{
					metaType = amfList.getString(0);

				}
				catch (Exception e)
				{

				}
				IAMFToID3Converter converter = getConverterByID(amfList, converterID);
				if (converter != null)
				{
					converter.convertAMFDataListToID3(amfList, context, id3Frames);
				}
				else
				{
					countFailedConversionMessages++;
					if (countFailedConversionMessages < maxFailedConversionMessages)
					{
						WMSLoggerFactory.getLogger(AMFToID3Converter.class)
								.warn("MetadataInjection:Unable to convert AMF data: No Converter found:" + metaType);
					}

				}

				break;
			}
		}
		catch (Exception e)
		{
			countFailedConversionMessages++;
			if (countFailedConversionMessages < maxFailedConversionMessages)
			{
				WMSLoggerFactory.getLogger(AMFToID3Converter.class)
						.error("MetadataInjection:Exception converting AMF data structure", e);
				WMSLoggerFactory.getLogger(AMFToID3Converter.class)
						.warn("MetadataInjection:Failed structure: " + AMFToDebugFormatter.amfToDebug(amfList)
								.replace('\n', '|'));
			}
		}

	}

	public static boolean hasCorrectStructure(AMFDataList amfList)
	{
		if (amfList == null)
			return false;

		if (amfList.size() <= 1)
			return false;

		AMFData amfDataID = amfList.get(0);
		AMFData amfDataPayload = amfList.get(1);
		if (!(amfDataID instanceof AMFDataItem))
			return false;
		if (!(amfDataPayload instanceof AMFDataObj))
			return false;

		if (amfDataID.getType() != AMFData.DATA_TYPE_STRING)
			return false;

		if (amfDataPayload.getType() != AMFData.DATA_TYPE_OBJECT && amfDataPayload.getType() != AMFData.DATA_TYPE_MIXED_ARRAY)
			return false;

		return true;
	}

	private IAMFToID3Converter getConverterByID(AMFDataList amfList, String converterID)
	{
		if (converterID == null)
		{
			try
			{
//				String payloadType = amfList.getString(0);
			}
			catch (Exception e)
			{
			}

		}
		else if (converterID.toLowerCase().equals(AMFToID3BasicStringConverter.WOWZA_CONVERTER_TYPE_BASIC_STRING))
		{
			return basicStringConverter;
		}
		else if (converterID.toLowerCase().equals(AMFToID3BasicJSONConverter.WOWZA_CONVERTER_TYPE_BASIC_JSON))
		{
			return basicJSONConverter;
		}
		else
		{

			WMSLoggerFactory.getLogger(AMFToID3Converter.class)
					.warn("MetadataInjection:No converter found for ID \"" + converterID + "\"");
		}

		return null;

	}

	public void setMaxFailedConversionMessages(int maxFailedConversionMessages)
	{
		this.maxFailedConversionMessages = maxFailedConversionMessages;
	}

	public void setMaxVerboseConversionMessages(int maxVerboseConversionMessages)
	{
		this.maxVerboseConversionMessages = maxVerboseConversionMessages;
		basicStringConverter.setMaxVerboseConversionMessages(maxVerboseConversionMessages);
		basicJSONConverter.setMaxVerboseConversionMessages(maxVerboseConversionMessages);
	}

	public void setContextStr(String contextStr)
	{
		this.contextStr = contextStr;
	}

	public String getContextStr()
	{
		return this.contextStr;
	}

	public static HashMap<String, Object> AMFObjtoJSON(AMFDataObj amfDataObj)
	{
		HashMap<String, Object> retVal = new HashMap<>();
		for (Object key : amfDataObj.getKeys())
		{
			AMFData amfData = amfDataObj.get(key.toString());
			Object value = AMFDataItemToObject(amfData);
			retVal.put(key.toString(), value);
		}
		return retVal;
	}

	private static Object AMFDataItemToObject(AMFData amfData)
	{
		Object retVal = null;
		if (amfData.getType() == AMFData.DATA_TYPE_STRING)
		{
			retVal = amfData.toString();
		}
		else if (amfData.getType() == AMFData.DATA_TYPE_NUMBER)
		{
			retVal = amfData.getValue();
		}
		else if (amfData.getType() == AMFData.DATA_TYPE_BOOLEAN)
		{
			retVal = amfData.getValue();
		}
		else if (amfData.getType() == AMFData.DATA_TYPE_DATE)
		{
			retVal = amfData.getValue();
		}
		else if (amfData.getType() == AMFData.DATA_TYPE_OBJECT)
		{
			retVal = AMFObjtoJSON((AMFDataObj)amfData);
		}
		else if (amfData.getType() == AMFData.DATA_TYPE_ARRAY)
		{
			int size = ((AMFDataArray)amfData).size();
			retVal = new ArrayList();
			for (int i = 0; i < size; i++)
			{
				((ArrayList)retVal).add(AMFDataItemToObject(((AMFDataArray)amfData).get(i)));
			}
		}
		return retVal;
	}
}
