package ortus.boxlang.modules.orm.hibernate.cache;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import javax.cache.CacheException;
import javax.cache.CacheManager;
import javax.cache.configuration.OptionalFeature;
import javax.cache.spi.CachingProvider;

/**
 * BoxLang Hibernate implementation of the {@link CachingProvider}.
 * 
 * @since 1.0.0
 */
public class BoxHibernateCachingProvider implements CachingProvider {

	/**
	 * The CacheManagers scoped by ClassLoader and URI.
	 */
	private ConcurrentHashMap<ClassLoader, HashMap<URI, CacheManager>> cacheManagersByClassLoader;

	/**
	 * Constructs an BoxHibernateCachingProvider.
	 */
	public BoxHibernateCachingProvider() {
		this.cacheManagersByClassLoader = new ConcurrentHashMap<ClassLoader, HashMap<URI, CacheManager>>();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized CacheManager getCacheManager( URI uri, ClassLoader classLoader, Properties properties ) {
		URI							managerURI			= uri == null ? getDefaultURI() : uri;
		ClassLoader					managerClassLoader	= classLoader == null ? getDefaultClassLoader() : classLoader;
		Properties					managerProperties	= properties == null ? new Properties() : properties;

		HashMap<URI, CacheManager>	cacheManagersByURI	= cacheManagersByClassLoader.get( managerClassLoader );

		if ( cacheManagersByURI == null ) {
			cacheManagersByURI = new HashMap<URI, CacheManager>();
		}

		CacheManager cacheManager = cacheManagersByURI.get( managerURI );

		if ( cacheManager == null ) {
			cacheManager = new BoxHibernateCacheManager( this, managerURI, managerClassLoader, managerProperties );

			cacheManagersByURI.put( managerURI, cacheManager );
		}

		if ( !cacheManagersByClassLoader.containsKey( managerClassLoader ) ) {
			cacheManagersByClassLoader.put( managerClassLoader, cacheManagersByURI );
		}

		return cacheManager;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public CacheManager getCacheManager( URI uri, ClassLoader classLoader ) {
		return getCacheManager( uri, classLoader, getDefaultProperties() );
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public CacheManager getCacheManager() {
		return getCacheManager( getDefaultURI(), getDefaultClassLoader(), null );
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ClassLoader getDefaultClassLoader() {
		return getClass().getClassLoader();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public URI getDefaultURI() {
		try {
			return new URI( this.getClass().getName() );
		} catch ( URISyntaxException e ) {
			throw new CacheException(
			    "Failed to create the default URI for the javax.cache Reference Implementation",
			    e );
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Properties getDefaultProperties() {
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void close() {
		ConcurrentHashMap<ClassLoader, HashMap<URI, CacheManager>> managersByClassLoader = this.cacheManagersByClassLoader;
		this.cacheManagersByClassLoader = new ConcurrentHashMap<ClassLoader, HashMap<URI, CacheManager>>();

		for ( ClassLoader classLoader : managersByClassLoader.keySet() ) {
			for ( CacheManager cacheManager : managersByClassLoader.get( classLoader ).values() ) {
				cacheManager.close();
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void close( ClassLoader classLoader ) {
		ClassLoader					managerClassLoader	= classLoader == null ? getDefaultClassLoader() : classLoader;

		HashMap<URI, CacheManager>	cacheManagersByURI	= cacheManagersByClassLoader.remove( managerClassLoader );

		if ( cacheManagersByURI != null ) {
			for ( CacheManager cacheManager : cacheManagersByURI.values() ) {
				cacheManager.close();
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void close( URI uri, ClassLoader classLoader ) {
		URI							managerURI			= uri == null ? getDefaultURI() : uri;
		ClassLoader					managerClassLoader	= classLoader == null ? getDefaultClassLoader() : classLoader;

		HashMap<URI, CacheManager>	cacheManagersByURI	= cacheManagersByClassLoader.get( managerClassLoader );
		if ( cacheManagersByURI != null ) {
			CacheManager cacheManager = cacheManagersByURI.remove( managerURI );

			if ( cacheManager != null ) {
				cacheManager.close();
			}

			if ( cacheManagersByURI.size() == 0 ) {
				cacheManagersByClassLoader.remove( managerClassLoader );
			}
		}
	}

	/**
	 * Releases the CacheManager with the specified URI and ClassLoader
	 * from this CachingProvider. This does not close the CacheManager. It
	 * simply releases it from being tracked by the CachingProvider.
	 * <p>
	 * This method does nothing if a CacheManager matching the specified
	 * parameters is not being tracked.
	 * </p>
	 *
	 * @param uri         the URI of the CacheManager
	 * @param classLoader the ClassLoader of the CacheManager
	 */
	public synchronized void releaseCacheManager( URI uri, ClassLoader classLoader ) {
		URI							managerURI			= uri == null ? getDefaultURI() : uri;
		ClassLoader					managerClassLoader	= classLoader == null ? getDefaultClassLoader() : classLoader;

		HashMap<URI, CacheManager>	cacheManagersByURI	= cacheManagersByClassLoader.get( managerClassLoader );
		if ( cacheManagersByURI != null ) {
			cacheManagersByURI.remove( managerURI );

			if ( cacheManagersByURI.size() == 0 ) {
				cacheManagersByClassLoader.remove( managerClassLoader );
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isSupported( OptionalFeature optionalFeature ) {
		switch ( optionalFeature ) {

			case STORE_BY_REFERENCE :
				return true;

			default :
				return false;
		}
	}

}
