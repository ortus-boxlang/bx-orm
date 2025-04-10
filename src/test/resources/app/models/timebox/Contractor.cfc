/**
 * Copyright since 2020 by Ortus Solutions, Corp
 * www.ortussolutions.com
 * ---
 * A TimeBox Contractor
 */
component
	persistent="true"
	table="contractor"
	extends="User"
	joinColumn="userId"
	discriminatorValue="contractor"
{

	/* *********************************************************************
	 **							PROPERTIES
	 ********************************************************************* */

	property
		name="startDate"
		type="date"
		ormtype="date"
		sqlType="date"
		notnull="false";

	property
		name="endDate"
		type="date"
		ormtype="date"
		sqlType="date"
		notnull="false";

	property
		name="costRate"
		notnull="false"
		ormtype="big_decimal"
		scale="4"
		default="0.00";

	property
		name="fixedFee"
		notnull="false"
		ormtype="big_decimal"
		scale="4"
		default="0.00";

	property
		name="businessName"
		length="255"
		notnull="false"
		default="";

	property
		name="contractorType"
		length="100"
		notnull="false"
		default="individual";

	property
		name="hasPayroll"
		ormtype="boolean"
		sqlType="boolean"
		dbdefault="false"
		default="false"
		notnull="true";

	property
		name="taxId"
		length="255"
		notnull="false"
		default="";

	property
		name="compensationType"
		notnull="true"
		length="50"
		default="fixed"
		db_html="textarea";

	/* *********************************************************************
	 **						RELATIONSHIPS
	 ********************************************************************* */

	/* *********************************************************************
	 **							FUNCTIONS
	 ********************************************************************* */



}
