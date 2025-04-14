/**
 * Copyright since 2020 by Ortus Solutions, Corp
 * www.ortussolutions.com
 * ---
 * A TimeBox TimeOff representation for users
 */
component persistent="true" table="timeOff" extends="BaseEntity" {

	/* *********************************************************************
	 **						DI
	 ********************************************************************* */

	/* *********************************************************************
	 **						NON-PERSISTED PROPERTIES
	 ********************************************************************* */

	/* *********************************************************************
	 **						PROPERTIES
	 ********************************************************************* */

	property
		name="timeOffId"
		fieldtype="id"
		generator="uuid"
		ormtype="string"
		setter="false";

	property
		name="requestType"
		notnull="true"
		default="vacation"
		length="100"
		db_html="textarea";

	property
		name="startDate"
		type="date"
		ormtype="date"
		sqlType="date"
		notnull="true"
		update="true";

	property
		name="endDate"
		type="date"
		ormtype="date"
		sqlType="date"
		notnull="true"
		update="false";

	property
		name="hours"
		notnull="true"
		ormtype="big_decimal"
		scale="4"
		default="0.00";

	// Time Preference type: "hourly" or "daily"
	property
		name="timePreference"
		notnull="false"
		ormtype="string"
		default="hourly";

	property
		name="employeeNote"
		notnull="false"
		default=""
		length="500"
		db_html="textarea";

	property
		name="employerNote"
		notnull="false"
		default=""
		length="500"
		db_html="textarea";

	property
		name="status"
		default="pending"
		notnull="true"
		index="idx_timeOffStatus";

	/* *********************************************************************
	 **						RELATIONSHIPS
	 ********************************************************************* */

	// M20 -> Employee
	property
		name="employee"
		notnull="true"
		fieldtype="many-to-one"
		cfc="Employee"
		fkcolumn="FK_userId"
		lazy="true"
		db_displayColumns="email";

	// M20 -> Approved By
	property
		name="approver"
		notnull="false"
		fieldtype="many-to-one"
		cfc="Employee"
		fkcolumn="FK_approverId"
		lazy="true"
		db_displayColumns="email";

	/* *********************************************************************
	 **						CALCULATED PROPERTIES
	 ********************************************************************* */


	/* *********************************************************************
	 **						PK + CONSTRAINTS + MEMENTO
	 ********************************************************************* */


}
