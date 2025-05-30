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

/**
 * A physical naming strategy that converts all identifiers to
 * <code>MACRO_CASE</code>.
 * <p>
 * Macro case is a naming convention where all characters are uppercase and
 * words are separated by underscores.
 * <p>
 * This naming strategy detects word separation by looking for uppercase
 * characters. Each word is then converted to uppercase and separated by
 * underscores:
 * <ul>
 * <li><code>authors</code> becomes <code>AUTHORS</code></li>
 * <li><code>bookAuthors</code> becomes <code>BOOK_AUTHOR</code></li>
 * <li><code>authorContact</code> becomes <code>AUTHOR_CONTACT</code></li>
 * </ul>
 * 
 * @since 1.0.0
 */
public class MacroCaseNamingStrategy implements PhysicalNamingStrategy {

	@Override
	public Identifier toPhysicalCatalogName( Identifier logicalName, JdbcEnvironment jdbcEnvironment ) {
		return toMacroCase( logicalName );
	}

	@Override
	public Identifier toPhysicalSchemaName( Identifier logicalName, JdbcEnvironment jdbcEnvironment ) {
		return toMacroCase( logicalName );
	}

	@Override
	public Identifier toPhysicalTableName( Identifier logicalName, JdbcEnvironment jdbcEnvironment ) {
		return toMacroCase( logicalName );
	}

	@Override
	public Identifier toPhysicalSequenceName( Identifier logicalName, JdbcEnvironment jdbcEnvironment ) {
		return toMacroCase( logicalName );
	}

	@Override
	public Identifier toPhysicalColumnName( Identifier logicalName, JdbcEnvironment jdbcEnvironment ) {
		return toMacroCase( logicalName );
	}

	private Identifier toMacroCase( Identifier identifier ) {
		if ( identifier == null ) {
			return null;
		}
		String			text	= identifier.getText();
		StringBuilder	result	= new StringBuilder();

		int				i		= 0;
		for ( char c : text.toCharArray() ) {
			if ( Character.isUpperCase( c ) && i > 0 ) {
				result.append( "_" );
			}
			result.append( c );
			i++;
		}

		return new Identifier( result.toString().toUpperCase(), identifier.isQuoted() );
	}

}
