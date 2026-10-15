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
package ortus.boxlang.modules.orm.criteria;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Breaks generated SQL into readable lines for {@code getSQL( format = true )}: each clause ({@code from},
 * {@code join}, {@code where}, {@code group by}, {@code having}, {@code order by}, {@code limit}, {@code offset},
 * {@code fetch}) starts a new line and top-level {@code and}/{@code or} are indented. Text inside string literals is
 * never changed.
 */
final class SqlFormat {

	/** Clause keywords that start a new line. */
	private static final Pattern	CLAUSE	= Pattern.compile(
	    "\\s+(from|where|group by|having|order by|limit|offset|fetch first|fetch next|(?:left |right |full |inner |cross )?(?:outer )?join)\\s+",
	    Pattern.CASE_INSENSITIVE );

	/** Boolean operators that start an indented line. */
	private static final Pattern	BOOLEAN	= Pattern.compile( "\\s+(and|or)\\s+", Pattern.CASE_INSENSITIVE );

	/**
	 * Not instantiable.
	 */
	private SqlFormat() {
	}

	/**
	 * Format SQL.
	 *
	 * @param sql The SQL on one line.
	 *
	 * @return The SQL on several lines.
	 */
	static String format( String sql ) {
		StringBuilder	out		= new StringBuilder();
		StringBuilder	chunk	= new StringBuilder();
		boolean			quoted	= false;
		for ( int i = 0; i < sql.length(); i++ ) {
			char c = sql.charAt( i );
			if ( c == '\'' ) {
				if ( !quoted ) {
					out.append( formatChunk( chunk.toString() ) );
					chunk.setLength( 0 );
				} else {
					out.append( chunk ).append( c );
					chunk.setLength( 0 );
					quoted = false;
					continue;
				}
				quoted = true;
			}
			chunk.append( c );
		}
		out.append( quoted ? chunk.toString() : formatChunk( chunk.toString() ) );
		return out.toString().trim();
	}

	/**
	 * Format SQL text that contains no string literal.
	 *
	 * @param text The text.
	 *
	 * @return The formatted text.
	 */
	private static String formatChunk( String text ) {
		Matcher			clause	= CLAUSE.matcher( text );
		StringBuilder	out		= new StringBuilder();
		while ( clause.find() ) {
			clause.appendReplacement( out, Matcher.quoteReplacement( "\n" + clause.group( 1 ) + " " ) );
		}
		clause.appendTail( out );
		Matcher			bool	= BOOLEAN.matcher( out.toString() );
		StringBuilder	result	= new StringBuilder();
		while ( bool.find() ) {
			bool.appendReplacement( result, Matcher.quoteReplacement( "\n    " + bool.group( 1 ) + " " ) );
		}
		bool.appendTail( result );
		return result.toString();
	}
}
