/**
 * This is the base class for all persistent entities
 */
component mappedsuperclass="true" accessors="true" {

	/* *********************************************************************
	 **							PROPERTIES
	 ********************************************************************* */

	property
		name="createdDate"
		type="date"
		ormtype="timestamp"
		notnull="true"
		update="false";

	property
		name="updatedDate"
		type="date"
		ormtype="timestamp"
		notnull="true";

	property
		name="isActive"
		ormtype="boolean"
		sqltype="boolean"
		default="true"
		dbdefault="true"
		notnull="true";


}
