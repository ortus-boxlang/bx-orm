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
package ortus.boxlang.modules.orm.bifs;

import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.runnables.IClassRunnable;

/**
 * Abstract, parent BIF utility class which all ORM bifs should extend for reuse.
 */
public abstract class BaseORMBIF extends BIF {

	/**
	 * ORM service
	 */
	protected ORMService ormService = ( ORMService ) runtime.getGlobalService( ORMKeys.ORMService );

	/**
	 * Constructor
	 */
	protected BaseORMBIF() {
		super();
		// Do we need a logger in the BIFs?
		// this.logger = runtime.getLoggingService().getLogger( "orm" );
	}

	/**
	 * Pull the entity name from the provided boxlang class
	 *
	 * @param entity Instance of IClassRunnable, aka the compiled/parsed entity.
	 */
	protected String getEntityName( IClassRunnable entity ) {
		return ORMService.getEntityName( entity );
	}

	/**
	 * Retrieve the last portion of the FQN as the class name.
	 *
	 * @param fqn Boxlang class FQN, like models.orm.foo
	 */
	protected String getClassNameFromFQN( String fqn ) {
		return ORMService.getClassNameFromFQN( fqn );
	}
}
