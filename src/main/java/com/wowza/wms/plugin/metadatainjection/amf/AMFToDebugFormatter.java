package com.wowza.wms.plugin.metadatainjection.amf;

import com.wowza.wms.amf.*;
import com.wowza.wms.logging.WMSLoggerFactory;

import java.util.*;

/** Given an AMF data structure, convert it to a human-readable string
 *
 * @author scott
 *
 */
public class AMFToDebugFormatter
{
	public static String amfToDebug(AMFData amf)
	{
		String ret = "";
		int indent = 0;

		try
		{
			ret = amfDataToDebugString(indent, amf, ret);
		}
		catch (Exception e)
		{
			WMSLoggerFactory.getLogger(AMFToDebugFormatter.class)
					.error("Exception converting AMF data structure to human readable form", e);
		}

		return ret;
	}

	@SuppressWarnings("rawtypes")
	private static String objToDebug(int indent, AMFDataObj amf, String ret)
	{
		String spaces = "";
		ret += "AMFDataObj: {";

		indent += 2;

		List keys = amf.getKeys();
		Iterator iter = keys.iterator();

		for (int i = 0; i < indent; i++) spaces += " ";  // indent

		while (iter.hasNext())
		{
			String key = (String)iter.next();
			AMFData value = amf.get(key);

			ret += "\n";
			String valueStr = amfDataToDebugString(indent, value, "");
			ret += spaces;

			ret += key + ": " + valueStr;

		}

		indent -= 2;

		spaces = "";
		for (int i = 0; i < indent; i++) spaces += " ";  // indent

		ret += "\n";
		ret += spaces;
		ret += "}";

		return ret;
	}

	@SuppressWarnings("rawtypes")
	private static String mixedArrayToDebug(int indent, AMFDataMixedArray amf, String ret)
	{
		ret += "AMFDataMixedArray: {";

		indent += 2;
		String spaces = "";
		for (int i = 0; i < indent; i++) spaces += " ";  // indent

		List keys = amf.getKeys();
		Iterator iter = keys.iterator();
		while (iter.hasNext())
		{
			String key = (String)iter.next();
			AMFData value = amf.get(key);

			ret += "\n";
			ret += spaces;  // indent

			String valueStr = amfDataToDebugString(indent, value, "");
			ret += key + ": " + valueStr;
		}

		indent -= 2;

		spaces = "";
		for (int i = 0; i < indent; i++) spaces += " ";  // indent

		ret += "\n";
		ret += spaces;
		ret += "}";

		return ret;
	}

	private static String listToDebug(int indent, AMFDataList amf, String ret)
	{
		String spaces = "";

		ret += "AMFDataList: [";

		indent += 2;

		for (int i = 0; i < amf.size(); i++)
		{
			AMFData value = amf.get(i);

			ret += "\n";
			for (int kkk = 0; kkk < indent; kkk++) ret += " ";  // indent

			indent += 5;
			String valueStr = amfDataToDebugString(indent, value, "");
			indent -= 5;

			ret += "[" + i + "]: " + valueStr + ",";
		}

		indent -= 2;

		spaces = "";
		for (int i = 0; i < indent; i++) spaces += " ";  // indent

		ret += "\n";
		ret += spaces;
		ret += "]";

		return ret;
	}

	private static String itemToDebug(int indent, AMFDataItem amf, String ret)
	{
		ret += "(AMFDataItem) ";

		if (amf.getType() == AMFData.DATA_TYPE_STRING)
			ret += "\"";
		ret += amf.toString();
		if (amf.getType() == AMFData.DATA_TYPE_STRING)
			ret += "\"";

		ret += " " + getItemTypeInfo(amf.getType());

		return ret;
	}

	private static String byteArrayToDebug(int indent, AMFDataByteArray amf, String ret)
	{
		ret += "(AMFDataByteArray) sz:" + amf.size();

		return ret;
	}

	private static String arrayToDebug(int indent, AMFDataArray amf, String ret)
	{
		String spaces = "";

		ret += "AMFDataArray: [";

		indent += 2;

		for (int i = 0; i < amf.size(); i++)
		{
			AMFData value = amf.get(i);
			ret += "\n";
			for (int kkk = 0; kkk < indent; kkk++) ret += " ";  // indent

			String valueStr = amfDataToDebugString(indent, value, "");
			ret += "[" + i + "]: " + valueStr + ", ";
		}

		indent -= 2;

		spaces = "";
		for (int i = 0; i < indent; i++) spaces += " ";  // indent

		ret += "\n";
		ret += spaces;
		ret += "]";

		return ret;
	}

	private static String getItemTypeInfo(int itemType)
	{
		switch (itemType)
		{
		case AMFData.DATA_TYPE_STRING:
			return "(STRING)";
		case AMFData.DATA_TYPE_BOOLEAN:
			return "(BOOLEAN)";
		case AMFData.DATA_TYPE_NUMBER:
			return "(NUMBER)";
		default:
			return "(unknown)";
		}
	}

	private static String amfDataToDebugString(int indent, AMFData amf, String ret)
	{
		if (amf instanceof AMFDataArray)
		{
			ret = arrayToDebug(indent, (AMFDataArray)amf, ret);
		}
		else if (amf instanceof AMFDataByteArray)
		{
			ret = byteArrayToDebug(indent, (AMFDataByteArray)amf, ret);
		}
		else if (amf instanceof AMFDataItem)
		{
			ret = itemToDebug(indent, (AMFDataItem)amf, ret);
		}
		else if (amf instanceof AMFDataList)
		{
			ret = listToDebug(indent, (AMFDataList)amf, ret);
		}
		else if (amf instanceof AMFDataMixedArray)
		{
			ret = mixedArrayToDebug(indent, (AMFDataMixedArray)amf, ret);
		}
		else if (amf instanceof AMFDataObj)
		{
			ret = objToDebug(indent, (AMFDataObj)amf, ret);
		}
		else
		{

		}

		return ret;
	}

}
