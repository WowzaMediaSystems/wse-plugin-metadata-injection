package com.wowza.wms.plugin.metadatainjection.httpprovider;

/**
 * Backward-compatibility shim for the metadata injection HTTP provider.
 * <p>
 * Prior to 2.0.0 the HTTP provider lived in this package. It has moved to
 * {@link com.wowza.wms.plugin.metadatainjection.HTTPProviderMetadataInjection} and this class
 * remains only so that existing {@code VHost.xml} entries referencing the old class name keep
 * working. It adds no behaviour of its own; every request is handled by the parent class.
 * <p>
 *
 * @deprecated As of 2.0.0, replaced by
 *             {@link com.wowza.wms.plugin.metadatainjection.HTTPProviderMetadataInjection}. This
 *             class will be removed in a future major release.
 */
@Deprecated
public class HTTPProviderMetadataInjection extends com.wowza.wms.plugin.metadatainjection.HTTPProviderMetadataInjection
{

}
