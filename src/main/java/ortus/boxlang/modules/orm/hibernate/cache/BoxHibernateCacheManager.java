package ortus.boxlang.modules.orm.hibernate.cache;

import java.lang.ref.WeakReference;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;

import javax.cache.Cache;
import javax.cache.CacheException;
import javax.cache.CacheManager;
import javax.cache.configuration.CompleteConfiguration;
import javax.cache.configuration.Configuration;
import javax.cache.spi.CachingProvider;

import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.logging.BoxLangLogger;

/**
 * BoxLang implementation of the JCache CacheManager {@link CacheManager}.
 * 
 * @since 1.0.0
 */
public class BoxHibernateCacheManager implements CacheManager {

	private static final BoxLangLogger						logger	= BoxRuntime.getInstance().getLoggingService()
	    .getLogger( BoxHibernateCacheManager.class.getSimpleName() );
	private final HashMap<String, BoxHibernateCache<?, ?>>	caches	= new HashMap<String, BoxHibernateCache<?, ?>>();

	private final BoxHibernateCachingProvider				cachingProvider;

	private final URI										uri;
	private final WeakReference<ClassLoader>				classLoaderReference;
	private final Properties								properties;

	private volatile boolean								isClosed;

	/**
	 * Constructs a new BoxHibernateCacheManager with the specified name.
	 *
	 * @param cachingProvider the CachingProvider that created the CacheManager
	 * @param uri             the name of this cache manager
	 * @param classLoader     the ClassLoader that should be used in converting values into Java Objects.
	 * @param properties      the vendor specific Properties for the CacheManager
	 *
	 * @throws NullPointerException if the URI and/or classLoader is null.
	 */
	public BoxHibernateCacheManager( BoxHibernateCachingProvider cachingProvider, URI uri, ClassLoader classLoader, Properties properties ) {
		this.cachingProvider = cachingProvider;

		if ( uri == null ) {
			throw new NullPointerException( "No CacheManager URI specified" );
		}
		this.uri = uri;

		if ( classLoader == null ) {
			throw new NullPointerException( "No ClassLoader specified" );
		}
		this.classLoaderReference	= new WeakReference<ClassLoader>( classLoader );

		//
		this.properties				= new Properties();
		if ( properties != null ) {
			for ( Object key : properties.keySet() ) {
				this.properties.put( key, properties.get( key ) );
			}
		}

		// this.properties = properties == null ? new Properties() : new Properties(properties);

		isClosed = false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public CachingProvider getCachingProvider() {
		return cachingProvider;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void close() {
		if ( !isClosed() ) {
			// first releaseCacheManager the CacheManager from the CacheProvider so that
			// future requests for this CacheManager won't return this one
			cachingProvider.releaseCacheManager( getURI(), getClassLoader() );

			isClosed = true;

			ArrayList<Cache<?, ?>> cacheList;
			synchronized ( caches ) {
				cacheList = new ArrayList<Cache<?, ?>>( caches.values() );
				caches.clear();
			}
			for ( Cache<?, ?> cache : cacheList ) {
				try {
					cache.close();
				} catch ( Exception e ) {
					logger.warn( "Error stopping cache: " + cache, e );
				}
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isClosed() {
		return isClosed;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public URI getURI() {
		return uri;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Properties getProperties() {
		return properties;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ClassLoader getClassLoader() {
		return classLoaderReference.get();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public <K, V, C extends Configuration<K, V>> Cache<K, V> createCache( String cacheName, C configuration ) {
		if ( isClosed() ) {
			throw new IllegalStateException();
		}

		if ( cacheName == null ) {
			throw new NullPointerException( "cacheName must not be null" );
		}

		if ( configuration == null ) {
			throw new NullPointerException( "configuration must not be null" );
		}

		synchronized ( caches ) {
			BoxHibernateCache<?, ?> cache = caches.get( cacheName );

			if ( cache == null ) {
				cache = new BoxHibernateCache( this, cacheName, getClassLoader(), configuration );
				caches.put( cache.getName(), cache );

				return ( Cache<K, V> ) cache;
			} else {
				throw new CacheException( "A cache named " + cacheName + " already exists." );
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public <K, V> Cache<K, V> getCache( String cacheName, Class<K> keyType, Class<V> valueType ) {
		if ( isClosed() ) {
			throw new IllegalStateException();
		}

		if ( keyType == null ) {
			throw new NullPointerException( "keyType can not be null" );
		}

		if ( valueType == null ) {
			throw new NullPointerException( "valueType can not be null" );
		}

		synchronized ( caches ) {
			BoxHibernateCache<?, ?> cache = caches.get( cacheName );

			if ( cache == null ) {
				return null;
			} else {
				Configuration<?, ?> configuration = cache.getConfiguration( CompleteConfiguration.class );

				if ( configuration.getKeyType() != null &&
				    configuration.getKeyType().equals( keyType ) ) {

					if ( configuration.getValueType() != null &&
					    configuration.getValueType().equals( valueType ) ) {

						return ( Cache<K, V> ) cache;
					} else {
						throw new ClassCastException( "Incompatible cache value types specified, expected " +
						    configuration.getValueType() + " but " + valueType + " was specified" );
					}
				} else {
					throw new ClassCastException( "Incompatible cache key types specified, expected " +
					    configuration.getKeyType() + " but " + keyType + " was specified" );
				}
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Cache getCache( String cacheName ) {
		if ( isClosed() ) {
			throw new IllegalStateException();
		}
		synchronized ( caches ) {
			return caches.get( cacheName );
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Iterable<String> getCacheNames() {
		if ( isClosed() ) {
			throw new IllegalStateException();
		}
		synchronized ( caches ) {
			HashSet<String> set = new HashSet<String>();
			for ( Cache<?, ?> cache : caches.values() ) {
				set.add( cache.getName() );
			}
			return Collections.unmodifiableSet( set );
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void destroyCache( String cacheName ) {
		if ( isClosed() ) {
			throw new IllegalStateException();
		}
		if ( cacheName == null ) {
			throw new NullPointerException();
		}

		Cache<?, ?> cache;
		synchronized ( caches ) {
			cache = caches.get( cacheName );
		}

		if ( cache != null ) {
			cache.close();
		}
	}

	/**
	 * Releases the Cache with the specified name from being managed by
	 * this CacheManager.
	 *
	 * @param cacheName the name of the Cache to releaseCacheManager
	 */
	void releaseCache( String cacheName ) {
		if ( cacheName == null ) {
			throw new NullPointerException();
		}
		synchronized ( caches ) {
			caches.remove( cacheName );
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void enableStatistics( String cacheName, boolean enabled ) {
		logger.warn( "A request to enable statistics for cache " + cacheName + " was ignored. Statistics are not supported by this implementation." );
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void enableManagement( String cacheName, boolean enabled ) {
		logger.warn( "A request to enable management for cache " + cacheName + " was ignored. JCache management are not supported by this implementation." );
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public <T> T unwrap( java.lang.Class<T> cls ) {
		if ( cls.isAssignableFrom( getClass() ) ) {
			return cls.cast( this );
		}

		throw new IllegalArgumentException( "Unwapping to " + cls + " is not a supported by this implementation" );
	}

}
