package ortus.boxlang.modules.orm.bifs;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import tools.BaseORMTest;

/**
 * Live (MySQL) tests for the Phase 2 inspection BIFs: entityGetName, entityGetDatasource, entityGetId,
 * entityGetMetadata, entityIsDirty, entityGetDirtyProperties, ormIsSessionDirty, ormGetSessionStatistics, and
 * ormFlush( datasource ). Every write is rolled back.
 */
public class EntityInspectionBIFsTest extends BaseORMTest {

	/**
	 * Clear the ORM session after each test so no entity leaks into the next one.
	 */
	@AfterEach
	public void clearSession() {
		instance.executeSource( "try { ormClearSession(); ormClearSession( \"dsn2\" ); } catch ( any e ) {}", context );
	}

	/**
	 * Run BoxLang code and return the value it stores in {@code result}.
	 *
	 * @param code BoxLang statements that set {@code result}.
	 *
	 * @return The value of {@code result}.
	 */
	private Object run( String code ) {
		instance.executeSource( code, context );
		return variables.get( result );
	}

	/**
	 * Run BoxLang code inside a rolled-back transaction and return the value it stores in {@code result}.
	 *
	 * @param code BoxLang statements that set {@code result}.
	 *
	 * @return The value of {@code result}.
	 */
	private Object runRolledBack( String code ) {
		return run( "transaction { try { " + code + " } finally { transactionRollback(); } }" );
	}

	/**
	 * Run BoxLang code that must throw, and return { type, message }.
	 *
	 * @param code BoxLang statements.
	 *
	 * @return The error's type and message, or { type : "NO ERROR" }.
	 */
	private IStruct error( String code ) {
		instance.executeSource( "try { " + code + " result = { type : \"NO ERROR\" }; } catch ( any e ) { result = { type : e.type, message : e.message }; }",
		    context );
		return variables.getAsStruct( result );
	}

	/* ----------------------------------------------------------------------------------------------------------- */
	/* entityGetName */
	/* ----------------------------------------------------------------------------------------------------------- */

	/**
	 * Test: entityGetName returns the entity name of a loaded instance.
	 */
	@DisplayName( "entityGetName returns the entity name of a loaded instance" )
	@Test
	public void testNameOfInstance() {
		assertThat( run( "result = entityGetName( entityLoadByPK( \"Manufacturer\", 1 ) );" ) ).isEqualTo( "Manufacturer" );
	}

	/**
	 * Test: entityGetName returns the declared casing for a name in any casing.
	 */
	@DisplayName( "entityGetName returns the declared casing for a name in any casing" )
	@Test
	public void testNameCasing() {
		assertThat( run( "result = entityGetName( \"mAnUfAcTuReR\" );" ) ).isEqualTo( "Manufacturer" );
	}

	/**
	 * Test: entityGetName honors a custom entityName annotation.
	 */
	@DisplayName( "entityGetName honors a custom entityName annotation" )
	@Test
	public void testNameCustomEntityName() {
		assertThat( run( "result = entityGetName( entityNew( \"cbAuthor\" ) );" ) ).isEqualTo( "cbAuthor" );
	}

	/**
	 * Test: entityGetName of a new, unsaved entity works.
	 */
	@DisplayName( "entityGetName of a new, unsaved entity works" )
	@Test
	public void testNameOfNewEntity() {
		assertThat( run( "result = entityGetName( entityNew( \"Vehicle\" ) );" ) ).isEqualTo( "Vehicle" );
	}

	/**
	 * Test: entityGetName of a lazy reference answers even after the session closed.
	 */
	@DisplayName( "entityGetName of a lazy reference answers even after the session closed" )
	@Test
	public void testNameOfLazyReference() {
		assertThat( run( """
		                 v = entityLoadByPK( "Vehicle", "9ABAZ85656A776723" );
		                 m = v.getManufacturer();
		                 ormCloseSession();
		                 result = entityGetName( m );
		                 """ ) ).isEqualTo( "Manufacturer" );
	}

	/**
	 * Test: entityGetName of an unknown name suggests the right one.
	 */
	@DisplayName( "entityGetName of an unknown name suggests the right one" )
	@Test
	public void testNameUnknown() {
		IStruct err = error( "entityGetName( \"Manufactuer\" );" );
		assertThat( err.getAsString( Key.type ) ).isEqualTo( "orm.entity.notFound" );
		assertThat( err.getAsString( Key.message ) ).contains( "Did you mean [Manufacturer]?" );
	}

	/**
	 * Test: entityGetName of a struct is an orm.argument error.
	 */
	@DisplayName( "entityGetName of a struct is an orm.argument error" )
	@Test
	public void testNameOfStruct() {
		IStruct err = error( "entityGetName( { name : 1 } );" );
		assertThat( err.getAsString( Key.type ) ).isEqualTo( "orm.argument" );
		assertThat( err.getAsString( Key.message ) ).contains( "received a struct" );
	}

	/**
	 * Test: entityGetName of an empty string is an orm.argument error.
	 */
	@DisplayName( "entityGetName of an empty string is an orm.argument error" )
	@Test
	public void testNameOfEmptyString() {
		assertThat( error( "entityGetName( \"\" );" ).getAsString( Key.type ) ).isEqualTo( "orm.argument" );
	}

	/* ----------------------------------------------------------------------------------------------------------- */
	/* entityGetDatasource */
	/* ----------------------------------------------------------------------------------------------------------- */

	/**
	 * Test: entityGetDatasource returns the default datasource for a default-datasource entity.
	 */
	@DisplayName( "entityGetDatasource returns the default datasource for a default-datasource entity" )
	@Test
	public void testDatasourceDefault() {
		assertThat( run( "result = entityGetDatasource( \"Manufacturer\" );" ) ).isEqualTo( "TestDB" );
	}

	/**
	 * Test: entityGetDatasource returns an entity's own datasource, by name or instance.
	 */
	@DisplayName( "entityGetDatasource returns an entity's own datasource, by name or instance" )
	@Test
	public void testDatasourceAlternate() {
		assertThat( run( "result = entityGetDatasource( \"AlternateDS\" );" ) ).isEqualTo( "dsn2" );
		assertThat( run( "result = entityGetDatasource( entityNew( \"AlternateDS\" ) );" ) ).isEqualTo( "dsn2" );
	}

	/* ----------------------------------------------------------------------------------------------------------- */
	/* entityGetId */
	/* ----------------------------------------------------------------------------------------------------------- */

	/**
	 * Test: entityGetId returns an integer id.
	 */
	@DisplayName( "entityGetId returns an integer id" )
	@Test
	public void testIdInteger() {
		assertThat( run( "result = entityGetId( entityLoadByPK( \"Manufacturer\", 1 ) );" ).toString() ).isEqualTo( "1" );
	}

	/**
	 * Test: entityGetId returns an assigned string id.
	 */
	@DisplayName( "entityGetId returns an assigned string id" )
	@Test
	public void testIdString() {
		assertThat( run( "result = entityGetId( entityLoadByPK( \"Vehicle\", \"9ABAZ85656A776723\" ) );" ) ).isEqualTo( "9ABAZ85656A776723" );
	}

	/**
	 * Test: entityGetId of a new entity is null.
	 */
	@DisplayName( "entityGetId of a new entity is null" )
	@Test
	public void testIdOfNewEntity() {
		assertThat( run( "result = isNull( entityGetId( entityNew( \"Manufacturer\" ) ) );" ) ).isEqualTo( true );
	}

	/**
	 * Test: entityGetId returns the generated id after save.
	 */
	@DisplayName( "entityGetId returns the generated id after save" )
	@Test
	public void testIdAfterSave() {
		assertThat( runRolledBack( """
		                           m = entityNew( "Manufacturer", { name : "Inspect Co", address : "1 Test St" } );
		                           entitySave( m );
		                           ormFlush();
		                           result = !isNull( entityGetId( m ) ) && entityGetId( m ) > 0;
		                           """ ) ).isEqualTo( true );
	}

	/**
	 * Test: entityGetId of a composite key is a struct of its key properties.
	 */
	@DisplayName( "entityGetId of a composite key is a struct of its key properties" )
	@Test
	public void testIdComposite() {
		Object id = run( "result = entityGetId( entityNew( \"PlayingField\", { fieldID : 7, userID : \"u1\" } ) );" );
		assertThat( id ).isInstanceOf( IStruct.class );
		IStruct composite = ( IStruct ) id;
		assertThat( composite.get( Key.of( "fieldID" ) ).toString() ).isEqualTo( "7" );
		assertThat( composite.get( Key.of( "userID" ) ) ).isEqualTo( "u1" );
	}

	/**
	 * Test: entityGetId of a lazy reference does not load it (works after the session closed).
	 */
	@DisplayName( "entityGetId of a lazy reference does not load it (works after the session closed)" )
	@Test
	public void testIdOfLazyReference() {
		assertThat( run( """
		                 v = entityLoadByPK( "Vehicle", "9ABAZ85656A776723" );
		                 m = v.getManufacturer();
		                 ormCloseSession();
		                 result = entityGetId( m );
		                 """ ).toString() ).isEqualTo( "1" );
	}

	/**
	 * Test: entityGetId of an entity name is an orm.argument error.
	 */
	@DisplayName( "entityGetId of an entity name is an orm.argument error" )
	@Test
	public void testIdOfName() {
		IStruct err = error( "entityGetId( \"Manufacturer\" );" );
		assertThat( err.getAsString( Key.type ) ).isEqualTo( "orm.argument" );
		assertThat( err.getAsString( Key.message ) ).contains( "needs an entity instance" );
	}

	/* ----------------------------------------------------------------------------------------------------------- */
	/* entityGetMetadata */
	/* ----------------------------------------------------------------------------------------------------------- */

	/**
	 * Read one entity's metadata struct.
	 *
	 * @param entity A BoxLang expression for the entity argument.
	 *
	 * @return The metadata struct.
	 */
	private IStruct metadata( String entity ) {
		return ( IStruct ) run( "result = entityGetMetadata( " + entity + " );" );
	}

	/**
	 * Find an entry by name in a metadata array (properties or associations).
	 *
	 * @param list The array of structs.
	 * @param name The entry name.
	 *
	 * @return The entry, or null.
	 */
	private static IStruct entry( Array list, String name ) {
		for ( Object item : list ) {
			IStruct s = ( IStruct ) item;
			if ( name.equalsIgnoreCase( s.getAsString( Key.of( "name" ) ) ) ) {
				return s;
			}
		}
		return null;
	}

	/**
	 * Test: entityGetMetadata describes the entity, table, datasource and id.
	 */
	@DisplayName( "entityGetMetadata describes the entity, table, datasource and id" )
	@Test
	public void testMetadataBasics() {
		IStruct meta = metadata( "\"Manufacturer\"" );
		assertThat( meta.getAsString( Key.of( "entityName" ) ) ).isEqualTo( "Manufacturer" );
		assertThat( meta.getAsString( Key.of( "tableName" ) ) ).isEqualTo( "manufacturers" );
		assertThat( meta.getAsString( Key.of( "datasource" ) ) ).isEqualTo( "TestDB" );
		assertThat( ( Array ) meta.get( Key.of( "idProperties" ) ) ).containsExactly( "id" );
		assertThat( meta.getAsString( Key.of( "idType" ) ) ).isEqualTo( "integer" );
		assertThat( meta.getAsString( Key.of( "className" ) ) ).contains( "Manufacturer" );
		assertThat( meta.getAsString( Key.of( "parent" ) ) ).isEmpty();
		assertThat( meta.get( Key.of( "readOnly" ) ) ).isEqualTo( false );
	}

	/**
	 * Test: entityGetMetadata lists column properties with their column details.
	 */
	@DisplayName( "entityGetMetadata lists column properties with their column details" )
	@Test
	public void testMetadataProperties() {
		IStruct	meta	= metadata( "\"ConstrainedThing\"" );
		IStruct	first	= entry( ( Array ) meta.get( Key.of( "properties" ) ), "firstName" );
		assertThat( first.getAsString( Key.of( "column" ) ) ).isEqualTo( "first_name" );
		assertThat( first.getAsString( Key.of( "ormtype" ) ) ).isEqualTo( "string" );
		assertThat( first.getAsString( Key.of( "fieldtype" ) ) ).isEqualTo( "column" );
		assertThat( first.getAsString( Key.of( "length" ) ) ).isEqualTo( "50" );
		IStruct code = entry( ( Array ) meta.get( Key.of( "properties" ) ), "code" );
		assertThat( code.get( Key.of( "nullable" ) ) ).isEqualTo( false );
		assertThat( entry( ( Array ) meta.get( Key.of( "properties" ) ), "id" ) ).isNull();
	}

	/**
	 * Test: entityGetMetadata lists associations with kind, target, fkcolumn, cascade and link table.
	 */
	@DisplayName( "entityGetMetadata lists associations with kind, target, fkcolumn, cascade and link table" )
	@Test
	public void testMetadataAssociations() {
		IStruct	meta			= metadata( "\"Vehicle\"" );
		Array	associations	= ( Array ) meta.get( Key.of( "associations" ) );
		IStruct	manufacturer	= entry( associations, "manufacturer" );
		assertThat( manufacturer.getAsString( Key.of( "kind" ) ) ).isEqualTo( "many-to-one" );
		assertThat( manufacturer.getAsString( Key.of( "target" ) ) ).isEqualTo( "Manufacturer" );
		assertThat( manufacturer.getAsString( Key.of( "fkcolumn" ) ) ).isEqualTo( "FK_manufacturer" );
		assertThat( manufacturer.getAsString( Key.of( "lazy" ) ) ).isEqualTo( "true" );
		IStruct features = entry( associations, "features" );
		assertThat( features.getAsString( Key.of( "kind" ) ) ).isEqualTo( "many-to-many" );
		assertThat( features.getAsString( Key.of( "target" ) ) ).isEqualTo( "Feature" );
		assertThat( features.getAsString( Key.of( "linkTable" ) ) ).isEqualTo( "vehicle_features" );
		assertThat( features.getAsString( Key.of( "cascade" ) ) ).isNotEmpty();
		assertThat( ( Array ) meta.get( Key.of( "propertyNames" ) ) ).containsAtLeast( "make", "model", "manufacturer", "features" );
	}

	/**
	 * Test: entityGetMetadata describes a one-to-many from the owning side.
	 */
	@DisplayName( "entityGetMetadata describes a one-to-many from the owning side" )
	@Test
	public void testMetadataOneToMany() {
		IStruct vehicles = entry( ( Array ) metadata( "\"Manufacturer\"" ).get( Key.of( "associations" ) ), "vehicles" );
		assertThat( vehicles.getAsString( Key.of( "kind" ) ) ).isEqualTo( "one-to-many" );
		assertThat( vehicles.getAsString( Key.of( "target" ) ) ).isEqualTo( "Vehicle" );
		assertThat( vehicles.getAsString( Key.of( "fkcolumn" ) ) ).isEqualTo( "FK_manufacturer" );
	}

	/**
	 * Test: entityGetMetadata includes the discriminator and parent of a subclass.
	 */
	@DisplayName( "entityGetMetadata includes the discriminator and parent of a subclass" )
	@Test
	public void testMetadataDiscriminator() {
		IStruct meta = metadata( "\"Employee\"" );
		assertThat( meta.getAsString( Key.of( "parent" ) ) ).isEqualTo( "User" );
		IStruct discriminator = meta.getAsStruct( Key.of( "discriminator" ) );
		assertThat( discriminator.getAsString( Key.of( "value" ) ) ).isEqualTo( "employee" );
		assertThat( discriminator.getAsString( Key.of( "column" ) ) ).isEqualTo( "userType" );
	}

	/**
	 * Test: entityGetMetadata includes formula properties.
	 */
	@DisplayName( "entityGetMetadata includes formula properties" )
	@Test
	public void testMetadataFormula() {
		IStruct pending = entry( ( Array ) metadata( "\"Employee\"" ).get( Key.of( "properties" ) ), "numberOfPendingTimeOffRequests" );
		assertThat( pending ).isNotNull();
		assertThat( pending.getAsString( Key.of( "formula" ) ) ).contains( "count(*)" );
		assertThat( pending.getAsString( Key.of( "column" ) ) ).isEmpty();
	}

	/**
	 * Test: entityGetMetadata of a composite id lists every key property.
	 */
	@DisplayName( "entityGetMetadata of a composite id lists every key property" )
	@Test
	public void testMetadataCompositeId() {
		IStruct meta = metadata( "\"PlayingField\"" );
		assertThat( ( Array ) meta.get( Key.of( "idProperties" ) ) ).containsExactly( "fieldID", "userID" );
		assertThat( meta.getAsString( Key.of( "idType" ) ) ).isEqualTo( "composite" );
	}

	/**
	 * Test: entityGetMetadata gives the same answer for a name and an instance.
	 */
	@DisplayName( "entityGetMetadata gives the same answer for a name and an instance" )
	@Test
	public void testMetadataNameOrInstance() {
		IStruct	byName		= metadata( "\"Manufacturer\"" );
		IStruct	byInstance	= metadata( "entityLoadByPK( \"Manufacturer\", 1 )" );
		assertThat( byInstance.toString() ).isEqualTo( byName.toString() );
	}

	/**
	 * Test: entityGetMetadata returns a copy: changing it does not change the next answer.
	 */
	@DisplayName( "entityGetMetadata returns a copy: changing it does not change the next answer" )
	@Test
	public void testMetadataIsACopy() {
		assertThat( run( """
		                 first = entityGetMetadata( "Manufacturer" );
		                 first.tableName = "hacked";
		                 first.properties.clear();
		                 second = entityGetMetadata( "Manufacturer" );
		                 result = second.tableName == "manufacturers" && second.properties.len() > 0;
		                 """ ) ).isEqualTo( true );
	}

	/**
	 * Test: entityGetMetadata of an unknown entity suggests the right one.
	 */
	@DisplayName( "entityGetMetadata of an unknown entity suggests the right one" )
	@Test
	public void testMetadataUnknown() {
		IStruct err = error( "entityGetMetadata( \"Vehicel\" );" );
		assertThat( err.getAsString( Key.type ) ).isEqualTo( "orm.entity.notFound" );
		assertThat( err.getAsString( Key.message ) ).contains( "Did you mean [Vehicle]?" );
	}

	/* ----------------------------------------------------------------------------------------------------------- */
	/* entityIsDirty / entityGetDirtyProperties */
	/* ----------------------------------------------------------------------------------------------------------- */

	/**
	 * Test: a freshly loaded entity is not dirty.
	 */
	@DisplayName( "a freshly loaded entity is not dirty" )
	@Test
	public void testCleanLoaded() {
		assertThat( run( """
		                 m = entityLoadByPK( "Manufacturer", 1 );
		                 result = !entityIsDirty( m ) && entityGetDirtyProperties( m ).isEmpty();
		                 """ ) ).isEqualTo( true );
	}

	/**
	 * Test: changing a property makes a managed entity dirty and names the property.
	 */
	@DisplayName( "changing a property makes a managed entity dirty and names the property" )
	@Test
	public void testDirtyManaged() {
		assertThat( runRolledBack( """
		                           m = entityLoadByPK( "Manufacturer", 1 );
		                           m.setName( m.getName() & " (changed)" );
		                           result = entityIsDirty( m ) & "|" & entityGetDirtyProperties( m ).toList();
		                           """ ) ).isEqualTo( "true|name" );
	}

	/**
	 * Test: several changed properties are all reported, in mapping order.
	 */
	@DisplayName( "several changed properties are all reported, in mapping order" )
	@Test
	public void testDirtySeveral() {
		assertThat( runRolledBack( """
		                           m = entityLoadByPK( "Manufacturer", 1 );
		                           m.setAddress( "elsewhere" );
		                           m.setName( "renamed" );
		                           result = entityGetDirtyProperties( m ).sort( "text" ).toList();
		                           """ ) ).isEqualTo( "address,name" );
	}

	/**
	 * Test: setting a property back to its loaded value makes it clean again.
	 */
	@DisplayName( "setting a property back to its loaded value makes it clean again" )
	@Test
	public void testDirtyRevert() {
		assertThat( runRolledBack( """
		                           m = entityLoadByPK( "Manufacturer", 1 );
		                           original = m.getName();
		                           m.setName( "temp" );
		                           m.setName( original );
		                           result = entityIsDirty( m );
		                           """ ) ).isEqualTo( false );
	}

	/**
	 * Test: a changed association is reported as dirty.
	 */
	@DisplayName( "a changed association is reported as dirty" )
	@Test
	public void testDirtyAssociation() {
		assertThat( runRolledBack( """
		                           v = entityLoadByPK( "Vehicle", "9ABAZ85656A776723" );
		                           other = entityLoad( "Manufacturer", {}, "id desc" )[ 1 ];
		                           v.setManufacturer( other );
		                           result = entityGetDirtyProperties( v ).toList();
		                           """ ) ).isEqualTo( "manufacturer" );
	}

	/**
	 * Test: after a flush the entity is clean again.
	 */
	@DisplayName( "after a flush the entity is clean again" )
	@Test
	public void testCleanAfterFlush() {
		assertThat( runRolledBack( """
		                           m = entityLoadByPK( "Manufacturer", 1 );
		                           m.setName( "flushed name" );
		                           ormFlush();
		                           result = entityIsDirty( m );
		                           """ ) ).isEqualTo( false );
	}

	/**
	 * Test: a new, never-saved entity is not dirty (cborm semantics).
	 */
	@DisplayName( "a new, never-saved entity is not dirty (cborm semantics)" )
	@Test
	public void testNewEntityNotDirty() {
		assertThat( run( """
		                 m = entityNew( "Manufacturer", { name : "brand new" } );
		                 result = entityIsDirty( m ) & "|" & entityGetDirtyProperties( m ).len();
		                 """ ) ).isEqualTo( "false|0" );
	}

	/**
	 * Test: a detached entity is compared with its database row.
	 */
	@DisplayName( "a detached entity is compared with its database row" )
	@Test
	public void testDetachedDirty() {
		assertThat( runRolledBack( """
		                           m = entityLoadByPK( "Manufacturer", 1 );
		                           ormClearSession();
		                           cleanWhileDetached = entityIsDirty( m );
		                           m.setName( "changed while detached" );
		                           result = cleanWhileDetached & "|" & entityIsDirty( m ) & "|" & entityGetDirtyProperties( m ).toList();
		                           """ ) ).isEqualTo( "false|true|name" );
	}

	/**
	 * Test: a lazy reference that was never loaded is not dirty and is not loaded by the check.
	 */
	@DisplayName( "a lazy reference that was never loaded is not dirty and is not loaded by the check" )
	@Test
	public void testLazyReferenceNotDirty() {
		assertThat( run( """
		                 v = entityLoadByPK( "Vehicle", "9ABAZ85656A776723" );
		                 m = v.getManufacturer();
		                 result = entityIsDirty( m );
		                 """ ) ).isEqualTo( false );
	}

	/**
	 * Test: entityIsDirty and entityGetDirtyProperties of a name are orm.argument errors.
	 */
	@DisplayName( "entityIsDirty and entityGetDirtyProperties of a name are orm.argument errors" )
	@Test
	public void testDirtyOfName() {
		assertThat( error( "entityIsDirty( \"Manufacturer\" );" ).getAsString( Key.type ) ).isEqualTo( "orm.argument" );
		assertThat( error( "entityGetDirtyProperties( \"Manufacturer\" );" ).getAsString( Key.type ) ).isEqualTo( "orm.argument" );
	}

	/**
	 * Test: dirty checks work for an entity on a non-default datasource.
	 */
	@DisplayName( "dirty checks work for an entity on a non-default datasource" )
	@Test
	public void testDirtyAlternateDatasource() {
		assertThat( run( """
		                 a = entityLoadByPK( "AlternateDS", "123e4567-e89b-12d3-a456-426614174000" );
		                 result = entityIsDirty( a );
		                 """ ) ).isEqualTo( false );
	}

	/* ----------------------------------------------------------------------------------------------------------- */
	/* ormIsSessionDirty / ormGetSessionStatistics / ormFlush( datasource ) */
	/* ----------------------------------------------------------------------------------------------------------- */

	/**
	 * Test: ormIsSessionDirty follows unsaved changes and flushes.
	 */
	@DisplayName( "ormIsSessionDirty follows unsaved changes and flushes" )
	@Test
	public void testSessionDirty() {
		assertThat( runRolledBack( """
		                           m = entityLoadByPK( "Manufacturer", 1 );
		                           before = ormIsSessionDirty();
		                           m.setName( "session dirty" );
		                           during = ormIsSessionDirty();
		                           ormFlush();
		                           after = ormIsSessionDirty();
		                           result = before & "|" & during & "|" & after;
		                           """ ) ).isEqualTo( "false|true|false" );
	}

	/**
	 * Test: ormIsSessionDirty for another datasource only sees that datasource's session.
	 */
	@DisplayName( "ormIsSessionDirty for another datasource only sees that datasource's session" )
	@Test
	public void testSessionDirtyPerDatasource() {
		assertThat( runRolledBack( """
		                           m = entityLoadByPK( "Manufacturer", 1 );
		                           m.setName( "only default is dirty" );
		                           result = ormIsSessionDirty() & "|" & ormIsSessionDirty( "dsn2" );
		                           """ ) ).isEqualTo( "true|false" );
	}

	/**
	 * Test: ormIsSessionDirty with an unknown datasource names the real ones.
	 */
	@DisplayName( "ormIsSessionDirty with an unknown datasource names the real ones" )
	@Test
	public void testSessionDirtyUnknownDatasource() {
		IStruct err = error( "ormIsSessionDirty( \"nope\" );" );
		assertThat( err.getAsString( Key.message ) ).contains( "nope" );
	}

	/**
	 * Test: ormGetSessionStatistics counts and names the loaded entities.
	 */
	@DisplayName( "ormGetSessionStatistics counts and names the loaded entities" )
	@Test
	public void testSessionStatistics() {
		IStruct stats = ( IStruct ) run( """
		                                 ormClearSession();
		                                 m = entityLoadByPK( "Manufacturer", 1 );
		                                 result = ormGetSessionStatistics();
		                                 """ );
		assertThat( ( Integer ) stats.get( Key.of( "entityCount" ) ) ).isAtLeast( 1 );
		assertThat( ( Array ) stats.get( Key.of( "entityKeys" ) ) ).contains( "Manufacturer#1" );
		assertThat( stats.containsKey( Key.of( "collectionCount" ) ) ).isTrue();
	}

	/**
	 * Test: ormGetSessionStatistics names collections as Entity.property#id.
	 */
	@DisplayName( "ormGetSessionStatistics names collections as Entity.property#id" )
	@Test
	public void testSessionStatisticsCollections() {
		IStruct stats = ( IStruct ) run( """
		                                 ormClearSession();
		                                 m = entityLoadByPK( "Manufacturer", 1 );
		                                 m.getVehicles().len();
		                                 result = ormGetSessionStatistics();
		                                 """ );
		assertThat( ( Array ) stats.get( Key.of( "collectionKeys" ) ) ).contains( "Manufacturer.vehicles#1" );
	}

	/**
	 * Test: ormGetSessionStatistics is empty after the session is cleared.
	 */
	@DisplayName( "ormGetSessionStatistics is empty after the session is cleared" )
	@Test
	public void testSessionStatisticsAfterClear() {
		assertThat( run( """
		                 m = entityLoadByPK( "Manufacturer", 1 );
		                 ormClearSession();
		                 result = ormGetSessionStatistics().entityCount;
		                 """ ) ).isEqualTo( 0 );
	}

	/**
	 * Test: ormGetSessionStatistics for another datasource.
	 */
	@DisplayName( "ormGetSessionStatistics for another datasource" )
	@Test
	public void testSessionStatisticsAlternateDatasource() {
		IStruct stats = ( IStruct ) run( """
		                                 a = entityLoadByPK( "AlternateDS", "123e4567-e89b-12d3-a456-426614174000" );
		                                 result = ormGetSessionStatistics( "dsn2" );
		                                 """ );
		assertThat( ( Array ) stats.get( Key.of( "entityKeys" ) ) ).contains( "AlternateDS#123e4567-e89b-12d3-a456-426614174000" );
	}

	/**
	 * Test: ormFlush( datasource ) flushes that datasource's session.
	 */
	@DisplayName( "ormFlush( datasource ) flushes that datasource's session" )
	@Test
	public void testFlushDatasource() {
		assertThat( runRolledBack( """
		                           a = entityLoadByPK( "AlternateDS", "123e4567-e89b-12d3-a456-426614174000" );
		                           a.setName( "flushed on dsn2" );
		                           before = ormIsSessionDirty( "dsn2" );
		                           ormFlush( "dsn2" );
		                           result = before & "|" & ormIsSessionDirty( "dsn2" );
		                           """ ) ).isEqualTo( "true|false" );
	}
}
