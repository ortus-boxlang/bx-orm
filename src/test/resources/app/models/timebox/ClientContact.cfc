component
	persistent="true"
	table="clientContact"
	extends="User"
	joinColumn="userId"
	discriminatorValue="clientContact"
{

	property
		name="isPrimaryContact"
		ormtype="boolean"
		sqltype="boolean"
		default="false"
		dbdefault="false"
		notnull="true";

	property
		name="Client"
		notnull="true"
		fieldtype="many-to-one"
		cfc="Client"
		fkcolumn="FK_clientId"
		lazy="true";
}