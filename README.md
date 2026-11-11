# Wowza Streaming Engine Metadata Injection Plugin

This module adds metadata to a live stream. Events are sent to a REST API, or by a WebRTC publisher over a [data channel](#injecting-over-a-data-channel), and are injected into the stream as AMFData, which can then be converted to ID3 tags for HLS and CMAF players. The same data can also be sent to WebRTC viewers over a [data channel](#webrtc-data-channels). A GUID is created for each event and returned to the sender.

This module leverages the WSE classes:
* `HTTPProvider2Base`: support of using a REST API with engine
* `ModuleBase`: support for accessing the LiveStreamPacktizers
* `IHTTPStreamerCupertinoLivePacketizerDataHandler2` to access HLS/TS media segments to add AMFData, convert to ID3, and insert Program Date Time
* `IHTTPStreamerMPEGDashLivePacketizerDataHandler` to access CMAF (fMP4) media segments and deliver the same ID3 tags as `emsg` boxes
* `IMediaStreamLivePacketNotify` and the `onWebRTCDataChannel*` module callbacks to send the same data to WebRTC viewers over a data channel, and to receive events from a WebRTC publisher on a data channel

### Packetizer support

| Packetizer | Output | Delivery |
| ---------- | ------ | -------- |
| `cupertinostreamingpacketizer` (HLS/TS) | ID3v2 tags in the transport stream | Per-chunk ID3 header (PDT) and timed ID3 frames (AMF data) |
| `cmafstreamingpacketizer` (HLS/DASH fMP4) | ID3v2 tags wrapped in `emsg` boxes, scheme `https://aomedia.org/emsg/ID3` | One `emsg` per injected data event (`presentation_time` = packet timecode, timescale 1000) plus one `programDateTime` `emsg` at the start of each segment |

The CMAF `emsg` scheme follows the [ID3 Timed Metadata in CMAF](https://aomediacodec.github.io/id3-emsg/) spec and is recognised by HLS.js, dash.js, Shaka Player and Apple AVPlayer. The `amfToID3ConversionAddToManifest` option (`#EXT-X-METADATA-EVENT-*` chunklist tags) applies to the Cupertino packetizer only.

## WebRTC data channels

Added in version 3.0.0. WebRTC playback has no packetizer and no ID3 tags, so the module can send each data event to WebRTC viewers over a data channel instead. In the other direction, a WebRTC publisher can [inject events over a data channel](#injecting-over-a-data-channel) instead of calling the REST API. Both are set up as described in [Configuration](#configuration). Requires Wowza Streaming Engine 4.12 or later; on older versions the setting is ignored with a warning and everything else keeps working.

* **What is sent:** one string message per data event, identical to the value of the ID3 `TXXX` frame for that event. For `"id3": true` injections that is the JSON `{...data, "eventType": "<event>", "guid": "<guid>"}` (plus `injectTime` when requested). Only events that are converted to ID3 are sent; `programDateTime` tags describe HLS/CMAF segments and are not sent.
* **When:** as soon as the event reaches the stream. WebRTC latency is sub-second, so the message arrives close to the frames it was injected next to, but it is not tied to a frame.
* **Channel:** the module opens a channel labeled `metadata` (`metadataDataChannelOutputLabel`) toward every viewer of the stream. Publishers do not get one. If the player has already opened a channel with that label, that channel is used.
* **Independent of HLS:** delivery does not depend on `amfToID3ConversionEnabled` or on any packetizer being active.
* **Size limit:** a message must be smaller than 65,535 bytes. Larger events, and events sent while a viewer's channel is closed or its send buffer is full, are not delivered to that viewer and are not retried. A warning is logged (limited by `amfToID3ConversionFailedMaximum`).
* **Simulcast:** viewers of a simulcast rendition receive the events of the stream the publisher sent, as with WSE's own data channel routing. Viewers of a Transcoder rendition receive the events present on that rendition's stream.

### Injecting over a data channel

With `metadataDataChannelEnabled` on and `metadataDataChannelInputLabel` set (under `<Application>` / `<Properties>`), the WebRTC publisher of a stream can inject metadata without calling the REST API. The publisher opens a data channel with that label and sends one string message per injection.

* **Message:** the same JSON as the body of an API `POST` (see [Payload](#payload-json)), for example `{"event":"test-event","id3":true,"data":{"stringTest":"hello"}}`. `delay`, `repeat` and `repeatInterval` work as they do in the API; the injection always runs asynchronously.
* **Result:** the event is added to the publisher's stream as AMF data, so it reaches HLS/CMAF players as ID3 and WebRTC viewers on the output channel, exactly like an API injection.
* **Reply:** the module answers on the same channel with the API's JSON: `{"status":"success", "guid":"..."}`, or `{"status":"failed", "reason":"..."}` when the message is not valid JSON or has no `event`.
* **Who can inject:** only the publisher of the stream. A viewer that opens the channel is answered with `failed`.
* **Relay:** like the output label, the input label is not relayed to viewers by `metadataDataChannelRelay`.

```xml
<Property>
	<Name>metadataDataChannelInputLabel</Name>
	<Value>metadata</Value>
	<Type>String</Type>
</Property>
```

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

The player and publisher in this repository connect with WHEP and WHIP, which need the following on top of a WebRTC-enabled application (see `wse.addon/conf` for a working setup):

* `Application.xml`: `<Enable>true</Enable>` in `<WebRTC>`, and `webrtc` added to `<HTTPStreamers>`
* `VHost.xml`: a `webrtc` entry in `<HTTPStreamerAdapters>`, and `webrtc` added to the `<HTTPStreamerAdapterIDs>` of an SSL `<HostPort>` (WHEP and WHIP do not work on a non-SSL port)

```xml
<HTTPStreamerAdapter>
	<ID>webrtc</ID>
	<Name>webrtc</Name>
	<Properties>
	</Properties>
</HTTPStreamerAdapter>
```

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

2. Open the sample pages at [https://wse-trial.wowza.com:8443/metadata-injection/](https://wse-trial.wowza.com:8443/metadata-injection/). `wse-trial.wowza.com` resolves to `127.0.0.1`, and both containers use the Wowza-provided certificate for that name, so the pages load over HTTPS without a browser warning. The landing page links to the two players and the publisher:

   * [HLS / ID3 player](https://wse-trial.wowza.com:8443/metadata-injection/hls.html?src=https%3A%2F%2Fwse-trial.wowza.com%2Flive%2FmyStream%2Fplaylist.m3u8) plays `https://wse-trial.wowza.com/live/myStream/playlist.m3u8` and shows the ID3 tags as they are received in the stream.
   * [WebRTC data channel player](https://wse-trial.wowza.com:8443/metadata-injection/webrtc.html?src=https%3A%2F%2Fwse-trial.wowza.com%2Flive%2FmyStream%2Fwhep) plays `https://wse-trial.wowza.com/live/myStream/whep` and shows the same events as they are received on the `metadata` data channel.
   * [WebRTC publisher](https://wse-trial.wowza.com:8443/metadata-injection/publish.html) publishes your camera to `https://wse-trial.wowza.com/live/webrtcStream/whip` and injects events by sending them on the `metadata` data channel. Press **Send Event (Channel)**; the module's reply appears in **Last GUID sent** and the log. To see the event arrive, play `webrtcStream` in one of the players.

3. On either player, press **Send Event (API)**. The GUID the API returns appears in **Last GUID sent** (or the API's error message if the request fails), and the event appears in the **Raw JSON Log** when the player receives it. Over HLS that takes a few segments; over WebRTC it is close to immediate.

   * **Event String** sets the `stringTest` value in the event data.
   * **Config** sets the `delay`, `repeat` and `repeatInterval` sent with the event (defaults: 500 ms, 2, 500 ms).
   * A repeated event keeps its GUID, so the second and later copies are marked `[Dup]` in the log.
   * **Clear** empties the log.

The same pages are also served over plain HTTP at [http://wse-trial.wowza.com:8088/metadata-injection/](http://wse-trial.wowza.com:8088/metadata-injection/).

### Ports used by the demo

| Port | Container | Use |
| ---- | --------- | --- |
| 8443 | Manager | HTTPS: sample pages and Wowza Streaming Engine Manager. Enabled by `JKS_TRIAL=true` in `docker-compose.yaml`; set `JKS_FILE` and `JKS_PASSWORD` instead to use your own certificate. |
| 8088 | Manager | HTTP: sample pages and Wowza Streaming Engine Manager |
| 443 | Engine | HTTPS streaming: HLS playback and WebRTC (WHEP playback, WHIP publish) |
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

Optional: to secure the API with a `metadata-api-key` header instead of the `<AuthenticationMethod>` above, add these to the VHost-level `<Properties>` (see [Securing the API](#securing-the-api)). `optionsCORSHeadersAddMain` is only needed when a browser page calls the API.

```xml
<Property>
	<Name>useMetadataApiKey</Name>
	<Value>true</Value>
</Property>
<Property>
	<Name>metadataApiKey</Name>
	<Value>secretkey</Value>
	<Type>String</Type>
</Property>
<Property>
	<Name>optionsCORSHeadersAddMain</Name>
	<Value>Access-Control-Allow-Headers:metadata-api-key</Value>
	<Type>String</Type>
</Property>
```

### Application.xml
Add the following module to `<Modules>`. It converts the injected AMF data to ID3 tags for HLS and CMAF, adds the `programDateTime` ID3 tags, and handles the WebRTC data channels:
```xml
<Module>
	<Name>MetadataInjectionModule</Name>
	<Description>MetadataInjectionModule</Description>
	<Class>com.wowza.wms.plugin.metadatainjection.MetadataInjectionModule</Class>
</Module>
```

> [!Note]
> ### Removed class names (upgrading from 1.x or 2.x)
> Version 2.0.0 moved the module and HTTP provider to the `com.wowza.wms.plugin.metadatainjection` package and deprecated the old class names. Version 3.0.0 removes them. A configuration that still uses an old name fails to load that class on 3.0.0, so update it before upgrading:
> | Removed class | Replacement | Config file |
> | :---------------- | :----------- | :----------- |
> | `com.wowza.wms.plugin.metadatainjection.module.ID3AndPDTInjectionModule` | `com.wowza.wms.plugin.metadatainjection.MetadataInjectionModule` | `Application.xml` |
> | `com.wowza.wms.plugin.metadatainjection.httpprovider.HTTPProviderMetadataInjection` | `com.wowza.wms.plugin.metadatainjection.HTTPProviderMetadataInjection` | `VHost.xml` |
>
> The replacements behave the same as the removed classes did.


### Properties:

| Name                  | Type                                           | Description                                                                      |
| -------------------- | ------------------------------------------------- | ---------------------------------------------------------------------------- |
| metadataApiKey             | String   | Can set a authorization key/header with Application.xml property.  This will require the api request to have a header `metadata-api-key`  Overrides property in vhost.xml if exists|
| amfToID3ConversionEnabled | Boolean | convert AMF data to ID3 data.  Default false |
| amfToID3ConversionAddToManifest | Boolean | Adds to HLS Manifest tag `#EXT-X-METADATA-EVENT-*` with guid of event. Default false |
| amfToID3ConversionVerboseMaximum | Integer | How many verbose log messges to log. Default 5| 
| amfToID3ConversionFailedMaximum | Integer | How many failed log messages to log. Default 5
| metadataDataChannelEnabled | Boolean | Send data events to WebRTC viewers over a data channel. Requires WSE 4.12+ and the WebRTC properties below. Default false |
| metadataDataChannelOutputLabel | String | Label of the data channel opened toward each WebRTC viewer. Default `metadata` |
| metadataDataChannelInputLabel | String | Label of a data channel on which the stream's WebRTC publisher can inject metadata (see [Injecting over a data channel](#injecting-over-a-data-channel)). May be the same as the output label. Not set by default, which turns this off |
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
* authorization: HTTP Basic/Digest credentials or a `metadata-api-key` header, see [Securing the API](#securing-the-api)

### API supports methods/verbs
`GET | POST `

### Securing the API

The API is an HTTP provider on the streaming ports, not part of the Wowza Streaming Engine REST API (port 8087). It can be protected in one of two ways, selected by the `useMetadataApiKey` property in the `<Properties>` of `VHost.xml`. The two are exclusive: when the API key is in use, the `<AuthenticationMethod>` is ignored.

| `useMetadataApiKey` | How a request is authorized |
| :------------------ | :-------------------------- |
| `false` (default) | By the `<AuthenticationMethod>` of the `<HTTPProvider>` entry in `VHost.xml`: `admin-basic` (HTTP Basic) or `admin-digest` (HTTP Digest) check the user against `conf/admin.password`, the same users as Wowza Streaming Engine Manager; `none` leaves the API open. |
| `true` | The request must carry a `metadata-api-key` header equal to the `metadataApiKey` property. A missing or wrong key is answered with `401`. |

**Username and password**

```shell
curl -u admin:password -X POST -H "Content-Type: application/json" -d '{"event":"dataTest","id3":true,"data":{}}' \
	http://127.0.0.1/v1/server/plugin/metaDataInjection/applications/live/streams/mystream
```

**API key**

```xml
<!-- VHost.xml, <VHost>/<Properties> -->
<Property>
	<Name>useMetadataApiKey</Name>
	<Value>true</Value>
</Property>
<Property>
	<Name>metadataApiKey</Name>
	<Value>secretkey</Value>
	<Type>String</Type>
</Property>
```

```shell
curl -X POST -H "Content-Type: application/json" -H "metadata-api-key:secretkey" -d '{"event":"dataTest","id3":true,"data":{}}' \
	http://127.0.0.1/v1/server/plugin/metaDataInjection/applications/live/streams/mystream
```

* `metadataApiKey` can also be set in the `<Properties>` of an `Application.xml`, which overrides the `VHost.xml` value for requests to that application.
* With `useMetadataApiKey` set to `true` and no `metadataApiKey` defined for the VHost or the application, every request is accepted. Always set a key when turning this on.
* A browser page that calls the API needs the header it sends allowed by CORS: set `optionsCORSHeadersAddMain` in `VHost.xml` to `Access-Control-Allow-Headers:authorization` or `Access-Control-Allow-Headers:metadata-api-key`.
* Credentials and keys are sent in clear text over HTTP. Serve the API from an SSL `<HostPort>` when it is reachable from outside the server.
* Injection over a [WebRTC data channel](#injecting-over-a-data-channel) does not use these settings: whoever can publish the stream can inject on it, so secure WebRTC publishing instead.

### Payload (JSON)

#### Properties
| Property      | Description                                              |
|:--------------|:---------------------------------------------------------|
| event       | Name of the data event to be triggeed          |
| async   | Return right away from api call (default false)          |
| delay       | Delay before the injection, in milliseconds (default 0)                |
| repeat            | How many times to inject data   (default 1) |
| repeatInterval            | Time between repeated events, in milliseconds (default 0)|
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