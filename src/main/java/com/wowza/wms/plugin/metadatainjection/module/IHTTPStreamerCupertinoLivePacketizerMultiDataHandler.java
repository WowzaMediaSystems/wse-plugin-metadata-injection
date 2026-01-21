package com.wowza.wms.plugin.metadatainjection.module;

import com.wowza.wms.httpstreamer.cupertinostreaming.livestreampacketizer.IHTTPStreamerCupertinoLivePacketizerDataHandler2;

public interface IHTTPStreamerCupertinoLivePacketizerMultiDataHandler extends IHTTPStreamerCupertinoLivePacketizerDataHandler2
{

	boolean isEnabled();

}
