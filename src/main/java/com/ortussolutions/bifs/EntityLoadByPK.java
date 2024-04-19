package com.ortussolutions.bifs;

import java.util.Set;

import org.hibernate.Session;

import com.ortussolutions.ORMEngine;
import com.ortussolutions.config.ORMKeys;

import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.dynamic.casters.IntegerCaster;
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
		Session session = ORMEngine.getInstance().getSessionForContext( context );

		session.beginTransaction();

		// TODO we should not use IntegerCaster
		// We need to cast the id argument to the correct java type based on the ormtype
		// of the field
		return session.get( arguments.getAsString( ORMKeys.entity ), IntegerCaster.cast( arguments.get( Key.id ) ) );
	}

}
