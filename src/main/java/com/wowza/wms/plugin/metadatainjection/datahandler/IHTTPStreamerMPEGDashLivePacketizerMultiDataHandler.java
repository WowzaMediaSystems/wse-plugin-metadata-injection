package com.wowza.wms.plugin.metadatainjection.datahandler;

import com.wowza.wms.httpstreamer.mpegdashstreaming.livestreampacketizer.IHTTPStreamerMPEGDashLivePacketizerDataHandler;

/**
 * Data handler for the CMAF (and MPEG-DASH) live stream packetizers. CMAF segments carry timed
 * metadata as emsg boxes rather than ID3 tags in a transport stream, so implementations convert
 * ID3 frames into emsg frames.
 */
public interface IHTTPStreamerMPEGDashLivePacketizerMultiDataHandler extends IHTTPStreamerMPEGDashLivePacketizerDataHandler
{

	boolean isEnabled();

}
