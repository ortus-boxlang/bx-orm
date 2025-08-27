/**
 * This is the base class for all note types
 */
component mappedsuperclass="true" accessors="true" extends="BaseEntity" {

    property
        name="noteId"
        fieldtype="id"
        generator="uuid"
        ormtype="string"
        setter="false";

    property
        name="notes"
        ormtype="text"
        notnull="true"
        default="";

	property
		name="employee"
		notnull="true"
		fieldtype="many-to-one"
		cfc="Employee"
		fkcolumn="FK_userId"
		lazy="true";
}