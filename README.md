# Metadata Injection
This module provides a rest API to added metadata to a live stream. This is done by injecting AMFData which can be converted to ID3.  A GUID is created for each event sent and returned back after a POST

## Install
* copy wse-plugin-metadata-injection jar to lib folder

* add HTTPProvider to VHost.xml

```xml
<HTTPProvider>
	<BaseClass>com.wowza.wms.plugin.cloud.httpprovider.HTTPProviderMetadataInjection</BaseClass>
	<RequestFilters>v1/server/plugin/metaDataInjection*</RequestFilters>
	<AuthenticationMethod>none</AuthenticationMethod>
</HTTPProvider>
```  

Can set a authorization key/header with Application.xml property.  This will require the api request to have a header `metadata-api-key`

```xml
<Property>
  <Name>metadataApiKey</Name>
  <Value>foo-bar</Value>
  <Type>String</Type>
</Property>
```

### Properties (details TBD)
* amfToID3ConversionVerboseMaximum
* amfToID3ConversionFailedMaximum
* amfToID3ConversionEnabled
* amfToID3ConversionAddToManifest

* cupertinoEnableProgramDateTime
* cupertinoEnableId3ProgramDateTime
* cupertinoProgramDateTimeOffset

## API
### API patterns is
* `v1/server/plugin/metaDataInjection/applications/{appName}/streams/{streamName}`
* can use header value `metadata-api-key` for authorization

### API supports methods/verbs
`GET | POST `

### Metadata 
A json object can be passed into the video stream 
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