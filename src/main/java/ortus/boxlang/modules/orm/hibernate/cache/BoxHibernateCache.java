/**
 * [BoxLang]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ortus.boxlang.modules.orm.hibernate.cache;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.CompleteConfiguration;
import javax.cache.configuration.Configuration;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.integration.CompletionListener;
import javax.cache.processor.EntryProcessor;
import javax.cache.processor.EntryProcessorException;
import javax.cache.processor.EntryProcessorResult;

import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.modules.orm.config.ORMConfig;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.cache.providers.ICacheProvider;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.dynamic.Attempt;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.services.CacheService;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * Box Hibernate cache which implements the JSR-107 Cache interface.
 */
public class BoxHibernateCache<K, V> implements Cache<K, V> {

	private BoxRuntime					runtime							= BoxRuntime.getInstance();
	private CacheService				cacheService					= runtime.getCacheService();
	private ORMService					ormService;
	private boolean						closed							= false;
	private BoxHibernateCacheManager	cacheManager;
	private MutableConfiguration<K, V>	configuration;
	private ICacheProvider				cacheProvider;
	private Key							cacheName;

	private static final String			LEGACY_CACHE_PROVIDER_MAP		= "ConcurrentHashMap";
	private static final String			LEGACY_CACHE_PROVIDER_TABLE		= "HashTable";
	private static final String			LEGACY_CACHE_PROVIDER_EHCACHE	= "ehcache";

	/**
	 * Constructs a cache.
	 *
	 * @param cacheManager  the CacheManager that's creating the RICache
	 * @param cacheName     the name of the Cache
	 * @param classLoader   the ClassLoader the RICache will use for loading classes
	 * @param configuration the Configuration of the Cache
	 */
	BoxHibernateCache( BoxHibernateCacheManager cacheManager,
	    String cacheName,
	    ClassLoader classLoader,
	    Configuration<K, V> configuration ) {

		this.ormService		= ( ORMService ) runtime.getGlobalService( ORMKeys.ORMService );
		this.cacheManager	= cacheManager;
		this.cacheName		= Key.of( cacheName );

		// we make a copy of the configuration here so that the provided one
		// may be changed and or used independently for other caches. we do this
		// as we don't know if the provided configuration is mutable
		if ( configuration instanceof CompleteConfiguration ) {
			// support use of CompleteConfiguration
			this.configuration = new MutableConfiguration<K, V>( ( MutableConfiguration ) configuration );
		} else {
			// support use of Basic Configuration
			MutableConfiguration<K, V> mutableConfiguration = new MutableConfiguration<K, V>();
			mutableConfiguration.setStoreByValue( configuration.isStoreByValue() );
			mutableConfiguration.setTypes( configuration.getKeyType(), configuration.getValueType() );
			this.configuration = new MutableConfiguration<K, V>( mutableConfiguration );
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public V get( K key ) {
		Attempt<Object> attempt = getCacheProvider().get( createCacheKey( key ) );
		return attempt.wasSuccessful()
		    ? vCast( attempt.get() )
		    : null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Map<K, V> getAll( Set<? extends K> keys ) {
		return getCacheProvider().getKeys().stream().parallel()
		    .map( key -> Map.entry( kCast( key ), get( kCast( key ) ) ) )
		    .collect( Collectors.toMap( Map.Entry::getKey, Map.Entry::getValue ) );
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean containsKey( K key ) {
		return getCacheProvider().lookup( createCacheKey( key ) );
	}

	@Override
	public void loadAll( Set<? extends K> keys, boolean replaceExistingValues, CompletionListener completionListener ) {

	}

	@Override
	public void put( K key, V value ) {
		getCacheProvider().set( createCacheKey( key ), value );
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public V getAndPut( K key, V value ) {
		V existing = get( key );
		put( key, value );
		return existing;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void putAll( Map<? extends K, ? extends V> map ) {
		map.forEach( this::put );
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean putIfAbsent( K key, V value ) {
		if ( !containsKey( key ) ) {
			put( key, value );
			return true;
		} else {
			return false;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean remove( K key ) {
		return getCacheProvider().clear( createCacheKey( key ) );
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean remove( K key, V oldValue ) {
		if ( !containsKey( key ) ) {
			return false;
		} else {
			remove( key );
			return true;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public V getAndRemove( K key ) {
		V existing = get( key );
		remove( key );
		return existing;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean replace( K key, V oldValue, V newValue ) {
		if ( containsKey( key ) && get( key ).equals( oldValue ) ) {
			put( key, newValue );
			return true;
		} else {
			return false;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean replace( K key, V value ) {
		if ( containsKey( key ) ) {
			put( key, value );
			return true;
		} else {
			return false;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public V getAndReplace( K key, V value ) {
		V existing = get( key );
		if ( existing != null ) {
			put( key, value );
		}
		return existing;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void removeAll( Set<? extends K> keys ) {
		getCacheProvider().clearAll( key -> keys.contains( kCast( key ) ) );
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void removeAll() {
		getCacheProvider().clearAll();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void clear() {
		getCacheProvider().clearAll();
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings( "unchecked" )
	@Override
	public <C extends Configuration<K, V>> C getConfiguration( Class<C> clazz ) {
		return ( C ) this.configuration;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public <T> T invoke( K key, EntryProcessor<K, V, T> entryProcessor, Object... arguments ) throws EntryProcessorException {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'invoke'" );
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public <T> Map<K, EntryProcessorResult<T>> invokeAll( Set<? extends K> keys, EntryProcessor<K, V, T> entryProcessor, Object... arguments ) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'invokeAll'" );
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getName() {
		return getClass().getSimpleName();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public CacheManager getCacheManager() {
		return this.cacheManager;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void close() {
		this.closed = true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isClosed() {
		return this.closed;
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings( "unchecked" )
	@Override
	public <T> T unwrap( Class<T> clazz ) {
		return ( T ) clazz;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void registerCacheEntryListener( CacheEntryListenerConfiguration<K, V> cacheEntryListenerConfiguration ) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'registerCacheEntryListener'" );
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void deregisterCacheEntryListener( CacheEntryListenerConfiguration<K, V> cacheEntryListenerConfiguration ) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException( "Unimplemented method 'deregisterCacheEntryListener'" );
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Iterator<Entry<K, V>> iterator() {
		throw new UnsupportedOperationException( "Unimplemented method 'deregisterCacheEntryListener'" );
		// return getCacheProvider().getKeys().stream().map( key -> new WrappedCacheEntry<K, V>( key, get( key ) ) ).iterator();
	}

	/**
	 * Returns the configured cache provider in a lazy fashion
	 *
	 * @return
	 */
	private ICacheProvider getCacheProvider() {
		if ( this.cacheProvider == null ) {
			RequestBoxContext context = RequestBoxContext.getCurrent();
			if ( context == null ) {
				throw new BoxRuntimeException( "A Hibernate Cache may not be created outside of a BoxLang application context" );
			}

			ORMApp		ormApp				= ormService.getORMAppByContext( context );
			ORMConfig	config				= ormApp.getConfig();
			Key			cacheProviderKey	= Key.of( config.cacheProvider );

			if ( cacheService.hasCache( this.cacheName ) ) {
				return cacheService.getCache( this.cacheName );
			} else if ( config.cacheProvider == null
			    || config.cacheProvider.equalsIgnoreCase( LEGACY_CACHE_PROVIDER_MAP )
			    || config.cacheProvider.equalsIgnoreCase( LEGACY_CACHE_PROVIDER_TABLE )
			    || cacheProviderKey.equals( Key.of( ORMConfig.DEFAULT_CACHEPROVIDER ) ) ) {
				return cacheService.createDefaultCache( this.cacheName );
			} else if ( cacheService.hasProvider( cacheProviderKey ) ) {
				return cacheService.createCache( this.cacheName, Key.of( config.cacheProvider ), config.cacheConfigProperties );
			} else {
				throw new BoxRuntimeException( "No BoxLang cache provider found with a name of [" + config.cacheProvider + "]." );
			}
		}

		return this.cacheProvider;

	}

	/**
	 * Creates a string cache key from the provided value
	 *
	 * @param key
	 *
	 * @return
	 */
	private String createCacheKey( K key ) {
		if ( key instanceof String stringKey ) {
			return stringKey;
		} else {
			return key.toString();
		}
	}

	/**
	 * Casts a value to the value type
	 *
	 * @param value
	 *
	 * @return
	 */
	@SuppressWarnings( "unchecked" )
	private V vCast( Object value ) {
		return ( V ) value;
	}

	/**
	 * Casts a key to the key type
	 *
	 * @param value
	 *
	 * @return
	 */
	@SuppressWarnings( "unchecked" )
	private K kCast( Object value ) {
		return ( K ) value;
	}

}
