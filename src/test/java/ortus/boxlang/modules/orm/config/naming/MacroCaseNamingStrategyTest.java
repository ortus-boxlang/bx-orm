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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.hibernate.boot.model.naming.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MacroCaseNamingStrategyTest {

	private MacroCaseNamingStrategy strategy;

	@BeforeEach
	public void setUp() {
		strategy = new MacroCaseNamingStrategy();
	}

	@Test
	public void testToPhysicalColumnNameWithCamelCase() {
		// Per ORM docs: OrderProduct should become ORDER_PRODUCT
		Identifier	input	= Identifier.toIdentifier( "OrderProduct" );
		Identifier	result	= strategy.toPhysicalColumnName( input, null );
		assertThat( result.getText() ).isEqualTo( "ORDER_PRODUCT" );
	}

	@Test
	public void testToPhysicalColumnNameWithLowerCamelCase() {
		// bookAuthors should become BOOK_AUTHORS
		Identifier	input	= Identifier.toIdentifier( "bookAuthors" );
		Identifier	result	= strategy.toPhysicalColumnName( input, null );
		assertThat( result.getText() ).isEqualTo( "BOOK_AUTHORS" );
	}

	@Test
	public void testToPhysicalColumnNameWithLowercase() {
		// authors should become AUTHORS
		Identifier	input	= Identifier.toIdentifier( "authors" );
		Identifier	result	= strategy.toPhysicalColumnName( input, null );
		assertThat( result.getText() ).isEqualTo( "AUTHORS" );
	}

	@Test
	public void testToPhysicalColumnNameWithCapitalized() {
		// Myclass should become MYCLASS (not M_Y_C_L_A_S_S)
		// Tests BLMODULES-136, See https://ortussolutions.atlassian.net/browse/BLMODULES-136
		Identifier	input	= Identifier.toIdentifier( "Myclass" );
		Identifier	result	= strategy.toPhysicalColumnName( input, null );
		assertThat( result.getText() ).isEqualTo( "MYCLASS" );
	}

	@Test
	public void testToPhysicalColumnNameWithMixedCase() {
		// authorContact should become AUTHOR_CONTACT
		Identifier	input	= Identifier.toIdentifier( "authorContact" );
		Identifier	result	= strategy.toPhysicalColumnName( input, null );
		assertThat( result.getText() ).isEqualTo( "AUTHOR_CONTACT" );
	}

	@Test
	public void testToPhysicalColumnNameWithNullIdentifier() {
		// Null input should return null
		Identifier result = strategy.toPhysicalColumnName( null, null );
		assertNull( result );
	}

	@Test
	public void testToPhysicalTableName() {
		// Table names should also be converted to macro case
		Identifier	input	= Identifier.toIdentifier( "OrderProduct" );
		Identifier	result	= strategy.toPhysicalTableName( input, null );
		assertThat( result.getText() ).isEqualTo( "ORDER_PRODUCT" );
	}

	@Test
	public void testToPhysicalCatalogName() {
		// Catalog names should also be converted to macro case
		Identifier	input	= Identifier.toIdentifier( "MyCatalog" );
		Identifier	result	= strategy.toPhysicalCatalogName( input, null );
		assertThat( result.getText() ).isEqualTo( "MY_CATALOG" );
	}

	@Test
	public void testToPhysicalSchemaName() {
		// Schema names should also be converted to macro case
		Identifier	input	= Identifier.toIdentifier( "MySchema" );
		Identifier	result	= strategy.toPhysicalSchemaName( input, null );
		assertThat( result.getText() ).isEqualTo( "MY_SCHEMA" );
	}

	@Test
	public void testToPhysicalSequenceName() {
		// Sequence names should also be converted to macro case
		Identifier	input	= Identifier.toIdentifier( "MySequence" );
		Identifier	result	= strategy.toPhysicalSequenceName( input, null );
		assertThat( result.getText() ).isEqualTo( "MY_SEQUENCE" );
	}

	@Test
	public void testQuotedIdentifierPreservation() {
		// Quoted identifiers should remain quoted after conversion
		Identifier	input	= Identifier.toIdentifier( "OrderProduct", true );
		Identifier	result	= strategy.toPhysicalColumnName( input, null );
		assertThat( result.getText() ).isEqualTo( "ORDER_PRODUCT" );
		assertThat( result.isQuoted() ).isTrue();
	}
}
