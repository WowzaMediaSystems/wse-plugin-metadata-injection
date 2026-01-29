package com.wowza.wms.plugin.metadatainjection.datahandler.id3;

import com.wowza.wms.amf.AMFDataList;
import com.wowza.wms.media.mp3.model.idtags.ID3Frames;

public interface IAMFToID3Converter
{

	public void convertAMFDataListToID3(AMFDataList amf, AMFToID3ConverterContext context, ID3Frames id3Frames);

}
