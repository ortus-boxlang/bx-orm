/**
 * A mapped-superclass whose properties are merged into the concrete entity's own table (no table of its own).
 */
component mappedsuperclass="true" accessors="true" {
	property name="createdDate" type="date" ormtype="timestamp" notnull="true" update="false";
	property name="updatedDate" type="date" ormtype="timestamp" notnull="true";
	property name="isActive" ormtype="boolean" sqltype="boolean" default="true" dbdefault="true" notnull="true";
}
