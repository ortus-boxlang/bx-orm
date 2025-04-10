/**
 * Copyright since 2020 by Ortus Solutions, Corp
 * www.ortussolutions.com
 * ---
 * A TimeBox Employee
 */
component
	persistent="true"
	table="employee"
	extends="User"
	joinColumn="userId"
	discriminatorValue="employee"
{

	property name="log" inject="logbox:logger:{this}" persistent="false";

	property name="employeeService" inject="EmployeeService" persistent="false";

	/* *********************************************************************
	 **							PROPERTIES
	 ********************************************************************* */

	property
		name="startDate"
		type="date"
		ormtype="date"
		sqlType="date"
		notnull="true";

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
		name="baseHours"
		notnull="true"
		ormtype="integer"
		default="40"
		dbdefault="40";

	property
		name="salary"
		notnull="false"
		ormtype="big_decimal"
		scale="4"
		default="0.00";

	property
		name="sickTimePerYear"
		notnull="true"
		ormtype="big_decimal"
		scale="4"
		default="0"
		dbdefault="0";

	property
		name="ptoPerYear"
		notnull="true"
		ormtype="big_decimal"
		scale="4"
		default="0"
		dbdefault="0";

	property
		name="hasPayroll"
		ormtype="boolean"
		sqlType="boolean"
		dbdefault="false"
		default="false"
		notnull="true";

	property
		name="hasTimeOff"
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

	/* *********************************************************************
	 **						RELATIONSHIPS
	 ********************************************************************* */

	// O2M -> Employee Time Off Requests
	property
		name="timeOffRequests"
		singularName="timeOffRequest"
		type="array"
		fieldtype="one-to-many"
		cfc="TimeOff"
		fkcolumn="FK_userId"
		inverse="true"
		lazy="extra"
		cascade="delete-orphan"
		batchsize="10"
		orderby="startDate DESC";

	property
		name="compensationType"
		notnull="true"
		length="50"
		default="salary"
		db_html="textarea";

	/* *********************************************************************
	 **							CALCULATED FIELDS
	 ********************************************************************* */

	property
		name="numberOfPendingTimeOffRequests"
		formula="select count(*) from timeOff where timeOff.FK_userId=userId and timeOff.status = 'pending'";


	/* *********************************************************************
	 **							FUNCTIONS
	 ********************************************************************* */

	/**
	 * Constructor
	 */
	function init() {
		// Type
		variables.userType = "employee";
		variables.costRate = 0;
		variables.salary = 0;
		variables.sickTimePerYear = 0;
		variables.ptoPerYear = 0;
		variables.hasPayroll = false;
		variables.hasTimeOff = false;
		variables.baseHours = 30;
		variables.compensationType = "salary";

		appendToMemento( [
			"startDate",
			"endDate",
			"costRate",
			"baseHours",
			"salary",
			"sickTimePerYear",
			"ptoPerYear",
			"hasPayroll",
			"hasTimeOff",
			"ptoBalance",
			"sickTimeBalance",
			"taxId",
			"numberOfPendingTimeOffRequests",
			"compensationType"
		] );

		// Validation Constraints
		this.constraints.append( {
			"startDate": { required: false, type: "date" },
			"endDate": { required: false, type: "date" },
			"costRate": { required: false, type: "float" },
			"salary": { required: false, type: "float" },
			"sickTimePerYear": { required: false, type: "float" },
			"ptoPerYear": { required: false, type: "float" },
			"hasPayroll": { required: true, type: "boolean" },
			"hasTimeOff": { required: true, type: "boolean" },
			"compensationType": { required: true, regex: "(salary|hourly)" }
		} );

		return super.init();
	}

	/**
	 * Get the PTO balance
	 *
	 * @result number of the remaining hours (PTOPerYear)
	 */
	numeric function getPtoBalance() {
		log.info( "ptobalance at #now()# - #getTickCount()#" );
		return variables.employeeService.getPtoBalance( this );
	}

	/**
	 * Get the Sick time balance
	 *
	 * @result number of the remaining hours (SickTimePerYear)
	 */
	numeric function getSickTimeBalance() {
		log.info( "sicktimebalance at #now()# - #getTickCount()#" );
		return variables.employeeService.getSickTimeBalance( this );
	}

}
