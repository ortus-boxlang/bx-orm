package com.ortussolutions.config.naming;

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

		for ( char c : text.toCharArray() ) {
			if ( Character.isUpperCase( c ) ) {
				result.append( "_" );
			}
			result.append( c );
		}

		return new Identifier( result.toString().toUpperCase(), identifier.isQuoted() );
	}

}
