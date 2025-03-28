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

import org.hibernate.SessionFactory;

import ortus.boxlang.modules.orm.ORMService;
import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;

@BoxBIF
public class ORMReload extends BaseORMBIF {

	/**
	 * Reload the ORM application.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 */
	public SessionFactory _invoke( IBoxContext context, ArgumentsScope arguments ) {
		ORMService ormService = ( ORMService ) runtime.getGlobalService( ORMKeys.ORMService );
		return ormService.reloadApp( context ).getDefaultSessionFactoryOrThrow();
	}

}
