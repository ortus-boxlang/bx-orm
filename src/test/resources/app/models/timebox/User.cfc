/**
 * Copyright since 2020 by Ortus Solutions, Corp
 * www.ortussolutions.com
 * ---
 * A TimeBox Abstract User
 */
component
	persistent="true"
	table="user"
	extends="BaseEntity"
	discriminatorColumn="userType"
{

	/* *********************************************************************
	 **						DI
	 ********************************************************************* */

	property name="userService" inject="UserService" persistent="false";

	property name="avatar" inject="Avatar" persistent="false";

	/* *********************************************************************
	 **						NON-PERSISTED PROPERTIES
	 ********************************************************************* */

	property
		name="loggedIn"
		persistent="false"
		default="false"
		type="boolean";

	/* *********************************************************************
	 **						PROPERTIES
	 ********************************************************************* */

	property
		name="userId"
		fieldtype="id"
		generator="uuid"
		ormtype="string"
		setter="false";

	property
		name="userType"
		setter="false"
		length="50"
		update="false"
		insert="false"
		default="";

	property
		name="fname"
		notnull="true"
		length="100"
		default="";

	property
		name="lname"
		notnull="true"
		length="100"
		default="";

	property
		name="title"
		notnull="false"
		default=""
		length="255";

	property
		name="email"
		unique="true"
		notnull="true"
		db_display="false";

	property name="password" notnull="true" db_display="false";

	property
		name="lastLogin"
		notnull="false"
		ormtype="timestamp"
		db_display="false";

	property name="mobilePhone" length="100" notnull="false";

	property name="homePhone" length="100" notnull="false";

	property
		name="dob"
		type="date"
		ormtype="date"
		sqlType="date"
		notnull="false";

	property
		name="tshirtSize"
		length="100"
		notnull="false"
		default="L";

	property
		name="preferences"
		ormtype="text"
		notnull="false"
		default="";

	property name="facebookURL" notnull="false" db_display="false";

	property name="twitterURL" notnull="false" db_display="false";

	property name="blogURL" notnull="false" db_display="false";

	property name="linkedinURL" notnull="false" db_display="false";

	property name="githubURL" notnull="false" db_display="false";

	property
		name="isPasswordReset"
		ormtype="boolean"
		sqltype="boolean"
		notnull="true"
		default="false"
		dbdefault="false";

	property
		name="biography"
		ormtype="text"
		notnull="false"
		default="";

	property
		name="address"
		ormtype="string"
		notnull="false"
		length="255"
		default="";

	property
		name="city"
		ormtype="string"
		notnull="false"
		length="75"
		dbdefault="";

	property
		name="stateOrProvince"
		ormtype="string"
		notnull="false"
		length="255"
		dbdefault="";

	property
		name="postalCode"
		ormtype="string"
		notnull="false"
		length="255"
		dbdefault="";

	property
		name="country"
		ormtype="string"
		notnull="true"
		length="75"
		default=""
		dbdefault="";

	property
		name="timeZone"
		ormtype="string"
		notnull="true"
		default="GMT-6:00"
		length="150";

	property
		name="avatarLink"
		formula="select( concat( '//www.gravatar.com/avatar.php?gravatar_id=',lcase( MD5( email ) ),'&s=40&r=PG' ) )"
		ormtype="string"
		default="";

	/* *********************************************************************
	 **						RELATIONSHIPS
	 ********************************************************************* */

	// M20 -> Manager
	property
		name="manager"
		notnull="false"
		fieldtype="many-to-one"
		cfc="User"
		fkcolumn="FK_managerId"
		lazy="true";


	/* *********************************************************************
	 **						CALCULATED PROPERTIES
	 ********************************************************************* */


	/* *********************************************************************
	 **						PK + CONSTRAINTS + MEMENTO
	 ********************************************************************* */


}
