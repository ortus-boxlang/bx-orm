package com.ortussolutions.bifs;

import org.apache.commons.lang3.NotImplementedException;

import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;

@BoxBIF
public class EntityLoad extends BIF {

	/**
	 * ExampleBIF
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 */
	public String _invoke( IBoxContext context, ArgumentsScope arguments ) {
		// TODO implement BIF
		throw new NotImplementedException();
	}

}
