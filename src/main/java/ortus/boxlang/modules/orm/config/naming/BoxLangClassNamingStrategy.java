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
package ortus.boxlang.modules.orm.config.naming;

import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;

import ortus.boxlang.modules.orm.config.ORMKeys;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.context.RequestBoxContext;
import ortus.boxlang.runtime.context.ScriptingRequestBoxContext;
import ortus.boxlang.runtime.interop.DynamicObject;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Struct;

/**
 * A physical naming strategy that wraps a custom naming strategy defined in a BoxLang class.
 * <p>
 * Historically the class would implement these methods:
 * <ul>
 * <li><code>getTableName()</code></li>
 * <li><code>getColumnName()</code></li>
 * </ul>
 *
 * For BoxLang, we also support the following methods:
 * <ul>
 * <li><code>getCatalogName()</code></li>
 * <li><code>getSchemaName()</code></li>
 * <li><code>getSequenceName()</code></li>
 * </ul>
 */
public class BoxLangClassNamingStrategy implements PhysicalNamingStrategy {

	private final DynamicObject wrappedBLClass;

	public BoxLangClassNamingStrategy( DynamicObject wrappedBLClass ) {
		this.wrappedBLClass = wrappedBLClass;
	}

	@Override
	public Identifier toPhysicalCatalogName( Identifier logicalName, JdbcEnvironment jdbcEnvironment ) {
		return fireImplementation( ORMKeys.getCatalogName, ORMKeys.catalogName, logicalName );
	}

	@Override
	public Identifier toPhysicalSchemaName( Identifier logicalName, JdbcEnvironment jdbcEnvironment ) {
		return fireImplementation( ORMKeys.getSchemaName, ORMKeys.schemaName, logicalName );
	}

	@Override
	public Identifier toPhysicalSequenceName( Identifier logicalName, JdbcEnvironment jdbcEnvironment ) {
		return fireImplementation( ORMKeys.getSequenceName, ORMKeys.sequenceName, logicalName );
	}

	@Override
	public Identifier toPhysicalTableName( Identifier logicalName, JdbcEnvironment jdbcEnvironment ) {
		return fireImplementation( ORMKeys.getTableName, ORMKeys.tableName, logicalName );
	}

	@Override
	public Identifier toPhysicalColumnName( Identifier logicalName, JdbcEnvironment jdbcEnvironment ) {
		return fireImplementation( ORMKeys.getColumnName, ORMKeys.columnName, logicalName );
	}

	/**
	 * Fire the naming strategy method in the wrapped BoxLang class IF implemented, else return the original identifier unmodified.
	 *
	 * @param methodName    Method name to invoke, like <code>getTableName()</code>
	 * @param argumentName  Argument name to pass to the method, like <code>tableName</code>
	 * @param theIdentifier The original identifier (like "autos") which the method should transform.
	 */
	private Identifier fireImplementation( Key methodName, Key argumentName, Identifier theIdentifier ) {
		if ( this.wrappedBLClass.hasMethodNoCase( methodName.getNameNoCase() ) ) {
			return Identifier.toIdentifier(
			    ( String ) this.wrappedBLClass.dereferenceAndInvoke(
			        getContextForInvocation(),
			        methodName,
			        Struct.of( argumentName, theIdentifier.getText() ),
			        true // throw on not found
			    )
			);
		}
		return theIdentifier;
	}

	private IBoxContext getContextForInvocation() {
		IBoxContext context = RequestBoxContext.getCurrent();
		if ( context == null ) {
			context = new ScriptingRequestBoxContext( BoxRuntime.getInstance().getRuntimeContext() );
		}
		return context;
	}
}
