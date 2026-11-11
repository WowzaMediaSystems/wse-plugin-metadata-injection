# Wowza Streaming Engine Metadata Injection Plugin

This module provides a REST API to add metadata to a live stream. This is done by injecting AMFData which can then be converted to ID3 tags.  A GUID is created for each event sent and returned in the HTTP POST request. The same data can also be sent to WebRTC viewers over a [data channel](#webrtc-data-channels).

This module leverages the WSE classes:
* `HTTPProvider2Base`: support of using a REST API with engine
* `ModuleBase`: support for accessing the LiveStreamPacktizers
* `IHTTPStreamerCupertinoLivePacketizerDataHandler2` to access HLS/TS media segments to add AMFData, convert to ID3, and insert Program Date Time
* `IHTTPStreamerMPEGDashLivePacketizerDataHandler` to access CMAF (fMP4) media segments and deliver the same ID3 tags as `emsg` boxes
* `IMediaStreamLivePacketNotify` and the `onWebRTCDataChannel*` module callbacks to send the same data to WebRTC viewers over a data channel

### Packetizer support

| Packetizer | Output | Delivery |
| ---------- | ------ | -------- |
| `cupertinostreamingpacketizer` (HLS/TS) | ID3v2 tags in the transport stream | Per-chunk ID3 header (PDT) and timed ID3 frames (AMF data) |
| `cmafstreamingpacketizer` (HLS/DASH fMP4) | ID3v2 tags wrapped in `emsg` boxes, scheme `https://aomedia.org/emsg/ID3` | One `emsg` per injected data event (`presentation_time` = packet timecode, timescale 1000) plus one `programDateTime` `emsg` at the start of each segment |

The CMAF `emsg` scheme follows the [ID3 Timed Metadata in CMAF](https://aomediacodec.github.io/id3-emsg/) spec and is recognised by HLS.js, dash.js, Shaka Player and Apple AVPlayer. The `amfToID3ConversionAddToManifest` option (`#EXT-X-METADATA-EVENT-*` chunklist tags) applies to the Cupertino packetizer only.

## WebRTC data channels

WebRTC playback has no packetizer and no ID3 tags, so the module can send each data event to WebRTC viewers over a data channel instead. Requires Wowza Streaming Engine 4.12 or later; on older versions the setting is ignored with a warning and everything else keeps working.

* **What is sent:** one string message per data event, identical to the value of the ID3 `TXXX` frame for that event. For `"id3": true` injections that is the JSON `{...data, "eventType": "<event>", "guid": "<guid>"}` (plus `injectTime` when requested). Only events that are converted to ID3 are sent; `programDateTime` tags describe HLS/CMAF segments and are not sent.
* **When:** as soon as the event reaches the stream. WebRTC latency is sub-second, so the message arrives close to the frames it was injected next to, but it is not tied to a frame.
* **Channel:** the module opens a channel labeled `metadata` (`metadataDataChannelLabel`) toward every viewer of the stream. Publishers do not get one. If the player has already opened a channel with that label, that channel is used.
* **Independent of HLS:** delivery does not depend on `amfToID3ConversionEnabled` or on any packetizer being active.
* **Size limit:** a message must be smaller than 65,535 bytes. Larger events, and events sent while a viewer's channel is closed or its send buffer is full, are not delivered to that viewer and are not retried. A warning is logged (limited by `amfToID3ConversionFailedMaximum`).
* **Simulcast:** viewers of a simulcast rendition receive the events of the stream the publisher sent, as with WSE's own data channel routing. Viewers of a Transcoder rendition receive the events present on that rendition's stream.

### Configuration

In `Application.xml`, enable data channels in `MODULE` mode under `<WebRTC>` / `<Properties>`:

```xml
<Property>
	<Name>webrtcEnableDataChannels</Name>
	<Value>true</Value>
	<Type>Boolean</Type>
</Property>
<Property>
	<Name>webrtcDataChannelMode</Name>
	<Value>MODULE</Value>
	<Type>String</Type>
</Property>
```

and turn the feature on under `<Application>` / `<Properties>`:

```xml
<Property>
	<Name>metadataDataChannelEnabled</Name>
	<Value>true</Value>
	<Type>Boolean</Type>
</Property>
```

`MODULE` mode is required: in the default `BROADCAST` mode WSE does not give modules access to the data channels. `MODULE` mode turns off WSE's own routing, so this module does that routing itself (`metadataDataChannelRelay`, on by default): each label the publisher opens is opened toward every viewer, including viewers who join later, and closed when the publisher closes it; publisher messages go to all viewers and viewer messages go to the publisher. Set `metadataDataChannelRelay` to `false` if another module in the application already routes data channels.

The player in this repository connects with WHEP, which needs the following on top of a WebRTC-enabled application (see `wse.addon/conf` for a working setup):

* `Application.xml`: `<Enable>true</Enable>` in `<WebRTC>`, and `webrtc` added to `<HTTPStreamers>`
* `VHost.xml`: a `webrtc` entry in `<HTTPStreamerAdapters>`, and `webrtc` added to the `<HTTPStreamerAdapterIDs>` of an SSL `<HostPort>` (WHEP does not work on a non-SSL port)

```xml
<HTTPStreamerAdapter>
	<ID>webrtc</ID>
	<Name>webrtc</Name>
	<Properties>
	</Properties>
</HTTPStreamerAdapter>
```

### Player

The player makes the WebRTC offer, so it has to ask for data channels even though the server opens the channel:

```js
const pc = new RTCPeerConnection();
pc.addTransceiver('video', { direction: 'recvonly' });
pc.addTransceiver('audio', { direction: 'recvonly' });

// Adds the data channel section to the offer without opening a visible channel
pc.createDataChannel('__sctp_bootstrap__', { negotiated: true, id: 0 });

pc.addEventListener('datachannel', (event) => {
  if (event.channel.label !== 'metadata') return;
  event.channel.addEventListener('message', (message) => {
    const metadata = JSON.parse(message.data); // { eventType, guid, ...data }
  });
});
// createOffer(), then WHEP: POST the offer to https://[address]/[application]/[stream]/whep
```

A complete player is in [webpage/webrtc.html](webpage/webrtc.html).

## Prerequisites

* Wowza Streaming Engine™ 4.9.4 or later is required
* WebRTC data channels require Wowza Streaming Engine™ 4.12 or later

## Build instructions

1. Clone this repository to your local filesystem.
2. Run `./build.sh`  This will build the module/jar file with the `wse-plugin-builder` using docker

The build needs the Wowza Streaming Engine 4.12 (or later) libraries because of the WebRTC data channel API. The jar still runs on 4.9.4 and later.

## Run the Demo

After building the module, start Wowza Streaming Engine and Wowza Streaming Engine Manager using the docker-compose.yaml file in this repository. It includes a pre-configured Wowza Streaming Engine instance and sample `live` and `simu-live` applications.

1. Run the following command to launch WSE and WSEM:

```bash
docker compose up
```

2. Open the sample pages at [https://wse-trial.wowza.com:8443/metadata-injection/](https://wse-trial.wowza.com:8443/metadata-injection/). `wse-trial.wowza.com` resolves to `127.0.0.1`, and both containers use the Wowza-provided certificate for that name, so the pages load over HTTPS without a browser warning. The landing page links to the two players:

   * [HLS / ID3 player](https://wse-trial.wowza.com:8443/metadata-injection/hls.html?src=https%3A%2F%2Fwse-trial.wowza.com%2Flive%2FmyStream%2Fplaylist.m3u8) plays `https://wse-trial.wowza.com/live/myStream/playlist.m3u8` and shows the ID3 tags as they are received in the stream.
   * [WebRTC data channel player](https://wse-trial.wowza.com:8443/metadata-injection/webrtc.html?src=https%3A%2F%2Fwse-trial.wowza.com%2Flive%2FmyStream%2Fwhep) plays `https://wse-trial.wowza.com/live/myStream/whep` and shows the same events as they are received on the `metadata` data channel.

3. On either player, press **Send Event**. The GUID the API returns appears in **Last GUID sent** (or the API's error message if the request fails), and the event appears in the **Raw JSON Log** when the player receives it. Over HLS that takes a few segments; over WebRTC it is close to immediate.

   * **Event String** sets the `stringTest` value in the event data.
   * **Event Config** sets the `delay`, `repeat` and `repeatInterval` sent with the event (defaults: 500 ms, 2, 500 ms).
   * A repeated event keeps its GUID, so the second and later copies are marked `[Dup]` in the log.
   * **Clear** empties the log.

The same pages are also served over plain HTTP at [http://wse-trial.wowza.com:8088/metadata-injection/](http://wse-trial.wowza.com:8088/metadata-injection/).

### Ports used by the demo

| Port | Container | Use |
| ---- | --------- | --- |
| 8443 | Manager | HTTPS: sample pages and Wowza Streaming Engine Manager. Enabled by `JKS_TRIAL=true` in `docker-compose.yaml`; set `JKS_FILE` and `JKS_PASSWORD` instead to use your own certificate. |
| 8088 | Manager | HTTP: sample pages and Wowza Streaming Engine Manager |
| 443 | Engine | HTTPS streaming: HLS playback and WebRTC (WHEP) |
| 8444 | Engine | HTTPS, metadata injection API only. Used by the sample pages when they are loaded over HTTPS. |
| 1935, 80 | Engine | HTTP streaming and the metadata injection API. Used by the sample pages when they are loaded over HTTP. |

The API has its own SSL port because a page loaded over HTTPS can only call HTTPS, and the API cannot share port 443 with playback: Wowza Streaming Engine does not answer an HTTP provider request that arrives on a keep-alive connection which has already carried HLS. A browser never reuses a connection across ports, so a port that serves only the API avoids this. See the `Metadata Injection API SSL` host port in [wse.addon/conf/VHost.xml](wse.addon/conf/VHost.xml).


## Install on existing WSE instance


* copy wse-plugin-metadata-injection jar to lib folder

### VHost.xml 
add HTTPProvider to each `<HostPort>` that should serve the API. If a browser page calls the API over HTTPS, give the API an SSL `<HostPort>` of its own rather than adding it to the one used for playback (see [Ports used by the demo](#ports-used-by-the-demo)).

```xml
<HTTPProvider>
	<BaseClass>com.wowza.wms.plugin.metadatainjection.HTTPProviderMetadataInjection</BaseClass>
	<RequestFilters>v1/server/plugin/metaDataInjection*</RequestFilters>
	<AuthenticationMethod>admin-basic</AuthenticationMethod>
	<PasswordEncodingScheme>none</PasswordEncodingScheme>
</HTTPProvider>
```  

add Property
						<Property>
							<Name>useMetadataApiKey</Name>
							<Value>true</Value>
						</Property>

add Property, update to either metadata-api-key or authorization
```
<Property>
	<Name>optionsCORSHeadersAddMain</Name>
	<Value>Access-Control-Allow-Headers:metadata-api-key</Value>
	<Type>String</Type>
</Property>	
```
Optional
```
<Property>
	<Name>metadataApiKey</Name>
	<Value>secretkey</Value>
	<Type>String</Type>
</Property>

```
### Application.xml
Need to turn on Program Date Time for HLS by adding the following module:
```
<Module>
	<Name>MetadataInjectionModule</Name>
	<Description>MetadataInjectionModule</Description>
	<Class>com.wowza.wms.plugin.metadatainjection.MetadataInjectionModule</Class>
</Module>
```
> [!Note]
> ### Deprecated class names (upgrading from 1.x)
> Version 2.0.0 moved the module and HTTP provider to the `com.wowza.wms.plugin.metadatainjection` package. The old class names still work but are deprecated and will be removed in a future major release. Update your configuration to the new names:
> | Deprecated class | Replacement | Config file |
> | :---------------- | :----------- | :----------- |
> | `com.wowza.wms.plugin.metadatainjection.module.ID3AndPDTInjectionModule` | `com.wowza.wms.plugin.metadatainjection.MetadataInjectionModule` | `Application.xml` |
> | `com.wowza.wms.plugin.metadatainjection.httpprovider.HTTPProviderMetadataInjection` | `com.wowza.wms.plugin.metadatainjection.HTTPProviderMetadataInjection` | `VHost.xml` |
> 
> The deprecated classes are empty subclasses of their replacements, so behaviour is identical until they are removed.


### Properties:

| Name                  | Type                                           | Description                                                                      |
| -------------------- | ------------------------------------------------- | ---------------------------------------------------------------------------- |
| metadataApiKey             | String   | Can set a authorization key/header with Application.xml property.  This will require the api request to have a header `metadata-api-key`  Overrides property in vhost.xml if exists|
| amfToID3ConversionEnabled | Boolean | convert AMF data to ID3 data.  Default false |
| amfToID3ConversionAddToManifest | Boolean | Adds to HLS Manifest tag `#EXT-X-METADATA-EVENT-*` with guid of event. Default false |
| amfToID3ConversionVerboseMaximum | Integer | How many verbose log messges to log. Default 5| 
| amfToID3ConversionFailedMaximum | Integer | How many failed log messages to log. Default 5
| metadataDataChannelEnabled | Boolean | Send data events to WebRTC viewers over a data channel. Requires WSE 4.12+ and the WebRTC properties below. Default false |
| metadataDataChannelLabel | String | Label of the data channel opened toward each WebRTC viewer. Default `metadata` |
| metadataDataChannelRelay | Boolean | Forward the publisher's own data channels to viewers (and viewer messages to the publisher), replacing the WSE routing that `MODULE` mode turns off. Default true |

### Properties (WebRTC):
| Name                  | Type                                           | Description                                                                      |
| -------------------- | ------------------------------------------------- | ---------------------------------------------------------------------------- |
| webrtcEnableDataChannels | Boolean | Built-in WSE property (4.12+). Must be true for `metadataDataChannelEnabled`. Default false |
| webrtcDataChannelMode | String | Built-in WSE property (4.12+). Must be `MODULE` for `metadataDataChannelEnabled`. Default `BROADCAST` |


### Properties (HTTPStreamer):
| Name                  | Type                                           | Description                                                                      |
| -------------------- | ------------------------------------------------- | ---------------------------------------------------------------------------- |
| cupertinoEnableProgramDateTime             | Boolean   | Built-in WSE property. Adds `EXT-X-PROGRAM-DATE-TIME` to HLS chunklists (TS and CMAF). Default false. Not required for the ID3 tags below. On WSE versions older than 4.11 this module sets the TS chunk PDT itself when this is enabled. [Wowza Documentation](https://www.wowza.com/docs/how-to-control-display-of-program-date-and-time-headers-in-hls-chunklists-for-live-streams-ext-x-program-date-time) |
| cupertinoEnableId3ProgramDateTime             | Boolean   | Add a `programDateTime` ID3 tag to the ID3 header of each HLS/TS chunk. Default true. The value is the wall clock time the chunk was created and matches the chunk's `EXT-X-PROGRAM-DATE-TIME`; on WSE 4.11+ the server's value is reused as-is. |
| cupertinoProgramDateTimeOffset | Integer | How much to adjust the HLS/TS PDT in milliseconds. Default 0. Only applied when this module computes the PDT itself (WSE older than 4.11); ignored when the server has already set the chunk PDT. |
| cmafEnableId3ProgramDateTime             | Boolean   | Add a `programDateTime` ID3 tag (as an `emsg`) at the start of each CMAF segment. Defaults to the value of `cupertinoEnableId3ProgramDateTime` (true). The value is the wall clock time the segment was created, matching how WSE calculates `EXT-X-PROGRAM-DATE-TIME`; on WSE 4.11+ the server's value is reused as-is. |
| cmafProgramDateTimeOffset | Integer | How much to adjust the CMAF ID3 PDT in milliseconds. Defaults to the value of `cupertinoProgramDateTimeOffset` (0). Only applied when this module computes the time itself; ignored if the packetizer already exposes the segment's native time. |

### Properties (LiveStreamPacketizer):
| Name                  | Type                                           | Description                                                                      |
| -------------------- | ------------------------------------------------- | ---------------------------------------------------------------------------- |
| cmafDataEventsTrackType | String | Built-in WSE property selecting which CMAF track carries `emsg` boxes: `audio` or `video`. WSE defaults to `audio`; this module changes the default to `video` when the property is not set in Application.xml. Set it explicitly under `<Application>/<LiveStreamPacketizer>/<Properties>` to override. audio-only CMAF outputs must set this back to audio |


## API
### API patterns is
* `v1/server/plugin/metaDataInjection/applications/{appName}/streams/{streamName}`
* opitional header value `metadata-api-key` for authorization

### API supports methods/verbs
`GET | POST `

### Payload (JSON)

#### Properties
| Property      | Description                                              |
|:--------------|:---------------------------------------------------------|
| event       | Name of the data event to be triggeed          |
| async   | Return right away from api call (default false)          |
| delay       | Delay injection by seconds (default 0)                                 |
| repeat            | How many times to inject data   (default 1) |
| repeatInterval            | Time between repeated events  (default 0)|
| injectTime             | Include current time in metadata json object (default false)   |
|      id3        | Conver to ID3 tag (default false)|
| data| The json object to be sent|



## curl Examples

```
curl -X GET http://127.0.0.1/v1/server/plugin/metaDataInjection/version
```

```shell
curl -X POST  -H "Content-Type: application/json" -H "metadata-api-key:secretkey" -d '{
	"event": "dataTest",
	"async": true,
	"delay": 5000,
	"repeat": 3,
	"repeatInterval": 500,
	"injectTime": true,
	"id3":true,
	"data": {
		"stringTest": "apple",
		"objsTest": {
			"name": "Gpa",
			"parent": {
				"name": "Mom",
				"children": [
					{"name":"Boy"},
					{"name":"Girl"}
				]
			}
		},
		"intTest":1,
		"boolTest":true,
		"doubleTest":1.123,
		"bitIntTest":12345678901234567890,
		"longTest":1234567890123, 
		"array1Test" : [1,2,3], 
		"array2Test" : [{ "q1" : "one" },{ "q2" : "two" }]
	}}' http://127.0.0.1/v1/server/plugin/metaDataInjection/applications/live/streams/mystream
```

```
curl -X GET http://127.0.0.1/v1/server/plugin/metaDataInjection/injections/{guid}
```