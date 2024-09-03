package ortus.boxlang.modules.orm.bifs;

import java.util.Set;

import org.hibernate.Session;
import org.hibernate.metadata.ClassMetadata;

import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.dynamic.casters.GenericCaster;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.validation.Validator;

@BoxBIF
public class EntityLoadByPK extends BIF {

	/**
	 * Constructor
	 */
	public EntityLoadByPK() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, "String", ORMKeys.entity, Set.of( Validator.REQUIRED, Validator.NON_EMPTY ) ),
		    new Argument( true, "String", Key.id, Set.of( Validator.REQUIRED, Validator.NON_EMPTY ) )
		};
	}

	/**
	 * ExampleBIF
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 */
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		Session session = ORMService.getInstance().getSessionForContext( context );

		session.beginTransaction();

		String	entityName	= arguments.getAsString( ORMKeys.entity );
		Object	keyValue	= arguments.get( Key.id );
		// return session.get( entityName, ( java.io.Serializable ) keyValue );
		String	keyType		= getKeyJavaType( session, entityName ).getSimpleName();
		return session.get( entityName,
		    ( java.io.Serializable ) GenericCaster.cast( context, keyValue, keyType ) );
	}

	/**
	 * TODO: Remove once we figure out how to get the JPA metamodel working.
	 */
	private Class<?> getKeyJavaType( Session session, String entityName ) {
		ClassMetadata metadata = session.getSessionFactory().getClassMetadata( entityName );
		return metadata.getIdentifierType().getReturnedClass();
	}

	/**
	 * TODO: Get this JPA metamodel stuff working. We're currently unable to get the class name for dynamic boxlang classes; this may change in the
	 * future.
	 * private Class<?> getKeyJavaType( Session session, Class entityClassType ) {
	 * Metamodel metamodel = session
	 * .getEntityManagerFactory()
	 * .getMetamodel();
	 * 
	 * EntityType<?> entityType = metamodel.entity( entityClassType );
	 * SingularAttribute<?, ?> idAttribute = entityType.getId( entityType.getIdType().getJavaType() );
	 * return idAttribute.getJavaType();
	 * }
	 */
}
