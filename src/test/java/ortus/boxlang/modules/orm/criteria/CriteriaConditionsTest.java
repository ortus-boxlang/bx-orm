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

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.types.IStruct;

/**
 * Live tests for {@code entityCriteria()} conditions: comparisons, patterns, ranges, lists, nulls, booleans,
 * collections, property comparisons, ids, native SQL, {@code where()}, groups and negation.
 */
public class CriteriaConditionsTest extends CriteriaTestSupport {

	/**
	 * Test: isEq and its eq alias.
	 */
	@DisplayName( "isEq / eq match a value" )
	@Test
	public void testIsEq() {
		assertThat( number( "result = entityCriteria( 'Vehicle' ).isEq( 'make', 'Honda' ).count();" ) ).isEqualTo( 3 );
		assertThat( number( "result = entityCriteria( 'Vehicle' ).eq( 'make', 'Ford' ).count();" ) ).isEqualTo( 1 );
	}

	/**
	 * Test: method names are case-insensitive.
	 */
	@DisplayName( "Method names are case-insensitive" )
	@Test
	public void testCaseInsensitiveMethods() {
		assertThat( number( "result = entityCriteria( 'Vehicle' ).ISEQ( 'make', 'Honda' ).Count();" ) ).isEqualTo( 3 );
	}

	/**
	 * Test: property names are case-insensitive and normalized to the declared casing.
	 */
	@DisplayName( "Property names are case-insensitive" )
	@Test
	public void testCaseInsensitiveProperties() {
		assertThat( number( "result = entityCriteria( 'Vehicle' ).isEq( 'MAKE', 'Honda' ).count();" ) ).isEqualTo( 3 );
	}

	/**
	 * Test: named arguments.
	 */
	@DisplayName( "Conditions accept named arguments" )
	@Test
	public void testNamedArguments() {
		assertThat( number( "result = entityCriteria( 'Vehicle' ).isEq( property = 'make', value = 'Honda' ).count();" ) ).isEqualTo( 3 );
		assertThat( number( "result = entityCriteria( 'Vehicle' ).isEq( propertyName = 'make', propertyValue = 'Ford' ).count();" ) ).isEqualTo( 1 );
	}

	/**
	 * Test: ne excludes a value.
	 */
	@DisplayName( "ne excludes a value" )
	@Test
	public void testNe() {
		assertThat( number( "result = entityCriteria( 'Vehicle' ).ne( 'make', 'Honda' ).count();" ) ).isEqualTo( 2 );
	}

	/**
	 * Test: numeric comparisons and their aliases.
	 */
	@DisplayName( "isGt / isGe / isLt / isLe and their aliases compare values" )
	@Test
	public void testComparisons() {
		assertThat( number( "result = entityCriteria( 'Manufacturer' ).isGt( 'id', 1 ).count();" ) ).isEqualTo( 2 );
		assertThat( number( "result = entityCriteria( 'Manufacturer' ).gt( 'id', 42 ).count();" ) ).isEqualTo( 1 );
		assertThat( number( "result = entityCriteria( 'Manufacturer' ).isGe( 'id', 42 ).count();" ) ).isEqualTo( 2 );
		assertThat( number( "result = entityCriteria( 'Manufacturer' ).gte( 'id', 42 ).count();" ) ).isEqualTo( 2 );
		assertThat( number( "result = entityCriteria( 'Manufacturer' ).isLt( 'id', 42 ).count();" ) ).isEqualTo( 1 );
		assertThat( number( "result = entityCriteria( 'Manufacturer' ).lt( 'id', 77 ).count();" ) ).isEqualTo( 2 );
		assertThat( number( "result = entityCriteria( 'Manufacturer' ).isLe( 'id', 42 ).count();" ) ).isEqualTo( 2 );
		assertThat( number( "result = entityCriteria( 'Manufacturer' ).lte( 'id', 1 ).count();" ) ).isEqualTo( 1 );
	}

	/**
	 * Test: a string value is converted to the property's type (Hibernate coerces bound values).
	 */
	@DisplayName( "A string value is converted to a numeric property's type" )
	@Test
	public void testValueCoercion() {
		assertThat( run( "result = entityCriteria( 'Manufacturer' ).isEq( 'id', '42' ).get().getName();" ) ).isEqualTo( "Honda Motor Co." );
	}

	/**
	 * Test: like with wildcards.
	 */
	@DisplayName( "like matches a pattern" )
	@Test
	public void testLike() {
		assertThat( list( "result = entityCriteria( 'Vehicle' ).like( 'model', 'C%' ).list().map( ( v ) => v.getModel() );" ) )
		    .containsExactly( "Civic" );
	}

	/**
	 * Test: ilike ignores case.
	 */
	@DisplayName( "ilike matches a pattern ignoring case" )
	@Test
	public void testIlike() {
		assertThat( number( "result = entityCriteria( 'Vehicle' ).ilike( 'make', 'HON%' ).count();" ) ).isEqualTo( 3 );
	}

	/**
	 * Test: between is inclusive.
	 */
	@DisplayName( "between includes both bounds" )
	@Test
	public void testBetween() {
		assertThat( number( "result = entityCriteria( 'Manufacturer' ).between( 'id', 1, 42 ).count();" ) ).isEqualTo( 2 );
		assertThat( number( "result = entityCriteria( 'Manufacturer' ).whereBetween( 'id', 2, 76 ).count();" ) ).isEqualTo( 1 );
	}

	/**
	 * Test: isIn with an array.
	 */
	@DisplayName( "isIn matches an array of values" )
	@Test
	public void testIsInArray() {
		assertThat( number( "result = entityCriteria( 'Vehicle' ).isIn( 'model', [ 'Civic', 'Fusion' ] ).count();" ) ).isEqualTo( 2 );
	}

	/**
	 * Test: isIn with a comma-separated list.
	 */
	@DisplayName( "isIn matches a comma-separated list" )
	@Test
	public void testIsInList() {
		assertThat( number( "result = entityCriteria( 'Vehicle' ).in( 'model', 'Civic, Fusion' ).count();" ) ).isEqualTo( 2 );
	}

	/**
	 * Test: an empty in-list matches nothing, and its negation everything.
	 */
	@DisplayName( "An empty in-list matches nothing; notIn with it matches everything" )
	@Test
	public void testEmptyIn() {
		assertThat( number( "result = entityCriteria( 'Vehicle' ).isIn( 'model', [] ).count();" ) ).isEqualTo( 0 );
		assertThat( number( "result = entityCriteria( 'Vehicle' ).isNotIn( 'model', [] ).count();" ) ).isEqualTo( 5 );
	}

	/**
	 * Test: isNotIn and the not-prefixed notIn.
	 */
	@DisplayName( "isNotIn and notIn exclude values" )
	@Test
	public void testNotIn() {
		assertThat( number( "result = entityCriteria( 'Vehicle' ).isNotIn( 'make', [ 'Honda' ] ).count();" ) ).isEqualTo( 2 );
		assertThat( number( "result = entityCriteria( 'Vehicle' ).notIn( 'make', [ 'Honda', 'Ford' ] ).count();" ) ).isEqualTo( 1 );
	}

	/**
	 * Test: isNull and isNotNull on an association.
	 */
	@DisplayName( "isNull / isNotNull check for missing values" )
	@Test
	public void testNulls() {
		assertThat( list( "result = entityCriteria( 'Vehicle' ).isNull( 'manufacturer' ).list().map( ( v ) => v.getMake() );" ) )
		    .containsExactly( "Studebaker" );
		assertThat( number( "result = entityCriteria( 'Vehicle' ).isNotNull( 'manufacturer' ).count();" ) ).isEqualTo( 4 );
	}

	/**
	 * Test: isEq with null becomes is null, ne with null is not null.
	 */
	@DisplayName( "isEq( p, null ) means is null; ne( p, null ) means is not null" )
	@Test
	public void testEqNull() {
		assertThat( number( "result = entityCriteria( 'Vehicle' ).isEq( 'manufacturer', javacast( 'null', '' ) ).count();" ) ).isEqualTo( 1 );
		assertThat( number( "result = entityCriteria( 'Vehicle' ).ne( 'manufacturer', javacast( 'null', '' ) ).count();" ) ).isEqualTo( 4 );
	}

	/**
	 * Test: isTrue and isFalse.
	 */
	@DisplayName( "isTrue / isFalse match boolean properties" )
	@Test
	public void testBooleans() {
		assertThat( number( "result = entityCriteria( 'cbAuthor' ).isTrue( 'isActive' ).count();" ) ).isEqualTo( 4 );
		assertThat( number( "result = entityCriteria( 'cbAuthor' ).isFalse( 'isActive' ).count();" ) ).isEqualTo( 1 );
	}

	/**
	 * Test: isEmpty and isNotEmpty on a one-to-many.
	 */
	@DisplayName( "isEmpty / isNotEmpty check collections" )
	@Test
	public void testEmpty() {
		assertThat( list( "result = entityCriteria( 'Manufacturer' ).isEmpty( 'vehicles' ).list().map( ( m ) => m.getName() );" ) )
		    .containsExactly( "General Moters Corporation" );
		assertThat( number( "result = entityCriteria( 'Manufacturer' ).isNotEmpty( 'vehicles' ).count();" ) ).isEqualTo( 2 );
	}

	/**
	 * Test: the size conditions.
	 */
	@DisplayName( "sizeEq / sizeGt / sizeGe / sizeLt / sizeLe / sizeNe compare collection sizes" )
	@Test
	public void testSizes() {
		assertThat( list( "result = entityCriteria( 'Manufacturer' ).sizeEq( 'vehicles', 3 ).list().map( ( m ) => m.getId() );" ) ).containsExactly( 42 );
		assertThat( number( "result = entityCriteria( 'Manufacturer' ).sizeGt( 'vehicles', 0 ).count();" ) ).isEqualTo( 2 );
		assertThat( number( "result = entityCriteria( 'Manufacturer' ).sizeGe( 'vehicles', 1 ).count();" ) ).isEqualTo( 2 );
		assertThat( number( "result = entityCriteria( 'Manufacturer' ).sizeLt( 'vehicles', 1 ).count();" ) ).isEqualTo( 1 );
		assertThat( number( "result = entityCriteria( 'Manufacturer' ).sizeLe( 'vehicles', 1 ).count();" ) ).isEqualTo( 2 );
		assertThat( number( "result = entityCriteria( 'Manufacturer' ).sizeNe( 'vehicles', 3 ).count();" ) ).isEqualTo( 2 );
	}

	/**
	 * Test: a size condition on a non-collection is a clear error.
	 */
	@DisplayName( "sizeEq on a non-collection is an orm.argument error" )
	@Test
	public void testSizeOnValue() {
		IStruct err = error( "entityCriteria( 'Manufacturer' ).sizeEq( 'name', 1 );" );
		assertThat( type( err ) ).isEqualTo( "orm.argument" );
		assertThat( message( err ) ).contains( "not a collection" );
	}

	/**
	 * Test: property-to-property comparisons.
	 */
	@DisplayName( "eqProperty / neProperty / gtProperty compare two properties" )
	@Test
	public void testPropertyComparisons() {
		assertThat( number( "result = entityCriteria( 'Vehicle' ).eqProperty( 'make', 'model' ).count();" ) ).isEqualTo( 0 );
		assertThat( number( "result = entityCriteria( 'Vehicle' ).neProperty( 'make', 'model' ).count();" ) ).isEqualTo( 5 );
		assertThat( list( "result = entityCriteria( 'Vehicle' ).gtProperty( 'make', 'model' ).list().map( ( v ) => v.getModel() );" ) )
		    .containsExactly( "Accord", "Civic" );
		assertThat( number( "result = entityCriteria( 'Vehicle' ).geProperty( 'make', 'model' ).count();" ) ).isEqualTo( 2 );
		assertThat( number( "result = entityCriteria( 'Vehicle' ).ltProperty( 'make', 'model' ).count();" ) ).isEqualTo( 3 );
		assertThat( number( "result = entityCriteria( 'Vehicle' ).leProperty( 'make', 'model' ).count();" ) ).isEqualTo( 3 );
	}

	/**
	 * Test: idEq with a single id.
	 */
	@DisplayName( "idEq matches the id" )
	@Test
	public void testIdEq() {
		assertThat( run( "result = entityCriteria( 'Vehicle' ).idEq( '0SB123' ).get().getMake();" ) ).isEqualTo( "Studebaker" );
	}

	/**
	 * Test: idEq with a composite id.
	 */
	@DisplayName( "idEq matches a composite id given as a struct" )
	@Test
	public void testIdEqComposite() {
		assertThat( run( "result = entityCriteria( 'VehicleType' ).idEq( { make : 'Ford', model : 'F-150' } ).get().getDescription();" ) )
		    .isEqualTo( "Full-size pickup truck" );
	}

	/**
	 * Test: idEq with a simple value on a composite id is a clear error.
	 */
	@DisplayName( "idEq with a simple value on a composite id is an orm.argument error" )
	@Test
	public void testIdEqCompositeError() {
		IStruct err = error( "entityCriteria( 'VehicleType' ).idEq( 'Ford' );" );
		assertThat( type( err ) ).isEqualTo( "orm.argument" );
		assertThat( message( err ) ).contains( "composite id" );
	}

	/**
	 * Test: sql() with {alias}.column and a bound parameter.
	 */
	@DisplayName( "sql() runs a native SQL condition with {alias}.column and bound params" )
	@Test
	public void testSql() {
		assertThat( number( "result = entityCriteria( 'Vehicle' ).sql( 'upper({alias}.make) = ?', [ 'HONDA' ] ).count();" ) ).isEqualTo( 3 );
	}

	/**
	 * Test: sql() with {property} and several parameters.
	 */
	@DisplayName( "sql() accepts {property} references and several params" )
	@Test
	public void testSqlProperty() {
		assertThat( number( "result = entityCriteria( 'Vehicle' ).sqlRestriction( 'lower({make}) = ? or {model} = ?', [ 'ford', 'Civic' ] ).count();" ) )
		    .isEqualTo( 2 );
	}

	/**
	 * Test: sql() with a foreign-key column.
	 */
	@DisplayName( "sql() resolves a foreign-key column" )
	@Test
	public void testSqlForeignKey() {
		assertThat( number( "result = entityCriteria( 'Vehicle' ).sql( '{alias}.FK_manufacturer = ?', [ 42 ] ).count();" ) ).isEqualTo( 3 );
	}

	/**
	 * Test: sql() with an association path reference.
	 */
	@DisplayName( "sql() resolves a {association.property} reference through a join" )
	@Test
	public void testSqlAssociationPath() {
		assertThat( number( "result = entityCriteria( 'Vehicle' ).sql( 'upper({manufacturer.name}) = ?', [ 'FORD MOTOR COMPANY' ] ).count();" ) )
		    .isEqualTo( 1 );
	}

	/**
	 * Test: sql() placeholder/param mismatches are clear errors.
	 */
	@DisplayName( "sql() with too many or too few params is an orm.query.parameter error" )
	@Test
	public void testSqlParamMismatch() {
		assertThat( type( error( "entityCriteria( 'Vehicle' ).sql( '{alias}.make = ?', [] );" ) ) ).isEqualTo( "orm.query.parameter" );
		assertThat( type( error( "entityCriteria( 'Vehicle' ).sql( '{alias}.make = ?', [ 1, 2 ] );" ) ) ).isEqualTo( "orm.query.parameter" );
	}

	/**
	 * Test: sql() with an unknown column.
	 */
	@DisplayName( "sql() with an unknown column is an orm.property.unknown error" )
	@Test
	public void testSqlUnknownColumn() {
		assertThat( type( error( "entityCriteria( 'Vehicle' ).sql( '{alias}.nope = ?', [ 1 ] );" ) ) ).isEqualTo( "orm.property.unknown" );
	}

	/**
	 * Test: where( property, value ).
	 */
	@DisplayName( "where( property, value ) is isEq" )
	@Test
	public void testWhereValue() {
		assertThat( number( "result = entityCriteria( 'Vehicle' ).where( 'make', 'Honda' ).count();" ) ).isEqualTo( 3 );
	}

	/**
	 * Test: where( property, operator, value ) for each operator.
	 */
	@DisplayName( "where( property, operator, value ) supports every operator" )
	@Test
	public void testWhereOperators() {
		assertThat( number( "result = entityCriteria( 'Manufacturer' ).where( 'id', '=', 42 ).count();" ) ).isEqualTo( 1 );
		assertThat( number( "result = entityCriteria( 'Manufacturer' ).where( 'id', '!=', 42 ).count();" ) ).isEqualTo( 2 );
		assertThat( number( "result = entityCriteria( 'Manufacturer' ).where( 'id', '>', 1 ).count();" ) ).isEqualTo( 2 );
		assertThat( number( "result = entityCriteria( 'Manufacturer' ).where( 'id', '>=', 42 ).count();" ) ).isEqualTo( 2 );
		assertThat( number( "result = entityCriteria( 'Manufacturer' ).where( 'id', '<', 42 ).count();" ) ).isEqualTo( 1 );
		assertThat( number( "result = entityCriteria( 'Manufacturer' ).where( 'id', 'lte', 42 ).count();" ) ).isEqualTo( 2 );
		assertThat( number( "result = entityCriteria( 'Vehicle' ).where( 'model', 'like', 'C%' ).count();" ) ).isEqualTo( 1 );
		assertThat( number( "result = entityCriteria( 'Vehicle' ).where( 'make', 'ilike', 'honda' ).count();" ) ).isEqualTo( 3 );
		assertThat( number( "result = entityCriteria( 'Vehicle' ).where( 'model', 'in', [ 'Civic', 'Fusion' ] ).count();" ) ).isEqualTo( 2 );
		assertThat( number( "result = entityCriteria( 'Vehicle' ).where( 'model', 'not in', [ 'Civic', 'Fusion' ] ).count();" ) ).isEqualTo( 3 );
	}

	/**
	 * Test: an unknown where operator.
	 */
	@DisplayName( "An unknown where operator is an orm.argument error" )
	@Test
	public void testWhereBadOperator() {
		IStruct err = error( "entityCriteria( 'Manufacturer' ).where( 'id', '=>', 42 );" );
		assertThat( type( err ) ).isEqualTo( "orm.argument" );
		assertThat( message( err ) ).contains( "operator" );
	}

	/**
	 * Test: where( struct ) with a null value.
	 */
	@DisplayName( "where( struct ) matches every key; a null value means is null" )
	@Test
	public void testWhereStruct() {
		assertThat( number( "result = entityCriteria( 'Vehicle' ).where( { make : 'Honda', model : 'Civic' } ).count();" ) ).isEqualTo( 1 );
		assertThat( number( "result = entityCriteria( 'Vehicle' ).where( { manufacturer : javacast( 'null', '' ) } ).count();" ) ).isEqualTo( 1 );
	}

	/**
	 * Test: where( closure ) is an and-group.
	 */
	@DisplayName( "where( closure ) groups conditions with and" )
	@Test
	public void testWhereClosure() {
		assertThat( number( "result = entityCriteria( 'Vehicle' ).where( ( c ) => c.isEq( 'make', 'Honda' ).isEq( 'model', 'Civic' ) ).count();" ) )
		    .isEqualTo( 1 );
	}

	/**
	 * Test: anyOf with one closure ors its conditions.
	 */
	@DisplayName( "anyOf ors the conditions of its closure" )
	@Test
	public void testAnyOf() {
		assertThat( number( "result = entityCriteria( 'Vehicle' ).anyOf( ( c ) => c.isEq( 'model', 'Civic' ).isEq( 'model', 'Fusion' ) ).count();" ) )
		    .isEqualTo( 2 );
	}

	/**
	 * Test: anyOf with several closures: each closure is one alternative.
	 */
	@DisplayName( "anyOf with several closures: each closure is one alternative" )
	@Test
	public void testAnyOfBranches() {
		assertThat( number( """
		                    result = entityCriteria( 'Vehicle' ).anyOf(
		                        ( c ) => c.isEq( 'make', 'Honda' ).isEq( 'model', 'Civic' ),
		                        ( c ) => c.isEq( 'make', 'Ford' )
		                    ).count();
		                    """ ) ).isEqualTo( 2 );
	}

	/**
	 * Test: the $or, or, orWhere and disjunction aliases.
	 */
	@DisplayName( "$or / or / orWhere / disjunction are anyOf" )
	@Test
	public void testOrAliases() {
		assertThat( number( "result = entityCriteria( 'Vehicle' ).$or( ( c ) => c.isEq( 'model', 'Civic' ).isEq( 'model', 'Fusion' ) ).count();" ) )
		    .isEqualTo( 2 );
		assertThat( number( "result = entityCriteria( 'Vehicle' ).orWhere( ( c ) => c.isEq( 'model', 'Civic' ).isEq( 'model', 'Fusion' ) ).count();" ) )
		    .isEqualTo( 2 );
		assertThat( number( "result = entityCriteria( 'Vehicle' ).disjunction( ( c ) => c.isEq( 'model', 'Civic' ).isEq( 'model', 'Fusion' ) ).count();" ) )
		    .isEqualTo( 2 );
		assertThat( number( "result = entityCriteria( 'Vehicle' ).or( ( c ) => c.isEq( 'model', 'Civic' ).isEq( 'model', 'Fusion' ) ).count();" ) )
		    .isEqualTo( 2 );
		assertThat( number( "result = entityCriteria( 'Vehicle' ).and( ( c ) => c.isEq( 'make', 'Honda' ).isEq( 'model', 'Civic' ) ).count();" ) )
		    .isEqualTo( 1 );
	}

	/**
	 * Test: allOf nested inside anyOf.
	 */
	@DisplayName( "allOf nests an and-group inside anyOf" )
	@Test
	public void testAllOfInAnyOf() {
		assertThat( number( """
		                    result = entityCriteria( 'Vehicle' ).anyOf( ( c ) => c
		                        .allOf( ( a ) => a.isEq( 'make', 'Honda' ).like( 'model', 'R%' ) )
		                        .isEq( 'make', 'Studebaker' )
		                    ).count();
		                    """ ) ).isEqualTo( 2 );
		assertThat( number( "result = entityCriteria( 'Vehicle' ).$and( ( c ) => c.isEq( 'make', 'Honda' ).isEq( 'model', 'Civic' ) ).count();" ) )
		    .isEqualTo( 1 );
	}

	/**
	 * Test: not( closure ) negates a group.
	 */
	@DisplayName( "not( closure ) negates its conditions" )
	@Test
	public void testNot() {
		assertThat( number( "result = entityCriteria( 'Vehicle' ).not( ( c ) => c.isEq( 'make', 'Honda' ).isEq( 'model', 'Civic' ) ).count();" ) )
		    .isEqualTo( 4 );
	}

	/**
	 * Test: the not prefix works on any condition.
	 */
	@DisplayName( "The not prefix negates any condition" )
	@Test
	public void testNotPrefix() {
		assertThat( number( "result = entityCriteria( 'Vehicle' ).notLike( 'make', 'Hon%' ).count();" ) ).isEqualTo( 2 );
		assertThat( number( "result = entityCriteria( 'Manufacturer' ).notBetween( 'id', 1, 42 ).count();" ) ).isEqualTo( 1 );
		assertThat( number( "result = entityCriteria( 'Vehicle' ).notEq( 'make', 'Honda' ).count();" ) ).isEqualTo( 2 );
		assertThat( number( "result = entityCriteria( 'Manufacturer' ).notEmpty( 'vehicles' ).count();" ) ).isEqualTo( 2 );
	}

	/**
	 * Test: not() keeps rows whose association is missing (left join).
	 */
	@DisplayName( "not() on an association path keeps rows without the association" )
	@Test
	public void testNotKeepsNulls() {
		assertThat( number( "result = entityCriteria( 'Vehicle' ).not( ( c ) => c.isEq( 'manufacturer.name', 'Honda Motor Co.' ) ).count();" ) )
		    .isEqualTo( 1 );
	}

	/**
	 * Test: conditions chain with and.
	 */
	@DisplayName( "Chained conditions are joined with and" )
	@Test
	public void testChaining() {
		assertThat( number( "result = entityCriteria( 'Vehicle' ).isEq( 'make', 'Honda' ).like( 'model', '%c%' ).count();" ) ).isEqualTo( 2 );
	}

	/**
	 * Test: an unknown property fails with did-you-mean.
	 */
	@DisplayName( "An unknown property is an orm.property.unknown error with a suggestion" )
	@Test
	public void testUnknownProperty() {
		IStruct err = error( "entityCriteria( 'Vehicle' ).isEq( 'mkae', 'Honda' );" );
		assertThat( type( err ) ).isEqualTo( "orm.property.unknown" );
		assertThat( message( err ) ).contains( "Did you mean [make]?" );
	}

	/**
	 * Test: an unknown method fails with did-you-mean.
	 */
	@DisplayName( "An unknown method is an orm.argument error with a suggestion" )
	@Test
	public void testUnknownMethod() {
		IStruct err = error( "entityCriteria( 'Vehicle' ).isEqual( 'make', 'Honda' );" );
		assertThat( type( err ) ).isEqualTo( "orm.argument" );
		assertThat( message( err ) ).contains( "no method [isEqual()]" );
		assertThat( message( err ) ).contains( "Did you mean" );
	}

	/**
	 * Test: an unknown named argument.
	 */
	@DisplayName( "An unknown named argument is an orm.argument error listing the arguments" )
	@Test
	public void testUnknownNamedArgument() {
		IStruct err = error( "entityCriteria( 'Vehicle' ).isEq( prop = 'make', value = 'Honda' );" );
		assertThat( type( err ) ).isEqualTo( "orm.argument" );
		assertThat( err.getAsString( ortus.boxlang.runtime.scopes.Key.detail ) ).contains( "property, value" );
	}

	/**
	 * Test: too many positional arguments.
	 */
	@DisplayName( "Too many positional arguments is an orm.argument error" )
	@Test
	public void testTooManyArguments() {
		assertThat( type( error( "entityCriteria( 'Vehicle' ).isNull( 'make', 'extra' );" ) ) ).isEqualTo( "orm.argument" );
	}

	/**
	 * Test: an unknown entity.
	 */
	@DisplayName( "An unknown entity is an orm.entity.notFound error" )
	@Test
	public void testUnknownEntity() {
		IStruct err = error( "entityCriteria( 'Vehicel' );" );
		assertThat( type( err ) ).isEqualTo( "orm.entity.notFound" );
		assertThat( message( err ) ).contains( "Did you mean [Vehicle]?" );
	}

	/**
	 * Test: a condition closure argument that is not a closure.
	 */
	@DisplayName( "anyOf with a non-closure is an orm.argument error" )
	@Test
	public void testAnyOfNotClosure() {
		assertThat( type( error( "entityCriteria( 'Vehicle' ).anyOf( 'make' );" ) ) ).isEqualTo( "orm.argument" );
	}

	/**
	 * Test: the association id shortcut needs no join.
	 */
	@DisplayName( "manufacturer.id compares the foreign key without a join" )
	@Test
	public void testAssociationIdNoJoin() {
		assertThat( number( "result = entityCriteria( 'Vehicle' ).isEq( 'manufacturer.id', 42 ).count();" ) ).isEqualTo( 3 );
		assertThat( ( String ) run( "result = entityCriteria( 'Vehicle' ).isEq( 'manufacturer.id', 42 ).getHQL();" ) ).doesNotContain( "join" );
	}

	/**
	 * Test: an association compared with an id or an entity.
	 */
	@DisplayName( "An association compares with an id or an entity instance" )
	@Test
	public void testAssociationValue() {
		assertThat( number( "result = entityCriteria( 'Vehicle' ).isEq( 'manufacturer', 42 ).count();" ) ).isEqualTo( 3 );
		assertThat( number( "m = entityLoadByPK( 'Manufacturer', 1 ); result = entityCriteria( 'Vehicle' ).isEq( 'manufacturer', m ).count();" ) )
		    .isEqualTo( 1 );
		assertThat( number( "result = entityCriteria( 'Vehicle' ).isIn( 'manufacturer', [ 1, 42 ] ).count();" ) ).isEqualTo( 4 );
	}
}
