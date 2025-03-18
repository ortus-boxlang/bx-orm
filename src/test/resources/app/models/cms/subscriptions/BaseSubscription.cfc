/**
 * I am a non-discriminated parent class
 */
component
	persistent="true"
	entityname="cbSubscription"
	table     ="cb_subscriptions"
	extends   ="root.models.cms.BaseEntityMethods"
	cachename ="cbSubscription"
	cacheuse  ="read-write"
{

	property
		name   ="createdDate"
		column ="createdDate"
		type   ="date"
		ormtype="timestamp"
		notnull="true"
		update ="false";

	property
		name   ="modifiedDate"
		column ="modifiedDate"
		type   ="date"
		ormtype="timestamp"
		notnull="true";

	property
		name   ="isDeleted"
		column ="isDeleted"
		ormtype="boolean"
		notnull="true"
		default="false";

	/* *********************************************************************
	 **                          PROPERTIES
	 ********************************************************************* */

	property
		name     ="subscriptionID"
		column   ="subscriptionID"
		fieldtype="id"
		generator="uuid"
		length   ="36"
		ormtype  ="string"
		update   ="false";

	/**
	 * This token identifies subscribers (emails) to appropriate subscriptions
	 */
	property
		name   ="subscriptionToken"
		column ="subscriptionToken"
		ormtype="string"
		length ="255"
		notnull="true";

	property
		name   ="type"
		column ="type"
		ormtype="string"
		notnull="true";

}
