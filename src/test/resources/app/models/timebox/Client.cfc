component persistent="true" table="client" extends="BaseEntity" {

	/* *********************************************************************
	 **						DI
	 ********************************************************************* */

	property name="contactService" inject="ClientContactService" persistent="false";


	/* *********************************************************************
	 **						PROPERTIES
	 ********************************************************************* */

	property
		name="clientId"
		fieldtype="id"
		generator="uuid"
		ormtype="string"
		setter="false";

	property
		name="name"
		notnull="true"
		unique="true"
		length="255"
		default="";

	property
		name="description"
		notnull="false"
		default=""
		length="500"
		db_html="textarea";

	property
		name="contacts"
		singularName="contact"
		type="array"
		inverse="true"
		fieldtype="one-to-many"
		cfc="ClientContact"
		fkcolumn="FK_clientId"
		lazy="extra";

}