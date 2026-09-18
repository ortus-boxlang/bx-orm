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
package ortus.boxlang.modules.orm.hibernate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.hibernate.EntityNameResolver;
import org.hibernate.collection.spi.PersistentBag;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.metamodel.spi.EntityInstantiator;

import ortus.boxlang.modules.orm.ORMApp;
import ortus.boxlang.modules.orm.ORMContext;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.modules.orm.mapping.EntityRecord;
import ortus.boxlang.modules.orm.mapping.inspectors.ClassicPropertyMeta;
import ortus.boxlang.modules.orm.mapping.inspectors.IPropertyMeta;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.dynamic.casters.StringCaster;
import ortus.boxlang.runtime.dynamic.casters.StructCaster;
import ortus.boxlang.runtime.loader.ClassLocator;
import ortus.boxlang.runtime.logging.BoxLangLogger;
import ortus.boxlang.runtime.runnables.IClassRunnable;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.scopes.VariablesScope;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.modules.orm.hibernate.facade.FacadeCollectionView;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.DynamicFunction;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;
import ortus.boxlang.runtime.validation.Validator;

/**
 * This class is used to instantiate a BoxLang class for a Hibernate entity.
 * <p>
 * In other words, here is where the magic happens to tie a Hibernate entity to a BoxLang class when loading entities from the database via
 * `entityLoadByPK()`, creating new entities via `entityNew()`, etc, etc.
 *
 * @since 1.0.0
 */
public class BoxClassInstantiator implements EntityInstantiator {

	/**
	 * Runtime
	 */
	private static final BoxRuntime		runtime				= BoxRuntime.getInstance();

	/**
	 * The logger for the ORM application.
	 */
	protected BoxLangLogger				logger;

	private String						entityName;
	private EntityRecord				entityRecord;
	private List<Key>					identifierKeys		= new ArrayList<>();
	private List<String>				subclassClassNames	= new ArrayList<>();
	private EntityNameResolver			entityNameResolver	= new BoxEntityNameResolver();

	private static final ClassLocator	CLASS_LOCATOR		= BoxRuntime.getInstance().getClassLocator();

	/**
	 * --------------------------------------------------------------------------
	 * Constructor
	 * --------------------------------------------------------------------------
	 */

	/**
	 * Constructor
	 *
	 * @param mappingInfo  The Hibernate boot-time descriptor of the entity
	 * @param entityRecord The BoxLang entity record (class location and metadata) for the entity
	 */
	public BoxClassInstantiator( PersistentClass mappingInfo, EntityRecord entityRecord ) {
		this.logger			= runtime.getLoggingService().getLogger( "orm" );
		this.entityName		= mappingInfo.getEntityName();
		this.entityRecord	= entityRecord;

		if ( mappingInfo.hasIdentifierProperty() ) {
			this.identifierKeys.add( Key.of( mappingInfo.getIdentifierProperty().getName() ) );
		} else if ( mappingInfo.getIdentifier() instanceof Component compositeId ) {
			compositeId.getProperties().forEach( idProperty -> this.identifierKeys.add( Key.of( idProperty.getName() ) ) );
		}

		if ( mappingInfo.hasSubclasses() ) {
			for ( PersistentClass subclassInfo : mappingInfo.getSubclassClosure() ) {
				subclassClassNames.add( subclassInfo.getEntityName() );
			}
		}
	}

	/**
	 * --------------------------------------------------------------------------
	 * All Methods
	 * --------------------------------------------------------------------------
	 */

	public IClassRunnable instantiate( IBoxContext context, EntityRecord entityRecord, IStruct properties ) {
		IClassRunnable theEntity = ( IClassRunnable ) RequestBoxContext.runInContext( context, ( ctx ) -> loadBoxClass( ctx, entityRecord.getClassFQN() ) );

		entityRecord.getEntityMeta().getAssociations().stream()
		    .forEach( prop -> {
			    IStruct		association		= prop.getAssociation();
			    String		collectionType	= association.getAsString( ORMKeys.collectionType );
			    // hasX(), used on all associations
			    DynamicFunction hasUDF		= null;
			    String		associationType	= association.getAsString( Key.type );
			    if ( associationType.endsWith( "to-many" ) ) {
				    hasUDF = getToManyHasMethod( collectionType, association );
			    } else {
				    hasUDF = getSimpleHasMethod( collectionType, association );
			    }
			    if ( !theEntity.getThisScope().containsKey( hasUDF.getName() ) ) {
				    logger.trace( "Adding '{}' method for property '{}' on entity '{}", hasUDF.getName().getName(),
				        prop.getName(), entityRecord.getEntityName() );
				    theEntity.getThisScope().put( hasUDF.getName(), hasUDF );
				    theEntity.getVariablesScope().put( hasUDF.getName(), hasUDF );
			    }

			    // @TODO: I'm not sure this conditional is correct at all... but without more & better testing, I don't want to change it.
			    boolean isCollectionType = association.containsKey( ORMKeys.collectionType );
			    if ( isCollectionType ) {
				    // addX(), used on to-many associations
				    DynamicFunction addUDF = getAddMethod( collectionType, association );
				    if ( !theEntity.getThisScope().containsKey( addUDF.getName() ) ) {
					    logger.trace( "Adding '{}' method for property '{}' on entity '{}", addUDF.getName().getName(), prop.getName(),
					        entityRecord.getEntityName() );
					    theEntity.getThisScope().put( addUDF.getName(), addUDF );
					    theEntity.getVariablesScope().put( addUDF.getName(), addUDF );
				    }

				    // removeX(), used on to-many associations
				    DynamicFunction removeUDF = getRemoveMethod( collectionType, association );
				    if ( !theEntity.getThisScope().containsKey( removeUDF.getName() ) ) {
					    logger.trace( "Adding '{}' method for property '{}' on entity '{}", removeUDF.getName().getName(),
					        prop.getName(), entityRecord.getEntityName() );
					    theEntity.getThisScope().put( removeUDF.getName(), removeUDF );
					    theEntity.getVariablesScope().put( removeUDF.getName(), removeUDF );
				    }

				    // getX() override: return a stable snapshot in facade mode so structural modification during iteration
				    // (getX().each( e => removeX(e) )) is safe; a no-op passthrough in MAP mode. Force-installed over the
				    // generated accessor.
				    if ( association.getAsString( Key.type ).endsWith( "to-many" ) ) {
					    DynamicFunction getUDF = getToManyGetMethod( association );
					    theEntity.getThisScope().put( getUDF.getName(), getUDF );
					    theEntity.getVariablesScope().put( getUDF.getName(), getUDF );
				    }
			    }
		    } );

		if ( entityRecord.getEntityMeta().isSubclass() ) {
			entityRecord.getEntityMeta().getParentMeta().getAsArray( Key.properties )
			    .stream()
			    .map( StructCaster::cast )
			    .filter( prop -> prop.getAsStruct( Key.annotations ).get( ORMKeys.fkcolumn ) != null )
			    .forEach( prop -> {
				    DynamicFunction hasUDF		= null;
				    IStruct		annotations		= prop.getAsStruct( Key.annotations );
				    String		associationType	= annotations.getAsString( ORMKeys.fieldtype );
				    IPropertyMeta tempMeta		= new ClassicPropertyMeta( entityRecord.getEntityName(), prop, entityRecord.getEntityMeta() );
				    IStruct		association		= tempMeta.getAssociation();
				    String		collectionType	= association.getAsString( ORMKeys.collectionType );
				    if ( associationType.endsWith( "to-many" ) ) {
					    hasUDF = getToManyHasMethod( collectionType, association );
				    } else {
					    hasUDF = getSimpleHasMethod( collectionType, association );
				    }
				    if ( !theEntity.getThisScope().containsKey( hasUDF.getName() ) ) {
					    logger.trace( "Adding '{}' method for property '{}' on entity '{}", hasUDF.getName().getName(),
					        prop.getAsString( Key._name ), entityRecord.getEntityName() );
					    theEntity.getThisScope().put( hasUDF.getName(), hasUDF );
					    theEntity.getVariablesScope().put( hasUDF.getName(), hasUDF );
				    }

				    // @TODO: I'm not sure this conditional is correct at all... but without more & better testing, I don't want to change it.
				    boolean isCollectionType = association.containsKey( ORMKeys.collectionType );
				    if ( isCollectionType ) {
					    // addX(), used on to-many associations
					    DynamicFunction addUDF = getAddMethod( collectionType, association );
					    if ( !theEntity.getThisScope().containsKey( addUDF.getName() ) ) {
						    logger.trace( "Adding '{}' method for property '{}' on entity '{}", addUDF.getName().getName(), prop.getAsString( Key._name ),
						        entityRecord.getEntityName() );
						    theEntity.getThisScope().put( addUDF.getName(), addUDF );
						    theEntity.getVariablesScope().put( addUDF.getName(), addUDF );
					    }

					    // removeX(), used on to-many associations
					    DynamicFunction removeUDF = getRemoveMethod( collectionType, association );
					    if ( !theEntity.getThisScope().containsKey( removeUDF.getName() ) ) {
						    logger.trace( "Adding '{}' method for property '{}' on entity '{}", removeUDF.getName().getName(),
						        prop.getAsString( Key._name ), entityRecord.getEntityName() );
						    theEntity.getThisScope().put( removeUDF.getName(), removeUDF );
						    theEntity.getVariablesScope().put( removeUDF.getName(), removeUDF );
					    }
				    }
			    } );
		}

		if ( properties != null && !properties.isEmpty() ) {
			theEntity.getVariablesScope().putAll( properties );
		}

		return theEntity;
	}

	/**
	 * Assemble a method name for the association, like `addManufacturer` for the association `manufacturer`.
	 *
	 * Will use the singular name, if it exists, otherwise it will use the property name.
	 *
	 * @param operationPrefix Operation type as a string, like "has", "add", or "remove"
	 * @param associationMeta The metadata for the association.
	 */
	private Key getMethodName( String operationPrefix, IStruct associationMeta ) {
		String methodName = associationMeta.getAsString( Key._NAME );
		if ( associationMeta.containsKey( ORMKeys.singularName ) ) {
			methodName = associationMeta.getAsString( ORMKeys.singularName );
		}
		return Key.of( operationPrefix + methodName.substring( 0, 1 ).toUpperCase() + methodName.substring( 1 ) );
	}

	@Override
	public Object instantiate() {
		return instantiate( RequestBoxContext.getCurrent(), this.entityRecord, null );
	}

	/**
	 * Strict class check used by Hibernate to pick the concrete persister in an inheritance hierarchy: true only when
	 * the object's entity name is exactly this instantiator's entity (subclasses do not match).
	 */
	@Override
	public boolean isSameClass( Object object ) {
		if ( object instanceof IClassRunnable theClass ) {
			return this.entityName.equals( entityNameResolver.resolveEntityName( theClass ) );
		}
		return false;
	}

	@Override
	public boolean isInstance( Object object ) {
		if ( object instanceof IClassRunnable theClass ) {
			logger.trace( "Checking to see if {} is an instance of {}", theClass.getClass().getName(), this.entityName );
			String objectEntityName = entityNameResolver.resolveEntityName( theClass );
			logger.trace( "Looking at annotations, found entity name {}", objectEntityName );
			return this.entityName.equals( objectEntityName )
			    || subclassClassNames.contains( objectEntityName );
		}
		return false;

	}

	/**
	 * Create a `has*` method for the entity association, like `hasManufacturer()`, which returns a boolean indicating whether the entity has a value (one
	 * or more ) for the given association.
	 *
	 * @param collectionType  The type of collection, like 'bag' or 'map'.
	 * @param associationMeta The metadata for the association.
	 *
	 * @return A DynamicFunction that can be injected into the entity class.
	 */
	public DynamicFunction getSimpleHasMethod( String collectionType, IStruct associationMeta ) {
		// uses the singular name, if it exists
		Key	methodName		= getMethodName( "has", associationMeta );
		// uses the property name
		Key	collectionKey	= Key.of( associationMeta.getAsString( Key._NAME ) );
		return new DynamicFunction(
		    methodName,
		    ( context, function ) -> {
			    VariablesScope scope	= context.getThisClass().getVariablesScope();
			    Object		collection	= scope.get( collectionKey );
			    return collection != null;
		    },
		    new Argument[] {},
		    "boolean",
		    "Returns true if the entity has a value for the association [" + collectionKey.getName() + "]",
		    Struct.EMPTY
		);
	}

	/**
	 * Create a `has*` method for the entity association, like `hasManufacturer( object relation )`, which returns a boolean indicating whether the given
	 * object is present in the association collection.
	 *
	 * @param collectionType  The type of collection, like 'bag' or 'map'.
	 * @param associationMeta The metadata for the association.
	 *
	 * @return A DynamicFunction that can be injected into the entity class.
	 */
	@SuppressWarnings( "unchecked" )
	public DynamicFunction getToManyHasMethod( String collectionType, IStruct associationMeta ) {
		// uses the singular name, if it exists
		Key	methodName		= getMethodName( "has", associationMeta );
		// uses the property name
		Key	collectionKey	= Key.of( associationMeta.getAsString( Key._NAME ) );
		return new DynamicFunction(
		    methodName,
		    ( context, function ) -> {
			    Object		itemToCheck	= context.getArgumentsScope().get( collectionKey );
			    VariablesScope scope	= context.getThisClass().getVariablesScope();
			    Object		collection	= scope.get( collectionKey );

			    if ( collection == null ) {
				    return false;
			    }

			    if ( collectionType == "bag" ) {
				    if ( collection instanceof PersistentBag bagCollection ) {
					    if ( itemToCheck != null ) {
						    return bagCollection.stream().filter( item -> item.equals( itemToCheck ) ).findFirst().isPresent();
					    } else {
						    return bagCollection.size() > 0;
					    }
				    }
				    // The scope collection is an Array (MAP mode) or a FacadeCollectionView (facade mode); both are List<Object>.
				    if ( itemToCheck != null ) {
					    return ( ( List<Object> ) collection ).stream().filter( item -> item.equals( itemToCheck ) ).findFirst().isPresent();
				    } else {
					    return ( ( List<Object> ) collection ).size() > 0;
				    }
			    } else {
				    if ( itemToCheck != null ) {
					    return scope.getAsStruct( collectionKey ).containsKey( itemToCheck );
				    } else {
					    return scope.getAsStruct( collectionKey ).size() > 0;
				    }
			    }
		    },
		    new Argument[] {
		        new Argument( false, "any", collectionKey, Set.of() )
		    },
		    "boolean",
		    "Returns false if the the entity association [" + collectionKey.getName()
		        + "] is empty OR the given object is not present in the collection; else returns true.",
		    Struct.EMPTY
		);
		// new Argument[] {
		// new Argument( true, "any", collectionKey, Set.of( Validator.REQUIRED ) ),
		// },
		// "class",
		// String.format( "Append the provided entity to the {} collection, creating it if it does not exist.", collectionKey.getName() ),
		// Struct.EMPTY
	}

	/**
	 * Create a `get*` accessor for a to-many association (e.g. {@code getVehicles()}) that, in facade (POJO) mode, returns
	 * a stable snapshot of the collection rather than the live {@link FacadeCollectionView}.
	 * <p>
	 * The facade view is a live window over Hibernate's managed collection, so structurally modifying the association
	 * while iterating what {@code getX()} returned (the common {@code getChildren().each( c => parent.removeChild( c ) )}
	 * pattern) would shift indices under BoxLang's index-based iteration and drop or null elements. Returning a snapshot
	 * (a copy of the current {@link IClassRunnable} elements) makes iteration stable while {@code addX}/{@code removeX}
	 * keep mutating the live view underneath - matching MAP-mode BoxLang {@code Array} semantics. In MAP mode the scope
	 * already holds a plain {@code Array}, which is returned as-is (live), preserving existing behavior.
	 *
	 * @param associationMeta The metadata for the association.
	 *
	 * @return A DynamicFunction that overrides the generated accessor for this association.
	 */
	public DynamicFunction getToManyGetMethod( IStruct associationMeta ) {
		String	propertyName	= associationMeta.getAsString( Key._NAME );
		Key		collectionKey	= Key.of( propertyName );
		Key		methodName		= Key.of( "get" + Character.toUpperCase( propertyName.charAt( 0 ) ) + propertyName.substring( 1 ) );
		return new DynamicFunction(
		    methodName,
		    ( context, function ) -> {
			    Object collection = context.getThisClass().getVariablesScope().get( collectionKey );
			    if ( collection instanceof FacadeCollectionView view ) {
				    return Array.copyFromList( new ArrayList<>( view ) );
			    }
			    return collection;
		    },
		    new Argument[] {},
		    "any",
		    "Returns the [" + collectionKey.getName() + "] association collection.",
		    Struct.EMPTY
		);
	}

	/**
	 * Create an `add*` method for the entity association, like `addManufacturer()`, which appends the provided entity to the association.
	 * <p>
	 * Supports both array and struct associations, or, in the Hibernate vernacular, "bag" and "map" collections.
	 *
	 * @param collectionType  The type of collection, like 'bag' or 'map'.
	 * @param associationMeta The metadata for the association.
	 *
	 * @return A DynamicFunction that can be injected into the entity class.
	 */
	@SuppressWarnings( "unchecked" )
	public DynamicFunction getAddMethod( String collectionType, IStruct associationMeta ) {
		// uses the singular name, if it exists
		Key	methodName		= getMethodName( "add", associationMeta );
		// uses the property name
		Key	collectionKey	= Key.of( associationMeta.getAsString( Key._NAME ) );
		return new DynamicFunction(
		    methodName,
		    ( context, function ) -> {
			    boolean		isArrayCollection	= collectionType == "bag";

			    Object		itemToAdd			= context.getArgumentsScope().get( collectionKey );
			    VariablesScope variablesScope	= context.getThisClass().getVariablesScope();

			    if ( itemToAdd == null ) {
				    throw new BoxRuntimeException( "Cannot add a null entity to the collection." );
			    }

			    // create collection if it doesn't exist
			    if ( !variablesScope.containsKey( collectionKey ) || variablesScope.get( collectionKey ) == null ) {
				    variablesScope.put( collectionKey, isArrayCollection ? new Array() : new Struct() );
			    }

			    if ( isArrayCollection ) {
				    Object bag = variablesScope.get( collectionKey );
				    if ( bag instanceof PersistentBag bagCollection ) {
					    bagCollection.add( itemToAdd );
				    } else {
					    // Array (MAP mode) or FacadeCollectionView (facade mode); both are List<Object>. The view wraps the
					    // added IClassRunnable into a facade and pushes it onto the managed Hibernate collection.
					    ( ( List<Object> ) bag ).add( itemToAdd );
				    }
			    } else {
				    // @TODO: implement/test this
				    String structKey = StringCaster.cast( context.getArgumentsScope().get( Key.key ) );
				    variablesScope.getAsStruct( collectionKey ).put( structKey, itemToAdd );
			    }

			    // Return this for chainability.
			    return context.getThisClass();
		    },
		    new Argument[] {
		        new Argument( true, "any", collectionKey, Set.of( Validator.REQUIRED ) ),
		    },
		    "class",
		    String.format( "Append the provided entity to the {} collection, creating it if it does not exist.", collectionKey.getName() ),
		    Struct.EMPTY
		);
	}

	/**
	 * Create an `remove*` method for the entity association, like `removeManufacturer()`, which removes the provided entity from the association, if
	 * found.
	 * <p>
	 * Supports both array and struct associations, or, in the Hibernate vernacular, "bag" and "map" collections.
	 *
	 * @param collectionType  The type of collection, like 'bag' or 'map'.
	 * @param associationMeta The metadata for the association.
	 *
	 * @return A DynamicFunction that can be injected into the entity class.
	 */
	@SuppressWarnings( "unchecked" )
	public DynamicFunction getRemoveMethod( String collectionType, IStruct associationMeta ) {
		// uses the singular name, if it exists
		Key	methodName		= getMethodName( "remove", associationMeta );
		// uses the property name
		Key	collectionKey	= Key.of( associationMeta.getAsString( Key._NAME ) );
		return new DynamicFunction(
		    methodName,
		    ( context, function ) -> {
			    boolean		isArrayCollection	= collectionType == "bag";
			    IClassRunnable itemToRemove		= ( IClassRunnable ) context.getArgumentsScope().get( collectionKey );
			    VariablesScope variablesScope	= context.getThisClass().getVariablesScope();

			    // return early if the collection doesn't exist - possibly throw an error for compat?
			    if ( !variablesScope.containsKey( collectionKey ) ) {
				    return context.getThisClass();
			    }
			    if ( itemToRemove == null ) {
				    throw new BoxRuntimeException( "Cannot remove a null entity from the collection." );
			    }

			    if ( isArrayCollection ) {
				    Object collection = variablesScope.get( collectionKey );
				    if ( collection instanceof PersistentBag bagCollection ) {
					    bagCollection.remove( itemToRemove );
				    } else {
					    // Array (MAP mode) or FacadeCollectionView (facade mode); both are List<Object>. Remove the element the
					    // developer passed by identity/equality. It comes from iterating this same association, so this matches it
					    // regardless of the element's id property name (which is not necessarily the owning entity's - e.g. owner
					    // id "id", element id "vin"). The FacadeCollectionView unwraps facades on compare, so an IClassRunnable
					    // argument still matches its managed facade in the backing collection.
					    List<Object> arrayCollection = ( List<Object> ) collection;
					    if ( !arrayCollection.remove( itemToRemove ) ) {
						    arrayCollection.stream()
						        .filter( item -> item == itemToRemove || java.util.Objects.equals( item, itemToRemove ) )
						        .findFirst()
						        .ifPresent( arrayCollection::remove );
					    }
				    }
			    } else {
				    // @TODO: test this!
				    String structKey = StringCaster.cast( context.getArgumentsScope().get( Key.key ) );
				    variablesScope.getAsStruct( collectionKey ).remove( structKey );
			    }

			    // Return this for chainability.
			    return context.getThisClass();
		    },
		    new Argument[] {
		        new Argument( true, "any", collectionKey, Set.of( Validator.REQUIRED ) ),
		    },
		    "class",
		    String.format( "Remove the provided entity from the {} collection.", collectionKey.getName() ),
		    Struct.EMPTY
		);
	}

	/**
	 * Load a BoxLang class from the class locator.
	 *
	 * @param context The current BoxLang context.
	 * @param fqn     The fully qualified name of the class to load, like "models.orm.Manufacturer".
	 */
	private IClassRunnable loadBoxClass( IBoxContext context, String fqn ) {
		return ( IClassRunnable ) CLASS_LOCATOR.load(
		    context,
		    fqn,
		    ClassLocator.BX_PREFIX,
		    true,
		    context.getCurrentImports()
		)
		    .invokeConstructor( context )
		    .unWrapBoxLangClass();
	}
}
